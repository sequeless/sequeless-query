package org.sequeless.query.field.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.Cardinality;
import org.sequeless.query.field.api.ConceptSchemeRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldErrorCode;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldResolutionException;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.InferenceSupport;
import org.sequeless.query.field.api.LocalizedText;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.OperatorRestriction;
import org.sequeless.query.field.api.PathSegment;
import org.sequeless.query.field.api.VersionPolicy;

class FieldMergerTest {

    private static final UUID CUSTOMER_NAME_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID REGION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID ISSUED_AT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    private static final OntologyVersionRef V1 = new OntologyVersionRef(1);
    private static final OntologyVersionRef V2 = new OntologyVersionRef(2);
    private static final OntologyVersionRef V3 = new OntologyVersionRef(3);
    private static final OntologyVersionRef UNKNOWN_VERSION = new OntologyVersionRef(9);

    private static final Capabilities ALL_TRUE = new Capabilities(true, true, true, true);

    private static LocalizedText label(String value) {
        return new LocalizedText(value, "en");
    }

    private static FieldPath path(UUID propertyId, String slug) {
        return new FieldPath(List.of(new PathSegment(propertyId, slug, null)));
    }

    private static Field facetWithVersions(
            UUID propertyId, String slug, FieldType type, Cardinality cardinality, Set<OntologyVersionRef> versions) {
        return new Field(
                path(propertyId, slug),
                type,
                null,
                cardinality,
                ALL_TRUE,
                label(slug),
                null,
                Optional.empty(),
                OperatorRestriction.unrestricted(),
                InferenceSupport.ASSERTED_ONLY,
                versions,
                Optional.empty());
    }

    private static Field facet(
            UUID propertyId, String slug, FieldType type, Cardinality cardinality, OntologyVersionRef version) {
        return facetWithVersions(propertyId, slug, type, cardinality, Set.of(version));
    }

    private static Field facet(
            UUID propertyId,
            String slug,
            FieldType type,
            Cardinality cardinality,
            Capabilities capabilities,
            OperatorRestriction restriction,
            String nativeDatatypeIri,
            Optional<ConceptSchemeRef> conceptScheme,
            InferenceSupport inference,
            OntologyVersionRef version) {
        return new Field(
                path(propertyId, slug),
                type,
                nativeDatatypeIri,
                cardinality,
                capabilities,
                label(slug),
                null,
                conceptScheme,
                restriction,
                inference,
                Set.of(version),
                Optional.empty());
    }

    @Test
    void unionMergesFacetSharingPropertyUuidAcrossVersions() {
        Field v1 = facet(CUSTOMER_NAME_ID, "custName", FieldType.STRING, Cardinality.SINGLE, V1);
        Field v2 = facet(CUSTOMER_NAME_ID, "customerName", FieldType.STRING, Cardinality.SINGLE, V2);

        List<Field> merged = FieldMerger.merge(List.of(v1, v2), new VersionPolicy.Union(), List.of(V1, V2));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).definedInVersions()).containsExactlyInAnyOrder(V1, V2);
    }

    @Test
    void unionNewestVersionSlugWinsForDisplay() {
        Field v1 = facet(CUSTOMER_NAME_ID, "custName", FieldType.STRING, Cardinality.SINGLE, V1);
        Field v2 = facet(CUSTOMER_NAME_ID, "customerName", FieldType.STRING, Cardinality.SINGLE, V2);

        List<Field> merged = FieldMerger.merge(List.of(v1, v2), new VersionPolicy.Union(), List.of(V1, V2));

        assertThat(merged.get(0).path().asSlugPath()).isEqualTo("customerName");
        assertThat(merged.get(0).label().value()).isEqualTo("customerName");
    }

    @Test
    void unionRecordsUnionOfDefinedInVersions() {
        Field v1 = facet(CUSTOMER_NAME_ID, "custName", FieldType.STRING, Cardinality.SINGLE, V1);
        Field v2 = facet(CUSTOMER_NAME_ID, "customerName", FieldType.STRING, Cardinality.SINGLE, V2);

        List<Field> merged = FieldMerger.merge(List.of(v1, v2), new VersionPolicy.Union(), List.of(V1, V2));

        assertThat(merged.get(0).definedInVersions()).isEqualTo(Set.of(V1, V2));
    }

    @Test
    void unionKeepsVersionExclusiveFacet() {
        Field customerNameV1 = facet(CUSTOMER_NAME_ID, "custName", FieldType.STRING, Cardinality.SINGLE, V1);
        Field customerNameV2 = facet(CUSTOMER_NAME_ID, "customerName", FieldType.STRING, Cardinality.SINGLE, V2);
        Field regionV2 = facet(REGION_ID, "region", FieldType.STRING, Cardinality.SINGLE, V2);

        List<Field> merged = FieldMerger.merge(
                List.of(customerNameV1, customerNameV2, regionV2), new VersionPolicy.Union(), List.of(V1, V2));

        assertThat(merged).hasSize(2);
        Field region = merged.stream()
                .filter(f -> f.path().asSlugPath().equals("region"))
                .findFirst()
                .orElseThrow();
        assertThat(region.definedInVersions()).containsExactly(V2);
    }

    @Test
    void pinnedSelectsOnlyThatVersionsDefinition() {
        Field customerNameV1 = facet(CUSTOMER_NAME_ID, "custName", FieldType.STRING, Cardinality.SINGLE, V1);
        Field customerNameV2 = facet(CUSTOMER_NAME_ID, "customerName", FieldType.STRING, Cardinality.SINGLE, V2);
        Field regionV2 = facet(REGION_ID, "region", FieldType.STRING, Cardinality.SINGLE, V2);

        List<Field> merged = FieldMerger.merge(
                List.of(customerNameV1, customerNameV2, regionV2), new VersionPolicy.Pinned(V1), List.of(V1, V2));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).path().asSlugPath()).isEqualTo("custName");
        assertThat(merged.get(0).definedInVersions()).containsExactly(V1);
    }

    @Test
    void pinnedOmitsFacetAbsentFromPinnedVersion() {
        Field customerNameV1 = facet(CUSTOMER_NAME_ID, "custName", FieldType.STRING, Cardinality.SINGLE, V1);
        Field customerNameV2 = facet(CUSTOMER_NAME_ID, "customerName", FieldType.STRING, Cardinality.SINGLE, V2);
        Field regionV2 = facet(REGION_ID, "region", FieldType.STRING, Cardinality.SINGLE, V2);

        List<Field> merged = FieldMerger.merge(
                List.of(customerNameV1, customerNameV2, regionV2), new VersionPolicy.Pinned(V1), List.of(V1, V2));

        assertThat(merged).noneMatch(f -> f.path().asSlugPath().equals("region"));
    }

    @Test
    void pinnedUnknownVersionThrowsVersionNotFound() {
        Field v1 = facet(CUSTOMER_NAME_ID, "custName", FieldType.STRING, Cardinality.SINGLE, V1);

        assertThatExceptionOfType(FieldResolutionException.class)
                .isThrownBy(() ->
                        FieldMerger.merge(List.of(v1), new VersionPolicy.Pinned(UNKNOWN_VERSION), List.of(V1, V2)))
                .satisfies(e -> assertThat(e.code()).isEqualTo(FieldErrorCode.VERSION_NOT_FOUND));
    }

    @Test
    void pinnedNeverProducesConflict() {
        Field v1 = facet(ISSUED_AT_ID, "issuedAt", FieldType.STRING, Cardinality.SINGLE, V1);
        Field v2 = facet(ISSUED_AT_ID, "issuedAt", FieldType.DATETIME, Cardinality.SINGLE, V2);

        List<Field> merged = FieldMerger.merge(List.of(v1, v2), new VersionPolicy.Pinned(V1), List.of(V1, V2));

        assertThat(merged.get(0).conflict()).isEmpty();
    }

    @Test
    void intersectionKeepsOnlyFacetsInEveryInputVersion() {
        Field customerNameV1 = facet(CUSTOMER_NAME_ID, "custName", FieldType.STRING, Cardinality.SINGLE, V1);
        Field customerNameV2 = facet(CUSTOMER_NAME_ID, "customerName", FieldType.STRING, Cardinality.SINGLE, V2);
        Field regionV2 = facet(REGION_ID, "region", FieldType.STRING, Cardinality.SINGLE, V2);

        List<Field> merged = FieldMerger.merge(
                List.of(customerNameV1, customerNameV2, regionV2), new VersionPolicy.Intersection(), List.of(V1, V2));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).path().asSlugPath()).isEqualTo("customerName");
    }

    @Test
    void intersectionOmitsVersionExclusiveFacet() {
        Field customerNameV1 = facet(CUSTOMER_NAME_ID, "custName", FieldType.STRING, Cardinality.SINGLE, V1);
        Field customerNameV2 = facet(CUSTOMER_NAME_ID, "customerName", FieldType.STRING, Cardinality.SINGLE, V2);
        Field regionV2 = facet(REGION_ID, "region", FieldType.STRING, Cardinality.SINGLE, V2);

        List<Field> merged = FieldMerger.merge(
                List.of(customerNameV1, customerNameV2, regionV2), new VersionPolicy.Intersection(), List.of(V1, V2));

        assertThat(merged).noneMatch(f -> f.path().asSlugPath().equals("region"));
    }

    @Test
    void intersectionEmptyResultThrowsEmptyIntersection() {
        Field onlyV1 = facet(CUSTOMER_NAME_ID, "custName", FieldType.STRING, Cardinality.SINGLE, V1);
        Field onlyV2 = facet(REGION_ID, "region", FieldType.STRING, Cardinality.SINGLE, V2);

        assertThatExceptionOfType(FieldResolutionException.class)
                .isThrownBy(() ->
                        FieldMerger.merge(List.of(onlyV1, onlyV2), new VersionPolicy.Intersection(), List.of(V1, V2)))
                .satisfies(e -> assertThat(e.code()).isEqualTo(FieldErrorCode.EMPTY_INTERSECTION));
    }

    @Test
    void intersectionUsesVersionsPresentInInputNotGlobalVersionOrder() {
        Field customerNameV1 = facet(CUSTOMER_NAME_ID, "custName", FieldType.STRING, Cardinality.SINGLE, V1);
        Field customerNameV2 = facet(CUSTOMER_NAME_ID, "customerName", FieldType.STRING, Cardinality.SINGLE, V2);

        // versionOrder carries v3 too, but no input facet is defined in v3. Using versionOrder as
        // the "in scope" set would wrongly demand a v3 definition and raise EMPTY_INTERSECTION.
        List<Field> merged = FieldMerger.merge(
                List.of(customerNameV1, customerNameV2), new VersionPolicy.Intersection(), List.of(V1, V2, V3));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).path().asSlugPath()).isEqualTo("customerName");
    }

    @Test
    void conflictKeepsFieldAndAttachesFieldConflict() {
        Field v1 = facet(ISSUED_AT_ID, "issuedAt", FieldType.STRING, Cardinality.SINGLE, V1);
        Field v2 = facet(ISSUED_AT_ID, "issuedAt", FieldType.DATETIME, Cardinality.SINGLE, V2);

        List<Field> merged = FieldMerger.merge(List.of(v1, v2), new VersionPolicy.Union(), List.of(V1, V2));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).conflict()).isPresent();
    }

    @Test
    void conflictDoesNotThrow() {
        Field v1 = facet(ISSUED_AT_ID, "issuedAt", FieldType.STRING, Cardinality.SINGLE, V1);
        Field v2 = facet(ISSUED_AT_ID, "issuedAt", FieldType.DATETIME, Cardinality.SINGLE, V2);

        assertThatCode(() -> FieldMerger.merge(List.of(v1, v2), new VersionPolicy.Union(), List.of(V1, V2)))
                .doesNotThrowAnyException();
    }

    @Test
    void conflictListsEveryDefinitionNotOnlyDiffering() {
        Field v1 = facet(ISSUED_AT_ID, "issuedAt", FieldType.STRING, Cardinality.SINGLE, V1);
        Field v2 = facet(ISSUED_AT_ID, "issuedAt", FieldType.DATETIME, Cardinality.SINGLE, V2);
        Field v3 = facet(ISSUED_AT_ID, "issuedAt", FieldType.STRING, Cardinality.SINGLE, V3);

        List<Field> merged = FieldMerger.merge(List.of(v1, v2, v3), new VersionPolicy.Union(), List.of(V1, V2, V3));

        assertThat(merged.get(0).conflict()).isPresent();
        assertThat(merged.get(0).conflict().orElseThrow().definitions()).hasSize(3);
        assertThat(merged.get(0).conflict().orElseThrow().definitions())
                .extracting(d -> d.version())
                .containsExactly(V1, V2, V3);
    }

    @Test
    void conflictTypeMismatchResolvesToStringWhenNeitherIsUnknown() {
        Field v1 = facet(ISSUED_AT_ID, "issuedAt", FieldType.STRING, Cardinality.SINGLE, V1);
        Field v2 = facet(ISSUED_AT_ID, "issuedAt", FieldType.DATETIME, Cardinality.SINGLE, V2);

        List<Field> merged = FieldMerger.merge(List.of(v1, v2), new VersionPolicy.Union(), List.of(V1, V2));

        assertThat(merged.get(0).type()).isEqualTo(FieldType.STRING);
    }

    @Test
    void conflictTypeMismatchResolvesToUnknownWhenAnyDefinitionIsUnknown() {
        Field v1 = facet(ISSUED_AT_ID, "issuedAt", FieldType.STRING, Cardinality.SINGLE, V1);
        Field v2 = facet(ISSUED_AT_ID, "issuedAt", FieldType.UNKNOWN, Cardinality.SINGLE, V2);

        List<Field> merged = FieldMerger.merge(List.of(v1, v2), new VersionPolicy.Union(), List.of(V1, V2));

        assertThat(merged.get(0).type()).isEqualTo(FieldType.UNKNOWN);
    }

    @Test
    void conflictCardinalityMismatchResolvesToMany() {
        Field v1 = facet(ISSUED_AT_ID, "issuedAt", FieldType.STRING, Cardinality.SINGLE, V1);
        Field v2 = facet(ISSUED_AT_ID, "issuedAt", FieldType.STRING, Cardinality.MANY, V2);

        List<Field> merged = FieldMerger.merge(List.of(v1, v2), new VersionPolicy.Union(), List.of(V1, V2));

        assertThat(merged.get(0).cardinality()).isEqualTo(Cardinality.MANY);
    }

    @Test
    void conflictCapabilitiesAreAndedAcrossDefinitions() {
        Capabilities allCapable = new Capabilities(true, true, true, true);
        Capabilities notSortable = new Capabilities(true, true, true, false);
        Field v1 = facet(
                ISSUED_AT_ID,
                "issuedAt",
                FieldType.STRING,
                Cardinality.SINGLE,
                allCapable,
                OperatorRestriction.unrestricted(),
                null,
                Optional.empty(),
                InferenceSupport.ASSERTED_ONLY,
                V1);
        Field v2 = facet(
                ISSUED_AT_ID,
                "issuedAt",
                FieldType.STRING,
                Cardinality.SINGLE,
                notSortable,
                OperatorRestriction.unrestricted(),
                null,
                Optional.empty(),
                InferenceSupport.ASSERTED_ONLY,
                V2);

        List<Field> merged = FieldMerger.merge(List.of(v1, v2), new VersionPolicy.Union(), List.of(V1, V2));

        assertThat(merged.get(0).capabilities()).isEqualTo(new Capabilities(true, true, true, false));
    }

    @Test
    void conflictRestrictionIntersectsAllowedList() {
        OperatorRestriction v1Restriction = new OperatorRestriction(List.of("is", "is not", "contains"), List.of());
        OperatorRestriction v2Restriction = new OperatorRestriction(List.of("is", "contains"), List.of());
        Field v1 = facet(
                ISSUED_AT_ID,
                "issuedAt",
                FieldType.STRING,
                Cardinality.SINGLE,
                ALL_TRUE,
                v1Restriction,
                null,
                Optional.empty(),
                InferenceSupport.ASSERTED_ONLY,
                V1);
        Field v2 = facet(
                ISSUED_AT_ID,
                "issuedAt",
                FieldType.STRING,
                Cardinality.SINGLE,
                ALL_TRUE,
                v2Restriction,
                null,
                Optional.empty(),
                InferenceSupport.ASSERTED_ONLY,
                V2);

        List<Field> merged = FieldMerger.merge(List.of(v1, v2), new VersionPolicy.Union(), List.of(V1, V2));

        assertThat(merged.get(0).restriction().allowed()).containsExactly("is", "contains");
    }

    @Test
    void conflictRestrictionUnionsDeniedList() {
        OperatorRestriction v1Restriction = new OperatorRestriction(List.of(), List.of("between"));
        OperatorRestriction v2Restriction = new OperatorRestriction(List.of(), List.of("is like"));
        Field v1 = facet(
                ISSUED_AT_ID,
                "issuedAt",
                FieldType.STRING,
                Cardinality.SINGLE,
                ALL_TRUE,
                v1Restriction,
                null,
                Optional.empty(),
                InferenceSupport.ASSERTED_ONLY,
                V1);
        Field v2 = facet(
                ISSUED_AT_ID,
                "issuedAt",
                FieldType.STRING,
                Cardinality.SINGLE,
                ALL_TRUE,
                v2Restriction,
                null,
                Optional.empty(),
                InferenceSupport.ASSERTED_ONLY,
                V2);

        List<Field> merged = FieldMerger.merge(List.of(v1, v2), new VersionPolicy.Union(), List.of(V1, V2));

        assertThat(merged.get(0).restriction().denied()).containsExactly("between", "is like");
    }

    @Test
    void conflictRestrictionEmptyAllowedActsAsNoOpinion() {
        OperatorRestriction v1Restriction = OperatorRestriction.unrestricted();
        OperatorRestriction v2Restriction = new OperatorRestriction(List.of("is", "is not"), List.of());
        Field v1 = facet(
                ISSUED_AT_ID,
                "issuedAt",
                FieldType.STRING,
                Cardinality.SINGLE,
                ALL_TRUE,
                v1Restriction,
                null,
                Optional.empty(),
                InferenceSupport.ASSERTED_ONLY,
                V1);
        Field v2 = facet(
                ISSUED_AT_ID,
                "issuedAt",
                FieldType.STRING,
                Cardinality.SINGLE,
                ALL_TRUE,
                v2Restriction,
                null,
                Optional.empty(),
                InferenceSupport.ASSERTED_ONLY,
                V2);

        List<Field> merged = FieldMerger.merge(List.of(v1, v2), new VersionPolicy.Union(), List.of(V1, V2));

        assertThat(merged.get(0).restriction().allowed()).containsExactly("is", "is not");
    }

    @Test
    void conflictInferenceMismatchResolvesToMayBeInferred() {
        Field v1 = facet(
                ISSUED_AT_ID,
                "issuedAt",
                FieldType.STRING,
                Cardinality.SINGLE,
                ALL_TRUE,
                OperatorRestriction.unrestricted(),
                null,
                Optional.empty(),
                InferenceSupport.ASSERTED_ONLY,
                V1);
        Field v2 = facet(
                ISSUED_AT_ID,
                "issuedAt",
                FieldType.STRING,
                Cardinality.SINGLE,
                ALL_TRUE,
                OperatorRestriction.unrestricted(),
                null,
                Optional.empty(),
                InferenceSupport.MAY_BE_INFERRED,
                V2);

        List<Field> merged = FieldMerger.merge(List.of(v1, v2), new VersionPolicy.Union(), List.of(V1, V2));

        assertThat(merged.get(0).inference()).isEqualTo(InferenceSupport.MAY_BE_INFERRED);
    }

    @Test
    void conflictReasonDescribesWhatChanged() {
        Field typeOnlyV1 = facet(ISSUED_AT_ID, "issuedAt", FieldType.STRING, Cardinality.SINGLE, V1);
        Field typeOnlyV2 = facet(ISSUED_AT_ID, "issuedAt", FieldType.DATETIME, Cardinality.SINGLE, V2);
        Field cardinalityOnlyV1 = facet(ISSUED_AT_ID, "issuedAt", FieldType.STRING, Cardinality.SINGLE, V1);
        Field cardinalityOnlyV2 = facet(ISSUED_AT_ID, "issuedAt", FieldType.STRING, Cardinality.MANY, V2);
        Field bothV1 = facet(ISSUED_AT_ID, "issuedAt", FieldType.STRING, Cardinality.SINGLE, V1);
        Field bothV2 = facet(ISSUED_AT_ID, "issuedAt", FieldType.DATETIME, Cardinality.MANY, V2);

        String typeReason = FieldMerger.merge(
                        List.of(typeOnlyV1, typeOnlyV2), new VersionPolicy.Union(), List.of(V1, V2))
                .get(0)
                .conflict()
                .orElseThrow()
                .reason();
        String cardinalityReason = FieldMerger.merge(
                        List.of(cardinalityOnlyV1, cardinalityOnlyV2), new VersionPolicy.Union(), List.of(V1, V2))
                .get(0)
                .conflict()
                .orElseThrow()
                .reason();
        String bothReason = FieldMerger.merge(List.of(bothV1, bothV2), new VersionPolicy.Union(), List.of(V1, V2))
                .get(0)
                .conflict()
                .orElseThrow()
                .reason();

        assertThat(typeReason).isEqualTo("datatype changed between versions");
        assertThat(cardinalityReason).isEqualTo("cardinality changed between versions");
        assertThat(bothReason).isEqualTo("datatype and cardinality changed between versions");
    }

    @Test
    void mergeRejectsInputFacetWithMultipleDefinedInVersions() {
        Field invalid =
                facetWithVersions(ISSUED_AT_ID, "issuedAt", FieldType.STRING, Cardinality.SINGLE, Set.of(V1, V2));

        assertThatThrownBy(() -> FieldMerger.merge(List.of(invalid), new VersionPolicy.Union(), List.of(V1, V2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuedAt");
    }

    @Test
    void mergeRejectsVersionNotInVersionOrder() {
        Field v1 = facet(ISSUED_AT_ID, "issuedAt", FieldType.STRING, Cardinality.SINGLE, UNKNOWN_VERSION);

        assertThatThrownBy(() -> FieldMerger.merge(List.of(v1), new VersionPolicy.Union(), List.of(V1, V2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'" + UNKNOWN_VERSION.value() + "'");
    }

    @Test
    void mergeOutputIsSortedBySlugPath() {
        Field zeta = facet(
                UUID.fromString("00000000-0000-0000-0000-0000000000d1"),
                "zeta",
                FieldType.STRING,
                Cardinality.SINGLE,
                V1);
        Field alpha = facet(
                UUID.fromString("00000000-0000-0000-0000-0000000000d2"),
                "alpha",
                FieldType.STRING,
                Cardinality.SINGLE,
                V1);
        Field mid = facet(
                UUID.fromString("00000000-0000-0000-0000-0000000000d3"),
                "mid",
                FieldType.STRING,
                Cardinality.SINGLE,
                V1);

        List<Field> merged = FieldMerger.merge(List.of(zeta, alpha, mid), new VersionPolicy.Union(), List.of(V1, V2));

        assertThat(merged).extracting(f -> f.path().asSlugPath()).containsExactly("alpha", "mid", "zeta");
    }
}
