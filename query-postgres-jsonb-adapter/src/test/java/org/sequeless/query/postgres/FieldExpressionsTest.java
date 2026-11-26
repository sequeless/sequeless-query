package org.sequeless.query.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sequeless.query.postgres.TestFields.direct;
import static org.sequeless.query.postgres.TestFields.traversal;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.field.api.FieldType;

class FieldExpressionsTest {

    private static final String IRI = "urn:ex:p";

    static Stream<Arguments> expressionPerType() {
        return Stream.of(
                Arguments.of(FieldType.STRING, "(props -> 'urn:ex:p' ->> 'text')"),
                Arguments.of(FieldType.LANG_STRING, "(props -> 'urn:ex:p' ->> 'text')"),
                Arguments.of(FieldType.TIME, "(props -> 'urn:ex:p' ->> 'text')"),
                Arguments.of(FieldType.DURATION, "(props -> 'urn:ex:p' ->> 'text')"),
                Arguments.of(FieldType.IRI_REF, "(props -> 'urn:ex:p' ->> 'text')"),
                Arguments.of(FieldType.CONCEPT_REF, "(props -> 'urn:ex:p' ->> 'text')"),
                Arguments.of(FieldType.INTEGER, "(props -> 'urn:ex:p' ->> 'integer')::bigint"),
                Arguments.of(FieldType.DECIMAL, "(props -> 'urn:ex:p' ->> 'decimal')::numeric"),
                Arguments.of(FieldType.BOOLEAN, "(props -> 'urn:ex:p' ->> 'bool')::boolean"),
                Arguments.of(FieldType.DATE, "(props -> 'urn:ex:p' ->> 'date')::date"),
                Arguments.of(FieldType.DATETIME, "(props -> 'urn:ex:p' ->> 'dateTime')::timestamptz"),
                Arguments.of(FieldType.OBJECT_REF, "(props -> 'urn:ex:p' ->> 'ref')::uuid"));
    }

    @ParameterizedTest
    @MethodSource("expressionPerType")
    void eachTypeMapsToItsWriterKeyAndCast(FieldType type, String expected) {
        assertThat(FieldExpressions.sqlOf(direct(IRI, type))).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(
            value = FieldType.class,
            names = {"GEO", "BINARY", "UNKNOWN"})
    void geoBinaryAndUnknownHaveNoExpression(FieldType type) {
        assertThat(FieldExpressions.supports(type)).isFalse();
        assertThatThrownBy(() -> FieldExpressions.sqlOf(direct(IRI, type)))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining(IRI)
                .hasMessageContaining(type.name());
    }

    @ParameterizedTest
    @EnumSource(
            value = FieldType.class,
            names = {
                "STRING",
                "LANG_STRING",
                "TIME",
                "DURATION",
                "IRI_REF",
                "CONCEPT_REF",
                "INTEGER",
                "DECIMAL",
                "BOOLEAN",
                "DATE",
                "DATETIME",
                "OBJECT_REF"
            })
    void everyOtherTypeHasAnExpression(FieldType type) {
        assertThat(FieldExpressions.supports(type)).isTrue();
    }

    @Test
    void aTraversalFieldIsRejectedNamingItsKey() {
        assertThatThrownBy(() -> FieldExpressions.sqlOf(traversal("urn:ex:a", "urn:ex:b")))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("urn:ex:a|urn:ex:b");
    }

    @Test
    void aSingleQuoteInTheIriIsDoubledInsideTheLiteral() {
        assertThat(FieldExpressions.sqlOf(direct("urn:ex:o'brien", FieldType.STRING)))
                .isEqualTo("(props -> 'urn:ex:o''brien' ->> 'text')");
    }

    @Test
    void anIriTryingToEndTheLiteralStaysInsideIt() {
        String hostile = "x' ->> 'text') OR true --";

        assertThat(FieldExpressions.sqlOf(direct(hostile, FieldType.STRING)))
                .isEqualTo("(props -> 'x'' ->> ''text'') OR true --' ->> 'text')");
    }

    @Test
    void theExpressionHasNoBindParameters() {
        assertThat(FieldExpressions.sqlExpressionOf(direct(IRI, FieldType.DATE)).parameters())
                .isEmpty();
    }
}
