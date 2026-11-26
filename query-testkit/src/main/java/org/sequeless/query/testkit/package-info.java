/**
 * The contract tests for the Query ports and their in-memory references (spec 14, section 7.3).
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
 */
package org.sequeless.query.testkit;
