package org.sequeless.query.schema;

import java.util.Map;
import java.util.Objects;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.spi.SchemaSource;

/** A hand-written {@link SchemaSource} that serves one fixed Schema per tenant. */
final class FakeSchemaSource implements SchemaSource {

    private final Map<TenantId, Schema> schemas;

    FakeSchemaSource(Map<TenantId, Schema> schemas) {
        this.schemas = Map.copyOf(schemas);
    }

    @Override
    public Schema schema(TenantId tenant) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Schema schema = schemas.get(tenant);
        if (schema == null) {
            throw new IllegalStateException("no active ontology for tenant " + tenant.value());
        }
        return schema;
    }
}
