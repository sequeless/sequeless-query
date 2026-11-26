package org.sequeless.query.field.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FieldTest {

    private static final UUID STATUS_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final String STATUS_IRI = "urn:ont:property:Invoice.status";
    private static final OntologyVersionRef V1 = new OntologyVersionRef(1);
    private static final OntologyVersionRef V2 = new OntologyVersionRef(2);

    private static LocalizedText label(String value) {
        return new LocalizedText(value, "en");
    }

    private static FieldPath path(String slug) {
        return new FieldPath(List.of(new PathSegment(STATUS_ID, STATUS_IRI, slug, null)));
    }

    private static ClassRef classRef(String slug) {
        return new ClassRef(UUID.nameUUIDFromBytes(slug.getBytes()), slug, "urn:class:" + slug, label(slug));
    }

    private static Field facet(FieldType type, Cardinality cardinality, Set<OntologyVersionRef> versions) {
        return new Field(
                path("status"),
                type,
                null,
                cardinality,
                new Capabilities(true, true, false, true),
                label("Status"),
                null,
                Optional.empty(),
                OperatorRestriction.unrestricted(),
                InferenceSupport.ASSERTED_ONLY,
                versions,
                Optional.empty());
    }

    @Test
    void operatorRestrictionUnrestrictedHasEmptyLists() {
        OperatorRestriction unrestricted = OperatorRestriction.unrestricted();

        assertThat(unrestricted.allowed()).isEmpty();
        assertThat(unrestricted.denied()).isEmpty();
    }

    @Test
    void operatorRestrictionDefensiveCopyIsolatesFromSourceMutation() {
        List<String> denied = new ArrayList<>(List.of("is like"));
        OperatorRestriction restriction = new OperatorRestriction(new ArrayList<>(), denied);

        denied.add("between");

        assertThat(restriction.denied()).containsExactly("is like");
    }

    @Test
    void relationshipRejectsEmptyDefinedInVersions() {
        assertThatThrownBy(() -> new Relationship(
                        STATUS_ID,
                        "urn:ont:property:Invoice.customer",
                        "customer",
                        label("Customer"),
                        classRef("Customer"),
                        Cardinality.SINGLE,
                        Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definedInVersions");
    }

    @Test
    void facetConflictRejectsEmptyDefinitions() {
        assertThatThrownBy(() -> new FieldConflict(List.of(), "datatype changed between versions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definitions");
    }

    @Test
    void facetRejectsNullType() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> facet(null, Cardinality.SINGLE, Set.of(V1)));
    }

    @Test
    void facetRejectsNullCardinality() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> facet(FieldType.STRING, null, Set.of(V1)));
    }

    @Test
    void facetRejectsEmptyDefinedInVersions() {
        assertThatThrownBy(() -> facet(FieldType.STRING, Cardinality.SINGLE, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definedInVersions");
    }

    @Test
    void facetAllowsNullNativeDatatypeIriAndDescription() {
        Field subject = facet(FieldType.STRING, Cardinality.SINGLE, Set.of(V1));

        assertThat(subject.nativeDatatypeIri()).isNull();
        assertThat(subject.description()).isNull();
    }

    @Test
    void facetDefensiveCopyIsolatesFromSourceMutation() {
        Set<OntologyVersionRef> versions = new HashSet<>(Set.of(V1));
        Field subject = facet(FieldType.STRING, Cardinality.SINGLE, versions);

        versions.add(V2);

        assertThat(subject.definedInVersions()).containsExactly(V1);
    }

    @Test
    void conflictingDefinitionRejectsNullVersion() {
        assertThatThrownBy(() -> new ConflictingDefinition(null, FieldType.STRING, Cardinality.SINGLE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("version");
    }

    @Test
    void conceptSchemeRefRejectsBlankSchemeId() {
        assertThatThrownBy(() -> new ConceptSchemeRef("", "categories", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemeId");
    }

    @Test
    void capabilitiesAreFourIndependentBooleans() {
        // 'total' from the field-contract.md section 2.3 worked example: filterable and sortable, but not
        // facetable — every value is distinct, so bucketing produces noise rather than a picker.
        Capabilities total = new Capabilities(true, false, false, true);

        assertThat(total.filterable()).isTrue();
        assertThat(total.facetable()).isFalse();
        assertThat(total.searchable()).isFalse();
        assertThat(total.sortable()).isTrue();
    }
}
