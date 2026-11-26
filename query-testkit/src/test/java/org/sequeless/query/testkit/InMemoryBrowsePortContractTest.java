package org.sequeless.query.testkit;

/**
 * This module's own proof that {@link BrowseContract} is satisfiable: it runs the contract against
 * {@link InMemoryBrowsePort} over the {@link InMemoryObjectStore}.
 */
class InMemoryBrowsePortContractTest extends BrowseContract {

    @Override
    protected Environment freshEnvironment() {
        InMemoryObjectStore store = new InMemoryObjectStore();
        return new Environment(store, new InMemoryBrowsePort(store));
    }
}
