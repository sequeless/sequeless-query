package org.sequeless.query.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.BrowseResult;
import org.sequeless.query.api.IndexPreparationResult;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.QueryService;
import org.sequeless.query.api.ReadRequest;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.spi.FieldSource;
import org.sequeless.query.spi.IndexPreparationPort;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionException;

/**
 * Context tests for {@link QueryAutoConfiguration} and {@link QueryServiceAutoConfiguration}, run together in the order
 * a host sees them. The data source never connects: its URL names no installed
 * driver, so {@link java.sql.DriverManager} refuses it without touching the network.
 */
class QueryAutoConfigurationTest {

    private static final TenantId TENANT = new TenantId("tenant-a");
    private static final OntologyVersionRef VERSION = new OntologyVersionRef(1);
    private static final String UNREACHABLE_URL = "jdbc:sequeless-unreachable:none";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(QueryAutoConfiguration.class, QueryServiceAutoConfiguration.class));

    @Test
    void withoutASchemaSourceNoQueryBeanIsRegistered() {
        runner.withBean(DataSource.class, () -> new DriverManagerDataSource(UNREACHABLE_URL))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(QueryService.class);
                    assertThat(context).doesNotHaveBean(IndexPreparationPort.class);
                });
    }

    @Test
    void withASchemaSourceButNoDataSourceTheQueryServiceAndIndexPreparationAreAbsent() {
        runner.withBean(SchemaSource.class, EmptySchemaSource::new).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(QueryService.class);
            assertThat(context).doesNotHaveBean(IndexPreparationPort.class);
        });
    }

    @Test
    void withASchemaSourceAndADataSourceTheQueryServiceDelegatesPrepareToTheIndexPreparationBean() {
        runner.withBean(SchemaSource.class, EmptySchemaSource::new)
                .withBean(DataSource.class, () -> new DriverManagerDataSource(UNREACHABLE_URL))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FieldSource.class);
                    assertThat(context).hasSingleBean(QueryService.class);
                    assertThat(context).hasSingleBean(IndexPreparationPort.class);
                    // The schema has no root classes, so the use case calls the index-preparation bean with an empty
                    // map.
                    // PostgresIndexPreparation then opens a transaction, and the only connection attempt in the
                    // context fails at once, since no driver accepts the URL. That failure proves the call reached
                    // the autoconfigured index-preparation bean.
                    assertThatThrownBy(() -> context.getBean(QueryService.class).prepare(TENANT, VERSION))
                            .isInstanceOf(TransactionException.class);
                });
    }

    @Test
    void aHostQueryServiceBacksOffTheAutoconfiguredOne() {
        QueryService host = new UnsupportedQueryService();
        runner.withBean(SchemaSource.class, EmptySchemaSource::new)
                .withBean(DataSource.class, () -> new DriverManagerDataSource(UNREACHABLE_URL))
                .withBean(QueryService.class, () -> host)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(QueryService.class);
                    assertThat(context.getBean(QueryService.class)).isSameAs(host);
                });
    }

    @Test
    void aHostIndexPreparationPortIsRespectedByTheQueryService() {
        RecordingIndexPreparationPort host = new RecordingIndexPreparationPort();
        runner.withBean(SchemaSource.class, EmptySchemaSource::new)
                .withBean(DataSource.class, () -> new DriverManagerDataSource(UNREACHABLE_URL))
                .withBean(IndexPreparationPort.class, () -> host)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IndexPreparationPort.class);
                    assertThat(context.getBean(IndexPreparationPort.class)).isSameAs(host);
                    assertThat(context.getBean(QueryService.class).prepare(TENANT, VERSION))
                            .isEqualTo(IndexPreparationResult.EMPTY);
                    assertThat(host.calls).isEqualTo(1);
                });
    }

    /** A schema source for any tenant with no types: the Schema has no root class, so no class is prepared. */
    static final class EmptySchemaSource implements SchemaSource {

        @Override
        public Schema schema(TenantId tenant) {
            return new Schema(new OntologyVersion(1), "http://example.org/onto", Optional.empty(), Map.of(), List.of());
        }
    }

    /** A host's own query service. The test checks identity only, so no method is ever called. */
    static final class UnsupportedQueryService implements QueryService {

        @Override
        public BrowseResult browse(FieldScopeRequest request, BrowseQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ObjectView> read(Scope scope, ReadRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IndexPreparationResult prepare(TenantId tenant, OntologyVersionRef version) {
            throw new UnsupportedOperationException();
        }
    }

    /** A host's own index-preparation port that counts its calls and prepares nothing. */
    static final class RecordingIndexPreparationPort implements IndexPreparationPort {

        int calls;

        @Override
        public IndexPreparationResult prepare(
                TenantId tenant, OntologyVersionRef version, Map<ClassRef, List<Field>> rootFields) {
            calls++;
            return IndexPreparationResult.EMPTY;
        }
    }
}
