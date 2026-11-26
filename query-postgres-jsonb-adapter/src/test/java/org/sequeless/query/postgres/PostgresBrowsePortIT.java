package org.sequeless.query.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.testkit.BrowseContract;
import org.sequeless.query.testkit.QueryFixtures;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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

    /** The scenario's status Field, a facetable and sortable STRING Field of the root class. */
    private static final String STATUS_IRI = "https://example.org/query-testkit#status";

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
     * A browse with one facet takes exactly one connection, and that connection is set read-only at REPEATABLE READ before
     * the statements run. The count, the page and the bucket query share the transaction's connection, so the total agrees
     * with the items (spec 14, section 6.4).
     */
    @Test
    void browseRunsInOneReadOnlyRepeatableReadTransaction() {
        AtomicInteger connections = new AtomicInteger();
        Set<String> settings = new HashSet<>();
        FieldScope scope = QueryFixtures.taskScope(QueryFixtures.TENANT);
        FieldPath status = scope.facets().stream()
                .map(Field::path)
                .filter(path ->
                        !path.isTraversing() && path.segments().get(0).iri().equals(STATUS_IRI))
                .findFirst()
                .orElseThrow();
        BrowseQuery query =
                new BrowseQuery(Optional.empty(), Optional.empty(), List.of(), new Page(0, 20), List.of(status));
        DataSource counting = countingConnections(dataSource, connections, settings);

        new PostgresBrowsePort(JdbcClient.create(counting), new DataSourceTransactionManager(counting))
                .browse(scope, query);

        assertThat(connections.get())
                .as("connections taken by one browse with one facet")
                .isEqualTo(1);
        assertThat(settings)
                .as("settings the browse applied to its connection")
                .contains(
                        "setReadOnly(true)", "setTransactionIsolation(" + Connection.TRANSACTION_REPEATABLE_READ + ")");
    }

    /**
     * A sort on a Field with no SQL expression is rejected with InvalidQueryException before any statement. The count of
     * connections taken is the observable: a statement or a transaction needs a connection, and none is taken for the call.
     * The problems name the rejected key and the Field's type.
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
                .isInstanceOfSatisfying(InvalidQueryException.class, e -> assertNamesKeyAndType(e, type));
        assertThat(connections.get())
                .as("connections taken for a sort on %s", type)
                .isZero();
    }

    /**
     * A facet on a Field with no SQL expression is rejected the same way, before the count, the page and the buckets. The
     * problems name the rejected key and the Field's type.
     */
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
                .isInstanceOfSatisfying(InvalidQueryException.class, e -> assertNamesKeyAndType(e, type));
        assertThat(connections.get())
                .as("connections taken for a facet on %s", type)
                .isZero();
    }

    /**
     * A GEO sort or facet is rejected even when the scope has no concrete type, so the port returns its empty result only
     * after the sort and facet keys are checked. No connection is taken.
     */
    @Test
    void emptyTypesScopeStillRejectsASortOrFacetWithNoExpression() {
        AtomicInteger connections = new AtomicInteger();
        Field noExpression = TestFields.direct(NO_EXPRESSION_IRI, FieldType.GEO, SORTABLE_FACET);
        FieldScope scope = withNoConcreteTypes(withFacet(QueryFixtures.taskScope(QueryFixtures.TENANT), noExpression));
        BrowseQuery sortQuery = new BrowseQuery(
                Optional.empty(),
                Optional.empty(),
                List.of(new Sort(noExpression.path(), Direction.ASC)),
                new Page(0, 20),
                List.of());
        BrowseQuery facetQuery = new BrowseQuery(
                Optional.empty(), Optional.empty(), List.of(), new Page(0, 20), List.of(noExpression.path()));

        assertThatThrownBy(() -> countingPort(connections).browse(scope, sortQuery))
                .isInstanceOfSatisfying(InvalidQueryException.class, e -> assertNamesKeyAndType(e, FieldType.GEO));
        assertThatThrownBy(() -> countingPort(connections).browse(scope, facetQuery))
                .isInstanceOfSatisfying(InvalidQueryException.class, e -> assertNamesKeyAndType(e, FieldType.GEO));
        assertThat(connections.get())
                .as("connections taken for a GEO sort and facet on an empty-types scope")
                .isZero();
    }

    /**
     * A browse called inside a caller's transaction runs in its own transaction (PROPAGATION_REQUIRES_NEW), so it returns
     * and does not wait on the caller's connection. It reads the committed objects.
     */
    @Test
    void browseInsideACallersTransactionRunsInItsOwnTransaction() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(QueryFixtures.TENANT);
        Instant createdAt = Instant.parse("2026-01-01T10:15:30Z");
        ObjectView committed = new ObjectView(
                ObjectId.random(),
                new TypeRef(scope.root().iri()),
                QueryFixtures.TENANT,
                1,
                Optional.empty(),
                Map.of(),
                new Audit(createdAt, "creator", createdAt, "creator"),
                false);
        env.seeder().seed(committed);
        BrowseQuery query = new BrowseQuery(Optional.empty(), Optional.empty(), List.of(), new Page(0, 20), List.of());

        BrowseResult result = new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                .execute(status -> env.browsePort().browse(scope, query));

        assertThat(result.items()).containsExactly(committed);
    }

    /** The problems of {@code e} name the rejected property IRI and the type of its Field. */
    private static void assertNamesKeyAndType(InvalidQueryException e, FieldType type) {
        assertThat(e.problems())
                .anySatisfy(problem ->
                        assertThat(problem).contains(NO_EXPRESSION_IRI).contains(type.name()));
    }

    private static PostgresBrowsePort countingPort(AtomicInteger connections) {
        DataSource counting = countingConnections(dataSource, connections, new HashSet<>());
        return new PostgresBrowsePort(JdbcClient.create(counting), new DataSourceTransactionManager(counting));
    }

    /**
     * A DataSource that counts the connections it hands out, each of which a statement or a transaction takes. Each
     * connection it returns records the settings applied to it, as {@code setReadOnly(true)} and so on, into {@code
     * settings}, and passes every call on to the real connection.
     */
    private static DataSource countingConnections(
            DataSource delegate, AtomicInteger connections, Set<String> settings) {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        connections.incrementAndGet();
                        return recordingConnection((Connection) method.invoke(delegate, args), settings);
                    }
                    return method.invoke(delegate, args);
                });
    }

    /** A Connection that records {@code setReadOnly} and {@code setTransactionIsolation} calls into {@code settings}. */
    private static Connection recordingConnection(Connection delegate, Set<String> settings) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("setReadOnly")
                            || method.getName().equals("setTransactionIsolation")) {
                        settings.add(method.getName() + "(" + args[0] + ")");
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

    /** The scope with no concrete type, so no object is a candidate: the port's empty-result path. */
    private static FieldScope withNoConcreteTypes(FieldScope scope) {
        return new FieldScope(
                scope.root(),
                scope.facets(),
                scope.relationships(),
                scope.stamp(),
                scope.ontologyVersions(),
                Set.of(),
                scope.request());
    }
}
