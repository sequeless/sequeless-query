package org.sequeless.query.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.PropertyRef;
import org.sequeless.query.api.ReadRequest;
import org.sequeless.query.spi.ReadPort;

/**
 * The mechanical form of the behavioural contract documented on {@link ReadPort}'s interface-level
 * javadoc. Every {@link ReadPort} implementation, adapter or test double, is expected to satisfy every
 * clause of that javadoc, and this class exercises each clause against whatever {@link Environment}
 * {@link #freshEnvironment()} supplies.
 *
 * <p>To use this contract, extend it from a test class in your own module and implement {@link
 * #freshEnvironment()} to return a brand-new, empty {@link ObjectSeeder} paired with a {@link
 * ReadPort} that reads the same storage:
 *
 * <pre>{@code
 * class MyAdapterReadContractTest extends ReadContract {
 *     protected Environment freshEnvironment() {
 *         MyStore store = new MyStore();
 *         return new Environment(store::seed, new MyReadPort(store));
 *     }
 * }
 * }</pre>
 *
 * <p>Every fixture object uses arbitrary type and property IRIs of its own devising, because {@link
 * ReadPort} has no notion of an ontology: a conforming implementation must not need one to answer a
 * read by id.
 */
public abstract class ReadContract {

    private static final String TYPE = "https://example.org/read#Thing";
    private static final TenantId TENANT = new TenantId("read-contract");
    private static final TenantId OTHER_TENANT = new TenantId("other-tenant");
    private static final Scope SCOPE = new Scope(TENANT, Principal.ANONYMOUS);
    private static final Instant AT = Instant.parse("2026-01-01T00:00:00Z");

    private static final PropertyRef TEXT_PROP = new PropertyRef("https://example.org/read#text");
    private static final PropertyRef INTEGER_PROP = new PropertyRef("https://example.org/read#integer");
    private static final PropertyRef DECIMAL_PROP = new PropertyRef("https://example.org/read#decimal");
    private static final PropertyRef BOOL_PROP = new PropertyRef("https://example.org/read#bool");
    private static final PropertyRef DATETIME_PROP = new PropertyRef("https://example.org/read#dateTime");
    private static final PropertyRef DATE_PROP = new PropertyRef("https://example.org/read#date");
    private static final PropertyRef REF_PROP = new PropertyRef("https://example.org/read#ref");
    private static final PropertyRef LIST_PROP = new PropertyRef("https://example.org/read#list");

    /**
     * A seeding hook paired with the {@link ReadPort} that reads the same storage.
     *
     * @param seeder the hook the contract seeds fixture objects through; must not be {@code null}
     * @param readPort the port under test; must not be {@code null}
     */
    public record Environment(ObjectSeeder seeder, ReadPort readPort) {

        public Environment {
            Objects.requireNonNull(seeder, "seeder must not be null");
            Objects.requireNonNull(readPort, "readPort must not be null");
        }
    }

    /**
     * @return a fresh {@link Environment} with empty storage; invoked for every {@code @Test} method, so
     *     it must not carry state over from an earlier test
     */
    protected abstract Environment freshEnvironment();

    @Test
    void readRejectsNullArguments() {
        Environment env = freshEnvironment();
        ReadRequest request = new ReadRequest(ObjectId.random());

        assertThatNullPointerException().isThrownBy(() -> env.readPort().read(null, request));
        assertThatNullPointerException().isThrownBy(() -> env.readPort().read(SCOPE, null));
    }

    @Test
    void readReturnsCommittedObjectWithItsAudit() {
        Environment env = freshEnvironment();
        ObjectId id = ObjectId.random();
        Map<PropertyRef, Value> properties = Map.of(
                TEXT_PROP, Value.text("hello"),
                INTEGER_PROP, Value.integer(42),
                DECIMAL_PROP, Value.decimal(new BigDecimal("12.50")),
                BOOL_PROP, Value.bool(true),
                DATETIME_PROP, Value.dateTime(AT),
                DATE_PROP, Value.date(LocalDate.of(2026, 1, 1)),
                REF_PROP, Value.ref(ObjectId.random()),
                LIST_PROP, Value.list(List.of(Value.text("a"), Value.text("b"))));
        ObjectView stored = objectAt(TENANT, id, properties, 1, false);

        env.seeder().seed(stored);

        Optional<ObjectView> found = env.readPort().read(SCOPE, new ReadRequest(id));
        assertThat(found).contains(stored);
        assertThat(found.orElseThrow().audit()).isEqualTo(stored.audit());
    }

    @Test
    void readOfUnknownIdIsEmpty() {
        Environment env = freshEnvironment();

        assertThat(env.readPort().read(SCOPE, new ReadRequest(ObjectId.random())))
                .isEmpty();
    }

    @Test
    void readReturnsLatestVersionAfterUpdate() {
        Environment env = freshEnvironment();
        ObjectId id = ObjectId.random();

        env.seeder().seed(objectAt(TENANT, id, Map.of(TEXT_PROP, Value.text("v1")), 1, false));
        env.seeder().seed(objectAt(TENANT, id, Map.of(TEXT_PROP, Value.text("v2")), 2, false));

        Optional<ObjectView> found = env.readPort().read(SCOPE, new ReadRequest(id));
        assertThat(found).isPresent();
        assertThat(found.orElseThrow().version()).isEqualTo(2);
        assertThat(found.orElseThrow().properties()).containsEntry(TEXT_PROP, Value.text("v2"));
    }

    @Test
    void readOfSoftDeletedObjectIsEmpty() {
        Environment env = freshEnvironment();
        ObjectId id = ObjectId.random();

        env.seeder().seed(objectAt(TENANT, id, Map.of(), 1, true));

        assertThat(env.readPort().read(SCOPE, new ReadRequest(id))).isEmpty();
    }

    @Test
    void readIsTenantScoped() {
        Environment env = freshEnvironment();
        Scope otherTenant = new Scope(OTHER_TENANT, Principal.ANONYMOUS);
        ObjectId id = ObjectId.random();

        env.seeder().seed(objectAt(TENANT, id, Map.of(), 1, false));

        assertThat(env.readPort().read(otherTenant, new ReadRequest(id))).isEmpty();
        assertThat(env.readPort().read(SCOPE, new ReadRequest(id))).isPresent();
    }

    private static ObjectView objectAt(
            TenantId tenant, ObjectId id, Map<PropertyRef, Value> properties, long version, boolean deleted) {
        return ObjectViews.of(id, TYPE, tenant, version, properties, AT, deleted);
    }
}
