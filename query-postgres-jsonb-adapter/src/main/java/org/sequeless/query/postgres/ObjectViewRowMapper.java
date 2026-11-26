package org.sequeless.query.postgres;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.query.api.Audit;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.TypeRef;

/**
 * The row shape the Postgres read ports share: the columns an {@code sq_object} row is selected by,
 * and the mapping of one such row to an {@link ObjectView}. {@link PostgresBrowsePort} and {@link PostgresReadPort} both
 * select {@link #COLUMNS} and map with {@link #toView}, so one stored object reads the same from either port.
 */
final class ObjectViewRowMapper {

    /** The columns a row is selected by, in any order: {@link #toView} reads them by name. */
    static final String COLUMNS = "id, tenant_id, type_iri, version, state, props, created_at, created_by, "
            + "updated_at, updated_by, deleted_at";

    private ObjectViewRowMapper() {}

    /**
     * Maps the current row of a result set selected with {@link #COLUMNS} to an {@link ObjectView}. The deleted flag is
     * set when {@code deleted_at} is not null.
     *
     * @param row the result set, positioned on a row; must not be {@code null}
     * @param rowNum the row number, unused
     * @return the object the row holds
     * @throws SQLException if a column cannot be read
     */
    static ObjectView toView(ResultSet row, int rowNum) throws SQLException {
        Audit audit = new Audit(
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getString("created_by"),
                row.getObject("updated_at", OffsetDateTime.class).toInstant(),
                row.getString("updated_by"));
        return new ObjectView(
                new ObjectId(row.getObject("id", UUID.class)),
                new TypeRef(row.getString("type_iri")),
                new TenantId(row.getString("tenant_id")),
                row.getLong("version"),
                Optional.ofNullable(row.getString("state")),
                ValueJsonDecoder.decode(row.getString("props")),
                audit,
                row.getObject("deleted_at") != null);
    }
}
