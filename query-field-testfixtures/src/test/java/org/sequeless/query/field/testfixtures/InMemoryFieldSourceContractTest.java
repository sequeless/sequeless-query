package org.sequeless.query.field.testfixtures;

import org.sequeless.query.field.spi.FieldSource;

/**
 * Proves {@link InMemoryFieldSource} upholds every guarantee {@link FieldSourceContractTest}
 * exercises, and serves as the executable specification the contract test kit itself is validated
 * against.
 */
class InMemoryFieldSourceContractTest extends FieldSourceContractTest {

    @Override
    protected FieldSource sourceFor(ContractFixture fixture) {
        return InMemoryFieldSource.from(fixture);
    }
}
