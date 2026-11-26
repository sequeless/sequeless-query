/**
 * Implementation detail of query-core: {@code DefaultQueryService}, which runs the use cases over the
 * ports, and the bridge from a resolved {@code FieldScope} to filter-core's {@code FieldRegistry}.
 *
 * <p>The traversal key encoding is {@code org.sequeless.query.api.FieldKey}, which is published API so
 * that the postgres adapter can read keys without importing this package (spec 14, section 6.5).
 * Nothing in this package is part of the published contract.
 */
package org.sequeless.query.internal;
