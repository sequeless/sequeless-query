package org.sequeless.query.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sequeless.query.schema.SchemaFixtures.TENANT;
import static org.sequeless.query.schema.SchemaFixtures.attribute;
import static org.sequeless.query.schema.SchemaFixtures.concreteType;
import static org.sequeless.query.schema.SchemaFixtures.iri;
import static org.sequeless.query.schema.SchemaFixtures.label;
import static org.sequeless.query.schema.SchemaFixtures.request;
import static org.sequeless.query.schema.SchemaFixtures.schema;
import static org.sequeless.query.schema.SchemaFixtures.type;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.RelationshipDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldScope;

/**
 * Parity of per-property usability (sort, filter, facet) over the prototype's reference ontology, for its two
 * abstract roots (Navigator parity check).
 *
 * <p>The ontology is {@code sequeless-app/src/main/resources/ontology/reference.ttl} in sequeless-prototype. It is
 * built here rather than loaded, because loading a TTL needs the Jena adapter, which this module does not depend
 * on. Each type lists its own properties and its inherited ones, and each copy carries the cardinality its
 * restrictions give it, as the Jena snapshot mapper attributes them (sequeless-ontology,
 * SnapshotMapper.java:264-267, :311-317, :445-451). Restrictions are the ones in reference.ttl: WorkItem's status
 * (:53), Task's title minimum (:61-62) and its single-valued restrictions (:64-74), and Project's (:80-88).
 *
 * <p>Three usability maps are compared for each root: {@code OLD} is origin/main's root-only scope, {@code ANY_COPY}
 * is the union rule before root-wins (every copy of an IRI, agreeing or not), and {@code NEW} is this change.
 */
class ReferenceOntologyParityTest {

    private static final org.sequeless.ontology.schema.api.Cardinality ATMOST_ONE =
            org.sequeless.ontology.schema.api.Cardinality.atMost(1);
    private static final org.sequeless.ontology.schema.api.Cardinality OPTIONAL =
            org.sequeless.ontology.schema.api.Cardinality.optional();
    /** reference.ttl:61-62, {@code title minCardinality 1}, with no maximum. */
    private static final org.sequeless.ontology.schema.api.Cardinality MIN_ONE =
            new org.sequeless.ontology.schema.api.Cardinality(1, OptionalInt.empty());

    /** Per-IRI usability: the three booleans the query contract gates on, sort, filter and facet. */
    private record Usability(boolean sortable, boolean filterable, boolean facetable) {}

    @Test
    void abstractRootsOfTheReferenceOntologyChangeOnlyTheExpectedIris() {
        TypeDefinition deliverable = deliverable();
        TypeDefinition workItem = workItem();
        Schema reference = schema(1, deliverable, workItem, task(), project(), person());

        // Subtype-only IRIs that become usable (OLD has no such Field). Every one is declared by a single subtype
        // or agrees across copies; none is a sibling conflict.
        assertRootParity(
                reference,
                deliverable,
                Set.of(
                        "status",
                        "description",
                        "priority",
                        "estimatedHours",
                        "dueDate",
                        "assignedTo",
                        "belongsToProject",
                        "openTaskCount",
                        "totalEstimatedHours",
                        "owner",
                        "hasTask"),
                Set.of("title"));
        assertRootParity(
                reference,
                workItem,
                Set.of(
                        "priority",
                        "estimatedHours",
                        "dueDate",
                        "assignedTo",
                        "belongsToProject",
                        "openTaskCount",
                        "totalEstimatedHours",
                        "owner",
                        "hasTask"),
                Set.of("title"));
    }

    private static void assertRootParity(
            Schema reference, TypeDefinition root, Set<String> expectedAdded, Set<String> expectedAgainstAnyCopy) {
        String rootName = root.label();
        Map<String, Usability> old = usabilityOf(resolve(schema(1, root), root.iri()));
        Map<String, Usability> anyCopy = anyCopyUsability(reference, root.iri());
        Map<String, Usability> neu = usabilityOf(resolve(reference, root.iri()));

        assertThat(notIn(neu, old))
                .as("IRIs only in the new scope, root %s", rootName)
                .containsExactlyInAnyOrderElementsOf(expectedAdded);
        assertThat(notIn(old, neu))
                .as("IRIs only in origin/main, root %s", rootName)
                .isEmpty();
        assertThat(changed(old, neu))
                .as("root-declared IRIs whose usability changed against origin/main, root %s", rootName)
                .isEmpty();
        assertThat(changed(anyCopy, neu))
                .as("usability that differs from the any-copy union, root %s", rootName)
                .containsExactlyInAnyOrderElementsOf(expectedAgainstAnyCopy);
        // No Field in the new scope is unusable, so the reference ontology has no sibling conflict.
        assertThat(neu.values())
                .allSatisfy(usability -> assertThat(usability.filterable()).isTrue());
    }

    /** Every copy of every covered IRI, grouped and mapped as the union rule did before root-wins. */
    private static Map<String, Usability> anyCopyUsability(Schema reference, String rootIri) {
        SchemaView view = new SchemaView(reference);
        TypeDefinition root = view.type(rootIri).orElseThrow();
        Map<String, List<PropertyDefinition>> groups = new LinkedHashMap<>();
        for (TypeDefinition type : view.typeAndSubtypes(root)) {
            for (PropertyDefinition property : type.properties()) {
                groups.computeIfAbsent(property.iri(), iri -> new ArrayList<>()).add(property);
            }
        }
        PropertyMapper mapper = new PropertyMapper(view);
        Map<String, Usability> usability = new TreeMap<>();
        for (List<PropertyDefinition> group : groups.values()) {
            Field field = mapper.unionField(group);
            usability.put(field.path().asSlugPath(), usabilityOf(field));
        }
        return usability;
    }

    static FieldScope resolve(Schema schema, String rootIri) {
        String localName = rootIri.substring(SchemaFixtures.NS.length());
        return new SchemaFieldSource(new FakeSchemaSource(Map.of(TENANT, schema))).resolve(request(localName));
    }

    private static Map<String, Usability> usabilityOf(FieldScope scope) {
        Map<String, Usability> usability = new TreeMap<>();
        for (Field field : scope.facets()) {
            usability.put(field.path().asSlugPath(), usabilityOf(field));
        }
        return usability;
    }

    private static Usability usabilityOf(Field field) {
        return new Usability(
                field.capabilities().sortable(),
                field.capabilities().filterable(),
                field.capabilities().facetable());
    }

    /** The keys of {@code from} that {@code against} does not carry. */
    private static Set<String> notIn(Map<String, Usability> from, Map<String, Usability> against) {
        return from.keySet().stream().filter(key -> !against.containsKey(key)).collect(Collectors.toSet());
    }

    /** The keys both maps carry whose usability differs. */
    private static Set<String> changed(Map<String, Usability> before, Map<String, Usability> after) {
        return before.keySet().stream()
                .filter(after::containsKey)
                .filter(key -> !before.get(key).equals(after.get(key)))
                .collect(Collectors.toSet());
    }

    // reference.ttl:43-46. Deliverable lists its own title and createdAt.
    static TypeDefinition deliverable() {
        return type(
                "Deliverable",
                true,
                List.of(),
                attribute("title", Datatype.STRING, OPTIONAL, false, true),
                attribute("createdAt", Datatype.DATE_TIME, OPTIONAL, false, false));
    }

    // reference.ttl:48-55. WorkItem lists its inherited title and createdAt, its status (restriction :53), and
    // description.
    static TypeDefinition workItem() {
        return type(
                "WorkItem",
                true,
                List.of("Deliverable"),
                attribute("title", Datatype.STRING, OPTIONAL, false, true),
                attribute("createdAt", Datatype.DATE_TIME, OPTIONAL, false, false),
                attribute("status", Datatype.STRING, ATMOST_ONE, true, false),
                attribute("description", Datatype.STRING, OPTIONAL, false, true));
    }

    // reference.ttl:57-75. Task narrows title to a minimum of one (:60-62) and restricts its own single-valued
    // properties.
    static TypeDefinition task() {
        return type(
                "Task",
                false,
                List.of("WorkItem"),
                attribute("title", Datatype.STRING, MIN_ONE, false, true),
                attribute("createdAt", Datatype.DATE_TIME, OPTIONAL, false, false),
                attribute("status", Datatype.STRING, ATMOST_ONE, true, false),
                attribute("description", Datatype.STRING, OPTIONAL, false, true),
                attribute("priority", Datatype.INTEGER, ATMOST_ONE, false, false),
                attribute("estimatedHours", Datatype.DECIMAL, ATMOST_ONE, false, false),
                attribute("dueDate", Datatype.DATE, ATMOST_ONE, false, false),
                relationship("assignedTo", "Person", ATMOST_ONE, true),
                relationship("belongsToProject", "Project", ATMOST_ONE, false));
    }

    // reference.ttl:77-89. Project lists its inherited properties and its own, with restrictions at :80-88.
    static TypeDefinition project() {
        return type(
                "Project",
                false,
                List.of("WorkItem"),
                attribute("title", Datatype.STRING, OPTIONAL, false, true),
                attribute("createdAt", Datatype.DATE_TIME, OPTIONAL, false, false),
                attribute("status", Datatype.STRING, ATMOST_ONE, true, false),
                attribute("description", Datatype.STRING, OPTIONAL, false, true),
                attribute("openTaskCount", Datatype.INTEGER, ATMOST_ONE, false, false),
                attribute("totalEstimatedHours", Datatype.DECIMAL, ATMOST_ONE, false, false),
                relationship("owner", "Person", ATMOST_ONE, false),
                relationship("hasTask", "Task", OPTIONAL, false));
    }

    // reference.ttl:91-100.
    static TypeDefinition person() {
        return concreteType(
                "Person",
                attribute(
                        "name", Datatype.STRING, org.sequeless.ontology.schema.api.Cardinality.exactly(1), false, true),
                attribute("email", Datatype.STRING, OPTIONAL, false, true));
    }

    /** A relationship whose facet flag is set as the TTL sets it (the fixture helper sets it false). */
    private static RelationshipDefinition relationship(
            String localName,
            String targetLocalName,
            org.sequeless.ontology.schema.api.Cardinality cardinality,
            boolean facet) {
        return new RelationshipDefinition(
                iri(localName),
                label(localName),
                cardinality,
                facet,
                false,
                false,
                false,
                false,
                DisplayHints.none(),
                iri(targetLocalName),
                Optional.empty(),
                false);
    }
}
