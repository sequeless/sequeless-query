package org.sequeless.query.autoconfig;

import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.query.api.QueryService;
import org.sequeless.query.api.QueryServiceFactory;
import org.sequeless.query.field.spi.FieldSource;
import org.sequeless.query.spi.BrowsePort;
import org.sequeless.query.spi.IndexPreparationPort;
import org.sequeless.query.spi.ReadPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * The host-facing query service, over the Field source and the three ports.
 *
 * <p>It is a separate autoconfiguration, ordered after {@link QueryAutoConfiguration}, so that the ports it needs
 * are registered before its condition is evaluated. Conditions on beans of the same class depend on the order of
 * member registration, which Spring does not guarantee. The condition also names the {@link SchemaSource}, so
 * nothing is registered without one. The ports may be the host's or the auto-configured ones.
 *
 * <p>The query service delegates {@code prepare} to the index-preparation bean, so the two are one instance.
 */
@AutoConfiguration(after = QueryAutoConfiguration.class)
@ConditionalOnBean({SchemaSource.class, FieldSource.class, BrowsePort.class, ReadPort.class, IndexPreparationPort.class
})
public class QueryServiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(QueryService.class)
    QueryService queryService(
            FieldSource fieldSource,
            BrowsePort browsePort,
            ReadPort readPort,
            IndexPreparationPort indexPreparationPort) {
        return QueryServiceFactory.create(fieldSource, browsePort, readPort, indexPreparationPort);
    }
}
