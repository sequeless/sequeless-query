package org.sequeless.query.postgres;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.query.api.PropertyRef;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Decodes {@code sq_object.props} into kernel {@link Value}s (spec 14, sections 6.1 and 6.4). This is the read half of the
 * prototype's {@code ValueJsonCodec}, at prototype commit 48a0cc6, and it reads the same tagged shape: a top-level
 * object keyed by property IRI, each value a single-entry object whose key names the {@link Value} variant
 * ({@code text}, {@code integer}, {@code decimal}, {@code bool}, {@code dateTime}, {@code date}, {@code ref} or
 * {@code list}). The Query side never writes, so the encoder stays with the Command side.
 *
 * <p>Decimals are read with {@code USE_BIG_DECIMAL_FOR_FLOATS}, so a stored {@code 12.50} keeps its scale. A value
 * with no known tag is an {@link IllegalArgumentException}.
 */
final class ValueJsonDecoder {

    private static final String TAG_TEXT = "text";
    private static final String TAG_INTEGER = "integer";
    private static final String TAG_DECIMAL = "decimal";
    private static final String TAG_BOOL = "bool";
    private static final String TAG_DATE_TIME = "dateTime";
    private static final String TAG_DATE = "date";
    private static final String TAG_REF = "ref";
    private static final String TAG_LIST = "list";

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private ValueJsonDecoder() {}

    /**
     * @param json the props text read from {@code sq_object}; must not be {@code null}
     * @return the decoded properties, keyed by property IRI, in the order the text holds them
     * @throws NullPointerException if {@code json} is {@code null}
     * @throws IllegalArgumentException if a value has no known tag
     */
    static Map<PropertyRef, Value> decode(String json) {
        Objects.requireNonNull(json, "json must not be null");
        JsonNode root = MAPPER.readTree(json);
        Map<PropertyRef, Value> result = new LinkedHashMap<>();
        root.properties().forEach(entry -> result.put(new PropertyRef(entry.getKey()), decodeValue(entry.getValue())));
        return result;
    }

    private static Value decodeValue(JsonNode node) {
        if (node.has(TAG_TEXT)) {
            return Value.text(node.get(TAG_TEXT).asString());
        }
        if (node.has(TAG_INTEGER)) {
            return Value.integer(node.get(TAG_INTEGER).longValue());
        }
        if (node.has(TAG_DECIMAL)) {
            return Value.decimal(node.get(TAG_DECIMAL).decimalValue());
        }
        if (node.has(TAG_BOOL)) {
            return Value.bool(node.get(TAG_BOOL).booleanValue());
        }
        if (node.has(TAG_DATE_TIME)) {
            return Value.dateTime(Instant.parse(node.get(TAG_DATE_TIME).asString()));
        }
        if (node.has(TAG_DATE)) {
            return Value.date(LocalDate.parse(node.get(TAG_DATE).asString()));
        }
        if (node.has(TAG_REF)) {
            return Value.ref(ObjectId.parse(node.get(TAG_REF).asString()));
        }
        if (node.has(TAG_LIST)) {
            List<Value> values = new ArrayList<>();
            for (JsonNode element : node.get(TAG_LIST)) {
                values.add(decodeValue(element));
            }
            return Value.list(values);
        }
        throw new IllegalArgumentException("Unrecognized tagged value: " + node);
    }
}
