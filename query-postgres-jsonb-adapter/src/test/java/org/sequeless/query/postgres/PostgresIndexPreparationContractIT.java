package org.sequeless.query.postgres;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.sequeless.query.testkit.IndexPreparationContract;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the query-testkit {@link IndexPreparationContract} against {@link PostgresIndexPreparation}. The adapter
 * prepares synchronously, so the await hook is a no-op.
 */
@Testcontainers
class PostgresIndexPreparationContractIT extends IndexPreparationContract {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static DataSource dataSource;

    @BeforeAll
    static void migrateSchema() {
        dataSource = PostgresTestSchema.migrated(POSTGRES);
    }

    /**
     * A fresh environment is an empty database state. The contract calls this twice in one test (the reversed-order
     * check), and both calls must start from nothing, so the state is cleared here, not once per test.
     */
    @Override
    protected Environment freshEnvironment() {
        PostgresTestSchema.resetQueryState(dataSource);
        PostgresIndexPreparation port = new PostgresIndexPreparation(
                JdbcClient.create(dataSource), new DataSourceTransactionManager(dataSource));
        return new Environment(port, () -> {});
    }
}
