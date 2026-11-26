package org.sequeless.query.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sequeless.query.testsupport.QueryFixtures.path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FieldKeyTest {

    @Test
    void aSingleSegmentEncodesToTheBareIri() {
        assertThat(FieldKey.encode(path("urn:ont:status"))).isEqualTo("urn:ont:status");
    }

    @Test
    void twoSegmentsEncodeToPipeJoinedIris() {
        assertThat(FieldKey.encode(path("a", "b"))).isEqualTo("a|b");
    }

    @Test
    void threeSegmentsEncodeInPathOrderWithoutSpaces() {
        assertThat(FieldKey.encode(path("urn:ont:customer", "urn:ont:address", "urn:ont:city")))
                .isEqualTo("urn:ont:customer|urn:ont:address|urn:ont:city");
    }

    @Test
    void decodeRoundTripsEncode() {
        String key = FieldKey.encode(path("urn:ont:customer", "urn:ont:city"));

        assertThat(FieldKey.decode(key)).containsExactly("urn:ont:customer", "urn:ont:city");
    }

    @Test
    void decodeOfABareIriIsOneToken() {
        assertThat(FieldKey.decode("urn:ont:status")).containsExactly("urn:ont:status");
    }

    @Test
    void decodeReturnsAnUnmodifiableList() {
        assertThatThrownBy(() -> FieldKey.decode("a|b").add("c")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void isTraversalIsFalseForABareIri() {
        assertThat(FieldKey.isTraversal("urn:ont:status")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a|b", "a|", "|a", "|"})
    void isTraversalIsTrueForAKeyWithASeparator(String key) {
        assertThat(FieldKey.isTraversal(key)).isTrue();
    }

    @Test
    void isTraversalIsTrueForTheEncodingOfAMultiSegmentPath() {
        assertThat(FieldKey.isTraversal(FieldKey.encode(path("a", "b")))).isTrue();
    }

    @Test
    void isTraversalRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> FieldKey.isTraversal(null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void decodeRejectsNullBlankAndEmptyKeys(String key) {
        assertThatIllegalArgumentException().isThrownBy(() -> FieldKey.decode(key));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a||b", "|a", "a|", "a| |b", "|"})
    void decodeRejectsEmptyTokens(String key) {
        assertThatIllegalArgumentException().isThrownBy(() -> FieldKey.decode(key));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a |b", "a\tb"})
    void decodeRejectsTokensThatContainWhitespace(String key) {
        assertThatIllegalArgumentException().isThrownBy(() -> FieldKey.decode(key));
    }

    @Test
    void encodeRejectsNullPath() {
        assertThatNullPointerException().isThrownBy(() -> FieldKey.encode(null));
    }
}
