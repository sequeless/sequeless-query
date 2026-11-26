package org.sequeless.query.postgres;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import org.sequeless.kernel.values.BoolValue;
import org.sequeless.kernel.values.DateTimeValue;
import org.sequeless.kernel.values.DateValue;
import org.sequeless.kernel.values.DecimalValue;
import org.sequeless.kernel.values.IntegerValue;
import org.sequeless.kernel.values.ListValue;
import org.sequeless.kernel.values.ReferenceValue;
import org.sequeless.kernel.values.TextValue;
import org.sequeless.kernel.values.Value;
import org.sequeless.query.api.Audit;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.PropertyRef;
import org.sequeless.query.testkit.ObjectSeeder;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes fixture objects the way the Command side stores them: one {@code sq_object} row per id, with the props
 * encoded in the tagged shape {@link ValueJsonDecoder} reads (the prototype's {@code ValueJsonCodec.encode}). The Query
 * side never writes, so this encoder lives in the tests. The upsert replaces the row of an id that already exists, as an
 * update does, and {@code search_vector} is filled by the trigger, as on the Command side.
 */
final class PostgresObjectSeeder implements ObjectSeeder {

    private static final String UPSERT = "INSERT INTO sq_object (id, tenant_id, type_iri, version, state, props, "
            + "created_at, created_by, updated_at, updated_by, deleted_at) "
            + "VALUES (:id, :tenant, :type, :version, :state, CAST(:props AS jsonb), :createdAt, :createdBy, "
            + ":updatedAt, :updatedBy, :deletedAt) "
            + "ON CONFLICT (id) DO UPDATE SET tenant_id = EXCLUDED.tenant_id, type_iri = EXCLUDED.type_iri, "
            + "version = EXCLUDED.version, state = EXCLUDED.state, props = EXCLUDED.props, "
            + "created_at = EXCLUDED.created_at, created_by = EXCLUDED.created_by, "
            + "updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by, deleted_at = EXCLUDED.deleted_at";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final JdbcClient jdbcClient;

    PostgresObjectSeeder(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
    }

    @Override
    public void seed(ObjectView view) {
        Objects.requireNonNull(view, "view must not be null");
        Audit audit = view.audit();
        OffsetDateTime createdAt = OffsetDateTime.ofInstant(audit.createdAt(), ZoneOffset.UTC);
        OffsetDateTime updatedAt = OffsetDateTime.ofInstant(audit.updatedAt(), ZoneOffset.UTC);
        jdbcClient
                .sql(UPSERT)
                .param("id", view.id().value())
                .param("tenant", view.tenant().value())
                .param("type", view.type().iri())
                .param("version", view.version())
                .param("state", view.state().orElse(null), Types.VARCHAR)
                .param("props", encode(view.properties()))
                .param("createdAt", createdAt)
                .param("createdBy", audit.createdBy())
                .param("updatedAt", updatedAt)
                .param("updatedBy", audit.updatedBy())
                .param("deletedAt", view.deleted() ? updatedAt : null, Types.TIMESTAMP_WITH_TIMEZONE)
                .update();
    }

    private static String encode(Map<PropertyRef, Value> properties) {
        ObjectNode root = MAPPER.createObjectNode();
        properties.forEach((property, value) -> root.set(property.iri(), encodeValue(value)));
        return MAPPER.writeValueAsString(root);
    }

    private static JsonNode encodeValue(Value value) {
        ObjectNode node = MAPPER.createObjectNode();
        switch (value) {
            case TextValue text -> node.put("text", text.value());
            case IntegerValue integer -> node.put("integer", integer.value());
            case DecimalValue decimal -> node.put("decimal", decimal.value());
            case BoolValue bool -> node.put("bool", bool.value());
            case DateTimeValue dateTime -> node.put("dateTime", dateTime.value().toString());
            case DateValue date -> node.put("date", date.value().toString());
            case ReferenceValue ref -> node.put("ref", ref.target().value().toString());
            case ListValue list -> {
                ArrayNode elements = node.putArray("list");
                list.values().forEach(element -> elements.add(encodeValue(element)));
            }
        }
        return node;
    }
}
