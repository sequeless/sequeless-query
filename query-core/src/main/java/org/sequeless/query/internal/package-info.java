/**
 * Implementation detail of query-core: {@code DefaultQueryService}, which runs the use cases over the
 * ports.
 *
 * <p>The traversal key encoding is {@code org.sequeless.query.api.FieldKey}, which is published API so
 * that the postgres adapter can read keys without importing this package.
 * Nothing in this package is part of the published contract.
 */
package org.sequeless.query.internal;
