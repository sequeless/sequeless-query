package org.sequeless.query.field.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OntologyVersionRefTest {

    @Test
    void acceptsTheFirstVersion() {
        assertThat(new OntologyVersionRef(1).value()).isEqualTo(1);
    }

    @Test
    void rejectsZeroAndNegativeValues() {
        assertThatThrownBy(() -> new OntologyVersionRef(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1");
        assertThatThrownBy(() -> new OntologyVersionRef(-3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalValuesAreEqual() {
        assertThat(new OntologyVersionRef(2)).isEqualTo(new OntologyVersionRef(2));
    }
}
