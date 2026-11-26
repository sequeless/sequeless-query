package org.sequeless.query.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sequeless.query.testsupport.QueryFixtures.path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FieldKeysTest {

    @Test
    void aSingleSegmentEncodesToTheBareIri() {
        assertThat(FieldKeys.encode(path("urn:ont:status"))).isEqualTo("urn:ont:status");
    }

    @Test
    void twoSegmentsEncodeToPipeJoinedIris() {
        assertThat(FieldKeys.encode(path("a", "b"))).isEqualTo("a|b");
    }

    @Test
    void threeSegmentsEncodeInPathOrderWithoutSpaces() {
        assertThat(FieldKeys.encode(path("urn:ont:customer", "urn:ont:address", "urn:ont:city")))
                .isEqualTo("urn:ont:customer|urn:ont:address|urn:ont:city");
    }

    @Test
    void decodeRoundTripsEncode() {
        String key = FieldKeys.encode(path("urn:ont:customer", "urn:ont:city"));

        assertThat(FieldKeys.decode(key)).containsExactly("urn:ont:customer", "urn:ont:city");
    }

    @Test
    void decodeOfABareIriIsOneToken() {
        assertThat(FieldKeys.decode("urn:ont:status")).containsExactly("urn:ont:status");
    }

    @Test
    void decodeReturnsAnUnmodifiableList() {
        assertThatThrownBy(() -> FieldKeys.decode("a|b").add("c")).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void decodeRejectsNullBlankAndEmptyKeys(String key) {
        assertThatIllegalArgumentException().isThrownBy(() -> FieldKeys.decode(key));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a||b", "|a", "a|", "a| |b", "|"})
    void decodeRejectsEmptyTokens(String key) {
        assertThatIllegalArgumentException().isThrownBy(() -> FieldKeys.decode(key));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a |b", "a\tb"})
    void decodeRejectsTokensThatContainWhitespace(String key) {
        assertThatIllegalArgumentException().isThrownBy(() -> FieldKeys.decode(key));
    }

    @Test
    void encodeRejectsNullPath() {
        assertThatNullPointerException().isThrownBy(() -> FieldKeys.encode(null));
    }
}
