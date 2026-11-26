package org.sequeless.query.postgres;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for the ITs of this module. Each test class gets its own PostgreSQL 18 container. Before the
 * first test it applies the test-only Command DDL (the {@code src/test/resources/db/test-command} copy of the
 * prototype's {@code sq_object} table and {@code search_vector} trigger), then the Query migrations under
 * test. Before every test it clears the Query state, so a test never sees a registration that an earlier
 * test made.
 *
 * <p>The test-only Command DDL is applied in its own Flyway history table, {@code
 * flyway_schema_history_command_test}, so it never mixes with the Query history.
 */
@Testcontainers
abstract class AbstractPostgresIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static DataSource dataSource;

    @BeforeAll
    static void migrateSchema() {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/test-command")
                .table("flyway_schema_history_command_test")
                .load()
                .migrate();
        PostgresMigrations.migrate(dataSource);
    }

    @BeforeEach
    void resetQueryState() {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql(
                        "SELECT indexname FROM pg_indexes WHERE schemaname = current_schema() AND indexname LIKE 'sq\\_idx\\_%'")
                .query(String.class)
                .list()
                .forEach(index -> jdbc.sql("DROP INDEX IF EXISTS " + index).update());
        jdbc.sql("TRUNCATE sq_index_registry, sq_searchable_property, sq_object")
                .update();
    }
}
