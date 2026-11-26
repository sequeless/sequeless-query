package org.sequeless.query.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sequeless.query.testkit.QueryFixtures.OTHER_TENANT;
import static org.sequeless.query.testkit.QueryFixtures.TENANT;
import static org.sequeless.query.testkit.QueryFixtures.V1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.query.api.IndexPreparationResult;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.spi.IndexPreparationPort;

/**
 * The mechanical form of the clauses of {@link IndexPreparationPort}'s interface-level javadoc that
 * the port exposes: null handling, the result (the newly registered searchable Fields, one per
 * distinct search key, sorted by that key), idempotence, immutability, the version not being checked
 * against the Fields, and per-tenant registration. Every {@link IndexPreparationPort} implementation,
 * adapter or test double, is expected to satisfy them, and this class exercises each one against
 * whatever {@link Environment} {@link #freshEnvironment()} supplies.
 *
 * <p>This contract does not assert index structures or derived state, such as expression indexes, DDL
 * or a search backfill. Each adapter's own tests assert those (spec 14, section 6.2, Q24).
 *
 * <p>Every call this contract makes to {@link IndexPreparationPort#prepare} goes through one helper,
 * which then runs the environment's {@code awaitPrepared} hook. That hook is the seam an adapter
 * whose preparation is asynchronous uses to say that the preparation is done or durably accepted
 * (ADR 0002). An adapter that prepares synchronously passes a no-op.
 *
 * <p>To use this contract, extend it from a test class in your own module and implement {@link
 * #freshEnvironment()}:
 *
 * <pre>{@code
 * class MyAdapterIndexPreparationContractTest extends IndexPreparationContract {
 *     protected Environment freshEnvironment() {
 *         MyIndexPort port = new MyIndexPort();
 *         return new Environment(port, port::awaitPrepared);
 *     }
 * }
 * }</pre>
 */
public abstract class IndexPreparationContract {

    /**
     * A port paired with the hook that waits for its preparation to finish.
     *
     * @param port the port under test; must not be {@code null}
     * @param awaitPrepared the hook that returns once every {@code prepare} call so far is done or durably
     *     accepted; must not be {@code null}
     */
    public record Environment(IndexPreparationPort port, Runnable awaitPrepared) {

        public Environment {
            Objects.requireNonNull(port, "port must not be null");
            Objects.requireNonNull(awaitPrepared, "awaitPrepared must not be null");
        }
    }

    /**
     * @return a fresh {@link Environment} with nothing prepared; invoked for every {@code @Test} method, so
     *     it must not carry state over from an earlier test
     */
    protected abstract Environment freshEnvironment();

    @Test
    void prepareRejectsNullArguments() {
        Environment env = freshEnvironment();
        Map<ClassRef, List<Field>> roots = taskRoots();

        assertThatNullPointerException().isThrownBy(() -> prepare(env, null, V1, roots));
        assertThatNullPointerException().isThrownBy(() -> prepare(env, TENANT, null, roots));
        assertThatNullPointerException().isThrownBy(() -> prepare(env, TENANT, V1, null));

        Map<ClassRef, List<Field>> nullKey = new HashMap<>();
        nullKey.put(null, List.of());
        assertThatNullPointerException().isThrownBy(() -> prepare(env, TENANT, V1, nullKey));

        Map<ClassRef, List<Field>> nullValue = new HashMap<>();
        nullValue.put(taskRoot(), null);
        assertThatNullPointerException().isThrownBy(() -> prepare(env, TENANT, V1, nullValue));
    }

    @Test
    void prepareWithNoRootsIsANoOp() {
        Environment env = freshEnvironment();

        IndexPreparationResult result = prepare(env, TENANT, V1, Map.of());

        assertThat(result.newlyRegistered()).isEmpty();
    }

    @Test
    void nonSearchableFieldsRegisterNothing() {
        Environment env = freshEnvironment();
        List<Field> notSearchable = taskFields().stream()
                .filter(field -> !field.capabilities().searchable())
                .toList();
        assertThat(notSearchable).isNotEmpty();

        IndexPreparationResult result = prepare(env, TENANT, V1, Map.of(taskRoot(), notSearchable));

        assertThat(result.newlyRegistered()).isEmpty();
    }

    @Test
    void filterableOnlyFieldRegistersNothing() {
        Environment env = freshEnvironment();
        Field email = QueryFixtures.resolve(TENANT, QueryFixtures.PERSON).facets().stream()
                .filter(field -> field.path().asSlugPath().equals("email"))
                .findFirst()
                .orElseThrow();
        assertThat(email.capabilities().filterable()).isTrue();
        assertThat(email.capabilities().searchable()).isFalse();
        assertThat(email.capabilities().facetable()).isFalse();
        assertThat(email.capabilities().sortable()).isFalse();

        IndexPreparationResult result = prepare(env, TENANT, V1, Map.of(personRoot(), List.of(email)));

        assertThat(result.newlyRegistered()).isEmpty();
    }

    @Test
    void indexOnlyFieldsRegisterNothing() {
        Environment env = freshEnvironment();
        List<Field> indexOnly = taskFields().stream()
                .filter(field -> !field.capabilities().searchable())
                .filter(field ->
                        field.capabilities().facetable() || field.capabilities().sortable())
                .toList();
        assertThat(indexOnly).isNotEmpty();

        IndexPreparationResult result = prepare(env, TENANT, V1, Map.of(taskRoot(), indexOnly));

        assertThat(result.newlyRegistered()).isEmpty();
    }

    @Test
    void searchableDateFieldIsListedExactlyOnce() {
        Environment env = freshEnvironment();
        Field dueDate = searchableDueDate();
        Map<ClassRef, List<Field>> roots = new LinkedHashMap<>();
        roots.put(taskRoot(), List.of(dueDate));
        roots.put(personRoot(), List.of(dueDate));

        IndexPreparationResult result = prepare(env, TENANT, V1, roots);

        assertThat(result.newlyRegistered()).containsExactly(dueDate.path());
        // The same roots again register nothing new: the one registration of the Field is still the only one.
        assertThat(prepare(env, TENANT, V1, roots).newlyRegistered()).isEmpty();
    }

    @Test
    void prepareReportsNewlyRegisteredSearchableFieldsInKeyOrder() {
        Environment first = freshEnvironment();
        IndexPreparationResult forward = prepare(first, TENANT, V1, taskRoots());
        // Keys are the property IRIs: ".../query-testkit#description" sorts before ".../query-testkit#title".
        assertThat(forward.newlyRegistered()).containsExactly(descriptionPath(), titlePath());

        // The same Fields, in the reverse order, in a fresh environment: the result is the same list.
        Environment second = freshEnvironment();
        IndexPreparationResult reversed = prepare(second, TENANT, V1, reversedTaskRoots());
        assertThat(reversed.newlyRegistered()).isEqualTo(forward.newlyRegistered());
    }

    @Test
    void twoRootsSharingAPropertyRegisterItOnce() {
        // Both roots carry the title Field, so the registration key is shared between the two classes.
        Map<ClassRef, List<Field>> taskFirst = new LinkedHashMap<>();
        taskFirst.put(taskRoot(), taskFields());
        taskFirst.put(personRoot(), personFieldsWithTitle());

        Map<ClassRef, List<Field>> personFirst = new LinkedHashMap<>();
        personFirst.put(personRoot(), personFieldsWithTitle());
        personFirst.put(taskRoot(), taskFields());

        IndexPreparationResult forward = prepare(freshEnvironment(), TENANT, V1, taskFirst);
        IndexPreparationResult reversed = prepare(freshEnvironment(), TENANT, V1, personFirst);

        // One entry per key, in key order: description, personName, title.
        assertThat(forward.newlyRegistered()).containsExactly(descriptionPath(), personNamePath(), titlePath());
        assertThat(reversed.newlyRegistered()).isEqualTo(forward.newlyRegistered());
    }

    @Test
    void searchableFieldsOfEveryRootAreRegistered() {
        Environment env = freshEnvironment();
        Map<ClassRef, List<Field>> roots = new LinkedHashMap<>(taskRoots());
        roots.put(personRoot(), personFields());

        IndexPreparationResult result = prepare(env, TENANT, V1, roots);

        assertThat(result.newlyRegistered()).containsExactly(descriptionPath(), personNamePath(), titlePath());
    }

    @Test
    void prepareIsIdempotentForTheSameVersion() {
        Environment env = freshEnvironment();
        Map<ClassRef, List<Field>> roots = taskRoots();

        IndexPreparationResult firstCall = prepare(env, TENANT, V1, roots);
        assertThat(firstCall.newlyRegistered()).containsExactly(descriptionPath(), titlePath());

        IndexPreparationResult repeat = prepare(env, TENANT, V1, roots);
        assertThat(repeat.newlyRegistered()).isEmpty();

        IndexPreparationResult again = prepare(env, TENANT, V1, roots);
        assertThat(again.newlyRegistered()).isEmpty();
    }

    @Test
    void newlyRegisteredIsImmutable() {
        Environment env = freshEnvironment();
        List<FieldPath> registered = prepare(env, TENANT, V1, taskRoots()).newlyRegistered();

        assertThatThrownBy(() -> registered.add(titlePath())).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void versionIsNotCheckedAgainstTheFields() {
        Environment env = freshEnvironment();
        OntologyVersionRef notDefinedByTheFields = new OntologyVersionRef(2);

        IndexPreparationResult result = prepare(env, TENANT, notDefinedByTheFields, taskRoots());

        assertThat(result.newlyRegistered()).containsExactly(descriptionPath(), titlePath());
    }

    @Test
    void registrationIsPerTenant() {
        Environment env = freshEnvironment();

        prepare(env, TENANT, V1, taskRoots());

        IndexPreparationResult otherTenant = prepare(env, OTHER_TENANT, V1, taskRoots());
        assertThat(otherTenant.newlyRegistered()).containsExactly(descriptionPath(), titlePath());
    }

    /**
     * The one place this contract calls {@link IndexPreparationPort#prepare}. It runs the environment's
     * {@code awaitPrepared} hook after every call, so an asynchronous adapter is waited for before its
     * state is read.
     */
    private static IndexPreparationResult prepare(
            Environment env, TenantId tenant, OntologyVersionRef version, Map<ClassRef, List<Field>> rootFields) {
        IndexPreparationResult result = env.port().prepare(tenant, version, rootFields);
        env.awaitPrepared().run();
        return result;
    }

    // -- Fixture helpers --------------------------------------------------------------------------

    private static Map<ClassRef, List<Field>> taskRoots() {
        return Map.of(taskRoot(), taskFields());
    }

    private static Map<ClassRef, List<Field>> reversedTaskRoots() {
        List<Field> reversed = new ArrayList<>(taskFields());
        Collections.reverse(reversed);
        return Map.of(taskRoot(), reversed);
    }

    private static List<Field> taskFields() {
        return QueryFixtures.taskScope(TENANT).facets();
    }

    private static List<Field> personFields() {
        return QueryFixtures.resolve(TENANT, QueryFixtures.PERSON).facets();
    }

    /** The Task's dueDate Field, a DATE, made searchable: search registration is by capability, not type. */
    private static Field searchableDueDate() {
        Field dueDate = taskFields().stream()
                .filter(field -> field.path().asSlugPath().equals("dueDate"))
                .findFirst()
                .orElseThrow();
        return new Field(
                dueDate.path(),
                dueDate.type(),
                dueDate.nativeDatatypeIri(),
                dueDate.cardinality(),
                new Capabilities(true, false, true, false),
                dueDate.label(),
                dueDate.description(),
                dueDate.conceptScheme(),
                dueDate.restriction(),
                dueDate.inference(),
                dueDate.definedInVersions(),
                dueDate.conflict());
    }

    /** The Person Fields plus the Task's title Field, which carries the same property IRI as Task's own. */
    private static List<Field> personFieldsWithTitle() {
        List<Field> fields = new ArrayList<>(personFields());
        fields.add(taskFields().stream()
                .filter(field -> field.path().asSlugPath().equals("title"))
                .findFirst()
                .orElseThrow());
        return fields;
    }

    private static ClassRef taskRoot() {
        return QueryFixtures.taskScope(TENANT).root();
    }

    private static ClassRef personRoot() {
        return QueryFixtures.resolve(TENANT, QueryFixtures.PERSON).root();
    }

    private static FieldPath titlePath() {
        return QueryFixtures.facetPath(QueryFixtures.taskScope(TENANT), "title");
    }

    private static FieldPath descriptionPath() {
        return QueryFixtures.facetPath(QueryFixtures.taskScope(TENANT), "description");
    }

    private static FieldPath personNamePath() {
        return QueryFixtures.facetPath(QueryFixtures.resolve(TENANT, QueryFixtures.PERSON), "name");
    }
}
