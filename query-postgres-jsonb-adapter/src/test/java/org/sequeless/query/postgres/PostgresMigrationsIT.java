package org.sequeless.query.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The Query migrations create the registries and the GIN index, in their own history table, idempotently. */
class PostgresMigrationsIT extends AbstractPostgresIT {

    @Test
    void theQueryMigrationsCreateTheRegistryTablesAndTheGinIndex() {
        JdbcClient jdbc = JdbcClient.create(dataSource);

        Long tables = jdbc.sql("SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = current_schema() "
                        + "AND table_name IN ('sq_index_registry', 'sq_searchable_property')")
                .query(Long.class)
                .single();
        Long gin = jdbc.sql("SELECT count(*) FROM pg_indexes WHERE indexname = 'sq_object_search_vector_gin_idx'")
                .query(Long.class)
                .single();

        assertThat(tables).isEqualTo(2L);
        assertThat(gin).isEqualTo(1L);
    }

    @Test
    void theQueryHistoryTableRecordsVersionsOneAndTwo() {
        List<String> versions = JdbcClient.create(dataSource)
                .sql("SELECT version FROM flyway_schema_history_query WHERE success ORDER BY installed_rank")
                .query(String.class)
                .list();

        assertThat(versions).contains("1", "2");
    }

    @Test
    void migratingAnUpToDateDatabaseChangesNothing() {
        assertThatCode(() -> PostgresMigrations.migrate(dataSource)).doesNotThrowAnyException();

        Long applied = JdbcClient.create(dataSource)
                .sql("SELECT count(*) FROM flyway_schema_history_query WHERE version IN ('1', '2') AND success")
                .query(Long.class)
                .single();
        assertThat(applied).isEqualTo(2L);
    }

    @Test
    void migrateRejectsANullDataSource() {
        assertThatNullPointerException().isThrownBy(() -> PostgresMigrations.migrate(null));
    }
}
