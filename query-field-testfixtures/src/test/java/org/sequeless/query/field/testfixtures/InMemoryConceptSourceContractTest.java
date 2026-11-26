package org.sequeless.query.field.testfixtures;

import java.util.Map;
import org.sequeless.query.field.spi.ConceptSource;

/**
 * Proves {@link InMemoryConceptSource} upholds every guarantee {@link ConceptSourceContractTest}
 * exercises, and serves as the executable specification the contract test kit itself is validated
 * against.
 */
class InMemoryConceptSourceContractTest extends ConceptSourceContractTest {

    @Override
    protected ConceptSource sourceFor() {
        return InMemoryConceptSource.withSchemes(Map.of(SCHEME_ID, categoriesConcepts()));
    }
}
