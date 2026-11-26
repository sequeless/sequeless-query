package org.sequeless.query.postgres;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.field.api.FieldType;

/**
 * Coerces a filter value to the Java type its Field's expression compares against (spec 14, section 6.4): a
 * {@code DATE} to {@link LocalDate}, a {@code DATETIME} to a {@link Timestamp} made from an {@link OffsetDateTime},
 * an {@code OBJECT_REF} to {@link UUID}, a {@code DECIMAL} to {@link BigDecimal}, an {@code INTEGER} to {@code
 * Long}, a {@code BOOLEAN} to {@code Boolean}, and every text type to {@code String}. A {@link List} is coerced
 * element by element and keeps its {@code null} elements, so {@code is in ['a', null]} stays legal.
 *
 * <p>A value that does not parse as its type is an {@link IllegalArgumentException}. The filter validator is
 * meant to reject it first. A type with no expression is rejected as {@link InvalidQueryException}.
 */
final class ValueConverter {

    private ValueConverter() {}

    /**
     * @param type the Field's type; must not be {@code null}
     * @param value the filter value, or {@code null}
     * @return the value as the expression binds it; {@code null} for {@code null}
     * @throws IllegalArgumentException if {@code value} does not parse as {@code type}
     * @throws InvalidQueryException if {@code type} has no expression
     */
    static Object toBindValue(FieldType type, Object value) {
        Objects.requireNonNull(type, "type must not be null");
        if (!FieldExpressions.supports(type)) {
            throw new InvalidQueryException(List.of("a value of type " + type + " cannot be bound"));
        }
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> values) {
            return values.stream().map(element -> toBindValue(type, element)).toList();
        }
        return switch (type) {
            case STRING, LANG_STRING, TIME, DURATION, IRI_REF, CONCEPT_REF -> text(value);
            case INTEGER -> integer(value);
            case DECIMAL -> decimal(value);
            case BOOLEAN -> bool(value);
            case DATE -> date(value);
            case DATETIME -> Timestamp.from(dateTime(value).toInstant());
            case OBJECT_REF -> id(value);
            case GEO, BINARY, UNKNOWN -> throw new IllegalStateException("unreachable: checked by supports");
        };
    }

    private static String text(Object value) {
        if (value instanceof CharSequence sequence) {
            return sequence.toString();
        }
        throw mismatch(value, "text");
    }

    private static Long integer(Object value) {
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("not an integer: " + value, e);
            }
        }
        if (value instanceof String text) {
            return Long.parseLong(text.strip());
        }
        throw mismatch(value, "integer");
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text) {
            return new BigDecimal(text.strip());
        }
        throw mismatch(value, "decimal");
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text && text.strip().equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (value instanceof String text && text.strip().equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        throw mismatch(value, "boolean");
    }

    private static LocalDate date(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof String text) {
            try {
                return LocalDate.parse(text.strip());
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("not a date: " + value, e);
            }
        }
        throw mismatch(value, "date");
    }

    private static OffsetDateTime dateTime(Object value) {
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof String text) {
            try {
                return OffsetDateTime.parse(text.strip());
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("not a date-time with an offset: " + value, e);
            }
        }
        throw mismatch(value, "date-time with an offset");
    }

    private static UUID id(Object value) {
        if (value instanceof UUID id) {
            return id;
        }
        if (value instanceof String text) {
            return UUID.fromString(text.strip());
        }
        throw mismatch(value, "UUID");
    }

    private static IllegalArgumentException mismatch(Object value, String expected) {
        return new IllegalArgumentException(
                "value '" + value + "' of type " + value.getClass().getSimpleName() + " is not a " + expected);
    }
}
