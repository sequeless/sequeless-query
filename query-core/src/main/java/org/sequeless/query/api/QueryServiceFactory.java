package org.sequeless.query.api;

import org.sequeless.query.field.spi.FieldSource;
import org.sequeless.query.internal.DefaultQueryService;
import org.sequeless.query.spi.BrowsePort;
import org.sequeless.query.spi.IndexPreparationPort;
import org.sequeless.query.spi.ReadPort;

/**
 * The composition seam of the Query side: builds the host-facing {@link QueryService} over the driven
 * ports. A composition root (the Spring Boot autoconfiguration, or a host wiring by hand) calls it once
 * and shares the result.
 *
 * <p>This is the one class in {@code api} that depends on {@code internal}. It exists so that a composition
 * root in another module never reaches into {@code internal}. The query-core boundary test names it as the
 * only exception.
 */
public final class QueryServiceFactory {

    private QueryServiceFactory() {}

    /**
     * Creates the query service. The service delegates {@link QueryService#prepare} to {@code
     * indexPreparationPort}, so the caller that registers that port and the service share one instance.
     *
     * @param fieldSource the Field contract the scopes and root classes come from; never {@code null}
     * @param browsePort the browse port; never {@code null}
     * @param readPort the read port; never {@code null}
     * @param indexPreparationPort the index preparation port; never {@code null}
     * @return the query service; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public static QueryService create(
            FieldSource fieldSource,
            BrowsePort browsePort,
            ReadPort readPort,
            IndexPreparationPort indexPreparationPort) {
        return new DefaultQueryService(fieldSource, browsePort, readPort, indexPreparationPort);
    }
}
