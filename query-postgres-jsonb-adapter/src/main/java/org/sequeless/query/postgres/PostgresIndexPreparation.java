package org.sequeless.query.postgres;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.query.api.FieldKey;
import org.sequeless.query.api.IndexPreparationResult;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.spi.IndexPreparationPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The PostgreSQL {@link IndexPreparationPort}.
 *
 * <p>Only root Fields are prepared: a Field whose path has one segment. A traversal Field is never indexed or
 * registered. Of each root Field:
 *
 * <ul>
 *   <li>a Field that is {@code facetable} or {@code sortable}, and whose expression is IMMUTABLE
 *       ({@link FieldExpressions#indexable}), gets an expression index over {@code sq_object} (the expression is
 *       {@link FieldExpressions#sqlOf}). It is created with {@code CREATE INDEX IF NOT EXISTS} and recorded in {@code
 *       sq_index_registry}, once per tenant and property. A {@code DATE} or {@code DATETIME} Field gets none, because
 *       its cast is STABLE and PostgreSQL refuses it in an index: a query on it still works, without the index;
 *   <li>a Field that is {@code searchable} is registered in {@code sq_searchable_property} once per tenant and
 *       property. This is the only thing that makes it newly registered, and the result lists it. Its type does not
 *       matter, because search is by capability (#28);
 *   <li>a Field that is {@code filterable} alone gets nothing.
 * </ul>
 *
 * <p>A searchable {@code DATE} or {@code DATETIME} Field is registered for search and listed, as any searchable Field is.
 * The V6 text extraction ({@code sq_extract_searchable_text}) reads only the JSON keys {@code text} and {@code list}, so
 * such a registration adds no search text; it only triggers the backfill.
 *
 * <p>A type with no expression ({@code GEO}, {@code BINARY}, {@code UNKNOWN}) gets no expression index, and the call
 * does not fail. A query that sorts, facets or filters on it is rejected at query time instead. A {@code DATE} or
 * {@code DATETIME} Field also gets no expression index, and the call does not fail, but its queries still run, only
 * unindexed. That is a deviation from the capability policy of #28, which PostgreSQL forces.
 *
 * <p>Two root classes that share a property share one key, one index and one registration. Which Field's type and
 * path represent the key is decided by slug path, then id path, so the result does not depend on the order of
 * {@code rootFields}.
 *
 * <p>All the writes of one call run in one transaction from {@code transactionManager}. Index names are
 * {@code sq_idx_} plus the first 16 hex characters of the MD5 of {@code tenant + "|" + property IRI}, as the prototype
 * names them, so that indexes created by the prototype keep their names at cut-over.
 */
public final class PostgresIndexPreparation implements IndexPreparationPort {

    private static final Comparator<Field> TIE_BREAK = Comparator.comparing(
                    (Field field) -> field.path().asSlugPath())
            .thenComparing(field -> field.path().asIdPath().toString());

    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactionTemplate;

    /**
     * @param jdbcClient the client that runs the statements; must not be {@code null}
     * @param transactionManager the transaction manager for the writes of one call; must not be {@code null}
     */
    public PostgresIndexPreparation(JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public IndexPreparationResult prepare(
            TenantId tenant, OntologyVersionRef version, Map<ClassRef, List<Field>> rootFields) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(rootFields, "rootFields must not be null");

        // Keyed by the registration key, which is the IRI of a root Field; TreeMap keeps the key order.
        Map<String, Field> indexed = new TreeMap<>();
        Map<String, Field> searchable = new TreeMap<>();
        for (Map.Entry<ClassRef, List<Field>> entry : rootFields.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "rootFields must not have a null key");
            Objects.requireNonNull(entry.getValue(), "rootFields must not have a null value");
            for (Field field : entry.getValue()) {
                Objects.requireNonNull(field, "rootFields must not contain a null Field");
                if (field.path().isTraversing()) {
                    continue;
                }
                String key = FieldKey.encode(field.path());
                Capabilities capabilities = field.capabilities();
                if ((capabilities.facetable() || capabilities.sortable()) && FieldExpressions.indexable(field.type())) {
                    indexed.merge(key, field, PostgresIndexPreparation::preferred);
                }
                if (capabilities.searchable()) {
                    searchable.merge(key, field, PostgresIndexPreparation::preferred);
                }
            }
        }

        String tenantId = tenant.value();
        List<FieldPath> newlyRegistered = transactionTemplate.execute(status -> {
            indexed.values().forEach(field -> ensureExpressionIndex(tenantId, field));
            List<FieldPath> registered = new ArrayList<>();
            searchable.forEach((key, field) -> {
                if (registerSearchable(tenantId, key)) {
                    registered.add(field.path());
                }
            });
            return registered;
        });
        return new IndexPreparationResult(newlyRegistered);
    }

    private void ensureExpressionIndex(String tenantId, Field field) {
        String propertyIri = field.path().segments().get(0).iri();
        Long registered = jdbcClient
                .sql(
                        "SELECT count(*) FROM sq_index_registry WHERE tenant_id = :tenantId AND property_iri = :propertyIri")
                .param("tenantId", tenantId)
                .param("propertyIri", propertyIri)
                .query(Long.class)
                .single();
        if (registered > 0) {
            return;
        }
        String indexName = indexNameFor(tenantId, propertyIri);
        jdbcClient
                .sql("CREATE INDEX IF NOT EXISTS " + indexName + " ON sq_object ((" + FieldExpressions.sqlOf(field)
                        + "))")
                .update();
        jdbcClient
                .sql("INSERT INTO sq_index_registry (tenant_id, property_iri, index_name, created_at) "
                        + "VALUES (:tenantId, :propertyIri, :indexName, :createdAt) "
                        + "ON CONFLICT (tenant_id, property_iri) DO NOTHING")
                .param("tenantId", tenantId)
                .param("propertyIri", propertyIri)
                .param("indexName", indexName)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    /** @return {@code true} iff this call inserted the registration; {@code false} when it was already there */
    private boolean registerSearchable(String tenantId, String propertyIri) {
        int inserted = jdbcClient
                .sql("INSERT INTO sq_searchable_property (tenant_id, property_iri) "
                        + "VALUES (:tenantId, :propertyIri) ON CONFLICT DO NOTHING")
                .param("tenantId", tenantId)
                .param("propertyIri", propertyIri)
                .update();
        return inserted == 1;
    }

    /**
     * The {@code CREATE INDEX} name for a tenant's property: {@code sq_idx_} and the first 16 hex characters of the MD5
     * of {@code tenantId|propertyIri}. Package-private, so the tests pin the scheme.
     *
     * <p>The MD5 is a derived identifier, not a security control. The names must match the prototype's existing indexes
     * until the prototype cut-over, so the scheme stays as it is. The input is not
     * secret, and the digest is not used for a password, a credential or an integrity check.
     */
    static String indexNameFor(String tenantId, String propertyIri) {
        try {
            // Deterministic index identifier, not a secret: the name must match the prototype's indexes
            // until the prototype cut-over.
            MessageDigest md5 = MessageDigest.getInstance("MD5"); // NOSONAR java:S4790
            byte[] digest = md5.digest((tenantId + "|" + propertyIri).getBytes(StandardCharsets.UTF_8));
            return "sq_idx_" + HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 must be available", e);
        }
    }

    private static Field preferred(Field first, Field second) {
        return TIE_BREAK.compare(first, second) <= 0 ? first : second;
    }
}
