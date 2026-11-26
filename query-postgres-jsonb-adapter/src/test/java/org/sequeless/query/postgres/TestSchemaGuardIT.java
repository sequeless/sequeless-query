package org.sequeless.query.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Fails when the test-only Command DDL lacks a column that the Query SQL reads. The list is the column set
 * of the prototype's {@code PostgresQueryStore} read and browse statements (its {@code SELECT id, tenant_id,
 * ... deleted_at FROM sq_object}), plus {@code search_vector}, which browse filters on. When a Query statement
 * reads another column of {@code sq_object}, add it here, or the test schema drifts from what the SQL needs.
 */
class TestSchemaGuardIT extends AbstractPostgresIT {

    static final Set<String> QUERY_COLUMNS = Set.of(
            "id",
            "tenant_id",
            "type_iri",
            "version",
            "state",
            "props",
            "created_at",
            "created_by",
            "updated_at",
            "updated_by",
            "deleted_at",
            "search_vector");

    @Test
    void theTestSchemaHasEveryColumnTheQuerySqlReads() {
        List<String> columns = JdbcClient.create(dataSource)
                .sql("SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = 'sq_object'")
                .query(String.class)
                .list();

        assertThat(columns)
                .as("columns of sq_object in the test-only Command DDL")
                .containsAll(QUERY_COLUMNS);
    }
}
