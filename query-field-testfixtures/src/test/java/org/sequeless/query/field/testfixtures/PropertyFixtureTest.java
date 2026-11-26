package org.sequeless.query.field.testfixtures;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.Cardinality;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.InferenceSupport;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.OperatorRestriction;

class PropertyFixtureTest {

    @Test
    void rejectsIriContainingPipe() {
        assertThatThrownBy(() -> new PropertyFixture(
                        UUID.randomUUID(),
                        "urn:ont:property:Invoice|total",
                        "total",
                        FieldType.DECIMAL,
                        Cardinality.SINGLE,
                        new Capabilities(true, false, false, false),
                        null,
                        Optional.empty(),
                        OperatorRestriction.unrestricted(),
                        InferenceSupport.ASSERTED_ONLY,
                        new OntologyVersionRef(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("iri must not contain '|'");
    }
}
