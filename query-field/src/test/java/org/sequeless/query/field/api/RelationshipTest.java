package org.sequeless.query.field.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelationshipTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final String CUSTOMER_IRI = "urn:ont:property:Invoice.customer";
    private static final OntologyVersionRef V1 = new OntologyVersionRef(1);

    private static LocalizedText label(String value) {
        return new LocalizedText(value, "en");
    }

    private static ClassRef classRef(String slug) {
        return new ClassRef(UUID.nameUUIDFromBytes(slug.getBytes()), slug, "urn:class:" + slug, label(slug));
    }

    private static Relationship customerRelationship(String iri) {
        return new Relationship(
                CUSTOMER_ID, iri, "customer", label("Customer"), classRef("Customer"), Cardinality.SINGLE, Set.of(V1));
    }

    @Test
    void exposesTheIriItWasBuiltWith() {
        assertThat(customerRelationship(CUSTOMER_IRI).iri()).isEqualTo(CUSTOMER_IRI);
    }

    @Test
    void rejectsBlankIri() {
        assertThatThrownBy(() -> customerRelationship("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("iri must not be blank");
    }

    @Test
    void rejectsIriContainingPipe() {
        assertThatThrownBy(() -> customerRelationship("urn:ont:property:a|b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("iri must not contain '|'");
    }

    @Test
    void rejectsNullIri() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> customerRelationship(null))
                .withMessage("iri must not be null");
    }
}
