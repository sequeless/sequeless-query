package org.sequeless.query.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sequeless.query.testkit.QueryFixtures.ASSIGNED_TO_IRI;
import static org.sequeless.query.testkit.QueryFixtures.BELONGS_TO_PROJECT_IRI;
import static org.sequeless.query.testkit.QueryFixtures.DESCRIPTION_IRI;
import static org.sequeless.query.testkit.QueryFixtures.DUE_DATE_IRI;
import static org.sequeless.query.testkit.QueryFixtures.OTHER_TENANT;
import static org.sequeless.query.testkit.QueryFixtures.PERSON_EMAIL_IRI;
import static org.sequeless.query.testkit.QueryFixtures.PERSON_IRI;
import static org.sequeless.query.testkit.QueryFixtures.PERSON_NAME_IRI;
import static org.sequeless.query.testkit.QueryFixtures.PRIORITY_IRI;
import static org.sequeless.query.testkit.QueryFixtures.PROJECT_IRI;
import static org.sequeless.query.testkit.QueryFixtures.PROJECT_NAME_IRI;
import static org.sequeless.query.testkit.QueryFixtures.PROJECT_STATUS_IRI;
import static org.sequeless.query.testkit.QueryFixtures.STATUS_IRI;
import static org.sequeless.query.testkit.QueryFixtures.TASK_IRI;
import static org.sequeless.query.testkit.QueryFixtures.TENANT;
import static org.sequeless.query.testkit.QueryFixtures.TITLE_IRI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.Filters;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.BrowseResult;
import org.sequeless.query.api.Direction;
import org.sequeless.query.api.FacetBucket;
import org.sequeless.query.api.FieldKey;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.Page;
import org.sequeless.query.api.PropertyRef;
import org.sequeless.query.api.Sort;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.spi.BrowsePort;

/**
 * The mechanical form of the behavioural contract documented on {@link BrowsePort}'s interface-level
 * javadoc. Every {@link BrowsePort} implementation, adapter or test double, is expected to satisfy
 * every clause of that javadoc, and this class exercises each clause against whatever {@link
 * Environment} {@link #freshEnvironment()} supplies.
 *
 * <p>To use this contract, extend it from a test class in your own module and implement {@link
 * #freshEnvironment()} to return a brand-new, empty {@link ObjectSeeder} paired with a {@link
 * BrowsePort} that answers queries over the same storage:
 *
 * <pre>{@code
 * class MyAdapterBrowseContractTest extends BrowseContract {
 *     protected Environment freshEnvironment() {
 *         MyStore store = new MyStore();
 *         return new Environment(store::seed, new MyBrowsePort(store));
 *     }
 * }
 * }</pre>
 *
 * <p>The scenario is the Task, Project and Person classes of {@code QueryFixtures}. The scopes come
 * from {@code query-field-testfixtures}, so the contract needs no ontology, no schema and no
 * particular storage technology.
 *
 * <p>Three clauses of {@link BrowsePort} are pinned with fixtures here. A reference facet bucket
 * drops a missing or soft-deleted target. Free text is matched against the root's own
 * searchable Fields, and a traversal Field that an expansion adds is not searched. A filter,
 * sort or facet shape the port does not evaluate is rejected with {@link InvalidQueryException}: AnyFilter,
 * a traversal path used as a filter, sort or facet path, {@code like} and {@code not like}. Free text
 * does not reach a property that is searchable only on a concrete subtype. That is a documented limit, so it
 * is not pinned here.
 */
public abstract class BrowseContract {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate FEB_1 = LocalDate.of(2026, 2, 1);
    private static final LocalDate FEB_2 = LocalDate.of(2026, 2, 2);
    private static final LocalDate FEB_3 = LocalDate.of(2026, 2, 3);
    private static final LocalDate FEB_4 = LocalDate.of(2026, 2, 4);
    private static final LocalDate FEB_5 = LocalDate.of(2026, 2, 5);

    private static final PropertyRef TITLE = new PropertyRef(TITLE_IRI);
    private static final PropertyRef DESCRIPTION = new PropertyRef(DESCRIPTION_IRI);
    private static final PropertyRef STATUS = new PropertyRef(STATUS_IRI);
    private static final PropertyRef PRIORITY = new PropertyRef(PRIORITY_IRI);
    private static final PropertyRef DUE_DATE = new PropertyRef(DUE_DATE_IRI);
    private static final PropertyRef ASSIGNED_TO = new PropertyRef(ASSIGNED_TO_IRI);
    private static final PropertyRef BELONGS_TO_PROJECT = new PropertyRef(BELONGS_TO_PROJECT_IRI);
    private static final PropertyRef PROJECT_NAME = new PropertyRef(PROJECT_NAME_IRI);
    private static final PropertyRef PROJECT_STATUS = new PropertyRef(PROJECT_STATUS_IRI);
    private static final PropertyRef PERSON_NAME = new PropertyRef(PERSON_NAME_IRI);
    private static final PropertyRef PERSON_EMAIL = new PropertyRef(PERSON_EMAIL_IRI);

    // Unsigned order: ID_A < ID_B < ID_C < ID_D. The signed UUID.compareTo order would be D, B, A, C.
    private static final ObjectId ID_A = ObjectId.parse("00000000-0000-4000-7fff-ffffffffffff");
    private static final ObjectId ID_B = ObjectId.parse("00000000-0000-4000-8000-000000000000");
    private static final ObjectId ID_C = ObjectId.parse("7fffffff-ffff-4fff-bfff-ffffffffffff");
    private static final ObjectId ID_D = ObjectId.parse("80000000-0000-4000-8000-000000000000");

    /**
     * A seeding hook paired with the {@link BrowsePort} that answers queries over the same storage. A
     * conforming port only makes sense against objects seeded through the same storage.
     *
     * @param seeder the hook the contract seeds fixture objects through; must not be {@code null}
     * @param browsePort the port under test; must not be {@code null}
     */
    public record Environment(ObjectSeeder seeder, BrowsePort browsePort) {

        public Environment {
            Objects.requireNonNull(seeder, "seeder must not be null");
            Objects.requireNonNull(browsePort, "browsePort must not be null");
        }
    }

    /**
     * @return a fresh {@link Environment} with empty storage; invoked for every {@code @Test} method, so
     *     it must not carry state over from an earlier test
     */
    protected abstract Environment freshEnvironment();

    // -- Null handling and the query shape ---------------------------------------------------------

    @Test
    void browseRejectsNullArguments() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);
        BrowseQuery query = plain(new Page(0, 20));

        assertThatNullPointerException().isThrownBy(() -> env.browsePort().browse(null, query));
        assertThatNullPointerException().isThrownBy(() -> env.browsePort().browse(scope, null));

        List<Sort> withNull = Arrays.asList(new Sort(priorityPath(scope), Direction.ASC), null);
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new BrowseQuery(Optional.empty(), Optional.empty(), withNull, new Page(0, 20), List.of()));
    }

    // -- Filters ----------------------------------------------------------------------------------

    @Test
    void equalityFilterMatchesOnlyExactValue() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        ObjectId open1 = seedTask(env, BASE, "Open task one", "OPEN", 1, FEB_1, null, null);
        ObjectId open2 = seedTask(env, BASE.plusSeconds(1), "Open task two", "OPEN", 2, FEB_2, null, null);
        seedTask(env, BASE.plusSeconds(2), "Closed task", "CLOSED", 3, FEB_3, null, null);

        BrowseResult result = env.browsePort().browse(scope, filtered(is(STATUS_IRI, text("OPEN")), new Page(0, 20)));

        assertThat(result.items()).extracting(ObjectView::id).containsExactlyInAnyOrder(open1, open2);
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    void referenceFilterMatchesByTargetId() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        ObjectId alice = seedPerson(env, ObjectId.random(), BASE, "Alice Smith", "alice@example.com");
        ObjectId bob = seedPerson(env, ObjectId.random(), BASE.plusSeconds(1), "Bob Jones", "bob@example.com");
        ObjectId taskForAlice = seedTask(env, BASE.plusSeconds(2), "Alice's task", "OPEN", 1, FEB_1, alice, null);
        seedTask(env, BASE.plusSeconds(3), "Bob's task", "OPEN", 2, FEB_2, bob, null);

        BrowseResult result =
                env.browsePort().browse(scope, filtered(is(ASSIGNED_TO_IRI, text(idText(alice))), new Page(0, 20)));

        assertThat(result.items()).extracting(ObjectView::id).containsExactly(taskForAlice);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void dateRangeFilterUsesGteAndLte() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        seedTask(env, BASE, "Too early", "OPEN", 1, LocalDate.of(2026, 1, 1), null, null);
        ObjectId inWindow =
                seedTask(env, BASE.plusSeconds(1), "In window", "OPEN", 2, LocalDate.of(2026, 2, 15), null, null);
        seedTask(env, BASE.plusSeconds(2), "Too late", "OPEN", 3, LocalDate.of(2026, 3, 1), null, null);

        FilterNode window = Filters.and(
                Filters.field(DUE_DATE_IRI, "gte", text("2026-02-01")),
                Filters.field(DUE_DATE_IRI, "lte", text("2026-02-28")));
        BrowseResult result = env.browsePort().browse(scope, filtered(window, new Page(0, 20)));

        assertThat(result.items()).extracting(ObjectView::id).containsExactly(inWindow);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void candidatesAreRestrictedToTheConcreteTypesOfTheScope() {
        Environment env = freshEnvironment();
        FieldScope tasks = QueryFixtures.taskScope(TENANT);

        ObjectId task = seedTask(env, BASE, "A task", "OPEN", 1, FEB_1, null, null);
        seedProject(env, BASE.plusSeconds(1), "A project", "ACTIVE");

        BrowseResult result = env.browsePort().browse(tasks, plain(new Page(0, 20)));

        assertThat(result.items()).extracting(ObjectView::id).containsExactly(task);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void emptyConcreteTypesGivesEmptyResultWithoutError() {
        Environment env = freshEnvironment();
        seedTask(env, BASE, "A task", "OPEN", 1, FEB_1, null, null);
        FieldScope noTypes = QueryFixtures.withConcreteTypes(QueryFixtures.taskScope(TENANT), Set.of());

        BrowseResult result = env.browsePort().browse(noTypes, plain(new Page(0, 20)));

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void workItemQueryReturnsBothTaskAndProjectSubtypes() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskAndProjectScope(TENANT);

        ObjectId task = seedTask(env, BASE, "A task", "OPEN", 1, FEB_1, null, null);
        ObjectId project = seedProject(env, BASE.plusSeconds(1), "A project", "ACTIVE");

        BrowseResult result = env.browsePort().browse(scope, plain(new Page(0, 20)));

        assertThat(result.items()).extracting(ObjectView::id).containsExactlyInAnyOrder(task, project);
        assertThat(result.items())
                .extracting(item -> item.type().iri())
                .containsExactlyInAnyOrder(TASK_IRI, PROJECT_IRI);
    }

    @Test
    void anyFilterIsRejectedAsInvalidQuery() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);
        seedTask(env, BASE, "A task", "OPEN", 1, FEB_1, null, null);

        assertRejectedAsInvalidQuery(env, scope, Filters.any("is", text("OPEN")));
    }

    @Test
    void traversalPathFilterIsRejectedAsInvalidQuery() {
        Environment env = freshEnvironment();
        // belongsToProject.name is a real, filterable traversal Field of the expanded scope, so only its shape is
        // rejected.
        FieldScope scope = QueryFixtures.taskScopeExpandedToProject(TENANT);
        seedTask(env, BASE, "A task", "OPEN", 1, FEB_1, null, null);

        // The key is taken from that Field in the scope, as the sort and facet tests take theirs, so a missing Field
        // fails the lookup and a hand-built key cannot pass the test.
        FieldPath projectName = QueryFixtures.facetPath(scope, "belongsToProject.name");
        assertThat(projectName.isTraversing()).isTrue();
        String key = FieldKey.encode(projectName);

        // The rejection names the traversal path, so it is not an unknown-path rejection.
        assertRejectedNamingKey(env, scope, filtered(is(key, text("Budget project")), new Page(0, 20)), key);
    }

    @Test
    void likeFilterIsRejectedAsInvalidQuery() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);
        seedTask(env, BASE, "A task", "OPEN", 1, FEB_1, null, null);

        assertRejectedNamingKey(
                env, scope, filtered(Filters.field(TITLE_IRI, "like", text("A%")), new Page(0, 20)), TITLE_IRI);
    }

    @Test
    void notLikeFilterIsRejectedAsInvalidQuery() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);
        seedTask(env, BASE, "A task", "OPEN", 1, FEB_1, null, null);

        assertRejectedNamingKey(
                env, scope, filtered(Filters.field(TITLE_IRI, "not like", text("A%")), new Page(0, 20)), TITLE_IRI);
    }

    // -- Sort and default order -------------------------------------------------------------------

    @Test
    void numericSortOrdersAscendingAndDescending() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        ObjectId low = seedTask(env, BASE, "Low priority", "OPEN", 1, FEB_1, null, null);
        ObjectId mid = seedTask(env, BASE.plusSeconds(1), "Mid priority", "OPEN", 5, FEB_2, null, null);
        ObjectId high = seedTask(env, BASE.plusSeconds(2), "High priority", "OPEN", 9, FEB_3, null, null);

        BrowseResult ascending = env.browsePort()
                .browse(scope, withSorts(List.of(new Sort(priorityPath(scope), Direction.ASC)), new Page(0, 20)));
        assertThat(ascending.items()).extracting(ObjectView::id).containsExactly(low, mid, high);

        BrowseResult descending = env.browsePort()
                .browse(scope, withSorts(List.of(new Sort(priorityPath(scope), Direction.DESC)), new Page(0, 20)));
        assertThat(descending.items()).extracting(ObjectView::id).containsExactly(high, mid, low);
    }

    @Test
    void missingSortValueSortsLastWhenAscendingAndFirstWhenDescending() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        ObjectId low = seedTask(env, BASE, "Low priority", "OPEN", 1, FEB_1, null, null);
        ObjectId none =
                seed(env, ObjectId.random(), TASK_IRI, BASE.plusSeconds(1), Map.of(TITLE, Value.text("No priority")));
        ObjectId high = seedTask(env, BASE.plusSeconds(2), "High priority", "OPEN", 9, FEB_3, null, null);

        BrowseResult ascending = env.browsePort()
                .browse(scope, withSorts(List.of(new Sort(priorityPath(scope), Direction.ASC)), new Page(0, 20)));
        assertThat(ascending.items()).extracting(ObjectView::id).containsExactly(low, high, none);

        BrowseResult descending = env.browsePort()
                .browse(scope, withSorts(List.of(new Sort(priorityPath(scope), Direction.DESC)), new Page(0, 20)));
        assertThat(descending.items()).extracting(ObjectView::id).containsExactly(none, high, low);
    }

    @Test
    void paginationRespectsPageAndSize() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        List<ObjectId> expectedOrder = List.of(
                seedTask(env, BASE, "Task 1", "OPEN", 1, FEB_1, null, null),
                seedTask(env, BASE.plusSeconds(1), "Task 2", "OPEN", 2, FEB_2, null, null),
                seedTask(env, BASE.plusSeconds(2), "Task 3", "OPEN", 3, FEB_3, null, null),
                seedTask(env, BASE.plusSeconds(3), "Task 4", "OPEN", 4, FEB_4, null, null),
                seedTask(env, BASE.plusSeconds(4), "Task 5", "OPEN", 5, FEB_5, null, null));

        // No explicit sort: relies on the documented default order, (createdAt, id) ascending.
        BrowseResult page0 = env.browsePort().browse(scope, plain(new Page(0, 2)));
        assertThat(page0.items()).extracting(ObjectView::id).containsExactlyElementsOf(expectedOrder.subList(0, 2));
        assertThat(page0.total()).isEqualTo(5);

        BrowseResult page1 = env.browsePort().browse(scope, plain(new Page(1, 2)));
        assertThat(page1.items()).extracting(ObjectView::id).containsExactlyElementsOf(expectedOrder.subList(2, 4));
        assertThat(page1.total()).isEqualTo(5);

        BrowseResult page2 = env.browsePort().browse(scope, plain(new Page(2, 2)));
        assertThat(page2.items()).extracting(ObjectView::id).containsExactlyElementsOf(expectedOrder.subList(4, 5));
        assertThat(page2.total()).isEqualTo(5);
    }

    @Test
    void pageOverrunIsEmptyWithTheCorrectTotal() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);
        for (int i = 0; i < 5; i++) {
            seedTask(env, BASE.plusSeconds(i), "Task " + i, "OPEN", i, FEB_1, null, null);
        }

        BrowseResult overrun = env.browsePort().browse(scope, plain(new Page(5, 2)));

        assertThat(overrun.items()).isEmpty();
        assertThat(overrun.total()).isEqualTo(5);
    }

    @Test
    void paginationOverUnionOfTypesIsOrderedByCreatedAtThenId() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskAndProjectScope(TENANT);

        List<ObjectId> expectedOrder = List.of(
                seedTask(env, BASE, "Task 1", "OPEN", 1, FEB_1, null, null),
                seedProject(env, BASE.plusSeconds(1), "Project 1", "ACTIVE"),
                seedTask(env, BASE.plusSeconds(2), "Task 2", "OPEN", 2, FEB_2, null, null),
                seedProject(env, BASE.plusSeconds(3), "Project 2", "ACTIVE"),
                seedTask(env, BASE.plusSeconds(4), "Task 3", "OPEN", 3, FEB_3, null, null));

        BrowseResult page0 = env.browsePort().browse(scope, plain(new Page(0, 2)));
        assertThat(page0.items()).extracting(ObjectView::id).containsExactlyElementsOf(expectedOrder.subList(0, 2));
        assertThat(page0.total()).isEqualTo(5);

        BrowseResult page2 = env.browsePort().browse(scope, plain(new Page(2, 2)));
        assertThat(page2.items()).extracting(ObjectView::id).containsExactlyElementsOf(expectedOrder.subList(4, 5));
        assertThat(page2.total()).isEqualTo(5);

        BrowseResult overrun = env.browsePort().browse(scope, plain(new Page(3, 2)));
        assertThat(overrun.items()).isEmpty();
        assertThat(overrun.total()).isEqualTo(5);
    }

    @Test
    void defaultOrderBreaksCreatedAtTiesByUnsignedId() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        seedTaskWithId(env, ID_D, BASE, "Tied task", 1);
        seedTaskWithId(env, ID_C, BASE, "Tied task", 1);
        seedTaskWithId(env, ID_B, BASE, "Tied task", 1);
        seedTaskWithId(env, ID_A, BASE, "Tied task", 1);

        assertThat(browseIds(env, scope, plain(new Page(0, 4)))).isEqualTo(List.of(ID_A, ID_B, ID_C, ID_D));
        assertThat(browseIds(env, scope, plain(new Page(1, 2)))).isEqualTo(List.of(ID_C, ID_D));
    }

    @Test
    void explicitSortBreaksTiesByUnsignedIdInEitherDirection() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        seedTaskWithId(env, ID_D, BASE, "Tied task", 5);
        seedTaskWithId(env, ID_C, BASE.plusSeconds(1), "Tied task", 5);

        for (Direction direction : Direction.values()) {
            BrowseQuery query = withSorts(List.of(new Sort(priorityPath(scope), direction)), new Page(0, 20));
            assertThat(browseIds(env, scope, query)).isEqualTo(List.of(ID_C, ID_D));
        }
    }

    @Test
    void referenceSortOrdersByUnsignedTargetId() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        seedPerson(env, ID_C, BASE, "Person C", "c@example.com");
        seedPerson(env, ID_D, BASE, "Person D", "d@example.com");

        ObjectId forD = seedTask(env, BASE.plusSeconds(1), "For D", "OPEN", 1, FEB_1, ID_D, null);
        ObjectId forC = seedTask(env, BASE.plusSeconds(2), "For C", "OPEN", 2, FEB_2, ID_C, null);

        FieldPath assignedTo = QueryFixtures.facetPath(scope, "assignedTo");
        BrowseQuery ascending = withSorts(List.of(new Sort(assignedTo, Direction.ASC)), new Page(0, 20));
        assertThat(browseIds(env, scope, ascending)).isEqualTo(List.of(forC, forD));

        BrowseQuery descending = withSorts(List.of(new Sort(assignedTo, Direction.DESC)), new Page(0, 20));
        assertThat(browseIds(env, scope, descending)).isEqualTo(List.of(forD, forC));
    }

    @Test
    void secondSortKeyBreaksTiesOfTheFirst() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        // With fixed ids, the id tie-break alone gives [closedLow, closedHigh, openLow, openHigh]. Status ASC
        // alone and priority DESC alone each give an order other than the expected one, so both keys are used.
        ObjectId closedLow = seedTaskWithStatus(env, ID_A, "CLOSED", 3);
        ObjectId closedHigh = seedTaskWithStatus(env, ID_B, "CLOSED", 7);
        ObjectId openLow = seedTaskWithStatus(env, ID_C, "OPEN", 1);
        ObjectId openHigh = seedTaskWithStatus(env, ID_D, "OPEN", 9);

        List<Sort> sorts = List.of(
                new Sort(QueryFixtures.facetPath(scope, "status"), Direction.ASC),
                new Sort(priorityPath(scope), Direction.DESC));

        assertThat(browseIds(env, scope, withSorts(sorts, new Page(0, 20))))
                .containsExactly(closedHigh, closedLow, openHigh, openLow);
    }

    @Test
    void traversalSortIsRejectedAsInvalidQuery() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScopeExpandedToProject(TENANT);
        seedTask(env, BASE, "A task", "OPEN", 1, FEB_1, null, null);

        // belongsToProject.status is a sortable traversal Field of the expanded scope, so only its shape is rejected.
        FieldPath projectStatus = QueryFixtures.facetPath(scope, "belongsToProject.status");
        assertRejectedNamingKey(
                env,
                scope,
                withSorts(List.of(new Sort(projectStatus, Direction.ASC)), new Page(0, 20)),
                FieldKey.encode(projectStatus));
    }

    // -- Soft delete and tenant scope -------------------------------------------------------------

    @Test
    void softDeletedObjectsAreNeverReturned() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        ObjectId id = seedTask(env, BASE, "Soon deleted", "OPEN", 1, FEB_1, null, null);
        ObjectId visible = seedTask(env, BASE.plusSeconds(1), "Still here", "CLOSED", 2, FEB_2, null, null);
        env.seeder()
                .seed(ObjectViews.of(
                        id,
                        TASK_IRI,
                        TENANT,
                        2,
                        Map.of(TITLE, Value.text("Soon deleted"), STATUS, Value.text("OPEN")),
                        BASE.plusSeconds(2),
                        true));

        FieldPath status = QueryFixtures.facetPath(scope, "status");
        BrowseResult result = env.browsePort().browse(scope, withFacets(new Page(0, 20), List.of(status)));

        assertThat(result.items()).extracting(ObjectView::id).containsExactly(visible);
        assertThat(result.total()).isEqualTo(1);
        // The deleted object's status group is gone too: the only bucket is the visible CLOSED task.
        assertThat(result.facets().get(status)).containsExactly(new FacetBucket("CLOSED", 1));
    }

    @Test
    void browseIsTenantScoped() {
        Environment env = freshEnvironment();
        FieldScope otherTenant = QueryFixtures.taskScope(OTHER_TENANT);
        FieldScope tenant = QueryFixtures.taskScope(TENANT);

        ObjectId id = seedTask(env, BASE, "Tenant task", "OPEN", 1, FEB_1, null, null);

        BrowseResult otherResult = env.browsePort().browse(otherTenant, plain(new Page(0, 20)));
        assertThat(otherResult.items()).isEmpty();
        assertThat(otherResult.total()).isZero();

        BrowseQuery facetQuery = withFacets(new Page(0, 20), List.of(QueryFixtures.facetPath(otherTenant, "status")));
        BrowseResult otherFacets = env.browsePort().browse(otherTenant, facetQuery);
        assertThat(otherFacets.facets().get(QueryFixtures.facetPath(otherTenant, "status")))
                .isEmpty();

        BrowseResult ownResult = env.browsePort().browse(tenant, plain(new Page(0, 20)));
        assertThat(ownResult.items()).extracting(ObjectView::id).containsExactly(id);
        assertThat(ownResult.total()).isEqualTo(1);
    }

    // -- Free text --------------------------------------------------------------------------------

    @Test
    void textSearchRanksBestMatchFirst() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        ObjectId bestMatch = seedTaskWithDescription(
                env, BASE, "Quarterly budget review", "Review the quarterly budget numbers", "OPEN", 1, FEB_1);
        seedTaskWithDescription(
                env, BASE.plusSeconds(1), "Unrelated task", "Something else entirely", "OPEN", 2, FEB_2);
        seedTaskWithDescription(
                env, BASE.plusSeconds(2), "Another task", "Nothing to do with the topic", "OPEN", 3, FEB_3);

        BrowseQuery query = textQuery("quarterly budget", new Page(0, 20));
        BrowseResult result = env.browsePort().browse(scope, query);

        assertThat(result.items()).isNotEmpty();
        assertThat(result.items().get(0).id()).isEqualTo(bestMatch);
    }

    @Test
    void textSearchBreaksRankTiesByCreatedAtThenUnsignedId() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        seedTaskWithId(env, ID_A, BASE.plusSeconds(1), "Tied task", 1);
        seedTaskWithId(env, ID_D, BASE, "Tied task", 1);
        seedTaskWithId(env, ID_C, BASE, "Tied task", 1);

        assertThat(browseIds(env, scope, textQuery("tied", new Page(0, 20)))).isEqualTo(List.of(ID_C, ID_D, ID_A));
    }

    @Test
    void textSearchNarrowsEveryFacet() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        seedTaskWithDescription(env, BASE, "Quarterly budget review", "Review the numbers", "OPEN", 1, FEB_1);
        seedTaskWithDescription(env, BASE.plusSeconds(1), "Unrelated task", "Something else", "OPEN", 2, FEB_2);
        seedTaskWithDescription(env, BASE.plusSeconds(2), "Another task", "Nothing to do", "CLOSED", 3, FEB_3);

        FieldPath status = QueryFixtures.facetPath(scope, "status");
        BrowseQuery query =
                new BrowseQuery(Optional.empty(), Optional.of("budget"), List.of(), new Page(0, 20), List.of(status));
        BrowseResult result = env.browsePort().browse(scope, query);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.facets().get(status)).containsExactly(new FacetBucket("OPEN", 1));
    }

    @Test
    void textSearchReadsOnlyTheRootsSearchableFields() {
        Environment env = freshEnvironment();
        // The expansion adds the Project's searchable name to the scope as a traversal Field. Text search reads only
        // the root's own searchable Fields, so the name of the Project a task belongs to does not match the task.
        FieldScope expanded = QueryFixtures.taskScopeExpandedToProject(TENANT);

        ObjectId budgetProject = seedProject(env, BASE, "Budget project", "ACTIVE");
        ObjectId titled = seedTask(env, BASE.plusSeconds(1), "Quarterly budget", "OPEN", 1, FEB_1, null, null);
        ObjectId linked = seedTask(env, BASE.plusSeconds(2), "Linked task", "OPEN", 2, FEB_2, null, budgetProject);

        assertThat(browseIds(env, expanded, plain(new Page(0, 20)))).containsExactly(titled, linked);
        assertThat(browseIds(env, expanded, textQuery("budget", new Page(0, 20))))
                .containsExactly(titled);
    }

    // -- Facets -----------------------------------------------------------------------------------

    @Test
    void facetCountsGroupByScalarPropertyReflectMultiSelectExclusion() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        ObjectId openLow = seedTask(env, BASE, "Open low", "OPEN", 1, FEB_1, null, null);
        seedTask(env, BASE.plusSeconds(1), "Open high", "OPEN", 2, FEB_2, null, null);
        seedTask(env, BASE.plusSeconds(2), "Closed low", "CLOSED", 1, FEB_3, null, null);
        seedTask(env, BASE.plusSeconds(3), "Closed high", "CLOSED", 2, FEB_4, null, null);

        // Both a criterion on the facet property (status is OPEN) and an unrelated one (priority is 1) apply.
        // The status buckets are counted with the status conjunct removed, so they show OPEN and CLOSED
        // among the priority-1 objects, not only the OPEN value the result is narrowed to.
        FieldPath status = QueryFixtures.facetPath(scope, "status");
        BrowseQuery query = new BrowseQuery(
                Optional.of(Filters.and(is(STATUS_IRI, text("OPEN")), is(PRIORITY_IRI, number(1)))),
                Optional.empty(),
                List.of(),
                new Page(0, 20),
                List.of(status));
        BrowseResult result = env.browsePort().browse(scope, query);

        assertThat(result.items()).extracting(ObjectView::id).containsExactly(openLow);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.facets().get(status))
                .containsExactlyInAnyOrder(new FacetBucket("OPEN", 1), new FacetBucket("CLOSED", 1));
    }

    @Test
    void facetOnSoleFilterOfSamePathCountsMatchAll() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        seedTask(env, BASE, "Open one", "OPEN", 1, FEB_1, null, null);
        seedTask(env, BASE.plusSeconds(1), "Open two", "OPEN", 2, FEB_2, null, null);
        seedTask(env, BASE.plusSeconds(2), "Closed one", "CLOSED", 3, FEB_3, null, null);
        seedTask(env, BASE.plusSeconds(3), "Closed two", "CLOSED", 4, FEB_4, null, null);

        // The filter is one FieldFilter on the facet path, so it is removed for the count and leaves match-all.
        FieldPath status = QueryFixtures.facetPath(scope, "status");
        BrowseQuery query = new BrowseQuery(
                Optional.of(is(STATUS_IRI, text("OPEN"))),
                Optional.empty(),
                List.of(),
                new Page(0, 20),
                List.of(status));
        BrowseResult result = env.browsePort().browse(scope, query);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.facets().get(status))
                .containsExactly(new FacetBucket("CLOSED", 2), new FacetBucket("OPEN", 2));
    }

    @Test
    void facetCountsOnReferencePropertyBucketByTargetId() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        ObjectId alice = seedPerson(env, ObjectId.random(), BASE, "Alice Smith", "alice@example.com");
        ObjectId bob = seedPerson(env, ObjectId.random(), BASE.plusSeconds(1), "Bob Jones", "bob@example.com");

        seedTask(env, BASE.plusSeconds(2), "Alice task one", "OPEN", 1, FEB_1, alice, null);
        seedTask(env, BASE.plusSeconds(3), "Alice task two", "OPEN", 2, FEB_2, alice, null);
        seedTask(env, BASE.plusSeconds(4), "Bob task", "OPEN", 3, FEB_3, bob, null);

        FieldPath assignedTo = QueryFixtures.facetPath(scope, "assignedTo");
        BrowseResult result = env.browsePort().browse(scope, withFacets(new Page(0, 20), List.of(assignedTo)));

        assertThat(result.facets().get(assignedTo))
                .containsExactly(new FacetBucket(idText(alice), 2), new FacetBucket(idText(bob), 1));
    }

    @Test
    void referenceFacetOrdersByCountBeforeTargetId() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        seedPerson(env, ID_A, BASE, "Alice Smith", "alice@example.com");
        seedPerson(env, ID_C, BASE.plusSeconds(1), "Bob Jones", "bob@example.com");

        seedTask(env, BASE.plusSeconds(2), "Alice task", "OPEN", 1, FEB_1, ID_A, null);
        seedTask(env, BASE.plusSeconds(3), "Bob task one", "OPEN", 2, FEB_2, ID_C, null);
        seedTask(env, BASE.plusSeconds(4), "Bob task two", "OPEN", 3, FEB_3, ID_C, null);

        FieldPath assignedTo = QueryFixtures.facetPath(scope, "assignedTo");
        BrowseResult result = env.browsePort().browse(scope, withFacets(new Page(0, 20), List.of(assignedTo)));

        // Id order puts ID_A first, but the count decides: ID_C (2) before ID_A (1).
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.facets().get(assignedTo))
                .containsExactly(new FacetBucket(idText(ID_C), 2), new FacetBucket(idText(ID_A), 1));
    }

    @Test
    void referenceFacetTiesOrderByCodePointOfTheTargetId() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        seedPerson(env, ID_A, BASE, "alice", "alice@example.com");
        seedPerson(env, ID_C, BASE.plusSeconds(1), "Bob", "bob@example.com");

        // Seeded with ID_C first, so the result cannot be insertion order.
        seedTask(env, BASE.plusSeconds(2), "Bob task", "OPEN", 2, FEB_2, ID_C, null);
        seedTask(env, BASE.plusSeconds(3), "Alice task", "OPEN", 1, FEB_1, ID_A, null);

        FieldPath assignedTo = QueryFixtures.facetPath(scope, "assignedTo");
        BrowseResult result = env.browsePort().browse(scope, withFacets(new Page(0, 20), List.of(assignedTo)));

        assertThat(result.facets().get(assignedTo))
                .containsExactly(new FacetBucket(idText(ID_A), 1), new FacetBucket(idText(ID_C), 1));
    }

    @Test
    void facetTiesOrderByCodePoint() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        seedTask(env, BASE, "Lower case", "b", 1, FEB_1, null, null);
        seedTask(env, BASE.plusSeconds(1), "Upper case", "B", 2, FEB_2, null, null);

        FieldPath status = QueryFixtures.facetPath(scope, "status");
        BrowseResult result = env.browsePort().browse(scope, withFacets(new Page(0, 20), List.of(status)));

        // Equal counts: U+0042 (B) sorts before U+0062 (b) in code point order.
        assertThat(result.facets().get(status)).containsExactly(new FacetBucket("B", 1), new FacetBucket("b", 1));
    }

    @Test
    void scalarFacetOrdersByCountDescendingBeforeCodePoint() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        // Code point, case-insensitive and insertion order all put "A" first, so only count order
        // gives [B:2, A:1].
        seedTask(env, BASE, "Upper case A", "A", 1, FEB_1, null, null);
        seedTask(env, BASE.plusSeconds(1), "Upper case B one", "B", 2, FEB_2, null, null);
        seedTask(env, BASE.plusSeconds(2), "Upper case B two", "B", 3, FEB_3, null, null);

        FieldPath status = QueryFixtures.facetPath(scope, "status");
        BrowseResult result = env.browsePort().browse(scope, withFacets(new Page(0, 20), List.of(status)));

        assertThat(result.facets().get(status)).containsExactly(new FacetBucket("B", 2), new FacetBucket("A", 1));
    }

    @Test
    void scalarFacetDropsObjectsWithoutAValue() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        seedTask(env, BASE, "Lower case", "b", 1, FEB_1, null, null);
        seedTask(env, BASE.plusSeconds(1), "Upper case", "B", 2, FEB_2, null, null);
        // No status: this object contributes to no bucket of the status facet.
        seed(
                env,
                ObjectId.random(),
                TASK_IRI,
                BASE.plusSeconds(2),
                Map.of(TITLE, Value.text("No status"), PRIORITY, Value.integer(3)));

        FieldPath status = QueryFixtures.facetPath(scope, "status");
        BrowseResult result = env.browsePort().browse(scope, withFacets(new Page(0, 20), List.of(status)));

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.facets().get(status)).containsExactly(new FacetBucket("B", 1), new FacetBucket("b", 1));
    }

    @Test
    void facetOrdersSupplementaryAfterBmpWhenCountsTie() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        // Code point order: U+00E9 < U+FFFD < U+1F600 (the emoji, a surrogate pair in UTF-16).
        // UTF-16 code units would put the surrogate pair (0xD83D) before U+FFFD (0xFFFD).
        seedTask(env, BASE, "Emoji", "😀", 3, FEB_3, null, null);
        seedTask(env, BASE.plusSeconds(1), "Accented", "é", 1, FEB_1, null, null);
        seedTask(env, BASE.plusSeconds(2), "Replacement", "�", 2, FEB_2, null, null);

        FieldPath status = QueryFixtures.facetPath(scope, "status");
        BrowseResult result = env.browsePort().browse(scope, withFacets(new Page(0, 20), List.of(status)));

        assertThat(result.facets().get(status))
                .containsExactly(new FacetBucket("é", 1), new FacetBucket("�", 1), new FacetBucket("😀", 1));
    }

    @Test
    void orConjunctNarrowsEveryFacetCountWhole() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        ObjectId openOne = seedTask(env, BASE, "Open one", "OPEN", 1, FEB_1, null, null);
        // Closed one fails the OR. If the OR were dropped from the count, it would add a CLOSED bucket.
        seedTask(env, BASE.plusSeconds(1), "Closed one", "CLOSED", 1, FEB_2, null, null);
        // Closed nine and open nine both fail the priority-is-1 conjunct.
        seedTask(env, BASE.plusSeconds(2), "Closed nine", "CLOSED", 9, FEB_3, null, null);
        seedTask(env, BASE.plusSeconds(3), "Open nine", "OPEN", 9, FEB_4, null, null);

        // The OR mentions status but is not a FieldFilter on it, so it is kept whole in the count.
        FieldPath status = QueryFixtures.facetPath(scope, "status");
        FilterNode filter = Filters.and(
                Filters.or(is(STATUS_IRI, text("OPEN")), is(PRIORITY_IRI, number(9))), is(PRIORITY_IRI, number(1)));
        BrowseQuery query =
                new BrowseQuery(Optional.of(filter), Optional.empty(), List.of(), new Page(0, 20), List.of(status));
        BrowseResult result = env.browsePort().browse(scope, query);

        assertThat(result.items()).extracting(ObjectView::id).containsExactly(openOne);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.facets().get(status)).containsExactly(new FacetBucket("OPEN", 1));
    }

    @Test
    void referenceFacetDropsMissingAndSoftDeletedTargets() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        ObjectId alice = seedPerson(env, ObjectId.random(), BASE, "Alice Smith", "alice@example.com");
        ObjectId carol = ObjectId.random();
        softDeletePerson(env, carol, BASE.plusSeconds(1), "Carol King", "carol@example.com");
        ObjectId missing = ObjectId.random(); // a target that is never seeded

        seedTask(env, BASE.plusSeconds(2), "Alice one", "OPEN", 1, FEB_1, alice, null);
        seedTask(env, BASE.plusSeconds(3), "Alice two", "OPEN", 2, FEB_2, alice, null);
        seedTask(env, BASE.plusSeconds(4), "Carol task", "OPEN", 3, FEB_3, carol, null);
        seedTask(env, BASE.plusSeconds(5), "Missing task", "OPEN", 4, FEB_4, missing, null);

        FieldPath assignedTo = QueryFixtures.facetPath(scope, "assignedTo");
        BrowseResult result = env.browsePort().browse(scope, withFacets(new Page(0, 20), List.of(assignedTo)));

        // The tasks that point at the deleted and the missing target still match. Their buckets are absent.
        assertThat(result.total()).isEqualTo(4);
        assertThat(result.facets().get(assignedTo)).containsExactly(new FacetBucket(idText(alice), 2));
    }

    @Test
    void referenceFacetDropsTargetsOfAnotherTenant() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScope(TENANT);

        // A Person in another tenant: the tenant's browse must not bucket it, though the task that names it still
        // counts.
        ObjectId otherTenantPerson = ObjectId.random();
        env.seeder()
                .seed(ObjectViews.of(
                        otherTenantPerson,
                        PERSON_IRI,
                        OTHER_TENANT,
                        1,
                        Map.of(PERSON_NAME, Value.text("Olivia Other"), PERSON_EMAIL, Value.text("olivia@example.com")),
                        BASE,
                        false));
        ObjectId alice = seedPerson(env, ObjectId.random(), BASE.plusSeconds(1), "Alice Smith", "alice@example.com");

        seedTask(env, BASE.plusSeconds(2), "Alice task", "OPEN", 1, FEB_1, alice, null);
        seedTask(env, BASE.plusSeconds(3), "Other tenant's person task", "OPEN", 2, FEB_2, otherTenantPerson, null);

        FieldPath assignedTo = QueryFixtures.facetPath(scope, "assignedTo");
        BrowseResult result = env.browsePort().browse(scope, withFacets(new Page(0, 20), List.of(assignedTo)));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.facets().get(assignedTo)).containsExactly(new FacetBucket(idText(alice), 1));
    }

    @Test
    void traversalFacetIsRejectedAsInvalidQuery() {
        Environment env = freshEnvironment();
        FieldScope scope = QueryFixtures.taskScopeExpandedToProject(TENANT);
        seedTask(env, BASE, "A task", "OPEN", 1, FEB_1, null, null);

        // belongsToProject.status is a facetable traversal Field of the expanded scope, so only its shape is rejected.
        FieldPath projectStatus = QueryFixtures.facetPath(scope, "belongsToProject.status");
        assertRejectedNamingKey(
                env, scope, withFacets(new Page(0, 20), List.of(projectStatus)), FieldKey.encode(projectStatus));
    }

    // -- Fixture helpers --------------------------------------------------------------------------

    /** The filter is rejected before anything is read, with at least one problem named. */
    private static void assertRejectedAsInvalidQuery(Environment env, FieldScope scope, FilterNode filter) {
        assertRejectedAsInvalidQuery(env, scope, filtered(filter, new Page(0, 20)));
    }

    /** The query is rejected before anything is read, with at least one problem named. */
    private static void assertRejectedAsInvalidQuery(Environment env, FieldScope scope, BrowseQuery query) {
        assertThatThrownBy(() -> env.browsePort().browse(scope, query))
                .isInstanceOfSatisfying(
                        InvalidQueryException.class,
                        error -> assertThat(error.problems()).isNotEmpty());
    }

    /**
     * The query is rejected before anything is read, and at least one problem contains {@code key}: the rejection names the
     * key it refuses, not only that something was refused.
     */
    private static void assertRejectedNamingKey(Environment env, FieldScope scope, BrowseQuery query, String key) {
        assertThatThrownBy(() -> env.browsePort().browse(scope, query))
                .isInstanceOfSatisfying(
                        InvalidQueryException.class,
                        error -> assertThat(error.problems())
                                .anySatisfy(problem -> assertThat(problem).contains(key)));
    }

    private static BrowseQuery plain(Page page) {
        return new BrowseQuery(Optional.empty(), Optional.empty(), List.of(), page, List.of());
    }

    private static BrowseQuery filtered(FilterNode filter, Page page) {
        return new BrowseQuery(Optional.of(filter), Optional.empty(), List.of(), page, List.of());
    }

    private static BrowseQuery withSorts(List<Sort> sorts, Page page) {
        return new BrowseQuery(Optional.empty(), Optional.empty(), sorts, page, List.of());
    }

    private static BrowseQuery withFacets(Page page, List<FieldPath> facets) {
        return new BrowseQuery(Optional.empty(), Optional.empty(), List.of(), page, facets);
    }

    private static BrowseQuery textQuery(String text, Page page) {
        return new BrowseQuery(Optional.empty(), Optional.of(text), List.of(), page, List.of());
    }

    private static FieldPath priorityPath(FieldScope scope) {
        return QueryFixtures.facetPath(scope, "priority");
    }

    private static FilterNode is(String iri, JsonNode value) {
        return Filters.field(iri, "is", value);
    }

    private static JsonNode text(String value) {
        return JsonNodeFactory.instance.textNode(value);
    }

    private static JsonNode number(long value) {
        return JsonNodeFactory.instance.numberNode(value);
    }

    /** The text a reference value is bucketed and compared under: the target's canonical id. */
    private static String idText(ObjectId id) {
        return id.value().toString();
    }

    private static List<ObjectId> browseIds(Environment env, FieldScope scope, BrowseQuery query) {
        return env.browsePort().browse(scope, query).items().stream()
                .map(ObjectView::id)
                .toList();
    }

    private static ObjectId seedTask(
            Environment env,
            Instant createdAt,
            String title,
            String status,
            long priority,
            LocalDate dueDate,
            ObjectId assignedTo,
            ObjectId belongsToProject) {
        return seedTaskWithDescription(
                env,
                createdAt,
                title,
                "Description of " + title,
                status,
                priority,
                dueDate,
                assignedTo,
                belongsToProject);
    }

    private static ObjectId seedTaskWithDescription(
            Environment env,
            Instant createdAt,
            String title,
            String description,
            String status,
            long priority,
            LocalDate dueDate) {
        return seedTaskWithDescription(env, createdAt, title, description, status, priority, dueDate, null, null);
    }

    private static ObjectId seedTaskWithDescription(
            Environment env,
            Instant createdAt,
            String title,
            String description,
            String status,
            long priority,
            LocalDate dueDate,
            ObjectId assignedTo,
            ObjectId belongsToProject) {
        Map<PropertyRef, Value> properties = new HashMap<>();
        properties.put(TITLE, Value.text(title));
        properties.put(DESCRIPTION, Value.text(description));
        properties.put(STATUS, Value.text(status));
        properties.put(PRIORITY, Value.integer(priority));
        properties.put(DUE_DATE, Value.date(dueDate));
        if (assignedTo != null) {
            properties.put(ASSIGNED_TO, Value.ref(assignedTo));
        }
        if (belongsToProject != null) {
            properties.put(BELONGS_TO_PROJECT, Value.ref(belongsToProject));
        }
        return seed(env, ObjectId.random(), TASK_IRI, createdAt, properties);
    }

    private static ObjectId seedTaskWithId(
            Environment env, ObjectId id, Instant createdAt, String title, long priority) {
        return seed(
                env,
                id,
                TASK_IRI,
                createdAt,
                Map.of(
                        TITLE, Value.text(title),
                        DESCRIPTION, Value.text("Description of " + title),
                        STATUS, Value.text("OPEN"),
                        PRIORITY, Value.integer(priority),
                        DUE_DATE, Value.date(FEB_1)));
    }

    private static ObjectId seedTaskWithStatus(Environment env, ObjectId id, String status, long priority) {
        return seed(
                env,
                id,
                TASK_IRI,
                BASE,
                Map.of(
                        TITLE,
                        Value.text("Task " + status + " " + priority),
                        STATUS,
                        Value.text(status),
                        PRIORITY,
                        Value.integer(priority)));
    }

    private static void softDeletePerson(Environment env, ObjectId id, Instant createdAt, String name, String email) {
        env.seeder()
                .seed(ObjectViews.of(
                        id,
                        PERSON_IRI,
                        TENANT,
                        2,
                        Map.of(PERSON_NAME, Value.text(name), PERSON_EMAIL, Value.text(email)),
                        createdAt,
                        true));
    }

    private static ObjectId seedProject(Environment env, Instant createdAt, String name, String status) {
        return seed(
                env,
                ObjectId.random(),
                PROJECT_IRI,
                createdAt,
                Map.of(PROJECT_NAME, Value.text(name), PROJECT_STATUS, Value.text(status)));
    }

    private static ObjectId seedPerson(Environment env, ObjectId id, Instant createdAt, String name, String email) {
        return seed(
                env, id, PERSON_IRI, createdAt, Map.of(PERSON_NAME, Value.text(name), PERSON_EMAIL, Value.text(email)));
    }

    private static ObjectId seed(
            Environment env, ObjectId id, String type, Instant createdAt, Map<PropertyRef, Value> properties) {
        env.seeder().seed(ObjectViews.of(id, type, TENANT, 1, properties, createdAt, false));
        return id;
    }
}
