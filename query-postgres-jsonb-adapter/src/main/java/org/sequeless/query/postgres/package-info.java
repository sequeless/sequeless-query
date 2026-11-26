/**
 * The PostgreSQL JSONB adapter of the Query side (spec 14, section 6.4). It implements the three driven
 * ports of {@code query-core}: {@code PostgresBrowsePort} (browse), {@code PostgresReadPort} (read) and
 * {@code PostgresIndexPreparation} (index preparation). Each Field maps to a SQL expression over
 * {@code sq_object.props}, filter paths resolve through that mapping, and the adapter prepares the
 * expression indexes and the search registrations and owns the Query migrations.
 *
 * <p>The adapter's integration tests run against a test-only Command DDL in
 * {@code src/test/resources/db/test-command}, which is not shipped.
 *
 * <p>Everything in this package is an adapter detail that a host wires in. The published Query contracts
 * are in {@code query-core}.
 */
package org.sequeless.query.postgres;
