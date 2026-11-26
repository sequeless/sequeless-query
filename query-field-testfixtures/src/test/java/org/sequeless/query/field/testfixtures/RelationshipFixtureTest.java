package org.sequeless.query.field.testfixtures;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.query.field.api.Cardinality;
import org.sequeless.query.field.api.OntologyVersionRef;

class RelationshipFixtureTest {

    @Test
    void rejectsIriContainingPipe() {
        assertThatThrownBy(() -> new RelationshipFixture(
                        UUID.randomUUID(),
                        "urn:ont:property:Invoice|customer",
                        "customer",
                        "Customer",
                        Cardinality.SINGLE,
                        false,
                        Set.of(new OntologyVersionRef(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("iri must not contain '|'");
    }
}
