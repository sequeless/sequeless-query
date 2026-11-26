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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.sequeless.query.api.IndexPreparationResult;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.spi.IndexPreparationPort;

/**
 * The mechanical form of the behavioural contract documented on {@link IndexPreparationPort}'s
 * interface-level javadoc. Every {@link IndexPreparationPort} implementation, adapter or test double,
 * is expected to satisfy every clause of that javadoc, and this class exercises each clause against
 * whatever {@link Environment} {@link #freshEnvironment()} supplies.
 *
 * <p>The contract calls {@link IndexPreparationPort#prepare}, then the environment's {@code
 * awaitPrepared} hook, and then asserts the state the port reports. The hook is how an adapter whose
 * preparation is asynchronous says that the preparation is done or durably accepted (ADR 0002). An
 * adapter that prepares synchronously passes a no-op.
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

        assertThatNullPointerException().isThrownBy(() -> env.port().prepare(null, V1, roots));
        assertThatNullPointerException().isThrownBy(() -> env.port().prepare(TENANT, null, roots));
        assertThatNullPointerException().isThrownBy(() -> env.port().prepare(TENANT, V1, null));

        Map<ClassRef, List<Field>> nullKey = new HashMap<>();
        nullKey.put(null, List.of());
        assertThatNullPointerException().isThrownBy(() -> env.port().prepare(TENANT, V1, nullKey));

        Map<ClassRef, List<Field>> nullValue = new HashMap<>();
        nullValue.put(taskRoot(), null);
        assertThatNullPointerException().isThrownBy(() -> env.port().prepare(TENANT, V1, nullValue));
    }

    @Test
    void prepareWithNoRootsIsANoOp() {
        Environment env = freshEnvironment();

        IndexPreparationResult result = env.port().prepare(TENANT, V1, Map.of());

        assertThat(result.newlyRegistered()).isEmpty();
    }

    @Test
    void fieldsWithoutSearchableCapabilityNeedNoPreparation() {
        Environment env = freshEnvironment();
        List<Field> notSearchable = taskFields().stream()
                .filter(field -> !field.capabilities().searchable())
                .toList();
        assertThat(notSearchable).isNotEmpty();

        IndexPreparationResult result = env.port().prepare(TENANT, V1, Map.of(taskRoot(), notSearchable));

        assertThat(result.newlyRegistered()).isEmpty();
    }

    @Test
    void prepareReportsNewlyRegisteredSearchableFieldsInDeterministicOrder() {
        Environment first = freshEnvironment();
        IndexPreparationResult forward = first.port().prepare(TENANT, V1, taskRoots());
        assertThat(forward.newlyRegistered()).containsExactlyInAnyOrder(titlePath(), descriptionPath());

        // The same Fields, in the reverse order, in a fresh environment: the result is the same list.
        Environment second = freshEnvironment();
        IndexPreparationResult reversed = second.port().prepare(TENANT, V1, reversedTaskRoots());
        assertThat(reversed.newlyRegistered()).isEqualTo(forward.newlyRegistered());
    }

    @Test
    void searchableFieldsOfEveryRootAreRegistered() {
        Environment env = freshEnvironment();
        Map<ClassRef, List<Field>> roots = new HashMap<>(taskRoots());
        roots.put(personRoot(), personFields());

        IndexPreparationResult result = env.port().prepare(TENANT, V1, roots);

        assertThat(result.newlyRegistered())
                .containsExactlyInAnyOrder(titlePath(), descriptionPath(), personNamePath());
    }

    @Test
    void prepareIsIdempotentForTheSameVersion() {
        Environment env = freshEnvironment();
        Map<ClassRef, List<Field>> roots = taskRoots();

        IndexPreparationResult firstCall = env.port().prepare(TENANT, V1, roots);
        assertThat(firstCall.newlyRegistered()).containsExactlyInAnyOrder(titlePath(), descriptionPath());

        env.awaitPrepared().run();

        IndexPreparationResult repeat = env.port().prepare(TENANT, V1, roots);
        assertThat(repeat.newlyRegistered()).isEmpty();

        IndexPreparationResult again = env.port().prepare(TENANT, V1, roots);
        assertThat(again.newlyRegistered()).isEmpty();
    }

    @Test
    void newlyRegisteredIsImmutable() {
        Environment env = freshEnvironment();
        List<FieldPath> registered = env.port().prepare(TENANT, V1, taskRoots()).newlyRegistered();

        assertThatThrownBy(() -> registered.add(titlePath())).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void versionIsNotCheckedAgainstTheFields() {
        Environment env = freshEnvironment();
        OntologyVersionRef notDefinedByTheFields = new OntologyVersionRef(2);

        IndexPreparationResult result = env.port().prepare(TENANT, notDefinedByTheFields, taskRoots());

        assertThat(result.newlyRegistered()).containsExactlyInAnyOrder(titlePath(), descriptionPath());
    }

    @Test
    void registrationIsPerTenant() {
        Environment env = freshEnvironment();

        env.port().prepare(TENANT, V1, taskRoots());
        env.awaitPrepared().run();

        IndexPreparationResult otherTenant = env.port().prepare(OTHER_TENANT, V1, taskRoots());
        assertThat(otherTenant.newlyRegistered()).containsExactlyInAnyOrder(titlePath(), descriptionPath());
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
