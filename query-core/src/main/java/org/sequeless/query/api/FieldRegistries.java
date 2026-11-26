package org.sequeless.query.api;

import java.util.List;
import java.util.Objects;
import org.sequeless.filter.api.FieldDefinition;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FieldRegistrySpec;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.FieldType;

/**
 * Bridges a resolved {@link FieldScope} to filter-core's {@link FieldRegistry}, so that
 * {@code FilterValidator} and the filter-sql translators can check a filter against the Fields the scope allows.
 *
 * <p>Each Field with {@code filterable} becomes one {@link FieldDefinition}, keyed by {@link
 * FieldKey#encode}. Its JSON Schema type and format come from its {@link FieldType}, and its
 * permitted operators are the canonical names filter-core's default registry applies to that type
 * and format. No filter-core JSON type leaves this class.
 *
 * <p>This is public API, not internal, because the postgres adapter builds its filter registry here
 * (spec 14, step 2a). It cannot reach a package-private bridge, and api may not depend on internal.
 */
public final class FieldRegistries {

    private FieldRegistries() {}

    /**
     * Builds the registry of the filterable Fields of {@code scope}.
     *
     * @param scope the resolved scope; never {@code null}
     * @return a strict registry holding one definition per filterable Field
     * @throws NullPointerException if {@code scope} is {@code null}
     */
    public static FieldRegistry of(FieldScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        OperatorRegistry operators = OperatorRegistry.defaults();
        List<FieldDefinition> definitions = scope.facets().stream()
                .filter(field -> field.capabilities().filterable())
                .map(field -> definitionOf(field, operators))
                .toList();
        return FieldRegistry.of(FieldRegistrySpec.builder().fields(definitions).build());
    }

    private static FieldDefinition definitionOf(Field field, OperatorRegistry operators) {
        JsonSchemaShape shape = shapeOf(field.type());
        List<String> permitted = operators.applicableTo(shape.type(), shape.format()).stream()
                .map(OperatorDefinition::getCanonicalName)
                .toList();
        return FieldDefinition.builder()
                .path(FieldKey.encode(field.path()))
                .jsonSchemaType(shape.type())
                .jsonSchemaFormat(shape.format())
                .permittedOperators(permitted)
                .build();
    }

    /** The JSON Schema type and format a {@link FieldType} maps to; the format may be {@code null}. */
    private record JsonSchemaShape(String type, String format) {}

    private static JsonSchemaShape shapeOf(FieldType type) {
        return switch (type) {
            case STRING, LANG_STRING, IRI_REF, CONCEPT_REF, BINARY, GEO, UNKNOWN -> new JsonSchemaShape("string", null);
            // An object id is a UUID string, not an IRI, so it has its own format.
            case OBJECT_REF -> new JsonSchemaShape("string", "uuid");
            case INTEGER -> new JsonSchemaShape("integer", null);
            case DECIMAL -> new JsonSchemaShape("number", null);
            case BOOLEAN -> new JsonSchemaShape("boolean", null);
            case DATE -> new JsonSchemaShape("string", "date");
            case DATETIME -> new JsonSchemaShape("string", "date-time");
            case TIME -> new JsonSchemaShape("string", "time");
            case DURATION -> new JsonSchemaShape("string", "duration");
        };
    }
}
