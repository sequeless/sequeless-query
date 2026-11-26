package org.sequeless.query.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.query.api.Audit;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.BrowseResult;
import org.sequeless.query.api.Direction;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.Page;
import org.sequeless.query.api.PropertyRef;
import org.sequeless.query.api.Sort;
import org.sequeless.query.api.TypeRef;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.testkit.BrowseContract;
import org.sequeless.query.testkit.QueryFixtures;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the query-testkit {@link BrowseContract} against {@link PostgresBrowsePort}, over a PostgreSQL 18 database that has
 * the test Command DDL and the Query migrations. Each test starts from an empty database with the scenario's searchable
 * and indexed Fields prepared, before it seeds, so the {@code search_vector} trigger has its registrations.
 */
@Testcontainers
class PostgresBrowsePortIT extends BrowseContract {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static DataSource dataSource;

    /** A property IRI no Field of the scenario uses, for a hand-built Field of a type with no SQL expression. */
    private static final String NO_EXPRESSION_IRI = "https://example.org/query-testkit#noExpression";

    private static final Capabilities SORTABLE_FACET = new Capabilities(true, true, false, true);

    @BeforeAll
    static void migrateSchema() {
        dataSource = PostgresTestSchema.migrated(POSTGRES);
    }

    @Override
    protected Environment freshEnvironment() {
        PostgresTestSchema.resetQueryState(dataSource);
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        new PostgresIndexPreparation(jdbcClient, new DataSourceTransactionManager(dataSource))
                .prepare(
                        QueryFixtures.TENANT,
                        new OntologyVersionRef(1),
                        QueryFixtures.rootFields(QueryFixtures.TENANT));
        return new Environment(
                new PostgresObjectSeeder(jdbcClient),
                new PostgresBrowsePort(jdbcClient, new DataSourceTransactionManager(dataSource)));
    }

    /**
     * The contract never reads properties, so this test pins the mapping of every column and of every value kind: an
     * object written with each kind comes back as the same {@link ObjectView}.
     */
    @Test
    void browseMapsEveryStoredColumnAndValueKindToTheObjectView() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(QueryFixtures.TENANT);
        String property = "https://example.org/mapping#";
        ObjectId reference = ObjectId.random();
        Instant createdAt = Instant.parse("2026-01-01T10:15:30.123456Z");
        ObjectView stored = new ObjectView(
                ObjectId.random(),
                new TypeRef(scope.root().iri()),
                QueryFixtures.TENANT,
                3,
                Optional.of("open"),
                Map.of(
                        new PropertyRef(property + "text"), Value.text("Mapped"),
                        new PropertyRef(property + "integer"), Value.integer(7),
                        new PropertyRef(property + "decimal"), Value.decimal(new BigDecimal("12.50")),
                        new PropertyRef(property + "bool"), Value.bool(true),
                        new PropertyRef(property + "date"), Value.date(LocalDate.of(2026, 2, 3)),
                        new PropertyRef(property + "dateTime"), Value.dateTime(Instant.parse("2026-03-04T05:06:07Z")),
                        new PropertyRef(property + "ref"), Value.ref(reference),
                        new PropertyRef(property + "list"), Value.list(List.of(Value.text("first"), Value.integer(2)))),
                new Audit(createdAt, "creator", createdAt.plusSeconds(60), "editor"),
                false);
        env.seeder().seed(stored);

        BrowseResult result = env.browsePort()
                .browse(
                        scope,
                        new BrowseQuery(Optional.empty(), Optional.empty(), List.of(), new Page(0, 20), List.of()));

        assertThat(result.items()).containsExactly(stored);
    }

    /**
     * A sort on a Field with no SQL expression is rejected with InvalidQueryException before any statement. The count of
     * connections taken is the observable: a statement or a transaction needs a connection, and none is taken for the call.
     */
    @ParameterizedTest
    @EnumSource(
            value = FieldType.class,
            names = {"GEO", "BINARY", "UNKNOWN"})
    void sortOnAFieldWithNoExpressionIsRejectedBeforeAnyQuery(FieldType type) {
        AtomicInteger connections = new AtomicInteger();
        Field noExpression = TestFields.direct(NO_EXPRESSION_IRI, type, SORTABLE_FACET);
        FieldScope scope = withFacet(QueryFixtures.taskScope(QueryFixtures.TENANT), noExpression);
        BrowseQuery query = new BrowseQuery(
                Optional.empty(),
                Optional.empty(),
                List.of(new Sort(noExpression.path(), Direction.ASC)),
                new Page(0, 20),
                List.of());

        assertThatThrownBy(() -> countingPort(connections).browse(scope, query))
                .isInstanceOf(InvalidQueryException.class);
        assertThat(connections.get())
                .as("connections taken for a sort on %s", type)
                .isZero();
    }

    /** A facet on a Field with no SQL expression is rejected the same way, before the count, the page and the buckets. */
    @ParameterizedTest
    @EnumSource(
            value = FieldType.class,
            names = {"GEO", "BINARY", "UNKNOWN"})
    void facetOnAFieldWithNoExpressionIsRejectedBeforeAnyQuery(FieldType type) {
        AtomicInteger connections = new AtomicInteger();
        Field noExpression = TestFields.direct(NO_EXPRESSION_IRI, type, SORTABLE_FACET);
        FieldScope scope = withFacet(QueryFixtures.taskScope(QueryFixtures.TENANT), noExpression);
        BrowseQuery query = new BrowseQuery(
                Optional.empty(), Optional.empty(), List.of(), new Page(0, 20), List.of(noExpression.path()));

        assertThatThrownBy(() -> countingPort(connections).browse(scope, query))
                .isInstanceOf(InvalidQueryException.class);
        assertThat(connections.get())
                .as("connections taken for a facet on %s", type)
                .isZero();
    }

    private static PostgresBrowsePort countingPort(AtomicInteger connections) {
        DataSource counting = countingConnections(dataSource, connections);
        return new PostgresBrowsePort(JdbcClient.create(counting), new DataSourceTransactionManager(counting));
    }

    /** A DataSource that counts the connections it hands out, each of which a statement or a transaction takes. */
    private static DataSource countingConnections(DataSource delegate, AtomicInteger connections) {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        connections.incrementAndGet();
                    }
                    return method.invoke(delegate, args);
                });
    }

    /** The scope with one more facet, which is how a hand-built Field of a type with no expression reaches the port. */
    private static FieldScope withFacet(FieldScope scope, Field extra) {
        List<Field> facets = new ArrayList<>(scope.facets());
        facets.add(extra);
        return new FieldScope(
                scope.root(),
                facets,
                scope.relationships(),
                scope.stamp(),
                scope.ontologyVersions(),
                scope.concreteTypes(),
                scope.request());
    }
}
