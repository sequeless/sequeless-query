package org.sequeless.query.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sequeless.query.postgres.TestFields.direct;
import static org.sequeless.query.postgres.TestFields.traversal;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.query.api.IndexPreparationResult;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.LocalizedText;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What the adapter leaves in the database: the expression indexes, the registries and the index names. The
 * query-testkit contract checks the result of a call and does not look at these.
 */
class PostgresIndexPreparationIT extends AbstractPostgresIT {

    private static final OntologyVersionRef V1 = new OntologyVersionRef(1);
    private static final TenantId TENANT = new TenantId("tenant-1");
    private static final TenantId OTHER_TENANT = new TenantId("tenant-2");
    private static final ClassRef TASK = new ClassRef(
            UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
            "task",
            "urn:ex:Task",
            new LocalizedText("Task", "en"));
    private static final ClassRef PROJECT = new ClassRef(
            UUID.fromString("00000000-0000-0000-0000-0000000000a2"),
            "project",
            "urn:ex:Project",
            new LocalizedText("Project", "en"));

    private static final String STATUS = "urn:ex:status";
    private static final String PRIORITY = "urn:ex:priority";
    private static final String DUE_DATE = "urn:ex:dueDate";
    private static final String ASSIGNED_TO = "urn:ex:assignedTo";
    private static final String TITLE = "urn:ex:title";
    private static final String EMAIL = "urn:ex:email";

    private static final Capabilities FACET_SORT = new Capabilities(true, true, false, true);
    private static final Capabilities SORT = new Capabilities(true, false, false, true);
    private static final Capabilities SEARCH = new Capabilities(true, false, true, false);
    private static final Capabilities FILTER = new Capabilities(true, false, false, false);

    @Test
    void facetableAndSortableFieldsGetAnExpressionIndexWithTheWriterKeyAndARegistration() {
        port().prepare(
                        TENANT,
                        V1,
                        Map.of(
                                TASK,
                                List.of(
                                        direct(STATUS, FieldType.STRING, FACET_SORT),
                                        direct(PRIORITY, FieldType.INTEGER, SORT),
                                        direct(DUE_DATE, FieldType.DATE, SORT),
                                        direct(ASSIGNED_TO, FieldType.OBJECT_REF, FACET_SORT))));

        // A DATE Field has no expression index: its cast is STABLE (see FieldExpressions.indexable).
        assertThat(indexRegistrations()).containsExactlyInAnyOrder(STATUS, PRIORITY, ASSIGNED_TO);

        assertThat(indexDefinition(indexFor(STATUS)))
                .contains("'urn:ex:status'")
                .contains("'text'");
        assertThat(indexDefinition(indexFor(PRIORITY)))
                .contains("'urn:ex:priority'")
                .contains("'integer'")
                .contains("::bigint");
        assertThat(indexDefinition(indexFor(ASSIGNED_TO))).contains("'ref'").contains("::uuid");
    }

    @Test
    void aDateFieldIsPreparedWithoutAnIndexAndTheCallSucceeds() {
        Field dueDate = direct(DUE_DATE, FieldType.DATE, SORT);

        assertThatCode(() -> port().prepare(TENANT, V1, Map.of(TASK, List.of(dueDate))))
                .doesNotThrowAnyException();

        assertThat(indexRegistrations()).isEmpty();
        assertThat(indexNamesInDatabase()).isEmpty();
    }

    @Test
    void theWhereExpressionOfAFieldUsesItsExpressionIndex() {
        port().prepare(
                        TENANT,
                        V1,
                        Map.of(
                                TASK,
                                List.of(
                                        direct(STATUS, FieldType.STRING, FACET_SORT),
                                        direct(PRIORITY, FieldType.INTEGER, SORT))));

        assertThat(plan(FieldExpressions.sqlOf(direct(PRIORITY, FieldType.INTEGER, SORT)) + " > 3"))
                .contains(indexFor(PRIORITY));
        assertThat(plan(FieldExpressions.sqlOf(direct(STATUS, FieldType.STRING, FACET_SORT)) + " = 'open'"))
                .contains(indexFor(STATUS));
    }

    @Test
    void anIriWithASingleQuoteIsIndexedAndQueriedAsOneLiteral() {
        String quoted = "urn:ex:o'brien";
        Field field = direct(quoted, FieldType.STRING, FACET_SORT);

        port().prepare(TENANT, V1, Map.of(TASK, List.of(field)));

        assertThat(indexFor(quoted)).isNotBlank();
        assertThat(plan(FieldExpressions.sqlOf(field) + " = 'x'")).contains(indexFor(quoted));
    }

    @Test
    void searchableFieldsAreRegisteredWithoutAnIndexAndFilterableAloneGetsNothing() {
        Field title = direct(TITLE, FieldType.STRING, SEARCH);

        IndexPreparationResult result =
                port().prepare(TENANT, V1, Map.of(TASK, List.of(title, direct(EMAIL, FieldType.STRING, FILTER))));

        assertThat(result.newlyRegistered()).containsExactly(title.path());
        assertThat(searchRegistrations()).containsExactly(TITLE);
        assertThat(indexRegistrations()).isEmpty();
        assertThat(indexNamesInDatabase()).isEmpty();
    }

    @Test
    void indexOnlyFieldsAreIndexedButNotListedInTheResult() {
        IndexPreparationResult result = port().prepare(
                        TENANT,
                        V1,
                        Map.of(
                                TASK,
                                List.of(
                                        direct(STATUS, FieldType.STRING, FACET_SORT),
                                        direct(PRIORITY, FieldType.INTEGER, SORT))));

        assertThat(result.newlyRegistered()).isEmpty();
        assertThat(indexRegistrations()).containsExactlyInAnyOrder(STATUS, PRIORITY);
        assertThat(searchRegistrations()).isEmpty();
    }

    @Test
    void aSearchableDateFieldIsListedOnceAndGetsNoIndex() {
        Field dueDate = direct(DUE_DATE, FieldType.DATE, SEARCH);

        IndexPreparationResult result =
                port().prepare(TENANT, V1, Map.of(TASK, List.of(dueDate), PROJECT, List.of(dueDate)));

        assertThat(result.newlyRegistered()).containsExactly(dueDate.path());
        assertThat(searchRegistrations()).containsExactly(DUE_DATE);
        assertThat(indexRegistrations()).isEmpty();
        // The same roots again register nothing new.
        assertThat(port().prepare(TENANT, V1, Map.of(TASK, List.of(dueDate), PROJECT, List.of(dueDate)))
                        .newlyRegistered())
                .isEmpty();
    }

    @Test
    void aTraversalFieldIsNeitherIndexedNorRegistered() {
        port().prepare(TENANT, V1, Map.of(TASK, List.of(traversal("urn:ex:project", "urn:ex:status"))));

        assertThat(indexRegistrations()).isEmpty();
        assertThat(searchRegistrations()).isEmpty();
    }

    @Test
    void aTypeWithNoExpressionGetsNoIndexAndDoesNotFailThePreparation() {
        Field where = direct("urn:ex:where", FieldType.GEO, FACET_SORT);

        assertThatCode(() -> port().prepare(TENANT, V1, Map.of(TASK, List.of(where))))
                .doesNotThrowAnyException();

        assertThat(indexRegistrations()).isEmpty();
    }

    @Test
    void aRepeatCallWithTheSameVersionCreatesNothingNewInTheDatabase() {
        Map<ClassRef, List<Field>> roots = Map.of(
                TASK, List.of(direct(STATUS, FieldType.STRING, FACET_SORT), direct(TITLE, FieldType.STRING, SEARCH)));
        port().prepare(TENANT, V1, roots);
        Set<String> indexesBefore = indexNamesInDatabase();
        Set<String> registrationsBefore = indexRegistrations();

        IndexPreparationResult repeat = port().prepare(TENANT, V1, roots);

        assertThat(repeat.newlyRegistered()).isEmpty();
        assertThat(indexNamesInDatabase()).isEqualTo(indexesBefore);
        assertThat(indexRegistrations()).isEqualTo(registrationsBefore);
    }

    @Test
    void eachTenantHasItsOwnRegistrationAndItsOwnIndex() {
        Map<ClassRef, List<Field>> roots = Map.of(TASK, List.of(direct(STATUS, FieldType.STRING, FACET_SORT)));

        port().prepare(TENANT, V1, roots);
        port().prepare(OTHER_TENANT, V1, roots);

        Set<String> names = jdbc().sql("SELECT index_name FROM sq_index_registry WHERE property_iri = :iri")
                .param("iri", STATUS)
                .query(String.class)
                .set();
        assertThat(names)
                .containsExactlyInAnyOrder(
                        PostgresIndexPreparation.indexNameFor("tenant-1", STATUS),
                        PostgresIndexPreparation.indexNameFor("tenant-2", STATUS));
    }

    @Test
    void theIndexNameIsThePrototypeSchemeOfTheTenantAndTheIri() {
        // Pinned values: the MD5 of "<tenant>|<iri>", first 16 hex characters, as the prototype names them.
        assertThat(PostgresIndexPreparation.indexNameFor("tenant-1", STATUS)).isEqualTo("sq_idx_b162858e46116da9");
        assertThat(PostgresIndexPreparation.indexNameFor("query-testkit", "https://example.org/query-testkit#title"))
                .isEqualTo("sq_idx_49c7e8886512c951");
    }

    @Test
    void aFailedRegistryInsertRollsBackTheIndexesOfTheWholeCall() {
        // "urn:ex:priority" sorts before "urn:ex:status", so its index is created first, in the same transaction.
        // The registry insert for STATUS then fails, and the whole call must roll back: no sq_idx_ index may remain.
        Map<ClassRef, List<Field>> roots = Map.of(
                TASK, List.of(direct(STATUS, FieldType.STRING, FACET_SORT), direct(PRIORITY, FieldType.INTEGER, SORT)));
        try {
            failRegistryInsertFor(STATUS);

            assertThatThrownBy(() -> port().prepare(TENANT, V1, roots))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("test-only registry insert failure");
        } finally {
            dropRegistryInsertFailure();
        }

        assertThat(indexNamesInDatabase()).isEmpty();
        assertThat(indexRegistrations()).isEmpty();
        assertThat(searchRegistrations()).isEmpty();
    }

    private static PostgresIndexPreparation port() {
        return new PostgresIndexPreparation(jdbc(), new DataSourceTransactionManager(dataSource));
    }

    /** Installs a test-only trigger that makes the registry insert for {@code propertyIri} fail. */
    private static void failRegistryInsertFor(String propertyIri) {
        jdbc().sql("CREATE OR REPLACE FUNCTION sq_test_fail_registry_insert() RETURNS trigger LANGUAGE plpgsql AS $$ "
                        + "BEGIN RAISE EXCEPTION 'test-only registry insert failure'; END; $$")
                .update();
        jdbc().sql("CREATE TRIGGER sq_test_fail_registry_insert BEFORE INSERT ON sq_index_registry "
                        + "FOR EACH ROW WHEN (NEW.property_iri = " + FieldExpressions.literal(propertyIri) + ") "
                        + "EXECUTE FUNCTION sq_test_fail_registry_insert()")
                .update();
    }

    /** Removes the trigger and the function installed by {@link #failRegistryInsertFor}. */
    private static void dropRegistryInsertFailure() {
        jdbc().sql("DROP TRIGGER IF EXISTS sq_test_fail_registry_insert ON sq_index_registry")
                .update();
        jdbc().sql("DROP FUNCTION IF EXISTS sq_test_fail_registry_insert()").update();
    }

    private static JdbcClient jdbc() {
        return JdbcClient.create(dataSource);
    }

    private static String indexFor(String propertyIri) {
        return jdbc().sql(
                        "SELECT index_name FROM sq_index_registry WHERE tenant_id = 'tenant-1' AND property_iri = :iri")
                .param("iri", propertyIri)
                .query(String.class)
                .single();
    }

    private static String indexDefinition(String indexName) {
        return jdbc().sql("SELECT indexdef FROM pg_indexes WHERE indexname = :name")
                .param("name", indexName)
                .query(String.class)
                .single();
    }

    private static Set<String> indexRegistrations() {
        return jdbc().sql("SELECT property_iri FROM sq_index_registry")
                .query(String.class)
                .set();
    }

    private static Set<String> searchRegistrations() {
        return jdbc().sql("SELECT property_iri FROM sq_searchable_property")
                .query(String.class)
                .set();
    }

    private static Set<String> indexNamesInDatabase() {
        return jdbc().sql(
                        "SELECT indexname FROM pg_indexes WHERE tablename = 'sq_object' AND indexname LIKE 'sq\\_idx\\_%'")
                .query(String.class)
                .set();
    }

    /**
     * The plan of {@code SELECT id FROM sq_object WHERE <where>}, with sequential scans disabled for the transaction,
     * as one string, so that a test can look for an index name in it.
     */
    private static String plan(String where) {
        List<String> lines = new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(status -> {
            jdbc().sql("SET LOCAL enable_seqscan = off").update();
            return jdbc().sql("EXPLAIN SELECT id FROM sq_object WHERE " + where)
                    .query(String.class)
                    .list();
        });
        return String.join("\n", lines);
    }
}
