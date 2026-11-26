package org.sequeless.query.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.query.api.Audit;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.BrowseResult;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.Page;
import org.sequeless.query.api.PropertyRef;
import org.sequeless.query.api.ReadRequest;
import org.sequeless.query.api.TypeRef;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.testkit.QueryFixtures;
import org.sequeless.query.testkit.ReadContract;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the query-testkit {@link ReadContract} against {@link PostgresReadPort}, over a PostgreSQL 18 database that has the
 * test Command DDL and the Query migrations. Read has no index dependency, so each test starts from an emptied Query state
 * and does not prepare anything.
 */
@Testcontainers
class PostgresReadContractIT extends ReadContract {

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
        return new Environment(
                new PostgresObjectSeeder(jdbcClient),
                new PostgresReadPort(jdbcClient, new DataSourceTransactionManager(dataSource)));
    }

    /**
     * The read and the browse of one object return the same {@link ObjectView}. Both ports map the row with {@link
     * ObjectViewRowMapper}, and this pins it on an object with every stored column set.
     */
    @Test
    void readReturnsTheSameViewAsTheBrowseOfTheSameObject() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(QueryFixtures.TENANT);
        Instant createdAt = Instant.parse("2026-01-01T10:15:30.123456Z");
        ObjectView stored = new ObjectView(
                ObjectId.random(),
                new TypeRef(scope.root().iri()),
                QueryFixtures.TENANT,
                3,
                Optional.of("open"),
                Map.of(
                        new PropertyRef("https://example.org/read-compare#title"), Value.text("Shared"),
                        new PropertyRef("https://example.org/read-compare#priority"), Value.integer(2)),
                new Audit(createdAt, "creator", createdAt.plusSeconds(60), "editor"),
                false);
        env.seeder().seed(stored);

        BrowseResult browsed = new PostgresBrowsePort(
                        JdbcClient.create(dataSource), new DataSourceTransactionManager(dataSource))
                .browse(
                        scope,
                        new BrowseQuery(Optional.empty(), Optional.empty(), List.of(), new Page(0, 20), List.of()));
        Optional<ObjectView> read =
                env.readPort().read(new Scope(QueryFixtures.TENANT, Principal.ANONYMOUS), new ReadRequest(stored.id()));

        assertThat(browsed.items()).containsExactly(stored);
        assertThat(read).contains(browsed.items().get(0));
    }
}
