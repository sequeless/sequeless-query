package org.sequeless.query.postgres;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for the ITs of this module that need no contract. Each test class gets its own PostgreSQL 18 container
 * and a schema from {@link PostgresTestSchema}. Before every test the Query state is cleared.
 */
@Testcontainers
abstract class AbstractPostgresIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static DataSource dataSource;

    @BeforeAll
    static void migrateSchema() {
        dataSource = PostgresTestSchema.migrated(POSTGRES);
    }

    @BeforeEach
    void resetQueryState() {
        PostgresTestSchema.resetQueryState(dataSource);
    }
}
