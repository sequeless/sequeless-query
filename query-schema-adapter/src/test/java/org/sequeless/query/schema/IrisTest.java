package org.sequeless.query.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IrisTest {

    @Test
    void slugIsTheLocalNameAfterTheLastHashSlashOrColon() {
        assertThat(Iris.slug("http://example.org/onto#Person")).isEqualTo("Person");
        assertThat(Iris.slug("http://example.org/onto/Person")).isEqualTo("Person");
        assertThat(Iris.slug("urn:example:Person")).isEqualTo("Person");
    }

    @Test
    void slugFallsBackToTheWholeIriWhenTheLocalNameIsEmpty() {
        assertThat(Iris.slug("http://example.org/onto#")).isEqualTo("http://example.org/onto#");
    }

    @Test
    void idIsTheNameBasedUuidOfTheIriSoItIsStableAndDistinct() {
        String person = SchemaFixtures.iri("Person");

        assertThat(Iris.id(person)).isEqualTo(SchemaFixtures.idOf(person));
        assertThat(Iris.id(person)).isEqualTo(Iris.id(person));
        assertThat(Iris.id(person)).isNotEqualTo(Iris.id(SchemaFixtures.iri("Company")));
    }
}
