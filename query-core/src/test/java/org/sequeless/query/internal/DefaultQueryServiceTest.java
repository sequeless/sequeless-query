package org.sequeless.query.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sequeless.query.testsupport.QueryFixtures.FILTER_FACET_SORT;
import static org.sequeless.query.testsupport.QueryFixtures.FILTER_ONLY;
import static org.sequeless.query.testsupport.QueryFixtures.FILTER_SORT;
import static org.sequeless.query.testsupport.QueryFixtures.TENANT;
import static org.sequeless.query.testsupport.QueryFixtures.V1;
import static org.sequeless.query.testsupport.QueryFixtures.V2;
import static org.sequeless.query.testsupport.QueryFixtures.classRef;
import static org.sequeless.query.testsupport.QueryFixtures.field;
import static org.sequeless.query.testsupport.QueryFixtures.path;
import static org.sequeless.query.testsupport.QueryFixtures.request;
import static org.sequeless.query.testsupport.QueryFixtures.scope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.Filters;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.query.api.Audit;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.BrowseResult;
import org.sequeless.query.api.Direction;
import org.sequeless.query.api.FacetBucket;
import org.sequeless.query.api.IndexPreparationResult;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.Page;
import org.sequeless.query.api.ReadRequest;
import org.sequeless.query.api.Sort;
import org.sequeless.query.api.TypeRef;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.VersionPolicy;
import org.sequeless.query.field.spi.FieldSource;
import org.sequeless.query.spi.BrowsePort;
import org.sequeless.query.spi.IndexPreparationPort;
import org.sequeless.query.spi.ReadPort;

class DefaultQueryServiceTest {

    private static final ClassRef INVOICE = classRef("Invoice");
    private static final ClassRef CUSTOMER = classRef("Customer");
    private static final FieldPath STATUS_PATH = path("urn:ont:status");
    private static final String STATUS_IRI = "urn:ont:status";
    private static final String DUE_DATE_IRI = "urn:ont:dueDate";
    private static final String SHIPPED_AT_IRI = "urn:ont:shippedAt";
    private static final FieldPath DUE_DATE_PATH = path(DUE_DATE_IRI);
    private static final FieldPath SHIPPED_AT_PATH = path(SHIPPED_AT_IRI);
    private static final FieldPath NOTES_PATH = path("urn:ont:notes");
    private static final FieldPath TOTAL_PATH = path("urn:ont:total");
    private static final Field STATUS = field(STATUS_PATH, FieldType.STRING, FILTER_FACET_SORT);
    private static final Field NOTES = field(NOTES_PATH, FieldType.STRING, FILTER_ONLY);
    private static final Field TOTAL = field(TOTAL_PATH, FieldType.DECIMAL, FILTER_SORT);
    private static final BrowseResult BROWSE_RESULT =
            new BrowseResult(List.of(), 7, Map.of(STATUS_PATH, List.of(new FacetBucket("open", 3))));

    private RecordingBrowsePort browsePort;
    private RecordingReadPort readPort;
    private RecordingIndexPreparationPort indexPreparationPort;
    private FakeFieldSource fieldSource;
    private DefaultQueryService service;

    @BeforeEach
    void setUp() {
        FieldScope invoiceScope = scope(INVOICE, List.of(STATUS, NOTES, TOTAL));
        fieldSource = new FakeFieldSource(List.of(INVOICE), Map.of(INVOICE, invoiceScope));
        browsePort = new RecordingBrowsePort();
        readPort = new RecordingReadPort();
        indexPreparationPort = new RecordingIndexPreparationPort();
        service = new DefaultQueryService(fieldSource, browsePort, readPort, indexPreparationPort);
    }

    @Test
    void browseRejectsASortOnAFieldThatIsNotSortable() {
        BrowseQuery query = query(Optional.empty(), List.of(new Sort(NOTES_PATH, Direction.ASC)), List.of());

        assertThatThrownBy(() -> service.browse(request(INVOICE), query))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessage("invalid query: sort path 'notes' is not a sortable field of this scope");
        assertThat(browsePort.scopes).isEmpty();
    }

    @Test
    void browsePassesAValidSortThroughToThePort() {
        BrowseQuery query = query(Optional.empty(), List.of(new Sort(STATUS_PATH, Direction.DESC)), List.of());

        BrowseResult result = service.browse(request(INVOICE), query);

        assertThat(result).isEqualTo(BROWSE_RESULT);
        assertThat(browsePort.queries).containsExactly(query);
        assertThat(browsePort.scopes)
                .singleElement()
                .satisfies(scope -> assertThat(scope.root()).isEqualTo(INVOICE));
    }

    @Test
    void browseRejectsAFacetOnAFieldThatIsNotFacetable() {
        BrowseQuery query = query(Optional.empty(), List.of(), List.of(TOTAL_PATH));

        assertThatThrownBy(() -> service.browse(request(INVOICE), query))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessage("invalid query: facet path 'total' is not a facetable field of this scope");
        assertThat(browsePort.scopes).isEmpty();
    }

    @Test
    void browseRejectsAFilterOnAnUnknownFieldAndReportsTheViolation() {
        FilterNode filter = Filters.field("urn:ont:unknown", "exists");

        assertThatThrownBy(() -> service.browse(request(INVOICE), query(Optional.of(filter), List.of(), List.of())))
                .isInstanceOfSatisfying(
                        InvalidQueryException.class,
                        e -> assertThat(e.problems())
                                .filteredOn(problem -> problem.contains("urn:ont:unknown"))
                                .hasSize(1));
        assertThat(browsePort.scopes).isEmpty();
    }

    @Test
    void browseRejectsASortOnAPathAbsentFromTheScope() {
        BrowseQuery query =
                query(Optional.empty(), List.of(new Sort(path("urn:ont:absent"), Direction.ASC)), List.of());

        assertThatThrownBy(() -> service.browse(request(INVOICE), query))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessage("invalid query: sort path 'absent' is not a sortable field of this scope");
        assertThat(browsePort.scopes).isEmpty();
    }

    @Test
    void browseRejectsAFacetOnAPathAbsentFromTheScope() {
        FieldScope scopeWithoutFacetPath = scope(INVOICE, List.of(STATUS));
        fieldSource = new FakeFieldSource(List.of(INVOICE), Map.of(INVOICE, scopeWithoutFacetPath));
        service = new DefaultQueryService(fieldSource, browsePort, readPort, indexPreparationPort);
        BrowseQuery query = query(Optional.empty(), List.of(), List.of(path("urn:ont:absent")));

        assertThatThrownBy(() -> service.browse(request(INVOICE), query))
                .isInstanceOfSatisfying(InvalidQueryException.class, e -> {
                    assertThat(e.problems()).hasSize(1);
                    assertThat(e.problems().get(0))
                            .isEqualTo("facet path 'absent' is not a facetable field of this scope");
                });
        assertThat(browsePort.scopes).isEmpty();
    }

    @Test
    void browseCollectsTheFilterSortAndFacetProblemsOfOneRequestInThatOrder() {
        FilterNode filter = Filters.field("urn:ont:unknown", "exists");
        BrowseQuery query =
                query(Optional.of(filter), List.of(new Sort(NOTES_PATH, Direction.ASC)), List.of(TOTAL_PATH));

        assertThatThrownBy(() -> service.browse(request(INVOICE), query))
                .isInstanceOfSatisfying(InvalidQueryException.class, e -> {
                    assertThat(e.problems()).hasSize(3);
                    assertThat(e.problems().get(0)).contains("urn:ont:unknown");
                    assertThat(e.problems().get(1))
                            .isEqualTo("sort path 'notes' is not a sortable field of this scope");
                    assertThat(e.problems().get(2))
                            .isEqualTo("facet path 'total' is not a facetable field of this scope");
                });
        assertThat(browsePort.scopes).isEmpty();
    }

    @Test
    void browseRejectsNullArguments() {
        BrowseQuery query = query(Optional.empty(), List.of(), List.of());

        assertThatNullPointerException().isThrownBy(() -> service.browse(null, query));
        assertThatNullPointerException().isThrownBy(() -> service.browse(request(INVOICE), null));
    }

    @Test
    void browsePassesAValidFilterThroughToThePort() {
        FilterNode filter = Filters.field("urn:ont:status", "exists");
        BrowseQuery query = query(Optional.of(filter), List.of(), List.of(STATUS_PATH));

        assertThat(service.browse(request(INVOICE), query)).isEqualTo(BROWSE_RESULT);
        assertThat(browsePort.queries).containsExactly(query);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "gt",
                "gte",
                "lt",
                "lte",
                "is greater than",
                "is greater than or equal to",
                "is less than",
                "is less than or equal to"
            })
    void aDateFieldAcceptsTheAliasAndTheCanonicalNameOfEachOrderOperator(String operator) {
        FilterNode filter = Filters.field(DUE_DATE_IRI, operator, text("2026-02-01"));

        assertThat(problemsOfFilter(DUE_DATE_PATH, FieldType.DATE, filter)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "gt",
                "gte",
                "lt",
                "lte",
                "is greater than",
                "is greater than or equal to",
                "is less than",
                "is less than or equal to"
            })
    void aDatetimeFieldAcceptsTheAliasAndTheCanonicalNameOfEachOrderOperator(String operator) {
        FilterNode filter = Filters.field(SHIPPED_AT_IRI, operator, text("2026-02-01T10:00:00Z"));

        assertThat(problemsOfFilter(SHIPPED_AT_PATH, FieldType.DATETIME, filter))
                .isEmpty();
    }

    @Test
    void aStringFieldRejectsTheOrderAliasGteBecauseItsTypeDoesNotAllowIt() {
        FilterNode filter = Filters.field(STATUS_IRI, "gte", text("open"));

        assertThat(problemsOfFilter(STATUS_PATH, FieldType.STRING, filter))
                .contains("Operator 'gte' is not applicable to type 'string'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=", "is", "is equal to", "!=", "is not"})
    void aStringFieldAcceptsTheEqualityAliasesAndTheirCanonicalNames(String operator) {
        FilterNode filter = Filters.field(STATUS_IRI, operator, text("open"));

        assertThat(problemsOfFilter(STATUS_PATH, FieldType.STRING, filter)).isEmpty();
    }

    @Test
    void aStringFieldAcceptsTheInAliasWithAListValue() {
        FilterNode filter = Filters.field(
                STATUS_IRI, "in", JsonNodeFactory.instance.arrayNode().add("open"));

        assertThat(problemsOfFilter(STATUS_PATH, FieldType.STRING, filter)).isEmpty();
    }

    /** Browses a scope holding one filterable Field of {@code type}; empty when the filter is accepted. */
    private List<String> problemsOfFilter(FieldPath path, FieldType type, FilterNode filter) {
        FieldScope scopeWithField = scope(INVOICE, List.of(field(path, type, FILTER_ONLY)));
        DefaultQueryService serviceOverField = new DefaultQueryService(
                new FakeFieldSource(List.of(INVOICE), Map.of(INVOICE, scopeWithField)),
                browsePort,
                readPort,
                indexPreparationPort);
        try {
            serviceOverField.browse(request(INVOICE), query(Optional.of(filter), List.of(), List.of()));
            return List.of();
        } catch (InvalidQueryException e) {
            return e.problems();
        }
    }

    private static JsonNode text(String value) {
        return JsonNodeFactory.instance.textNode(value);
    }

    @Test
    void readPassesTheScopeAndRequestThroughAndReturnsThePortsAnswer() {
        Scope scope = new Scope(TENANT, Principal.ANONYMOUS);
        ReadRequest request = new ReadRequest(new ObjectId(UUID.fromString("00000000-0000-0000-0000-0000000000b1")));
        ObjectView view = new ObjectView(
                request.id(),
                new TypeRef("urn:class:Invoice"),
                TENANT,
                1,
                Optional.empty(),
                Map.of(),
                new Audit(Instant.EPOCH, "creator", Instant.EPOCH, "updater"),
                false);
        readPort.result = Optional.of(view);

        assertThat(service.read(scope, request)).contains(view);
        assertThat(readPort.scopes).containsExactly(scope);
        assertThat(readPort.requests).containsExactly(request);
    }

    @Test
    void prepareReturnsEmptyAndCallsNoPortWhenTheVersionIsSuperseded() {
        FieldScope supersededScope = scope(INVOICE, List.of(STATUS), Set.of(V2));
        fieldSource = new FakeFieldSource(List.of(INVOICE), Map.of(INVOICE, supersededScope));
        service = new DefaultQueryService(fieldSource, browsePort, readPort, indexPreparationPort);

        IndexPreparationResult result = service.prepare(TENANT, V1);

        assertThat(result).isEqualTo(IndexPreparationResult.EMPTY);
        assertThat(indexPreparationPort.calls).isEmpty();
    }

    @Test
    void prepareReturnsEmptyAndCallsNoPortWhenALaterRootSupersedesTheVersion() {
        FieldScope invoiceScope = scope(INVOICE, List.of(STATUS), Set.of(V1));
        FieldScope customerScope = scope(CUSTOMER, List.of(NOTES), Set.of(V2));
        fieldSource =
                new FakeFieldSource(List.of(INVOICE, CUSTOMER), Map.of(INVOICE, invoiceScope, CUSTOMER, customerScope));
        service = new DefaultQueryService(fieldSource, browsePort, readPort, indexPreparationPort);

        IndexPreparationResult result = service.prepare(TENANT, V1);

        assertThat(result).isEqualTo(IndexPreparationResult.EMPTY);
        assertThat(indexPreparationPort.calls).isEmpty();
    }

    @Test
    void prepareStopsAtTheFirstSupersededRootAndResolvesNoLaterRoot() {
        FieldScope invoiceScope = scope(INVOICE, List.of(STATUS), Set.of(V2));
        FieldScope customerScope = scope(CUSTOMER, List.of(NOTES), Set.of(V1));
        fieldSource =
                new FakeFieldSource(List.of(INVOICE, CUSTOMER), Map.of(INVOICE, invoiceScope, CUSTOMER, customerScope));
        service = new DefaultQueryService(fieldSource, browsePort, readPort, indexPreparationPort);

        IndexPreparationResult result = service.prepare(TENANT, V1);

        assertThat(result).isEqualTo(IndexPreparationResult.EMPTY);
        assertThat(fieldSource.resolvedRequests)
                .singleElement()
                .satisfies(request -> assertThat(request.root()).isEqualTo(INVOICE));
        assertThat(indexPreparationPort.calls).isEmpty();
    }

    @Test
    void prepareCallsThePortOnceWithAnEmptyMapWhenTheTenantHasNoRoots() {
        fieldSource = new FakeFieldSource(List.of(), Map.of());
        service = new DefaultQueryService(fieldSource, browsePort, readPort, indexPreparationPort);
        IndexPreparationResult portResult = new IndexPreparationResult(List.of(STATUS_PATH));
        indexPreparationPort.result = portResult;

        IndexPreparationResult result = service.prepare(TENANT, V1);

        assertThat(result).isEqualTo(portResult);
        assertThat(indexPreparationPort.calls)
                .singleElement()
                .satisfies(call -> assertThat(call.rootFields()).isEmpty());
    }

    @Test
    void prepareRejectsADuplicateRootBeforeResolvingAnyScope() {
        FieldScope invoiceScope = scope(INVOICE, List.of(STATUS));
        fieldSource = new FakeFieldSource(List.of(INVOICE, INVOICE), Map.of(INVOICE, invoiceScope));
        service = new DefaultQueryService(fieldSource, browsePort, readPort, indexPreparationPort);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.prepare(TENANT, V1))
                .withMessage("duplicate root class: urn:class:Invoice");
        assertThat(fieldSource.resolvedRequests).isEmpty();
        assertThat(indexPreparationPort.calls).isEmpty();
    }

    @Test
    void prepareRejectsAResolvedRootThatDoesNotMatchTheListedRootWithoutCallingThePort() {
        FieldScope otherRootScope = scope(CUSTOMER, List.of(STATUS));
        fieldSource = new FakeFieldSource(List.of(INVOICE), Map.of(INVOICE, otherRootScope));
        service = new DefaultQueryService(fieldSource, browsePort, readPort, indexPreparationPort);

        assertThatIllegalStateException()
                .isThrownBy(() -> service.prepare(TENANT, V1))
                .withMessage("resolved root does not match the listed root: urn:class:Invoice");
        assertThat(indexPreparationPort.calls).isEmpty();
    }

    @Test
    void prepareCallsThePortOnceWithEveryRootInOrderAndReturnsItsResult() {
        FieldScope invoiceScope = scope(INVOICE, List.of(STATUS));
        FieldScope customerScope = scope(CUSTOMER, List.of(NOTES));
        fieldSource =
                new FakeFieldSource(List.of(INVOICE, CUSTOMER), Map.of(INVOICE, invoiceScope, CUSTOMER, customerScope));
        service = new DefaultQueryService(fieldSource, browsePort, readPort, indexPreparationPort);
        IndexPreparationResult portResult = new IndexPreparationResult(List.of(STATUS_PATH));
        indexPreparationPort.result = portResult;

        IndexPreparationResult result = service.prepare(TENANT, V1);

        assertThat(result).isEqualTo(portResult);
        assertThat(indexPreparationPort.calls).singleElement().satisfies(call -> {
            assertThat(call.tenant()).isEqualTo(TENANT);
            assertThat(call.version()).isEqualTo(V1);
            assertThat(call.rootFields().keySet()).containsExactly(INVOICE, CUSTOMER);
            assertThat(call.rootFields().get(INVOICE)).containsExactly(STATUS);
            assertThat(call.rootFields().get(CUSTOMER)).containsExactly(NOTES);
        });
    }

    @Test
    void prepareResolvesEachRootUnderTheUnionPolicy() {
        FieldScope invoiceScope = scope(INVOICE, List.of(STATUS));
        fieldSource = new FakeFieldSource(List.of(INVOICE), Map.of(INVOICE, invoiceScope));
        service = new DefaultQueryService(fieldSource, browsePort, readPort, indexPreparationPort);

        service.prepare(TENANT, V1);

        assertThat(fieldSource.resolvedRequests)
                .singleElement()
                .satisfies(request -> assertThat(request.versionPolicy()).isInstanceOf(VersionPolicy.Union.class));
    }

    @Test
    void readRejectsNullArguments() {
        Scope scope = new Scope(TENANT, Principal.ANONYMOUS);
        ReadRequest request = new ReadRequest(new ObjectId(UUID.fromString("00000000-0000-0000-0000-0000000000b1")));

        assertThatNullPointerException().isThrownBy(() -> service.read(null, request));
        assertThatNullPointerException().isThrownBy(() -> service.read(scope, null));
    }

    @Test
    void prepareRejectsNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> service.prepare(null, V1));
        assertThatNullPointerException().isThrownBy(() -> service.prepare(TENANT, null));
    }

    @Test
    void constructorRejectsNullPorts() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultQueryService(null, browsePort, readPort, indexPreparationPort));
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultQueryService(fieldSource, null, readPort, indexPreparationPort));
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultQueryService(fieldSource, browsePort, null, indexPreparationPort));
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultQueryService(fieldSource, browsePort, readPort, null));
    }

    private static BrowseQuery query(Optional<FilterNode> filter, List<Sort> sorts, List<FieldPath> facets) {
        return new BrowseQuery(filter, Optional.empty(), sorts, new Page(0, 20), facets);
    }

    /** Returns a fixed scope per root class and a fixed list of roots; records each resolve. */
    private static final class FakeFieldSource implements FieldSource {

        private final List<ClassRef> roots;
        private final Map<ClassRef, FieldScope> scopes;
        final List<FieldScopeRequest> resolvedRequests = new ArrayList<>();

        FakeFieldSource(List<ClassRef> roots, Map<ClassRef, FieldScope> scopes) {
            this.roots = roots;
            this.scopes = scopes;
        }

        @Override
        public FieldScope resolve(FieldScopeRequest request) {
            resolvedRequests.add(request);
            FieldScope scope = scopes.get(request.root());
            if (scope == null) {
                throw new IllegalStateException(
                        "no scope registered for " + request.root().slug());
            }
            return scope;
        }

        @Override
        public FieldScope expand(FieldScope scope, FieldPath relationship) {
            throw new UnsupportedOperationException("not used by query-core");
        }

        @Override
        public Field describe(FieldScopeRequest request, FieldPath path) {
            throw new UnsupportedOperationException("not used by query-core");
        }

        @Override
        public List<ClassRef> rootClasses(TenantId tenant) {
            return roots;
        }
    }

    private static final class RecordingBrowsePort implements BrowsePort {

        final List<FieldScope> scopes = new ArrayList<>();
        final List<BrowseQuery> queries = new ArrayList<>();

        @Override
        public BrowseResult browse(FieldScope scope, BrowseQuery query) {
            scopes.add(scope);
            queries.add(query);
            return BROWSE_RESULT;
        }
    }

    private static final class RecordingReadPort implements ReadPort {

        final List<Scope> scopes = new ArrayList<>();
        final List<ReadRequest> requests = new ArrayList<>();
        Optional<ObjectView> result = Optional.empty();

        @Override
        public Optional<ObjectView> read(Scope scope, ReadRequest request) {
            scopes.add(scope);
            requests.add(request);
            return result;
        }
    }

    private record PrepareCall(TenantId tenant, OntologyVersionRef version, Map<ClassRef, List<Field>> rootFields) {}

    private static final class RecordingIndexPreparationPort implements IndexPreparationPort {

        final List<PrepareCall> calls = new ArrayList<>();
        IndexPreparationResult result = IndexPreparationResult.EMPTY;

        @Override
        public IndexPreparationResult prepare(
                TenantId tenant, OntologyVersionRef version, Map<ClassRef, List<Field>> rootFields) {
            calls.add(new PrepareCall(tenant, version, rootFields));
            return result;
        }
    }
}
