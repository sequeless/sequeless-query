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
 * IRI inside its literal, and a backslash is rejected, since its meaning depends on {@code standard_conforming_strings}.
 * A backslash in the IRI of any Field with an expression fails the whole scope with IllegalArgumentException (the
 * BrowseSql constructor builds every such Field of the scope).
 * Only a direct Field (one segment) has an expression. A traversal Field, and a Field of
 * a type with no expression ({@code GEO}, {@code BINARY}, {@code UNKNOWN}), is rejected with {@link
 * InvalidQueryException}.
 *
 * <p>The JSON key per type is the tag the prototype's writer stores for the value's kind (its {@code
 * ValueJsonCodec}): {@code text}, {@code integer}, {@code decimal}, {@code bool}, {@code date}, {@code dateTime}
 * and {@code ref}. Language-tagged strings, IRI references, concept references, times and durations are stored as
 * text values and read with the JSON key {@code text} and no cast (spec 14, Q25).
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
     * Whether a Field of {@code type} can have an expression index. PostgreSQL allows an index expression only when
     * every function in it is IMMUTABLE. The text-to-{@code date} and text-to-{@code timestamptz} casts are STABLE
     * (their input depends on DateStyle and TimeZone; checked on postgres:18, where {@code date_in} and {@code
     * timestamptz_in} are {@code s}), so a {@code DATE} or {@code DATETIME} expression can be queried but not indexed.
     * Every other type's cast is IMMUTABLE.
     *
     * @param type the Field's type; must not be {@code null}
     * @return {@code true} when {@link #supports} holds and the expression is IMMUTABLE
     */
    static boolean indexable(FieldType type) {
        return supports(type) && type != FieldType.DATE && type != FieldType.DATETIME;
    }

    /**
     * The SQL expression of {@code field} over {@code sq_object}, with no bind parameters.
     *
     * @param field a Field; must not be {@code null}
     * @return the expression text, the same text for WHERE, ORDER BY and the index DDL
     * @throws InvalidQueryException if the Field is a traversal, or its type has no expression
     * @throws IllegalArgumentException if the property IRI contains a backslash
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
     * @throws IllegalArgumentException as {@link #sqlOf(Field)}
     */
    static SqlExpression sqlExpressionOf(Field field) {
        return new SqlExpression(sqlOf(field), List.of());
    }

    /**
     * The SQL text of a facet bucket's value for {@code field}, as a parenthesised expression over {@code sq_object}. It
     * writes the value as the in-memory reference does (spec 14, Q21 and Q25), so the bucket text is the same whichever
     * adapter answers:
     *
     * <ul>
     *   <li>a {@code DATE} is its ISO day, {@code yyyy-MM-dd}, as {@code LocalDate.toString} writes it;
     *   <li>a {@code DATETIME} is {@code Instant.toString}: whole seconds, a {@code Z}, and a fraction only when there is
     *       one, of three digits (whole milliseconds) or six (otherwise);
     *   <li>every other type is its {@link #sqlOf expression} as text.
     * </ul>
     *
     * @param field a Field; must not be {@code null}
     * @return the bucket text expression, with no bind parameters
     * @throws InvalidQueryException as {@link #sqlOf(Field)}
     * @throws IllegalArgumentException as {@link #sqlOf(Field)}
     */
    static String bucketTextOf(Field field) {
        String expression = sqlOf(field);
        return switch (field.type()) {
            case DATE -> "(to_char(" + expression + ", 'YYYY-MM-DD'))";
            case DATETIME -> instantText(expression);
            default -> "((" + expression + ")::text)";
        };
    }

    /** The {@code Instant.toString} form of a {@code timestamptz} expression, in UTC, as one parenthesised expression. */
    private static String instantText(String expression) {
        String utc = "((" + expression + ") AT TIME ZONE 'UTC')";
        String micros = "to_char(" + utc + ", 'US')";
        return "(to_char(" + utc + ", 'YYYY-MM-DD\"T\"HH24:MI:SS')"
                + " || CASE WHEN " + micros + " = '000000' THEN '' WHEN right(" + micros
                + ", 3) = '000' THEN '.' || left("
                + micros + ", 3) ELSE '.' || " + micros + " END"
                + " || 'Z')";
    }

    /**
     * A single-quoted SQL string literal, with each embedded single quote doubled. A backslash is rejected: whether it
     * escapes depends on {@code standard_conforming_strings}, so the doubled-quote rule alone is safe only with that setting
     * on.
     *
     * @throws IllegalArgumentException if {@code text} contains a backslash
     */
    static String literal(String text) {
        if (text.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(
                    "the property IRI '" + text + "' contains a backslash, which an embedded literal does not allow");
        }
        return "'" + text.replace("'", "''") + "'";
    }
}
