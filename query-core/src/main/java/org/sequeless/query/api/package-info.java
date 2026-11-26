/**
 * The published value types and the host-facing {@code QueryService} of the Query side: the browse
 * query and result, the object view, the page and sort requests, the read request, the index
 * preparation result and {@code InvalidQueryException}. Every type here is public.
 *
 * <p>This layer does not depend on {@code internal}. Its only link to {@code spi} is a javadoc
 * reference.
 */
package org.sequeless.query.api;
