/**
 * Implementation detail of query-core: {@code DefaultQueryService}, which runs the use cases over the
 * ports; {@code FieldKeys}, the one place a traversal key is built and read (spec 14, section 6.5);
 * and the bridge from a resolved {@code FieldScope} to filter-core's {@code FieldRegistry}.
 *
 * <p>Only {@code DefaultQueryService} and {@code FieldKeys} are public, because the host and the
 * postgres adapter use them. Nothing in this package is part of the published contract.
 */
package org.sequeless.query.internal;
