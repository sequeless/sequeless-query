package org.sequeless.query.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.query.api.Audit;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.BrowseResult;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.Page;
import org.sequeless.query.api.PropertyRef;
import org.sequeless.query.api.TypeRef;
import org.sequeless.query.field.api.FieldScope;
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
        return new Environment(new PostgresObjectSeeder(jdbcClient), new PostgresBrowsePort(jdbcClient));
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
}
