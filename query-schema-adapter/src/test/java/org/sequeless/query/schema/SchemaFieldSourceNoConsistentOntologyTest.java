package org.sequeless.query.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sequeless.query.schema.SchemaFixtures.OTHER_TENANT;
import static org.sequeless.query.schema.SchemaFixtures.TENANT;
import static org.sequeless.query.schema.SchemaFixtures.attribute;
import static org.sequeless.query.schema.SchemaFixtures.classRef;
import static org.sequeless.query.schema.SchemaFixtures.concreteType;
import static org.sequeless.query.schema.SchemaFixtures.path;
import static org.sequeless.query.schema.SchemaFixtures.relationship;
import static org.sequeless.query.schema.SchemaFixtures.request;
import static org.sequeless.query.schema.SchemaFixtures.schema;
import static org.sequeless.query.schema.SchemaFixtures.segment;

import java.util.concurrent.atomic.AtomicBoolean;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.spi.NoConsistentOntologyException;
import org.sequeless.query.field.api.FieldErrorCode;
import org.sequeless.query.field.api.FieldResolutionException;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.TraversalConfig;
import org.sequeless.query.field.api.VersionPolicy;
import org.sequeless.query.field.spi.FieldSource;

/** Every FieldSource method translates a tenant with no consistent Ontology into UNKNOWN_TENANT (spec 14 Q19). */
class SchemaFieldSourceNoConsistentOntologyTest {

    private static final Schema PEOPLE = schema(
            1,
            concreteType("Person", attribute("name", Datatype.STRING, true, true), relationship("employer", "Company")),
            concreteType("Company", attribute("legalName", Datatype.STRING, true, true)));
    private static final FieldScopeRequest PERSON =
            request(TENANT, "Person", new VersionPolicy.Union(), TraversalConfig.defaults());

    @Test
    void resolveTranslatesNoConsistentOntologyToUnknownTenant() {
        AtomicBoolean consistent = new AtomicBoolean(true);
        FieldSource source = source(consistent);
        assertThat(source.resolve(PERSON).root()).isEqualTo(classRef("Person"));

        consistent.set(false);

        assertCode(() -> source.resolve(PERSON), FieldErrorCode.UNKNOWN_TENANT);
    }

    @Test
    void expandTranslatesNoConsistentOntologyToUnknownTenant() {
        AtomicBoolean consistent = new AtomicBoolean(true);
        FieldSource source = source(consistent);
        FieldScope scope = source.resolve(PERSON);

        consistent.set(false);

        assertCode(() -> source.expand(scope, path(segment("employer", "Company"))), FieldErrorCode.UNKNOWN_TENANT);
    }

    @Test
    void describeTranslatesNoConsistentOntologyToUnknownTenant() {
        AtomicBoolean consistent = new AtomicBoolean(true);
        FieldSource source = source(consistent);
        assertThat(source.describe(PERSON, path(segment("name", null))).path()).isEqualTo(path(segment("name", null)));

        consistent.set(false);

        assertCode(() -> source.describe(PERSON, path(segment("name", null))), FieldErrorCode.UNKNOWN_TENANT);
    }

    @Test
    void rootClassesTranslatesNoConsistentOntologyToUnknownTenant() {
        AtomicBoolean consistent = new AtomicBoolean(true);
        FieldSource source = source(consistent);
        assertThat(source.rootClasses(TENANT)).containsExactly(classRef("Company"), classRef("Person"));

        consistent.set(false);

        assertCode(() -> source.rootClasses(TENANT), FieldErrorCode.UNKNOWN_TENANT);
    }

    @Test
    void aTenantWithAConsistentOntologyIsUnaffectedByAnotherTenantWithoutOne() {
        FieldSource source = new SchemaFieldSource(tenant -> {
            if (tenant.equals(TENANT)) {
                throw new NoConsistentOntologyException("no consistent Ontology active for " + tenant.value());
            }
            return PEOPLE;
        });

        assertCode(() -> source.resolve(PERSON), FieldErrorCode.UNKNOWN_TENANT);
        assertThat(source.rootClasses(OTHER_TENANT)).containsExactly(classRef("Company"), classRef("Person"));
    }

    @Test
    void otherExceptionsFromSchemaSourcePropagateUnchanged() {
        IllegalStateException failure = new IllegalStateException("store unavailable");
        FieldSource source = new SchemaFieldSource(tenant -> {
            throw failure;
        });

        assertThatThrownBy(() -> source.rootClasses(TENANT)).isSameAs(failure);
    }

    /** A source for PEOPLE that has no consistent Ontology for any tenant once {@code consistent} is cleared. */
    private static FieldSource source(AtomicBoolean consistent) {
        return new SchemaFieldSource(tenant -> {
            if (!consistent.get()) {
                throw new NoConsistentOntologyException("no consistent Ontology active for " + tenant.value());
            }
            return PEOPLE;
        });
    }

    private static void assertCode(ThrowingCallable call, FieldErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        FieldResolutionException.class,
                        error -> assertThat(error.code()).isEqualTo(code));
    }
}
