package org.sequeless.query.field.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.TenantId;

class FieldScopeTest {

    private static final OntologyVersionRef V1 = new OntologyVersionRef(1);
    private static final OntologyVersionRef V2 = new OntologyVersionRef(2);

    private static LocalizedText label(String value) {
        return new LocalizedText(value, "en");
    }

    private static ClassRef classRef(String slug) {
        return new ClassRef(UUID.nameUUIDFromBytes(slug.getBytes()), slug, "urn:class:" + slug, label(slug));
    }

    private static FieldScopeRequest request(String tenantId) {
        return new FieldScopeRequest(
                new TenantId(tenantId),
                classRef("Invoice"),
                new VersionPolicy.Union(),
                TraversalConfig.defaults(),
                Optional.empty(),
                Optional.empty());
    }

    private static Field facet(String slug) {
        return new Field(
                new FieldPath(List.of(new PathSegment(
                        UUID.nameUUIDFromBytes(slug.getBytes()), "urn:ont:property:" + slug, slug, null))),
                FieldType.STRING,
                null,
                Cardinality.SINGLE,
                new Capabilities(true, true, false, true),
                label(slug),
                null,
                Optional.empty(),
                OperatorRestriction.unrestricted(),
                InferenceSupport.ASSERTED_ONLY,
                Set.of(V1),
                Optional.empty());
    }

    @Test
    void traversalConfigDefaultsMatchSpec() {
        assertThat(TraversalConfig.defaults())
                .isEqualTo(new TraversalConfig(OptionalInt.of(3), true, StampMode.VERSION_ONLY, false));
    }

    @Test
    void traversalConfigRejectsMaxDepthLessThanOne() {
        assertThatThrownBy(() -> new TraversalConfig(OptionalInt.of(0), true, StampMode.VERSION_ONLY, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDepth");
    }

    @Test
    void traversalConfigAllowsAbsentMaxDepth() {
        TraversalConfig unbounded = new TraversalConfig(OptionalInt.empty(), true, StampMode.VERSION_ONLY, false);

        assertThat(unbounded.maxDepth()).isEmpty();
    }

    @Test
    void pinnedRejectsNullVersion() {
        assertThatThrownBy(() -> new VersionPolicy.Pinned(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("version");
    }

    @Test
    void distinctUnionInstancesAreEqual() {
        // Record equality derives purely from the component list, and Union has none — so two
        // separate instances are always equal. This is why no singleton constant is needed.
        assertThat(new VersionPolicy.Union()).isEqualTo(new VersionPolicy.Union());
        assertThat(new VersionPolicy.Union()).hasSameHashCodeAs(new VersionPolicy.Union());
    }

    @Test
    void distinctIntersectionInstancesAreEqual() {
        assertThat(new VersionPolicy.Intersection()).isEqualTo(new VersionPolicy.Intersection());
        assertThat(new VersionPolicy.Intersection()).hasSameHashCodeAs(new VersionPolicy.Intersection());
    }

    @Test
    void pinnedInstancesWithDifferentVersionsAreNotEqual() {
        assertThat(new VersionPolicy.Pinned(V1)).isNotEqualTo(new VersionPolicy.Pinned(V2));
    }

    @Test
    void facetScopeRequestRejectsBlankTenantId() {
        assertThatThrownBy(() -> request("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TenantId");
    }

    @Test
    void principalDefensiveCopyIsolatesFromSourceMutation() {
        Set<String> roles = new HashSet<>(Set.of("analyst"));
        Principal principal = new Principal("u-1", roles);

        roles.add("admin");

        assertThat(principal.roles()).containsExactly("analyst");
    }

    @Test
    void facetScopeDefensiveCopyIsolatesFromSourceMutation() {
        List<Field> facets = new ArrayList<>(List.of(facet("status")));
        FieldScope scope =
                new FieldScope(classRef("Invoice"), facets, new ArrayList<>(), "stamp", Set.of(V1), request("acme"));

        facets.add(facet("total"));

        assertThat(scope.facets()).hasSize(1);
    }

    @Test
    void facetScopeRejectsBlankStamp() {
        assertThatThrownBy(() ->
                        new FieldScope(classRef("Invoice"), List.of(), List.of(), "  ", Set.of(V1), request("acme")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stamp");
    }
}
