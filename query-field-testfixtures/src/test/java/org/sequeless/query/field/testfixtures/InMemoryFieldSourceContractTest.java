package org.sequeless.query.field.testfixtures;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.LocalizedText;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.TraversalConfig;
import org.sequeless.query.field.api.VersionPolicy;
import org.sequeless.query.field.spi.FieldSource;

/**
 * Proves {@link InMemoryFieldSource} upholds every guarantee {@link FieldSourceContractTest}
 * exercises, and serves as the executable specification the contract test kit itself is validated
 * against.
 */
class InMemoryFieldSourceContractTest extends FieldSourceContractTest {

    @Override
    protected FieldSource sourceFor(ContractFixture fixture) {
        return InMemoryFieldSource.from(fixture);
    }

    @Test
    void builderRenameWithIdAndIriGivesOneIriForBothDrafts() {
        UUID sameId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        String sameIri = "urn:ont:property:Invoice.customer";
        OntologyVersionRef v1 = new OntologyVersionRef(1);
        OntologyVersionRef v2 = new OntologyVersionRef(2);
        FieldSource source = InMemoryFieldSource.builder()
                .tenant(new TenantId("acme"))
                .clazz("Invoice")
                .version(v1)
                .property("custName", FieldType.STRING)
                .id(sameId)
                .iri(sameIri)
                .version(v2)
                .property("customerName", FieldType.STRING)
                .id(sameId)
                .iri(sameIri)
                .build();
        // The builder derives a class id from its slug alone, so the root can be named directly.
        ClassRef root = new ClassRef(
                UUID.nameUUIDFromBytes("Invoice".getBytes(StandardCharsets.UTF_8)),
                "Invoice",
                "urn:ont:class:Invoice",
                new LocalizedText("Invoice", null));

        Field inV1 = pinnedProperty(source, root, v1);
        Field inV2 = pinnedProperty(source, root, v2);

        assertThat(inV1.path().asSlugPath()).isEqualTo("custName");
        assertThat(inV2.path().asSlugPath()).isEqualTo("customerName");
        assertThat(inV1.path().segments().get(0).propertyId()).isEqualTo(sameId);
        assertThat(inV2.path().segments().get(0).propertyId()).isEqualTo(sameId);
        assertThat(inV1.path().segments().get(0).iri()).isEqualTo(sameIri);
        assertThat(inV2.path().segments().get(0).iri()).isEqualTo(sameIri);
    }

    private static Field pinnedProperty(FieldSource source, ClassRef root, OntologyVersionRef version) {
        FieldScopeRequest request = new FieldScopeRequest(
                new TenantId("acme"),
                root,
                new VersionPolicy.Pinned(version),
                TraversalConfig.defaults(),
                Optional.empty(),
                Optional.empty());
        return source.resolve(request).facets().get(0);
    }
}
