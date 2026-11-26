package org.sequeless.query.postgres;

import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/**
 * Runs the Query side's Flyway migrations, V1 (the index registries) and V2 (the GIN index over
 * {@code sq_object.search_vector}), against a {@link DataSource}, in their own history table,
 * {@code flyway_schema_history_query}. It is the only place that names the Query migration location.
 *
 * <p>The Command side (the {@code sq_object} table, its {@code search_vector} column and the trigger
 * that fills it) is not owned by this module. V2 indexes that column, so the Command migrations must
 * already be applied to the same database before {@link #migrate} runs. The ITs of this module apply a
 * test-only copy of those migrations, which is not shipped (spec 14, section 2.3, Q5).
 *
 * <p>Baselining at version {@code 0} lets this set apply to a database that already has the Command
 * schema but no Query history table, as the prototype's Query set did.
 */
public final class PostgresMigrations {

    /** The classpath location of the Query migrations. */
    static final String LOCATION = "classpath:db/migration/sequeless/postgres-query";

    /** The Flyway history table of the Query set, separate from the Command set's default table. */
    static final String HISTORY_TABLE = "flyway_schema_history_query";

    private PostgresMigrations() {}

    /**
     * Applies every Query migration that is not yet in the history table. Calling it again on a database
     * that is up to date changes nothing.
     *
     * @param dataSource the data source to migrate; must not be {@code null}
     * @throws NullPointerException if {@code dataSource} is {@code null}
     */
    public static void migrate(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        Flyway.configure()
                .dataSource(dataSource)
                .locations(LOCATION)
                .table(HISTORY_TABLE)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }
}
