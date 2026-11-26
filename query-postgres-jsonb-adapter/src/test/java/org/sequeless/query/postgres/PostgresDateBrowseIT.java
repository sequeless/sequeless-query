package org.sequeless.query.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.sequeless.query.postgres.TestFields.classRef;
import static org.sequeless.query.postgres.TestFields.direct;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.Filters;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.query.api.Audit;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.BrowseResult;
import org.sequeless.query.api.Direction;
import org.sequeless.query.api.FacetBucket;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.Page;
import org.sequeless.query.api.PropertyRef;
import org.sequeless.query.api.Sort;
import org.sequeless.query.api.TypeRef;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.TraversalConfig;
import org.sequeless.query.field.api.VersionPolicy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * The DATE and DATETIME facets and sorts of the browse adapter, over unindexed Fields. Neither
 * has an expression index until cutover, so these run the same casts as WHERE and ORDER BY without one. The facet buckets
 * are the value's text as the in-memory reference writes it: a DATE is its ISO day, and a DATETIME is
 * {@code Instant.toString()}.
 *
 * <p>The scope is hand-built over two facetable and sortable Fields, one DATE and one DATETIME, because the testkit
 * scenario has no DATETIME Field.
 */
class PostgresDateBrowseIT extends AbstractPostgresIT {

    private static final TenantId TENANT = new TenantId("tenant-dates");
    private static final OntologyVersionRef V1 = new OntologyVersionRef(1);
    private static final String TASK_IRI = "urn:ex:date-task";
    private static final String DUE_DATE_IRI = "urn:ex:dueDate";
    private static final String SHIPPED_AT_IRI = "urn:ex:shippedAt";
    private static final Capabilities FACET_SORT = new Capabilities(true, true, false, true);
    private static final ClassRef TASK = classRef(TASK_IRI);
    private static final Field DUE_DATE = direct(DUE_DATE_IRI, FieldType.DATE, FACET_SORT);
    private static final Field SHIPPED_AT = direct(SHIPPED_AT_IRI, FieldType.DATETIME, FACET_SORT);
    private static final FieldScope SCOPE = new FieldScope(
            TASK,
            List.of(DUE_DATE, SHIPPED_AT),
            List.of(),
            "date-browse-stamp",
            Set.of(V1),
            Set.of(TASK),
            new FieldScopeRequest(
                    TENANT,
                    TASK,
                    new VersionPolicy.Union(),
                    TraversalConfig.defaults(),
                    Optional.empty(),
                    Optional.empty()));

    private static final ObjectId FIRST = ObjectId.parse("00000000-0000-4000-8000-000000000001");
    private static final ObjectId SECOND = ObjectId.parse("00000000-0000-4000-8000-000000000002");
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void dateFacetBucketsAreIsoDaysOrderedByCountThenCodePoint() {
        prepareIndexes();
        seedDue(ObjectId.random(), "2026-02-02");
        seedDue(ObjectId.random(), "2026-02-02");
        seedDue(ObjectId.random(), "2026-02-01");
        seedDue(ObjectId.random(), "2026-01-31");

        BrowseResult result = browse(query(List.of(), List.of(DUE_DATE.path())));

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.facets().get(DUE_DATE.path()))
                .containsExactly(
                        new FacetBucket("2026-02-02", 2),
                        new FacetBucket("2026-01-31", 1),
                        new FacetBucket("2026-02-01", 1));
    }

    @Test
    void dateFacetDropsObjectsWithoutAValueButTheyStillCountInTheTotal() {
        prepareIndexes();
        seedDue(ObjectId.random(), "2026-02-01");
        seedTask(ObjectId.random(), Map.of());

        BrowseResult result = browse(query(List.of(), List.of(DUE_DATE.path())));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.facets().get(DUE_DATE.path())).containsExactly(new FacetBucket("2026-02-01", 1));
    }

    @Test
    void dateSortPutsAMissingValueLastAscendingAndFirstDescendingWithIdTieBreaks() {
        prepareIndexes();
        seedTask(SECOND, Map.of(dueDate(), Value.date(LocalDate.parse("2026-02-01"))));
        seedTask(FIRST, Map.of(dueDate(), Value.date(LocalDate.parse("2026-02-01"))));
        ObjectId later = ObjectId.random();
        seedTask(later, Map.of(dueDate(), Value.date(LocalDate.parse("2026-02-03"))));
        ObjectId none = ObjectId.random();
        seedTask(none, Map.of());

        assertThat(ids(browse(sorted(new Sort(DUE_DATE.path(), Direction.ASC)))))
                .containsExactly(FIRST, SECOND, later, none);
        assertThat(ids(browse(sorted(new Sort(DUE_DATE.path(), Direction.DESC)))))
                .containsExactly(none, later, FIRST, SECOND);
    }

    @Test
    void dateTimeFacetBucketsUseTheInstantTextWithAFractionOnlyWhenThereIsOne() {
        prepareIndexes();
        seedShippedAt(ObjectId.random(), "2026-02-01T10:00:00Z");
        seedShippedAt(ObjectId.random(), "2026-02-01T10:00:00Z");
        seedShippedAt(ObjectId.random(), "2026-02-01T10:00:00.250Z");
        seedShippedAt(ObjectId.random(), "2026-02-01T10:00:00.000100Z");
        seedShippedAt(ObjectId.random(), "2026-02-01T10:00:00.000001Z");

        BrowseResult result = browse(query(List.of(), List.of(SHIPPED_AT.path())));

        // Count descending, then code point: the fraction's "." sorts before "Z", and the six-digit fractions sort by
        // digit.
        assertThat(result.facets().get(SHIPPED_AT.path()))
                .containsExactly(
                        new FacetBucket("2026-02-01T10:00:00Z", 2),
                        new FacetBucket("2026-02-01T10:00:00.000001Z", 1),
                        new FacetBucket("2026-02-01T10:00:00.000100Z", 1),
                        new FacetBucket("2026-02-01T10:00:00.250Z", 1));
    }

    @Test
    void dateTimeSortOrdersChronologicallyInBothDirections() {
        prepareIndexes();
        ObjectId late = ObjectId.random();
        ObjectId early = ObjectId.random();
        ObjectId middle = ObjectId.random();
        seedShippedAt(late, "2026-03-01T00:00:00Z");
        seedShippedAt(early, "2026-01-01T12:30:00.500Z");
        seedShippedAt(middle, "2026-02-01T10:00:00Z");

        assertThat(ids(browse(sorted(new Sort(SHIPPED_AT.path(), Direction.ASC)))))
                .containsExactly(early, middle, late);
        assertThat(ids(browse(sorted(new Sort(SHIPPED_AT.path(), Direction.DESC)))))
                .containsExactly(late, middle, early);
    }

    /**
     * A gte and an lte on the DATETIME Field: the {@code ::timestamptz} comparison and the {@code Timestamp} binding of
     * {@link ValueConverter}, which no other IT exercises. Two rows fall outside the window, one on each side. One row sits
     * exactly on each bound, so the test pins gte against gt and lte against lt.
     */
    @Test
    void dateTimeRangeFilterUsesGteAndLteOnTheInstant() {
        prepareIndexes();
        ObjectId atLowerBound = ObjectId.random();
        ObjectId inWindow = ObjectId.random();
        ObjectId atUpperBound = ObjectId.random();
        seedShippedAt(atLowerBound, "2026-02-01T00:00:00Z");
        seedShippedAt(inWindow, "2026-02-01T10:00:00Z");
        seedShippedAt(atUpperBound, "2026-02-28T23:59:59Z");
        seedShippedAt(ObjectId.random(), "2026-01-31T23:59:59.999Z");
        seedShippedAt(ObjectId.random(), "2026-03-01T00:00:00Z");
        FilterNode window = Filters.and(
                Filters.field(SHIPPED_AT_IRI, "gte", JsonNodeFactory.instance.textNode("2026-02-01T00:00:00Z")),
                Filters.field(SHIPPED_AT_IRI, "lte", JsonNodeFactory.instance.textNode("2026-02-28T23:59:59Z")));

        BrowseResult result =
                browse(new BrowseQuery(Optional.of(window), Optional.empty(), List.of(), new Page(0, 20), List.of()));

        assertThat(ids(result)).containsExactlyInAnyOrder(atLowerBound, inWindow, atUpperBound);
        assertThat(result.total()).isEqualTo(3);
    }

    @Test
    void preparingIndexesForDateAndDateTimeFieldsSucceedsAndCreatesNoExpressionIndex() {
        assertThatCode(PostgresDateBrowseIT::prepareIndexes).doesNotThrowAnyException();

        assertThat(jdbc().sql("SELECT indexname FROM pg_indexes WHERE tablename = 'sq_object' "
                                + "AND indexname LIKE 'sq\\_idx\\_%'")
                        .query(String.class)
                        .list())
                .isEmpty();
    }

    private static void prepareIndexes() {
        new PostgresIndexPreparation(jdbc(), new DataSourceTransactionManager(dataSource))
                .prepare(TENANT, V1, Map.of(TASK, List.of(DUE_DATE, SHIPPED_AT)));
    }

    private static BrowseResult browse(BrowseQuery query) {
        return new PostgresBrowsePort(jdbc(), new DataSourceTransactionManager(dataSource)).browse(SCOPE, query);
    }

    private static BrowseQuery query(List<Sort> sorts, List<FieldPath> facets) {
        return new BrowseQuery(Optional.empty(), Optional.empty(), sorts, new Page(0, 20), facets);
    }

    private static BrowseQuery sorted(Sort sort) {
        return query(List.of(sort), List.of());
    }

    private static List<ObjectId> ids(BrowseResult result) {
        return result.items().stream().map(ObjectView::id).toList();
    }

    private static PropertyRef dueDate() {
        return new PropertyRef(DUE_DATE_IRI);
    }

    private static void seedDue(ObjectId id, String isoDay) {
        seedTask(id, Map.of(dueDate(), Value.date(LocalDate.parse(isoDay))));
    }

    private static void seedShippedAt(ObjectId id, String instant) {
        seedTask(id, Map.of(new PropertyRef(SHIPPED_AT_IRI), Value.dateTime(Instant.parse(instant))));
    }

    private static void seedTask(ObjectId id, Map<PropertyRef, Value> properties) {
        new PostgresObjectSeeder(jdbc())
                .seed(new ObjectView(
                        id,
                        new TypeRef(TASK_IRI),
                        TENANT,
                        1,
                        Optional.empty(),
                        properties,
                        new Audit(CREATED, "tester", CREATED, "tester"),
                        false));
    }

    private static JdbcClient jdbc() {
        return JdbcClient.create(dataSource);
    }
}
