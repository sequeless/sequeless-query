package org.sequeless.query.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.field.api.FieldType;

class ValueConverterTest {

    @Test
    void textTypesBindAStringAndKeepNull() {
        assertThat(ValueConverter.toBindValue(FieldType.STRING, "open")).isEqualTo("open");
        assertThat(ValueConverter.toBindValue(FieldType.IRI_REF, "urn:ex:x")).isEqualTo("urn:ex:x");
        assertThat(ValueConverter.toBindValue(FieldType.STRING, null)).isNull();
    }

    @Test
    void integerBindsALongAndRejectsAFraction() {
        assertThat(ValueConverter.toBindValue(FieldType.INTEGER, 42)).isEqualTo(42L);
        assertThat(ValueConverter.toBindValue(FieldType.INTEGER, 7L)).isEqualTo(7L);
        assertThat(ValueConverter.toBindValue(FieldType.INTEGER, " 7 ")).isEqualTo(7L);
        assertThatIllegalArgumentException().isThrownBy(() -> ValueConverter.toBindValue(FieldType.INTEGER, 1.5));
    }

    @Test
    void decimalBindsABigDecimalFromTheTextOfTheValue() {
        assertThat(ValueConverter.toBindValue(FieldType.DECIMAL, 0.1d)).isEqualTo(new BigDecimal("0.1"));
        assertThat(ValueConverter.toBindValue(FieldType.DECIMAL, "12.50")).isEqualTo(new BigDecimal("12.50"));
        assertThat(ValueConverter.toBindValue(FieldType.DECIMAL, new BigDecimal("3.000")))
                .isEqualTo(new BigDecimal("3.000"));
    }

    @Test
    void booleanBindsABooleanFromTrueOrFalseText() {
        assertThat(ValueConverter.toBindValue(FieldType.BOOLEAN, true)).isEqualTo(true);
        assertThat(ValueConverter.toBindValue(FieldType.BOOLEAN, "TRUE")).isEqualTo(true);
        assertThat(ValueConverter.toBindValue(FieldType.BOOLEAN, "false")).isEqualTo(false);
        assertThatIllegalArgumentException().isThrownBy(() -> ValueConverter.toBindValue(FieldType.BOOLEAN, "yes"));
    }

    @Test
    void dateBindsALocalDate() {
        assertThat(ValueConverter.toBindValue(FieldType.DATE, "2026-11-26")).isEqualTo(LocalDate.of(2026, 11, 26));
        assertThat(ValueConverter.toBindValue(FieldType.DATE, LocalDate.of(2026, 1, 2)))
                .isEqualTo(LocalDate.of(2026, 1, 2));
        assertThatIllegalArgumentException().isThrownBy(() -> ValueConverter.toBindValue(FieldType.DATE, "26/11/2026"));
    }

    @Test
    void dateTimeBindsATimestampOfTheInstantOfAnOffsetDateTime() {
        Timestamp expected = Timestamp.from(Instant.parse("2026-11-26T08:00:00Z"));

        assertThat(ValueConverter.toBindValue(FieldType.DATETIME, "2026-11-26T10:00:00+02:00"))
                .isEqualTo(expected);
        assertThat(ValueConverter.toBindValue(FieldType.DATETIME, OffsetDateTime.parse("2026-11-26T10:00:00+02:00")))
                .isEqualTo(expected);
    }

    @Test
    void dateTimeWithoutAnOffsetIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ValueConverter.toBindValue(FieldType.DATETIME, "2026-11-26T10:00:00"));
    }

    @Test
    void objectRefBindsAUuid() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

        assertThat(ValueConverter.toBindValue(FieldType.OBJECT_REF, id.toString()))
                .isEqualTo(id);
        assertThat(ValueConverter.toBindValue(FieldType.OBJECT_REF, id)).isEqualTo(id);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ValueConverter.toBindValue(FieldType.OBJECT_REF, "not-a-uuid"));
    }

    @Test
    void aListIsConvertedElementByElementAndKeepsItsNulls() {
        Object bound = ValueConverter.toBindValue(FieldType.INTEGER, Arrays.asList(1, null, 3));

        assertThat(bound).isEqualTo(Arrays.asList(1L, null, 3L));
    }

    @ParameterizedTest
    @EnumSource(
            value = FieldType.class,
            names = {"GEO", "BINARY", "UNKNOWN"})
    void aTypeWithNoExpressionCannotBindAValue(FieldType type) {
        assertThatThrownBy(() -> ValueConverter.toBindValue(type, "x")).isInstanceOf(InvalidQueryException.class);
    }
}
