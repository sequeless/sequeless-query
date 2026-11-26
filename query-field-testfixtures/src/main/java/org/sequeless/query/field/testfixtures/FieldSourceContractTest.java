package org.sequeless.query.field.testfixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.ConflictingDefinition;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldErrorCode;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldResolutionException;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.LocalizedText;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.PathSegment;
import org.sequeless.query.field.api.Relationship;
import org.sequeless.query.field.api.StampMode;
import org.sequeless.query.field.api.TraversalConfig;
import org.sequeless.query.field.api.VersionPolicy;
import org.sequeless.query.field.spi.FieldSource;

/**
 * The reusable contract test kit any {@link FieldSource} implementation, including a future
 * RDF-backed one, must pass.
 *
 * <p>Every test method below exercises one guarantee documented on {@link FieldSource}'s three
 * methods — {@code resolve}, {@code expand}, and {@code describe} — against the nine canned {@link
 * ContractFixtures} scenarios. A concrete subclass supplies only {@link #sourceFor(ContractFixture)}
 * — how to build its {@code FieldSource} implementation over a given scenario — and inherits every
 * test unchanged. See {@code package-info}.
 */
public abstract class FieldSourceContractTest {

    private static final OntologyVersionRef V1 = new OntologyVersionRef(1);
    private static final OntologyVersionRef V2 = new OntologyVersionRef(2);
    /** The IRI of a property that exists in no fixture, for paths that must name nothing real. */
    private static final String UNKNOWN_IRI = "urn:ont:property:unknown";

    /**
     * Builds the {@code FieldSource} implementation under test, backed by {@code fixture}.
     *
     * @param fixture the scenario the returned source must serve; never {@code null}
     * @return a {@code FieldSource} over {@code fixture}
     */
    protected abstract FieldSource sourceFor(ContractFixture fixture);

    // ------------------------------------------------------------------------------------------
    // Request/path building helpers
    // ------------------------------------------------------------------------------------------

    private static ClassRef classRefOf(ClassFixture classFixture) {
        return new ClassRef(
                classFixture.id(),
                classFixture.slug(),
                classFixture.iri(),
                new LocalizedText(classFixture.slug(), null));
    }

    private static ClassRef rootOf(ContractFixture fixture) {
        return classRefOf(fixture.classes().get(0));
    }

    private static ClassRef randomClassRef() {
        return new ClassRef(UUID.randomUUID(), "unknown", "urn:ont:class:unknown", new LocalizedText("unknown", null));
    }

    private static FieldScopeRequest request(
            ContractFixture fixture, VersionPolicy policy, TraversalConfig traversal, Optional<Principal> principal) {
        return new FieldScopeRequest(
                fixture.tenantId(), rootOf(fixture), policy, traversal, principal, Optional.empty());
    }

    private static FieldScopeRequest defaultRequest(ContractFixture fixture) {
        return request(fixture, new VersionPolicy.Union(), TraversalConfig.defaults(), Optional.empty());
    }

    private static RelationshipFixture relationshipOn(ClassFixture classFixture, String slug) {
        return classFixture.relationships().stream()
                .filter(relationship -> relationship.slug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "no relationship named '" + slug + "' on class '" + classFixture.slug() + "'"));
    }

    /**
     * Builds a {@link FieldPath} from {@code fixture}'s root, walking every slug but the last as a
     * relationship (looked up on the current class, advancing to its target) and resolving the last
     * slug as a terminal scalar property on the class the walk lands on.
     *
     * <p>{@code pathTo(fixture, "customer", "city")} on {@code invoiceAndCustomer()} therefore
     * produces a two-segment path: {@code customer} (a relationship from Invoice to Customer) then
     * {@code city} (a property on Customer).
     */
    private static FieldPath pathTo(ContractFixture fixture, String... classAndPropertySlugs) {
        List<PathSegment> segments = new ArrayList<>();
        ClassFixture current = fixture.classes().get(0);
        for (int i = 0; i < classAndPropertySlugs.length; i++) {
            String slug = classAndPropertySlugs[i];
            boolean isLast = i == classAndPropertySlugs.length - 1;
            if (!isLast) {
                RelationshipFixture relationship = relationshipOn(current, slug);
                ClassFixture target = fixture.classNamed(relationship.targetClassSlug());
                segments.add(new PathSegment(
                        relationship.id(), relationship.iri(), relationship.slug(), classRefOf(target)));
                current = target;
            } else {
                ClassFixture terminalClass = current;
                PropertyFixture property = terminalClass.properties().stream()
                        .filter(p -> p.slug().equals(slug))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "no property named '" + slug + "' on class '" + terminalClass.slug() + "'"));
                segments.add(new PathSegment(property.id(), property.iri(), property.slug(), null));
            }
        }
        return new FieldPath(segments);
    }

    /**
     * Builds a pure-relationship {@link FieldPath} from {@code fixture}'s root, every segment
     * (including the last) naming a relationship rather than a terminal scalar property — the shape
     * {@link FieldSource#expand(FieldScope, FieldPath)} requires of its {@code relationship}
     * parameter.
     */
    private static FieldPath relationshipPath(ContractFixture fixture, String... relationshipSlugs) {
        List<PathSegment> segments = new ArrayList<>();
        ClassFixture current = fixture.classes().get(0);
        for (String slug : relationshipSlugs) {
            RelationshipFixture relationship = relationshipOn(current, slug);
            ClassFixture target = fixture.classNamed(relationship.targetClassSlug());
            segments.add(
                    new PathSegment(relationship.id(), relationship.iri(), relationship.slug(), classRefOf(target)));
            current = target;
        }
        return new FieldPath(segments);
    }

    /** A single-segment path naming a relationship on {@code fixture}'s root as the terminal segment. */
    private static FieldPath singleRelationshipTerminalPath(ContractFixture fixture, String slug) {
        RelationshipFixture relationship = relationshipOn(fixture.classes().get(0), slug);
        return new FieldPath(
                List.of(new PathSegment(relationship.id(), relationship.iri(), relationship.slug(), null)));
    }

    /** A single-segment terminal path naming a property id that exists nowhere in any fixture. */
    private static FieldPath randomTerminalPath() {
        return new FieldPath(List.of(new PathSegment(UUID.randomUUID(), UNKNOWN_IRI, "unknown", null)));
    }

    /** A single-segment relationship path naming a property id that exists nowhere in any fixture. */
    private static FieldPath randomRelationshipPath() {
        return new FieldPath(List.of(new PathSegment(UUID.randomUUID(), UNKNOWN_IRI, "unknown", randomClassRef())));
    }

    private static FieldErrorCode codeOf(ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        assertThat(thrown).isInstanceOf(FieldResolutionException.class);
        return ((FieldResolutionException) thrown).code();
    }

    // ------------------------------------------------------------------------------------------
    // resolve
    // ------------------------------------------------------------------------------------------

    @Test
    final void resolveReturnsRootFacets() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);

        FieldScope scope = source.resolve(defaultRequest(fixture));

        assertThat(scope.facets())
                .extracting(f -> f.path().asSlugPath())
                .containsExactlyInAnyOrder("status", "total", "description", "internalNotes");
    }

    @Test
    final void resolveReturnsOnlyWalkableRelationships() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);

        FieldScope scope = source.resolve(defaultRequest(fixture));

        assertThat(scope.relationships()).extracting(Relationship::slug).containsExactly("customer");
    }

    @Test
    final void resolveEchoesRequestInScope() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);
        FieldScopeRequest request = defaultRequest(fixture);

        FieldScope scope = source.resolve(request);

        assertThat(scope.request()).isEqualTo(request);
    }

    @Test
    final void resolveFillsConcreteTypesWithTheRootOfAConcreteClass() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);

        FieldScope scope = source.resolve(defaultRequest(fixture));

        // Every fixture class is concrete, so the root must be one of its own concrete types.
        assertThat(scope.concreteTypes()).isNotNull().contains(scope.root());
    }

    @Test
    final void resolveUnknownTenantRaisesUnknownTenant() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);
        FieldScopeRequest request = new FieldScopeRequest(
                new TenantId("not-" + fixture.tenantId().value()),
                rootOf(fixture),
                new VersionPolicy.Union(),
                TraversalConfig.defaults(),
                Optional.empty(),
                Optional.empty());

        assertThat(codeOf(() -> source.resolve(request))).isEqualTo(FieldErrorCode.UNKNOWN_TENANT);
    }

    @Test
    final void rootClassesIncludesTheRootAndIsStableAcrossCalls() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);

        List<ClassRef> roots = source.rootClasses(fixture.tenantId());

        assertThat(roots).extracting(ClassRef::id).contains(rootOf(fixture).id());
        assertThat(source.rootClasses(fixture.tenantId())).isEqualTo(roots);
    }

    @Test
    final void rootClassesUnknownTenantRaisesUnknownTenant() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);

        assertThat(codeOf(() -> source.rootClasses(
                        new TenantId("not-" + fixture.tenantId().value()))))
                .isEqualTo(FieldErrorCode.UNKNOWN_TENANT);
    }

    @Test
    final void resolveUnknownClassRaisesUnknownClass() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);
        FieldScopeRequest request = new FieldScopeRequest(
                fixture.tenantId(),
                randomClassRef(),
                new VersionPolicy.Union(),
                TraversalConfig.defaults(),
                Optional.empty(),
                Optional.empty());

        assertThat(codeOf(() -> source.resolve(request))).isEqualTo(FieldErrorCode.UNKNOWN_CLASS);
    }

    // ------------------------------------------------------------------------------------------
    // expand
    // ------------------------------------------------------------------------------------------

    @Test
    final void expandAddsExactlyOneHopOfFacets() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);
        FieldScope scope = source.resolve(defaultRequest(fixture));

        FieldScope expanded = source.expand(scope, relationshipPath(fixture, "customer"));

        assertThat(expanded.facets())
                .extracting(f -> f.path().asSlugPath())
                .contains("customer.name", "customer.city", "customer.tier");
        assertThat(expanded.relationships()).extracting(Relationship::slug).contains("account");
    }

    @Test
    final void expandPreservesAlreadyResolvedFacets() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);
        FieldScope scope = source.resolve(defaultRequest(fixture));

        FieldScope expanded = source.expand(scope, relationshipPath(fixture, "customer"));

        assertThat(expanded.facets()).extracting(f -> f.path().asSlugPath()).contains("status");
    }

    @Test
    final void expandDoesNotAddTwoHopFacets() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);
        FieldScope scope = source.resolve(defaultRequest(fixture));

        FieldScope expanded = source.expand(scope, relationshipPath(fixture, "customer"));

        assertThat(expanded.facets()).noneMatch(f -> f.path().asSlugPath().equals("customer.account.reference"));
    }

    @Test
    final void expandUnknownRelationshipRaisesUnknownPath() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);
        FieldScope scope = source.resolve(defaultRequest(fixture));

        assertThat(codeOf(() -> source.expand(scope, randomRelationshipPath()))).isEqualTo(FieldErrorCode.UNKNOWN_PATH);
    }

    @Test
    final void expandPreservesStampFromOriginalScope() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);
        FieldScope scope = source.resolve(defaultRequest(fixture));

        FieldScope expanded = source.expand(scope, relationshipPath(fixture, "customer"));

        assertThat(expanded.stamp()).isEqualTo(scope.stamp());
        assertThat(expanded.concreteTypes()).isEqualTo(scope.concreteTypes());
    }

    // ------------------------------------------------------------------------------------------
    // describe
    // ------------------------------------------------------------------------------------------

    @Test
    final void describeResolvesDeepPathWithoutEnumerating() {
        ContractFixture fixture = ContractFixtures.deep();
        FieldSource source = sourceFor(fixture);
        FieldPath path = pathTo(fixture, "step", "step", "step", "value");

        Field facet = source.describe(defaultRequest(fixture), path);

        assertThat(facet.path()).isEqualTo(path);
        assertThat(facet.type()).isEqualTo(FieldType.STRING);
    }

    @Test
    final void describeUnknownTenantRaisesUnknownTenant() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);
        FieldScopeRequest request = new FieldScopeRequest(
                new TenantId("not-" + fixture.tenantId().value()),
                rootOf(fixture),
                new VersionPolicy.Union(),
                TraversalConfig.defaults(),
                Optional.empty(),
                Optional.empty());

        assertThat(codeOf(() -> source.describe(request, pathTo(fixture, "status"))))
                .isEqualTo(FieldErrorCode.UNKNOWN_TENANT);
    }

    @Test
    final void describeUnreachableClassRaisesClassNotReachable() {
        ContractFixture fixture = ContractFixtures.unwalkableEdge();
        FieldSource source = sourceFor(fixture);

        assertThat(codeOf(() -> source.describe(defaultRequest(fixture), pathTo(fixture, "customer", "city"))))
                .isEqualTo(FieldErrorCode.CLASS_NOT_REACHABLE);
    }

    @Test
    final void describeUnknownPropertyRaisesUnknownPath() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);

        assertThat(codeOf(() -> source.describe(defaultRequest(fixture), randomTerminalPath())))
                .isEqualTo(FieldErrorCode.UNKNOWN_PATH);
    }

    @Test
    final void describePathNotFilterableRaisesPathNotFilterable() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);

        assertThat(codeOf(() -> source.describe(defaultRequest(fixture), pathTo(fixture, "internalNotes"))))
                .isEqualTo(FieldErrorCode.PATH_NOT_FILTERABLE);
    }

    @Test
    final void describeReturnsCurrentFacetForValidPath() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);

        Field facet = source.describe(defaultRequest(fixture), pathTo(fixture, "status"));

        assertThat(facet.type()).isEqualTo(FieldType.STRING);
        assertThat(facet.capabilities()).isEqualTo(new Capabilities(true, true, false, true));
    }

    // ------------------------------------------------------------------------------------------
    // Union
    // ------------------------------------------------------------------------------------------

    @Test
    final void unionMergesRenamedPropertyByUuid() {
        ContractFixture fixture = ContractFixtures.renamedAcrossVersions();
        FieldSource source = sourceFor(fixture);

        FieldScope scope = source.resolve(defaultRequest(fixture));

        assertThat(scope.facets().stream()
                        .filter(f -> f.path().asSlugPath().equals("customerName"))
                        .toList())
                .hasSize(1);
    }

    @Test
    final void unionUsesNewestSlugForDisplay() {
        ContractFixture fixture = ContractFixtures.renamedAcrossVersions();
        FieldSource source = sourceFor(fixture);

        FieldScope scope = source.resolve(defaultRequest(fixture));

        Field renamed = scope.facets().stream()
                .filter(f -> f.definedInVersions().containsAll(Set.of(V1, V2)))
                .findFirst()
                .orElseThrow();
        assertThat(renamed.path().asSlugPath()).isEqualTo("customerName");
    }

    @Test
    final void unionRecordsAccurateDefinedInVersions() {
        ContractFixture fixture = ContractFixtures.renamedAcrossVersions();
        FieldSource source = sourceFor(fixture);

        FieldScope scope = source.resolve(defaultRequest(fixture));

        Field renamed = scope.facets().stream()
                .filter(f -> f.path().asSlugPath().equals("customerName"))
                .findFirst()
                .orElseThrow();
        Field region = scope.facets().stream()
                .filter(f -> f.path().asSlugPath().equals("region"))
                .findFirst()
                .orElseThrow();

        assertThat(renamed.definedInVersions()).isEqualTo(Set.of(V1, V2));
        assertThat(region.definedInVersions()).isEqualTo(Set.of(V2));
    }

    @Test
    final void unionKeepsVersionExclusiveFacet() {
        ContractFixture fixture = ContractFixtures.renamedAcrossVersions();
        FieldSource source = sourceFor(fixture);

        FieldScope scope = source.resolve(defaultRequest(fixture));

        assertThat(scope.facets()).anyMatch(f -> f.path().asSlugPath().equals("region"));
    }

    // ------------------------------------------------------------------------------------------
    // Pinned
    // ------------------------------------------------------------------------------------------

    /**
     * Per {@link VersionPolicy.Pinned}'s own worked example, typing a path that names a property
     * absent from the pinned version raises {@code UNKNOWN_PATH} — "as far as this resolution is
     * concerned, that property does not exist". This uses a property id absent from {@code
     * renamedAcrossVersions()} entirely, rather than {@code region} (present only in v2), because
     * {@code region} under {@code Pinned(v1)} currently trips an unrelated bug in {@link
     * InMemoryFieldSource#describe(FieldScopeRequest, FieldPath)}'s terminal-hop handling — it
     * calls {@code merged.get(0)} without checking {@code merged} is non-empty once the pinned
     * version filters out every candidate row for an id that does have rows in other versions, and
     * so throws {@code ArrayIndexOutOfBoundsException} rather than the documented {@code
     * FieldResolutionException}/{@code UNKNOWN_PATH}. That gap belongs to {@code
     * InMemoryFieldSource}, not to this contract, and is out of scope to fix here.
     */
    @Test
    final void pinnedAbsentPathRaisesUnknownPath() {
        // 'region' is defined only in v2 (see ContractFixtures.renamedAcrossVersions) -- this is the worked example:
        // under Pinned(v1), region does not exist and typing it raises UNKNOWN_PATH.
        ContractFixture fixture = ContractFixtures.renamedAcrossVersions();
        FieldSource source = sourceFor(fixture);
        FieldScopeRequest request =
                request(fixture, new VersionPolicy.Pinned(V1), TraversalConfig.defaults(), Optional.empty());

        assertThat(codeOf(() -> source.describe(request, pathTo(fixture, "region"))))
                .isEqualTo(FieldErrorCode.UNKNOWN_PATH);
    }

    @Test
    final void pinnedUnknownVersionRaisesVersionNotFound() {
        ContractFixture fixture = ContractFixtures.renamedAcrossVersions();
        FieldSource source = sourceFor(fixture);
        FieldScopeRequest request = request(
                fixture,
                new VersionPolicy.Pinned(new OntologyVersionRef(9)),
                TraversalConfig.defaults(),
                Optional.empty());

        assertThat(codeOf(() -> source.resolve(request))).isEqualTo(FieldErrorCode.VERSION_NOT_FOUND);
    }

    @Test
    final void pinnedUsesThatVersionsSlug() {
        ContractFixture fixture = ContractFixtures.renamedAcrossVersions();
        FieldSource source = sourceFor(fixture);
        FieldScopeRequest request =
                request(fixture, new VersionPolicy.Pinned(V1), TraversalConfig.defaults(), Optional.empty());

        FieldScope scope = source.resolve(request);

        assertThat(scope.facets())
                .extracting(f -> f.path().asSlugPath())
                .contains("custName")
                .doesNotContain("customerName");
    }

    // ------------------------------------------------------------------------------------------
    // Intersection
    // ------------------------------------------------------------------------------------------

    @Test
    final void intersectionOmitsVersionExclusiveFacets() {
        ContractFixture fixture = ContractFixtures.versionExclusive();
        FieldSource source = sourceFor(fixture);
        FieldScopeRequest request =
                request(fixture, new VersionPolicy.Intersection(), TraversalConfig.defaults(), Optional.empty());

        FieldScope scope = source.resolve(request);

        assertThat(scope.facets()).noneMatch(f -> f.path().asSlugPath().equals("region"));
    }

    @Test
    final void intersectionWithNoCommonFacetsRaisesEmptyIntersection() {
        ContractFixture fixture = ContractFixtures.disjointVersions();
        FieldSource source = sourceFor(fixture);
        FieldScopeRequest request =
                request(fixture, new VersionPolicy.Intersection(), TraversalConfig.defaults(), Optional.empty());

        assertThat(codeOf(() -> source.resolve(request))).isEqualTo(FieldErrorCode.EMPTY_INTERSECTION);
    }

    // ------------------------------------------------------------------------------------------
    // Conflict
    // ------------------------------------------------------------------------------------------

    @Test
    final void typeMismatchMarksFacetAndKeepsIt() {
        ContractFixture fixture = ContractFixtures.conflictingTypes();
        FieldSource source = sourceFor(fixture);

        FieldScope scope = source.resolve(defaultRequest(fixture));

        Field issuedAt = scope.facets().stream()
                .filter(f -> f.path().asSlugPath().equals("issuedAt"))
                .findFirst()
                .orElseThrow();
        assertThat(issuedAt.conflict()).isPresent();
    }

    @Test
    final void typeMismatchRecordsEveryConflictingDefinition() {
        ContractFixture fixture = ContractFixtures.conflictingTypes();
        FieldSource source = sourceFor(fixture);

        FieldScope scope = source.resolve(defaultRequest(fixture));

        Field issuedAt = scope.facets().stream()
                .filter(f -> f.path().asSlugPath().equals("issuedAt"))
                .findFirst()
                .orElseThrow();
        List<ConflictingDefinition> definitions =
                issuedAt.conflict().orElseThrow().definitions();

        assertThat(definitions).hasSize(2);
        assertThat(definitions).extracting(ConflictingDefinition::version).containsExactly(V1, V2);
        assertThat(definitions)
                .extracting(ConflictingDefinition::type)
                .containsExactly(FieldType.STRING, FieldType.DATETIME);
    }

    @Test
    final void typeMismatchDoesNotThrow() {
        ContractFixture fixture = ContractFixtures.conflictingTypes();
        FieldSource source = sourceFor(fixture);

        assertThatCode(() -> source.resolve(defaultRequest(fixture))).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------------------------
    // Guards
    // ------------------------------------------------------------------------------------------

    @Test
    final void unwalkableEdgeRaisesEdgeNotWalkable() {
        ContractFixture fixture = ContractFixtures.unwalkableEdge();
        FieldSource source = sourceFor(fixture);

        assertThat(codeOf(() ->
                        source.describe(defaultRequest(fixture), singleRelationshipTerminalPath(fixture, "customer"))))
                .isEqualTo(FieldErrorCode.EDGE_NOT_WALKABLE);
    }

    @Test
    final void cyclicPathRaisesCycleDetected() {
        ContractFixture fixture = ContractFixtures.cyclic();
        FieldSource source = sourceFor(fixture);
        FieldScope scope = source.resolve(defaultRequest(fixture));
        FieldScope expanded = source.expand(scope, relationshipPath(fixture, "toB"));

        assertThat(codeOf(() -> source.expand(expanded, relationshipPath(fixture, "toB", "toA"))))
                .isEqualTo(FieldErrorCode.CYCLE_DETECTED);
    }

    @Test
    final void sameClassInTwoDifferentPathsIsAllowed() {
        ContractFixture fixture = ContractFixtures.cyclic();
        FieldSource source = sourceFor(fixture);
        FieldScope scope = source.resolve(defaultRequest(fixture));

        assertThatCode(() -> source.expand(scope, relationshipPath(fixture, "toB")))
                .doesNotThrowAnyException();
        assertThatCode(() -> source.expand(scope, relationshipPath(fixture, "toC", "toB")))
                .doesNotThrowAnyException();
    }

    @Test
    final void exceedingMaxDepthRaisesDepthExceeded() {
        ContractFixture fixture = ContractFixtures.deep();
        FieldSource source = sourceFor(fixture);
        FieldScope scope = source.resolve(defaultRequest(fixture));
        FieldPath tooDeep = relationshipPath(fixture, "step", "step", "step", "step");

        assertThat(codeOf(() -> source.expand(scope, tooDeep))).isEqualTo(FieldErrorCode.DEPTH_EXCEEDED);
    }

    @Test
    final void unboundedMaxDepthAllowsDeepTraversal() {
        ContractFixture fixture = ContractFixtures.deep();
        FieldSource source = sourceFor(fixture);
        TraversalConfig unbounded = new TraversalConfig(OptionalInt.empty(), true, StampMode.VERSION_ONLY, false);
        FieldScopeRequest request = request(fixture, new VersionPolicy.Union(), unbounded, Optional.empty());
        FieldScope scope = source.resolve(request);
        FieldPath deepPath = relationshipPath(fixture, "step", "step", "step", "step", "step");

        assertThatCode(() -> source.expand(scope, deepPath)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------------------------
    // Stamps
    // ------------------------------------------------------------------------------------------

    @Test
    final void identicalRequestsProduceIdenticalStamps() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);
        FieldScopeRequest request1 = defaultRequest(fixture);
        FieldScopeRequest request2 = defaultRequest(fixture);
        assertThat(request1).isNotSameAs(request2).isEqualTo(request2);

        String stamp1 = source.resolve(request1).stamp();
        String stamp2 = source.resolve(request2).stamp();

        assertThat(stamp1).isEqualTo(stamp2);
    }

    @Test
    final void contentHashStampChangesWithPrincipal() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);
        TraversalConfig contentHash = new TraversalConfig(OptionalInt.of(3), true, StampMode.CONTENT_HASH, false);
        FieldScopeRequest asAlice =
                request(fixture, new VersionPolicy.Union(), contentHash, Optional.of(new Principal("alice", Set.of())));
        FieldScopeRequest asBob =
                request(fixture, new VersionPolicy.Union(), contentHash, Optional.of(new Principal("bob", Set.of())));

        assertThat(source.resolve(asAlice).stamp())
                .isNotEqualTo(source.resolve(asBob).stamp());
    }

    @Test
    final void versionOnlyStampIgnoresTraversalConfigAndPrincipal() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);
        TraversalConfig shallow = new TraversalConfig(OptionalInt.of(1), true, StampMode.VERSION_ONLY, false);
        TraversalConfig deep = new TraversalConfig(OptionalInt.of(5), false, StampMode.VERSION_ONLY, false);
        FieldScopeRequest request1 =
                request(fixture, new VersionPolicy.Union(), shallow, Optional.of(new Principal("alice", Set.of())));
        FieldScopeRequest request2 =
                request(fixture, new VersionPolicy.Union(), deep, Optional.of(new Principal("bob", Set.of())));

        assertThat(source.resolve(request1).stamp())
                .isEqualTo(source.resolve(request2).stamp());
    }

    @Test
    final void unionStampReflectsAllVersionsInScope() {
        ContractFixture fixture = ContractFixtures.renamedAcrossVersions();
        FieldSource source = sourceFor(fixture);

        FieldScope scope = source.resolve(defaultRequest(fixture));

        assertThat(scope.ontologyVersions()).contains(V1, V2);
    }

    @Test
    final void expandStampEqualsOriginalScopeStamp() {
        ContractFixture fixture = ContractFixtures.cyclic();
        FieldSource source = sourceFor(fixture);
        FieldScope scope = source.resolve(defaultRequest(fixture));

        FieldScope expanded = source.expand(scope, relationshipPath(fixture, "toB"));

        assertThat(expanded.stamp()).isEqualTo(scope.stamp());
        assertThat(expanded.concreteTypes()).isEqualTo(scope.concreteTypes());
    }

    // ------------------------------------------------------------------------------------------
    // RBAC
    // ------------------------------------------------------------------------------------------

    @Test
    final void rbacFilteringHidesUnreadableClass() {
        ContractFixture fixture = ContractFixtures.rbacRestricted();
        FieldSource source = sourceFor(fixture);
        TraversalConfig rbacOn = new TraversalConfig(OptionalInt.of(3), true, StampMode.VERSION_ONLY, true);
        Principal noFinance = new Principal("no-finance", Set.of("sales"));
        FieldScopeRequest request = request(fixture, new VersionPolicy.Union(), rbacOn, Optional.of(noFinance));

        FieldScope scope = source.resolve(request);

        assertThat(scope.relationships()).noneMatch(r -> r.slug().equals("customer"));
    }

    @Test
    final void rbacDeniedPathRaisesPrincipalDenied() {
        ContractFixture fixture = ContractFixtures.rbacRestricted();
        FieldSource source = sourceFor(fixture);
        TraversalConfig rbacOn = new TraversalConfig(OptionalInt.of(3), true, StampMode.VERSION_ONLY, true);
        Principal noFinance = new Principal("no-finance", Set.of("sales"));
        FieldScopeRequest request = request(fixture, new VersionPolicy.Union(), rbacOn, Optional.of(noFinance));

        assertThat(codeOf(() -> source.describe(request, pathTo(fixture, "customer", "city"))))
                .isEqualTo(FieldErrorCode.PRINCIPAL_DENIED);
    }

    @Test
    final void rbacDisabledByDefaultShowsAllClasses() {
        ContractFixture fixture = ContractFixtures.rbacRestricted();
        FieldSource source = sourceFor(fixture);
        Principal noFinance = new Principal("no-finance", Set.of("sales"));
        FieldScopeRequest request =
                request(fixture, new VersionPolicy.Union(), TraversalConfig.defaults(), Optional.of(noFinance));

        FieldScope scope = source.resolve(request);

        assertThat(scope.relationships()).anyMatch(r -> r.slug().equals("customer"));
    }

    // ------------------------------------------------------------------------------------------
    // Codes
    // ------------------------------------------------------------------------------------------

    @Test
    final void pathNotFilterableIsDistinctFromUnknownPath() {
        ContractFixture fixture = ContractFixtures.invoiceAndCustomer();
        FieldSource source = sourceFor(fixture);
        FieldScopeRequest request = defaultRequest(fixture);

        FieldErrorCode notFilterable = codeOf(() -> source.describe(request, pathTo(fixture, "internalNotes")));
        FieldErrorCode unknown = codeOf(() -> source.describe(request, randomTerminalPath()));

        assertThat(notFilterable).isEqualTo(FieldErrorCode.PATH_NOT_FILTERABLE);
        assertThat(unknown).isEqualTo(FieldErrorCode.UNKNOWN_PATH);
        assertThat(notFilterable).isNotEqualTo(unknown);
    }

    /**
     * One triggering call per {@link FieldErrorCode} that {@link FieldSource} can raise (every value
     * except {@link FieldErrorCode#SCHEME_UNAVAILABLE}, which is a {@code ConceptSource} concern per
     * {@code FieldSource}'s own Javadoc), reusing the same fixtures and request-building helpers the
     * scenario-specific tests above use.
     */
    private Map<FieldErrorCode, ThrowingCallable> errorTriggers() {
        Map<FieldErrorCode, ThrowingCallable> triggers = new LinkedHashMap<>();

        ContractFixture invoiceAndCustomer = ContractFixtures.invoiceAndCustomer();
        FieldSource invoiceSource = sourceFor(invoiceAndCustomer);
        triggers.put(
                FieldErrorCode.UNKNOWN_TENANT,
                () -> invoiceSource.resolve(new FieldScopeRequest(
                        new TenantId("not-" + invoiceAndCustomer.tenantId().value()),
                        rootOf(invoiceAndCustomer),
                        new VersionPolicy.Union(),
                        TraversalConfig.defaults(),
                        Optional.empty(),
                        Optional.empty())));
        triggers.put(
                FieldErrorCode.UNKNOWN_CLASS,
                () -> invoiceSource.resolve(new FieldScopeRequest(
                        invoiceAndCustomer.tenantId(),
                        randomClassRef(),
                        new VersionPolicy.Union(),
                        TraversalConfig.defaults(),
                        Optional.empty(),
                        Optional.empty())));
        triggers.put(
                FieldErrorCode.UNKNOWN_PATH,
                () -> invoiceSource.describe(defaultRequest(invoiceAndCustomer), randomTerminalPath()));
        triggers.put(
                FieldErrorCode.PATH_NOT_FILTERABLE,
                () -> invoiceSource.describe(
                        defaultRequest(invoiceAndCustomer), pathTo(invoiceAndCustomer, "internalNotes")));

        ContractFixture unwalkableEdge = ContractFixtures.unwalkableEdge();
        FieldSource unwalkableSource = sourceFor(unwalkableEdge);
        triggers.put(
                FieldErrorCode.CLASS_NOT_REACHABLE,
                () -> unwalkableSource.describe(
                        defaultRequest(unwalkableEdge), pathTo(unwalkableEdge, "customer", "city")));
        triggers.put(
                FieldErrorCode.EDGE_NOT_WALKABLE,
                () -> unwalkableSource.describe(
                        defaultRequest(unwalkableEdge), singleRelationshipTerminalPath(unwalkableEdge, "customer")));

        ContractFixture deep = ContractFixtures.deep();
        FieldSource deepSource = sourceFor(deep);
        triggers.put(
                FieldErrorCode.DEPTH_EXCEEDED,
                () -> deepSource.expand(
                        deepSource.resolve(defaultRequest(deep)),
                        relationshipPath(deep, "step", "step", "step", "step")));

        ContractFixture cyclic = ContractFixtures.cyclic();
        FieldSource cyclicSource = sourceFor(cyclic);
        triggers.put(FieldErrorCode.CYCLE_DETECTED, () -> {
            FieldScope scope = cyclicSource.resolve(defaultRequest(cyclic));
            FieldScope expanded = cyclicSource.expand(scope, relationshipPath(cyclic, "toB"));
            cyclicSource.expand(expanded, relationshipPath(cyclic, "toB", "toA"));
        });

        ContractFixture renamed = ContractFixtures.renamedAcrossVersions();
        FieldSource renamedSource = sourceFor(renamed);
        triggers.put(
                FieldErrorCode.VERSION_NOT_FOUND,
                () -> renamedSource.resolve(request(
                        renamed,
                        new VersionPolicy.Pinned(new OntologyVersionRef(9)),
                        TraversalConfig.defaults(),
                        Optional.empty())));

        ContractFixture disjoint = ContractFixtures.disjointVersions();
        FieldSource disjointSource = sourceFor(disjoint);
        triggers.put(
                FieldErrorCode.EMPTY_INTERSECTION,
                () -> disjointSource.resolve(request(
                        disjoint, new VersionPolicy.Intersection(), TraversalConfig.defaults(), Optional.empty())));

        ContractFixture rbac = ContractFixtures.rbacRestricted();
        FieldSource rbacSource = sourceFor(rbac);
        TraversalConfig rbacOn = new TraversalConfig(OptionalInt.of(3), true, StampMode.VERSION_ONLY, true);
        Principal noFinance = new Principal("no-finance", Set.of());
        triggers.put(
                FieldErrorCode.PRINCIPAL_DENIED,
                () -> rbacSource.describe(
                        request(rbac, new VersionPolicy.Union(), rbacOn, Optional.of(noFinance)),
                        pathTo(rbac, "customer", "city")));

        return triggers;
    }

    @Test
    final void everyFieldErrorCodeIsReachable() {
        Map<FieldErrorCode, ThrowingCallable> triggers = errorTriggers();
        Set<FieldErrorCode> observed = new LinkedHashSet<>();
        for (Map.Entry<FieldErrorCode, ThrowingCallable> entry : triggers.entrySet()) {
            Throwable thrown = catchThrowable(entry.getValue());
            assertThat(thrown).as("triggering %s", entry.getKey()).isInstanceOf(FieldResolutionException.class);
            observed.add(((FieldResolutionException) thrown).code());
        }

        assertThat(observed).hasSize(11);
        assertThat(observed).containsExactlyInAnyOrderElementsOf(triggers.keySet());
    }

    @Test
    final void failuresAreAlwaysFieldResolutionException() {
        Map<FieldErrorCode, ThrowingCallable> triggers = errorTriggers();

        assertThat(catchThrowable(triggers.get(FieldErrorCode.UNKNOWN_TENANT)))
                .isExactlyInstanceOf(FieldResolutionException.class);
        assertThat(catchThrowable(triggers.get(FieldErrorCode.CLASS_NOT_REACHABLE)))
                .isExactlyInstanceOf(FieldResolutionException.class);
        assertThat(catchThrowable(triggers.get(FieldErrorCode.CYCLE_DETECTED)))
                .isExactlyInstanceOf(FieldResolutionException.class);
    }
}
