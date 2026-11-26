package org.sequeless.query.postgres;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.sequeless.filter.sql.api.SqlFragment;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.query.api.Audit;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.BrowseResult;
import org.sequeless.query.api.FacetBucket;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.TypeRef;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.spi.BrowsePort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The PostgreSQL {@link BrowsePort} over {@code sq_object} (spec 14, sections 6.1 and 6.4).
 *
 * <p>A browse runs three kinds of statement: a count of the matching objects, the page of items in result order, and one
 * bucket query per facet. Each takes the same candidates and the same translated filter, built by {@link BrowseSql}.
 * The shapes this port does not evaluate are rejected first, by {@link BrowseValidation}, and every SQL fragment is built
 * before the first statement runs, so a key this port cannot express (a GEO, BINARY or UNKNOWN Field) fails with no query.
 *
 * <p>Result order is the one {@code BrowsePort} states. Ids compare as {@code uuid}, which Postgres orders by unsigned
 * bytes, so a sort on an id matches the contract. Soft-deleted objects are never read, and the tenant is the scope's.
 * Text search matches {@code search_vector}, which the trigger fills from every property the tenant registers as searchable,
 * whatever class it belongs to (spec 14, Q22 and Q29). That is a known difference from the in-memory reference, which reads
 * only the root's own searchable Fields. {@code PostgresTextSearchKnownLimitIT} pins it.
 *
 * <p>The count, the page and the facet buckets run in one read-only transaction at REPEATABLE READ, so they read one
 * snapshot and the total agrees with the items. The browse never writes.
 */
public final class PostgresBrowsePort implements BrowsePort {

    private static final String ITEM_COLUMNS =
            "id, tenant_id, type_iri, version, state, props, created_at, created_by, "
                    + "updated_at, updated_by, deleted_at";

    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactionTemplate;

    /**
     * @param jdbcClient the client that runs the statements; must not be {@code null}
     * @param transactionManager the manager of the read-only transaction the statements share; must not be {@code null},
     *     and must manage the same DataSource as {@code jdbcClient}
     */
    public PostgresBrowsePort(JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setReadOnly(true);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override
    public BrowseResult browse(FieldScope scope, BrowseQuery query) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(query, "query must not be null");
        BrowseValidation.rejectUnsupportedShapes(query);
        if (scope.concreteTypes().isEmpty()) {
            return new BrowseResult(List.of(), 0, emptyFacets(query.facets()));
        }

        // Every fragment that can throw is built here, outside the transaction and before its first statement.
        BrowseSql sql = new BrowseSql(scope, query);
        SqlFragment matching = sql.matching();
        SqlFragment order = sql.orderBy();
        Map<FieldPath, SqlFragment> bucketQueries = new LinkedHashMap<>();
        for (FieldPath facet : query.facets()) {
            bucketQueries.put(facet, sql.bucketsOf(facet));
        }
        return transactionTemplate.execute(status -> read(query, matching, order, bucketQueries));
    }

    private BrowseResult read(
            BrowseQuery query, SqlFragment matching, SqlFragment order, Map<FieldPath, SqlFragment> bucketQueries) {
        long total = jdbcClient
                .sql("SELECT count(*) FROM sq_object WHERE " + matching.sql())
                .params(matching.parameters())
                .query(Long.class)
                .single();

        List<Object> itemParameters = new ArrayList<>(matching.parameters());
        itemParameters.addAll(order.parameters());
        itemParameters.add(query.page().size());
        itemParameters.add((long) query.page().number() * query.page().size());
        List<ObjectView> items = jdbcClient
                .sql("SELECT " + ITEM_COLUMNS + " FROM sq_object WHERE " + matching.sql() + " ORDER BY " + order.sql()
                        + " LIMIT ? OFFSET ?")
                .params(itemParameters)
                .query(PostgresBrowsePort::toView)
                .list();

        Map<FieldPath, List<FacetBucket>> facets = new LinkedHashMap<>();
        bucketQueries.forEach((facet, buckets) -> facets.put(
                facet,
                jdbcClient
                        .sql(buckets.sql())
                        .params(buckets.parameters())
                        .query(PostgresBrowsePort::toBucket)
                        .list()));
        return new BrowseResult(items, total, facets);
    }

    private static Map<FieldPath, List<FacetBucket>> emptyFacets(List<FieldPath> facets) {
        Map<FieldPath, List<FacetBucket>> result = new LinkedHashMap<>();
        facets.forEach(facet -> result.put(facet, List.of()));
        return result;
    }

    private static ObjectView toView(ResultSet row, int rowNum) throws SQLException {
        Audit audit = new Audit(
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getString("created_by"),
                row.getObject("updated_at", OffsetDateTime.class).toInstant(),
                row.getString("updated_by"));
        return new ObjectView(
                new ObjectId(row.getObject("id", UUID.class)),
                new TypeRef(row.getString("type_iri")),
                new TenantId(row.getString("tenant_id")),
                row.getLong("version"),
                Optional.ofNullable(row.getString("state")),
                ValueJsonDecoder.decode(row.getString("props")),
                audit,
                row.getObject("deleted_at") != null);
    }

    private static FacetBucket toBucket(ResultSet row, int rowNum) throws SQLException {
        return new FacetBucket(row.getString("bucket_value"), row.getLong("bucket_count"));
    }
}
