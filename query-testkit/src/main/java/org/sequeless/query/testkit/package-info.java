/**
 * The contract tests for the Query ports and their in-memory references.
 *
 * <p>{@link org.sequeless.query.testkit.BrowseContract}, {@link
 * org.sequeless.query.testkit.ReadContract} and {@link
 * org.sequeless.query.testkit.IndexPreparationContract} assert the behavioural clauses documented on
 * their ports. An adapter extends a contract and supplies an environment: a seeding hook plus the
 * port under test, or the port and an {@code awaitPrepared} hook for index preparation.
 *
 * <p>{@link org.sequeless.query.testkit.InMemoryBrowsePort}, {@link
 * org.sequeless.query.testkit.InMemoryReadPort} and {@link
 * org.sequeless.query.testkit.InMemoryIndexPreparationPort} are the references the contracts are
 * proven against, and the ones the BOM's contract test runs.
 *
 * <p>{@link org.sequeless.query.testkit.ObjectSeeder} is the seeding hook an adapter implements to put
 * objects into its own storage. {@link org.sequeless.query.testkit.InMemoryObjectStore} is the store
 * behind the in-memory references, and it is an {@code ObjectSeeder} too. {@link
 * org.sequeless.query.testkit.QueryFixtures} is the scenario the contracts run over; it is public so
 * an adapter's integration test can prepare its index over the same root Fields.
 */
package org.sequeless.query.testkit;
