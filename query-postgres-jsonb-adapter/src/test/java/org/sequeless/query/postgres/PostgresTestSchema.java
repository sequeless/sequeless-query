package org.sequeless.query.postgres;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The schema setup shared by the ITs of this module. An IT class that extends a contract cannot also extend a base
 * class, so both paths call these helpers.
 *
 * <p>The test-only Command DDL (the {@code src/test/resources/db/test-command} copy of the prototype's {@code sq_object}
 * table, its indexes and its {@code search_vector} trigger) is applied first, in its own Flyway history table,
 * {@code flyway_schema_history_command_test}. Then the Query migrations run. The Command DDL is not shipped.
 */
final class PostgresTestSchema {

    private PostgresTestSchema() {}

    /**
     * Migrates a new database in {@code postgres} to the test Command schema and then the Query schema.
     *
     * @param postgres the running container
     * @return a data source for that database
     */
    static DataSource migrated(PostgreSQLContainer postgres) {
        DataSource dataSource =
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/test-command")
                .table("flyway_schema_history_command_test")
                .load()
                .migrate();
        PostgresMigrations.migrate(dataSource);
        return dataSource;
    }

    /**
     * Drops every {@code sq_idx_} index and empties the Query registries and {@code sq_object}, so that the next test
     * starts from nothing it did not set up.
     *
     * @param dataSource the migrated database
     */
    static void resetQueryState(DataSource dataSource) {
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
