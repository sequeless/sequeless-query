package org.sequeless.query.schema;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.sequeless.query.schema.SchemaFixtures.OTHER_TENANT;
import static org.sequeless.query.schema.SchemaFixtures.TENANT;
import static org.sequeless.query.schema.SchemaFixtures.attribute;
import static org.sequeless.query.schema.SchemaFixtures.classRef;
import static org.sequeless.query.schema.SchemaFixtures.concreteType;
import static org.sequeless.query.schema.SchemaFixtures.idOf;
import static org.sequeless.query.schema.SchemaFixtures.iri;
import static org.sequeless.query.schema.SchemaFixtures.path;
import static org.sequeless.query.schema.SchemaFixtures.relationship;
import static org.sequeless.query.schema.SchemaFixtures.request;
import static org.sequeless.query.schema.SchemaFixtures.schema;
import static org.sequeless.query.schema.SchemaFixtures.segment;
import static org.sequeless.query.schema.SchemaFixtures.subtype;
import static org.sequeless.query.schema.SchemaFixtures.type;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.Cardinality;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldErrorCode;
import org.sequeless.query.field.api.FieldResolutionException;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.InferenceSupport;
import org.sequeless.query.field.api.LocalizedText;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.OperatorRestriction;
import org.sequeless.query.field.api.Relationship;
import org.sequeless.query.field.api.StampMode;
import org.sequeless.query.field.api.TraversalConfig;
import org.sequeless.query.field.api.VersionPolicy;
import org.sequeless.query.field.spi.FieldSource;

class SchemaFieldSourceTest {

    private static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";
    private static final OntologyVersionRef V1 = new OntologyVersionRef(1);
    private static final OntologyVersionRef V2 = new OntologyVersionRef(2);
    private static final OntologyVersionRef V3 = new OntologyVersionRef(3);

    @Test
    void attributeMapsToItsFieldTypeAndCapabilities() {
        FieldScope scope = resolve(
                schema(
                        1,
                        concreteType(
                                "Person",
                                attribute("name", Datatype.STRING, true, true),
                                attribute("age", Datatype.INTEGER, false, false))),
                "Person");

        assertThat(scope.facets()).hasSize(2);
        Field name = scope.facets().get(0);
        assertThat(name.path()).isEqualTo(path(segment("name", null)));
        assertThat(name.type()).isEqualTo(FieldType.STRING);
        assertThat(name.nativeDatatypeIri()).isEqualTo(XSD_STRING);
        assertThat(name.cardinality()).isEqualTo(Cardinality.MANY);
        assertThat(name.capabilities()).isEqualTo(new Capabilities(true, true, true, false));
        assertThat(name.label()).isEqualTo(new LocalizedText("name label", "und"));
        assertThat(name.description()).isEqualTo(new LocalizedText("", "und"));
        assertThat(name.conceptScheme()).isEmpty();
        assertThat(name.restriction()).isEqualTo(OperatorRestriction.unrestricted());
        assertThat(name.inference()).isEqualTo(InferenceSupport.MAY_BE_INFERRED);
        assertThat(name.definedInVersions()).containsExactly(V1);
        assertThat(name.conflict()).isEmpty();

        Field age = scope.facets().get(1);
        assertThat(age.type()).isEqualTo(FieldType.INTEGER);
        assertThat(age.capabilities()).isEqualTo(new Capabilities(true, false, false, false));
    }

    @Test
    void relationshipIsAnObjectReferenceWithATargetClassAndYieldsARelationshipRecord() {
        FieldScope scope = resolve(
                schema(
                        1,
                        concreteType("Person", relationship("employer", "Company")),
                        concreteType("Company", attribute("legalName", Datatype.STRING, true, true))),
                "Person");

        Field employer = scope.facets().get(0);
        assertThat(employer.type()).isEqualTo(FieldType.OBJECT_REF);
        assertThat(employer.nativeDatatypeIri()).isNull();
        assertThat(employer.path()).isEqualTo(path(segment("employer", "Company")));
        assertThat(employer.path().segments().get(0).targetClass()).isEqualTo(classRef("Company"));
        assertThat(scope.relationships())
                .containsExactly(new Relationship(
                        idOf(iri("employer")),
                        iri("employer"),
                        "employer",
                        new LocalizedText("employer label", "und"),
                        classRef("Company"),
                        Cardinality.MANY,
                        Set.of(V1)));
    }

    @Test
    void everyDatatypeConstantMapsToItsFieldType() {
        Map<Datatype, FieldType> expected = Map.ofEntries(
                entry(Datatype.STRING, FieldType.STRING),
                entry(Datatype.BOOLEAN, FieldType.BOOLEAN),
                entry(Datatype.INTEGER, FieldType.INTEGER),
                entry(Datatype.LONG, FieldType.INTEGER),
                entry(Datatype.DECIMAL, FieldType.DECIMAL),
                entry(Datatype.DOUBLE, FieldType.DECIMAL),
                entry(Datatype.DATE, FieldType.DATE),
                entry(Datatype.TIME, FieldType.TIME),
                entry(Datatype.DATE_TIME, FieldType.DATETIME),
                entry(Datatype.DURATION, FieldType.DURATION),
                entry(Datatype.ANY_URI, FieldType.IRI_REF));
        assertThat(expected).containsOnlyKeys(Datatype.values());

        Datatype[] datatypes = Datatype.values();
        PropertyDefinition[] attributes = Arrays.stream(datatypes)
                .map(datatype -> attribute(datatype.name().toLowerCase(Locale.ROOT), datatype, true, true))
                .toArray(PropertyDefinition[]::new);
        FieldScope scope = resolve(schema(1, concreteType("Person", attributes)), "Person");

        assertThat(scope.facets()).hasSize(datatypes.length);
        for (int i = 0; i < datatypes.length; i++) {
            assertThat(scope.facets().get(i).type()).as(datatypes[i].name()).isEqualTo(expected.get(datatypes[i]));
        }
    }

    @Test
    void cardinalityMapsAtMostOneValueToSingleAndAnythingElseToMany() {
        FieldScope scope = resolve(
                schema(
                        1,
                        concreteType(
                                "Person",
                                attribute(
                                        "optional",
                                        Datatype.STRING,
                                        org.sequeless.ontology.schema.api.Cardinality.optional(),
                                        true,
                                        true),
                                attribute(
                                        "required",
                                        Datatype.STRING,
                                        org.sequeless.ontology.schema.api.Cardinality.required(),
                                        true,
                                        true),
                                attribute(
                                        "exactlyOne",
                                        Datatype.STRING,
                                        org.sequeless.ontology.schema.api.Cardinality.exactly(1),
                                        true,
                                        true),
                                attribute(
                                        "atMostOne",
                                        Datatype.STRING,
                                        org.sequeless.ontology.schema.api.Cardinality.atMost(1),
                                        true,
                                        true),
                                attribute(
                                        "range",
                                        Datatype.STRING,
                                        org.sequeless.ontology.schema.api.Cardinality.range(0, 5),
                                        true,
                                        true),
                                attribute(
                                        "exactlyZero",
                                        Datatype.STRING,
                                        org.sequeless.ontology.schema.api.Cardinality.exactly(0),
                                        true,
                                        true),
                                attribute(
                                        "atMostTwo",
                                        Datatype.STRING,
                                        org.sequeless.ontology.schema.api.Cardinality.atMost(2),
                                        true,
                                        true))),
                "Person");

        assertThat(scope.facets())
                .extracting(Field::cardinality)
                .containsExactly(
                        Cardinality.MANY,
                        Cardinality.MANY,
                        Cardinality.SINGLE,
                        Cardinality.SINGLE,
                        Cardinality.MANY,
                        Cardinality.SINGLE,
                        Cardinality.MANY);
    }

    @Test
    void concreteRootCoversItselfAndItsConcreteSubtypesOnly() {
        FieldScope scope = resolve(
                schema(
                        1,
                        concreteType("Root"),
                        subtype("ConcreteSub", false, "Root"),
                        subtype("AbstractSub", true, "Root")),
                "Root");

        assertThat(scope.concreteTypes()).containsExactlyInAnyOrder(classRef("Root"), classRef("ConcreteSub"));
    }

    @Test
    void abstractRootWithNoConcreteSubtypesHasAnEmptyConcreteSet() {
        FieldScope scope =
                resolve(schema(1, type("Root", true, List.of()), subtype("AbstractSub", true, "Root")), "Root");

        assertThat(scope.concreteTypes()).isEmpty();
        assertThat(scope.stamp()).isNotBlank();
    }

    @Test
    void abstractRootCoversConcreteSubtypesReachedThroughAbstractOnes() {
        FieldScope scope = resolve(
                schema(1, type("Root", true, List.of()), subtype("Mid", true, "Root"), subtype("Leaf", false, "Mid")),
                "Root");

        assertThat(scope.concreteTypes()).containsExactly(classRef("Leaf"));
    }

    @Test
    void reachTerminatesOnCyclicSuperTypesAndCollectsOnlyTheConcreteTypesReached() {
        FieldSource source = source(schema(
                1,
                type("A", false, List.of("B")),
                type("B", false, List.of("A")),
                type("C", false, List.of("A")),
                concreteType("Unrelated")));

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertThat(source.resolve(request("A")).concreteTypes())
                    .containsExactlyInAnyOrder(classRef("A"), classRef("B"), classRef("C"));
            // Unrelated reaches no type of the A/B cycle, so its walk must visit the cycle and stop.
            assertThat(source.resolve(request("Unrelated")).concreteTypes()).containsExactly(classRef("Unrelated"));
        });
    }

    @Test
    void rootClassesIncludeAbstractTypesAndAreSortedByIri() {
        FieldSource source =
                source(schema(1, concreteType("Zeta"), type("Alpha", true, List.of()), concreteType("Mid")));

        assertThat(source.rootClasses(TENANT)).containsExactly(classRef("Alpha"), classRef("Mid"), classRef("Zeta"));
    }

    @Test
    void stampIsTheVersionAndConcreteTypesInTheVersionOnlyForm() {
        FieldScope scope = resolve(schema(7, concreteType("Person")), "Person");

        assertThat(scope.stamp()).isEqualTo("7|concreteTypes:" + iri("Person"));
        assertThat(scope.ontologyVersions()).containsExactly(new OntologyVersionRef(7));
    }

    @Test
    void unknownRootThrowsUnknownClass() {
        FieldSource source = source(schema(1, concreteType("Person")));

        assertCode(() -> source.resolve(request("Missing")), FieldErrorCode.UNKNOWN_CLASS);
    }

    @Test
    void pinnedToAnotherVersionThrowsVersionNotFound() {
        FieldSource source = source(schema(2, concreteType("Person")));
        FieldScopeRequest request = request(TENANT, "Person", new VersionPolicy.Pinned(V1), TraversalConfig.defaults());

        assertCode(() -> source.resolve(request), FieldErrorCode.VERSION_NOT_FOUND);
    }

    @Test
    void pinnedToTheActiveVersionResolves() {
        FieldSource source = source(schema(2, concreteType("Person")));
        FieldScopeRequest request = request(TENANT, "Person", new VersionPolicy.Pinned(V2), TraversalConfig.defaults());

        assertThat(source.resolve(request).ontologyVersions()).containsExactly(V2);
    }

    @Test
    void unionAndIntersectionBothResolveAgainstTheActiveSchema() {
        FieldSource source = source(schema(3, concreteType("Person", attribute("name", Datatype.STRING, true, true))));

        for (VersionPolicy policy : List.of(new VersionPolicy.Union(), new VersionPolicy.Intersection())) {
            FieldScope scope = source.resolve(request(TENANT, "Person", policy, TraversalConfig.defaults()));

            assertThat(scope.ontologyVersions()).as(policy.toString()).containsExactly(V3);
            assertThat(scope.facets()).hasSize(1);
            assertThat(scope.facets().get(0).definedInVersions()).containsExactly(V3);
        }
    }

    @Test
    void describeAnUnknownPathThrowsUnknownPath() {
        FieldSource source = source(schema(1, concreteType("Person", attribute("name", Datatype.STRING, true, true))));

        assertCode(
                () -> source.describe(request("Person"), path(segment("missing", null))), FieldErrorCode.UNKNOWN_PATH);
    }

    @Test
    void describeFollowsRelationshipsToTheTerminalField() {
        FieldSource source = source(schema(
                1,
                concreteType("Person", relationship("employer", "Company")),
                concreteType("Company", attribute("legalName", Datatype.STRING, true, true))));

        Field field =
                source.describe(request("Person"), path(segment("employer", "Company"), segment("legalName", null)));

        assertThat(field.path().asSlugPath()).isEqualTo("employer.legalName");
        assertThat(field.type()).isEqualTo(FieldType.STRING);
    }

    @Test
    void describeThroughAMissingOrScalarSegmentThrowsClassNotReachable() {
        FieldSource source = source(schema(
                1,
                concreteType(
                        "Person", attribute("name", Datatype.STRING, true, true), relationship("employer", "Company")),
                concreteType("Company", attribute("legalName", Datatype.STRING, true, true))));

        assertCode(
                () -> source.describe(
                        request("Person"), path(segment("missing", "Company"), segment("legalName", null))),
                FieldErrorCode.CLASS_NOT_REACHABLE);
        assertCode(
                () -> source.describe(request("Person"), path(segment("name", "Company"), segment("legalName", null))),
                FieldErrorCode.CLASS_NOT_REACHABLE);
    }

    @Test
    void describeOfOneSegmentPathSucceedsUnderMaxDepthOne() {
        FieldSource source = source(schema(1, concreteType("Person", attribute("name", Datatype.STRING, true, true))));

        Field field = source.describe(depthOneRequest(), path(segment("name", null)));

        assertThat(field.type()).isEqualTo(FieldType.STRING);
    }

    @Test
    void describeTerminalHopIsNotCheckedAgainstMaxDepth() {
        FieldSource source = source(schema(
                1,
                concreteType("Person", relationship("employer", "Company")),
                concreteType("Company", attribute("legalName", Datatype.STRING, true, true))));

        Field field =
                source.describe(depthOneRequest(), path(segment("employer", "Company"), segment("legalName", null)));

        assertThat(field.path().asSlugPath()).isEqualTo("employer.legalName");
    }

    @Test
    void describeRejectsANonTerminalHopBeyondMaxDepth() {
        FieldSource source = source(schema(
                1,
                concreteType("Person", relationship("employer", "Company")),
                concreteType("Company", relationship("headquarters", "Place")),
                concreteType("Place", attribute("name", Datatype.STRING, true, true))));

        assertCode(
                () -> source.describe(
                        depthOneRequest(),
                        path(segment("employer", "Company"), segment("headquarters", "Place"), segment("name", null))),
                FieldErrorCode.DEPTH_EXCEEDED);
    }

    @Test
    void describeOfASelfReferencingRelationshipAsTheTerminalSegmentIsNotACycle() {
        FieldSource source = source(schema(1, concreteType("Person", relationship("friend", "Person"))));

        Field field = source.describe(request("Person"), path(segment("friend", "Person")));

        assertThat(field.type()).isEqualTo(FieldType.OBJECT_REF);
    }

    @Test
    void expandAddsTheTargetsFieldsUnderExtendedPathsAndKeepsEverythingElse() {
        FieldSource source = source(schema(
                1,
                concreteType(
                        "Person", attribute("name", Datatype.STRING, true, true), relationship("employer", "Company")),
                concreteType(
                        "Company",
                        attribute("legalName", Datatype.STRING, true, true),
                        relationship("headquarters", "Place")),
                concreteType("Place")));
        FieldScope root = source.resolve(request("Person"));

        FieldScope expanded = source.expand(root, path(segment("employer", "Company")));

        // Company has two properties, so expansion adds two Fields after the root's own.
        assertThat(expanded.facets()).hasSize(root.facets().size() + 2);
        assertThat(expanded.facets().subList(0, root.facets().size())).isEqualTo(root.facets());
        Field legalName = expanded.facets().get(root.facets().size());
        assertThat(legalName.path().asSlugPath()).isEqualTo("employer.legalName");
        assertThat(legalName.type()).isEqualTo(FieldType.STRING);
        Field headquarters = expanded.facets().get(root.facets().size() + 1);
        assertThat(headquarters.path().asSlugPath()).isEqualTo("employer.headquarters");
        assertThat(headquarters.type()).isEqualTo(FieldType.OBJECT_REF);
        assertThat(expanded.relationships()).hasSize(root.relationships().size() + 1);
        assertThat(expanded.relationships()
                        .get(expanded.relationships().size() - 1)
                        .slug())
                .isEqualTo("headquarters");
        assertThat(expanded.stamp()).isEqualTo(root.stamp());
        assertThat(expanded.request()).isEqualTo(root.request());
        assertThat(expanded.concreteTypes()).isEqualTo(root.concreteTypes());
    }

    @Test
    void expandRejectsARevisitWhenClassesMayNotBeRevisited() {
        FieldSource source = source(schema(1, concreteType("Person", relationship("friend", "Person"))));
        FieldScope scope = source.resolve(request("Person"));

        assertCode(() -> source.expand(scope, path(segment("friend", "Person"))), FieldErrorCode.CYCLE_DETECTED);
    }

    @Test
    void expandRejectsAPathDeeperThanMaxDepth() {
        FieldSource source = source(schema(
                1,
                concreteType("Person", relationship("employer", "Company")),
                concreteType("Company", relationship("owner", "Person"))));
        TraversalConfig maxDepthOne = new TraversalConfig(OptionalInt.of(1), false, StampMode.VERSION_ONLY, false);
        FieldScope scope = source.resolve(request(TENANT, "Person", new VersionPolicy.Union(), maxDepthOne));

        assertCode(
                () -> source.expand(scope, path(segment("employer", "Company"), segment("owner", "Person"))),
                FieldErrorCode.DEPTH_EXCEEDED);
    }

    @Test
    void expandRejectsAScalarOrLiteralSegmentAsUnknownPath() {
        FieldSource source = source(schema(
                1,
                concreteType(
                        "Person", attribute("name", Datatype.STRING, true, true), relationship("employer", "Company")),
                concreteType("Company")));
        FieldScope scope = source.resolve(request("Person"));

        assertCode(() -> source.expand(scope, path(segment("name", null))), FieldErrorCode.UNKNOWN_PATH);
        assertCode(() -> source.expand(scope, path(segment("employer", null))), FieldErrorCode.UNKNOWN_PATH);
    }

    @Test
    void expandRejectsAPinnedVersionThatIsNoLongerActive() {
        AtomicReference<Schema> active = new AtomicReference<>(
                schema(1, concreteType("Person", relationship("employer", "Company")), concreteType("Company")));
        FieldSource source = new SchemaFieldSource(tenant -> active.get());
        FieldScope scope =
                source.resolve(request(TENANT, "Person", new VersionPolicy.Pinned(V1), TraversalConfig.defaults()));

        active.set(schema(2, concreteType("Person", relationship("employer", "Company")), concreteType("Company")));

        assertCode(() -> source.expand(scope, path(segment("employer", "Company"))), FieldErrorCode.VERSION_NOT_FOUND);
    }

    @Test
    void theRequestsTenantSelectsTheSchemaRead() {
        FieldSource source = new SchemaFieldSource(new FakeSchemaSource(Map.of(
                TENANT, schema(1, concreteType("Person")),
                OTHER_TENANT, schema(2, concreteType("Robot")))));

        assertThat(source.rootClasses(OTHER_TENANT)).containsExactly(classRef("Robot"));
        FieldScope scope =
                source.resolve(request(OTHER_TENANT, "Robot", new VersionPolicy.Union(), TraversalConfig.defaults()));
        assertThat(scope.ontologyVersions()).containsExactly(new OntologyVersionRef(2));
    }

    @Test
    void rejectsANullSchemaSource() {
        assertThatNullPointerException().isThrownBy(() -> new SchemaFieldSource(null));
    }

    private static FieldSource source(Schema schema) {
        return new SchemaFieldSource(new FakeSchemaSource(Map.of(TENANT, schema)));
    }

    /** A request for {@code Person} that may hop once, with the revisit guard on, so only depth is in play. */
    private static FieldScopeRequest depthOneRequest() {
        return request(
                TENANT,
                "Person",
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(1), true, StampMode.VERSION_ONLY, false));
    }

    private static FieldScope resolve(Schema schema, String rootLocalName) {
        return source(schema).resolve(request(rootLocalName));
    }

    private static void assertCode(ThrowingCallable call, FieldErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        FieldResolutionException.class,
                        error -> assertThat(error.code()).isEqualTo(code));
    }
}
