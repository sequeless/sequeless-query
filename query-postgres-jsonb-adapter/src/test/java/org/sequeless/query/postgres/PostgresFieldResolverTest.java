package org.sequeless.query.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sequeless.query.postgres.TestFields.direct;
import static org.sequeless.query.postgres.TestFields.traversal;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.sql.api.Field.Expression;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldType;

class PostgresFieldResolverTest {

    private static final Field STATUS = direct("urn:ex:status", FieldType.STRING);
    private static final Field DUE = direct("urn:ex:due", FieldType.DATE);

    @Test
    void aKnownKeyResolvesToItsExpression() {
        PostgresFieldResolver resolver = new PostgresFieldResolver(List.of(STATUS, DUE));

        Optional<org.sequeless.filter.sql.api.Field> resolved = resolver.resolve("urn:ex:due");

        assertThat(resolved).hasValueSatisfying(field -> {
            assertThat(field).isInstanceOf(Expression.class);
            Expression expression = (Expression) field;
            assertThat(expression.expression().sql()).isEqualTo(FieldExpressions.sqlOf(DUE));
            assertThat(expression.expression().parameters()).isEmpty();
        });
    }

    @Test
    void anUnknownKeyHasNoMapping() {
        PostgresFieldResolver resolver = new PostgresFieldResolver(List.of(STATUS));

        assertThat(resolver.resolve("urn:ex:missing")).isEmpty();
    }

    @Test
    void aTraversalKeyIsRejectedNamingTheKey() {
        PostgresFieldResolver resolver = new PostgresFieldResolver(List.of(STATUS));

        assertThatThrownBy(() -> resolver.resolve("urn:ex:a|urn:ex:b"))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("urn:ex:a|urn:ex:b");
    }

    @Test
    void aTraversalFieldInTheScopeIsStillRejectedByItsKey() {
        PostgresFieldResolver resolver = new PostgresFieldResolver(List.of(traversal("urn:ex:a", "urn:ex:b")));

        assertThatThrownBy(() -> resolver.resolve("urn:ex:a|urn:ex:b")).isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void aGeoFieldIsRejectedWhenItIsResolved() {
        PostgresFieldResolver resolver = new PostgresFieldResolver(List.of(direct("urn:ex:where", FieldType.GEO)));

        assertThatThrownBy(() -> resolver.resolve("urn:ex:where"))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("urn:ex:where");
    }

    @Test
    void twoFieldsWithOneKeyAreRejectedAtConstruction() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PostgresFieldResolver(List.of(STATUS, direct("urn:ex:status", FieldType.DATE))));
    }

    @Test
    void nullsAreRejected() {
        assertThatNullPointerException().isThrownBy(() -> new PostgresFieldResolver(null));
        assertThatNullPointerException().isThrownBy(() -> new PostgresFieldResolver(Arrays.asList(STATUS, null)));
        assertThatNullPointerException().isThrownBy(() -> new PostgresFieldResolver(List.of(STATUS)).resolve(null));
    }
}
