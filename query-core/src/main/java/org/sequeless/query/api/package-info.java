/**
 * The published value types and the host-facing {@code QueryService} of the Query side: the browse
 * query and result, the object view, the page and sort requests, the read request, the index
 * preparation result, {@code InvalidQueryException}, and the two published helpers
 * {@code FieldRegistries} (the filter-core registry of a scope's Fields) and {@code FieldKey} (the
 * traversal key encoding). Every type here is public.
 *
 * <p>This layer does not depend on {@code internal}, with one exception: {@code QueryServiceFactory}, the
 * composition seam that builds the internal service for a composition root. Its links to {@code spi} are
 * javadoc references, except that factory, which takes the ports.
 */
package org.sequeless.query.api;
