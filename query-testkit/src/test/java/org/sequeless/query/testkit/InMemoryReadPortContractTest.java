package org.sequeless.query.testkit;

/**
 * This module's own proof that {@link ReadContract} is satisfiable: it runs the contract against
 * {@link InMemoryReadPort} over the {@link InMemoryObjectStore}.
 */
class InMemoryReadPortContractTest extends ReadContract {

    @Override
    protected Environment freshEnvironment() {
        InMemoryObjectStore store = new InMemoryObjectStore();
        return new Environment(store, new InMemoryReadPort(store));
    }
}
