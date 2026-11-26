package org.sequeless.query.field.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PathSegmentTest {

    private static final UUID CITY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final String CITY_IRI = "urn:ont:property:Customer.city";

    @Test
    void exposesTheIriItWasBuiltWith() {
        PathSegment segment = new PathSegment(CITY_ID, CITY_IRI, "city", null);

        assertThat(segment.iri()).isEqualTo(CITY_IRI);
    }

    @Test
    void rejectsBlankIri() {
        assertThatThrownBy(() -> new PathSegment(CITY_ID, "  ", "city", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("iri must not be blank");
    }

    @Test
    void rejectsIriContainingPipe() {
        assertThatThrownBy(() -> new PathSegment(CITY_ID, "urn:ont:property:a|b", "city", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("iri must not contain '|'");
    }

    @Test
    void rejectsNullIri() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new PathSegment(CITY_ID, null, "city", null))
                .withMessage("iri must not be null");
    }
}
