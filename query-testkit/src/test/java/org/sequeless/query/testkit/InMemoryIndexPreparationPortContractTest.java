package org.sequeless.query.testkit;

/**
 * This module's own proof that {@link IndexPreparationContract} is satisfiable: it runs the contract
 * against {@link InMemoryIndexPreparationPort}, which registers synchronously, so its hook is a no-op.
 */
class InMemoryIndexPreparationPortContractTest extends IndexPreparationContract {

    @Override
    protected Environment freshEnvironment() {
        InMemoryIndexPreparationPort port = new InMemoryIndexPreparationPort();
        return new Environment(port, port::awaitPrepared);
    }
}
