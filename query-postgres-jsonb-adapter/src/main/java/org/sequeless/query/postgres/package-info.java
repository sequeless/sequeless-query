/**
 * The PostgreSQL JSONB adapter of the Query side (spec 14, section 6.4). It maps each Field to a SQL
 * expression over {@code sq_object.props}, resolves filter paths through that mapping, prepares the
 * expression indexes and the search registrations, and owns the Query migrations.
 *
 * <p>Everything in this package is an adapter detail that a host wires in. The published Query contracts
 * are in {@code query-core}.
 */
package org.sequeless.query.postgres;
