package org.sequeless.query.autoconfig;

import javax.sql.DataSource;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.query.field.spi.FieldSource;
import org.sequeless.query.postgres.PostgresBrowsePort;
import org.sequeless.query.postgres.PostgresIndexPreparation;
import org.sequeless.query.postgres.PostgresReadPort;
import org.sequeless.query.schema.SchemaFieldSource;
import org.sequeless.query.spi.BrowsePort;
import org.sequeless.query.spi.IndexPreparationPort;
import org.sequeless.query.spi.ReadPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Field source and Postgres ports for the Query side, in a Spring Boot host (spec 14, section 5). The query service
 * is {@link QueryServiceAutoConfiguration}, which runs after this class.
 *
 * <p>Nothing is registered unless the host defines a {@link SchemaSource} bean. The Field source is built over it.
 * The Postgres ports are registered when a {@link DataSource} bean exists. Every bean backs off when the host defines
 * a bean of its type. There are no {@code sequeless.query.*} properties.
 *
 * <p>Ordering. This class runs after {@link DataSourceAutoConfiguration}, so the {@code DataSource} condition sees the
 * host's and the auto-configured data source. The {@code SchemaSource} bean must come from a regular configuration,
 * because the condition on this class is evaluated when this class is registered.
 *
 * <p>The adapters need a {@link PlatformTransactionManager}. They use the host's when it defines one, and otherwise a
 * {@link DataSourceTransactionManager} over the same data source. That fallback is not a bean. A host with several
 * transaction managers and no primary fails at startup, as {@link ObjectProvider} reports it.
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnBean(SchemaSource.class)
public class QueryAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    static class SchemaFieldSourceConfiguration {

        @Bean
        @ConditionalOnMissingBean(FieldSource.class)
        FieldSource fieldSource(SchemaSource schemaSource) {
            return new SchemaFieldSource(schemaSource);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(DataSource.class)
    static class PostgresConfiguration {

        @Bean
        @ConditionalOnMissingBean(BrowsePort.class)
        BrowsePort browsePort(DataSource dataSource, ObjectProvider<PlatformTransactionManager> transactionManagers) {
            return new PostgresBrowsePort(
                    JdbcClient.create(dataSource), transactionManager(dataSource, transactionManagers));
        }

        @Bean
        @ConditionalOnMissingBean(ReadPort.class)
        ReadPort readPort(DataSource dataSource, ObjectProvider<PlatformTransactionManager> transactionManagers) {
            return new PostgresReadPort(
                    JdbcClient.create(dataSource), transactionManager(dataSource, transactionManagers));
        }

        @Bean
        @ConditionalOnMissingBean(IndexPreparationPort.class)
        IndexPreparationPort indexPreparationPort(
                DataSource dataSource, ObjectProvider<PlatformTransactionManager> transactionManagers) {
            return new PostgresIndexPreparation(
                    JdbcClient.create(dataSource), transactionManager(dataSource, transactionManagers));
        }

        private static PlatformTransactionManager transactionManager(
                DataSource dataSource, ObjectProvider<PlatformTransactionManager> transactionManagers) {
            return transactionManagers.getIfAvailable(() -> new DataSourceTransactionManager(dataSource));
        }
    }
}
