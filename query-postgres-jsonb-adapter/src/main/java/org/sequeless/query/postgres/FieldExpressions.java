package org.sequeless.query.postgres;

import java.util.List;
import java.util.Objects;
import org.sequeless.filter.sql.api.SqlExpression;
import org.sequeless.query.api.FieldKey;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldType;

/**
 * The one place a Field becomes SQL over {@code sq_object.props} (spec 14, section 6.4). The filter's WHERE
 * clause, the sort and the index DDL all use {@link #sqlOf}, so an expression index always matches the
 * expression a query compares against.
 *
 * <p>The property IRI is embedded as a string literal, never a bind parameter: an index expression cannot take
 * parameters, and the WHERE text must be the same text. Each single quote in the IRI is doubled, which keeps the
 * IRI inside its literal. Only a direct Field (one segment) has an expression. A traversal Field, and a Field of
 * a type with no expression ({@code GEO}, {@code BINARY}, {@code UNKNOWN}), is rejected with {@link
 * InvalidQueryException}.
 *
 * <p>The JSON key per type is the tag the prototype's writer stores for the value's kind (its {@code
 * ValueJsonCodec}): {@code text}, {@code integer}, {@code decimal}, {@code bool}, {@code date}, {@code dateTime}
 * and {@code ref}. IRI references, concept references, times and durations are stored as text values, because
 * the kernel value types have no variant for them.
 */
final class FieldExpressions {

    private FieldExpressions() {}

    /**
     * Whether a Field of {@code type} has an expression.
     *
     * @param type the Field's type; must not be {@code null}
     * @return {@code false} for {@code GEO}, {@code BINARY} and {@code UNKNOWN}, {@code true} otherwise
     */
    static boolean supports(FieldType type) {
        Objects.requireNonNull(type, "type must not be null");
        return switch (type) {
            case STRING,
                    LANG_STRING,
                    TIME,
                    DURATION,
                    IRI_REF,
                    CONCEPT_REF,
                    INTEGER,
                    DECIMAL,
                    BOOLEAN,
                    DATE,
                    DATETIME,
                    OBJECT_REF -> true;
            case GEO, BINARY, UNKNOWN -> false;
        };
    }

    /**
     * The SQL expression of {@code field} over {@code sq_object}, with no bind parameters.
     *
     * @param field a Field; must not be {@code null}
     * @return the expression text, the same text for WHERE, ORDER BY and the index DDL
     * @throws InvalidQueryException if the Field is a traversal, or its type has no expression
     */
    static String sqlOf(Field field) {
        Objects.requireNonNull(field, "field must not be null");
        if (field.path().isTraversing()) {
            throw new InvalidQueryException(List.of("traversal path '" + FieldKey.encode(field.path())
                    + "' has no SQL expression: only a direct Field does"));
        }
        String iri = field.path().segments().get(0).iri();
        String head = "(props -> " + literal(iri);
        return switch (field.type()) {
            case STRING, LANG_STRING, TIME, DURATION, IRI_REF, CONCEPT_REF -> head + " ->> 'text')";
            case INTEGER -> head + " ->> 'integer')::bigint";
            case DECIMAL -> head + " ->> 'decimal')::numeric";
            case BOOLEAN -> head + " ->> 'bool')::boolean";
            case DATE -> head + " ->> 'date')::date";
            case DATETIME -> head + " ->> 'dateTime')::timestamptz";
            case OBJECT_REF -> head + " ->> 'ref')::uuid";
            case GEO, BINARY, UNKNOWN ->
                throw new InvalidQueryException(List.of("field '" + iri + "' of type " + field.type()
                        + " has no SQL expression, so it cannot be filtered, sorted, faceted or indexed"));
        };
    }

    /**
     * The expression of {@code field} as a filter {@link SqlExpression}, which has no parameters.
     *
     * @param field a Field; must not be {@code null}
     * @return the expression of {@link #sqlOf(Field)}
     * @throws InvalidQueryException as {@link #sqlOf(Field)}
     */
    static SqlExpression sqlExpressionOf(Field field) {
        return new SqlExpression(sqlOf(field), List.of());
    }

    /** A single-quoted SQL string literal, with each embedded single quote doubled. */
    static String literal(String text) {
        return "'" + text.replace("'", "''") + "'";
    }
}
