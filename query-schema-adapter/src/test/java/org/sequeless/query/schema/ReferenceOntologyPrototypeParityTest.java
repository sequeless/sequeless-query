package org.sequeless.query.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sequeless.query.schema.SchemaFixtures.schema;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldScope;

/**
 * The released union agrees with the prototype's union on the reference ontology, for each abstract root, up to
 * the difference set this test asserts (sequeless-prototype #45).
 *
 * <p><b>Baseline.</b> The prototype's answers are reproduced from sequeless-prototype,
 * sequeless-core DefaultBusinessObjectService:
 * <ul>
 *   <li>{@code propertyIndex}, lines 389 to 406: every property of the root and its transitive subtypes is put
 *       into a map with {@code index.put}, so the last copy visited wins. Visiting follows the Schema's type
 *       order. {@link #prototypeAnswers} reproduces this, and asserts the winner does not change any answer.
 *   <li>{@code resolveQueryableProperty}, lines 417 to 441: it rejects a multi-valued property (the scalar
 *       check, {@code isScalar}, lines 445 to 448: max present and equal to 1), and a derived property whose
 *       rule is not materialised (lines 434 to 441). Sort and filter go through it. Facet also requires the
 *       facet flag (line 595).
 * </ul>
 *
 * <p><b>Released side.</b> The scope's Capabilities. Sortable is max equal to 1. Filterable is always true,
 * because a filter has no cardinality gate in the released query (PropertyMapper.java:274 and query-core main).
 * Facetable is the facet flag.
 *
 * <p><b>Expected differences, exactly.</b> Each root, Deliverable and WorkItem, has the same 13-IRI union. The
 * differences are:
 * <ul>
 *   <li>{@code totalEstimatedHours}, sort and filter: the derived category. The prototype rejects a derived
 *       property that is not materialised (reference.ttl:237-248, no {@code sq:materialised}); the released
 *       scope accepts it.
 *   <li>{@code title}, {@code createdAt}, {@code description}, {@code hasTask}, filter: the multi-valued category.
 *       The prototype's scalar check rejects them; the released filter does not check cardinality.
 * </ul>
 * {@code openTaskCount} is materialised (reference.ttl:235), so both sides accept it and it differs nowhere.
 * Facet differs nowhere, and no Field in the released union is a sibling conflict.
 *
 * <p>The fixture is {@link ReferenceOntologyParityTest}'s reference ontology.
 */
class ReferenceOntologyPrototypeParityTest {

    /**
     * Derived properties and their {@code sq:materialised} flag. {@code openTaskCount} is materialised
     * (reference.ttl:235); {@code totalEstimatedHours} is not (reference.ttl:237-248, see :216-217).
     */
    private static final Map<String, Boolean> DERIVED_MATERIALISED =
            Map.of("openTaskCount", true, "totalEstimatedHours", false);

    /** The usability answers the clauses gate on. */
    private record Answer(boolean sort, boolean filter, boolean facet) {}

    /** One capability on which the prototype and the released union answer differently, for one root and IRI. */
    private record Difference(String root, String iri, String capability, boolean prototype, boolean released) {}

    /** The exact difference set, sorted by root, IRI and capability on assertion. */
    private static final Set<Difference> EXPECTED = Set.of(
            new Difference("Deliverable", "totalEstimatedHours", "sort", false, true),
            new Difference("WorkItem", "totalEstimatedHours", "sort", false, true),
            new Difference("Deliverable", "totalEstimatedHours", "filter", false, true),
            new Difference("WorkItem", "totalEstimatedHours", "filter", false, true),
            new Difference("Deliverable", "title", "filter", false, true),
            new Difference("WorkItem", "title", "filter", false, true),
            new Difference("Deliverable", "createdAt", "filter", false, true),
            new Difference("WorkItem", "createdAt", "filter", false, true),
            new Difference("Deliverable", "description", "filter", false, true),
            new Difference("WorkItem", "description", "filter", false, true),
            new Difference("Deliverable", "hasTask", "filter", false, true),
            new Difference("WorkItem", "hasTask", "filter", false, true));

    @Test
    void releasedUnionAgreesWithThePrototypeUnionOnTheReferenceOntology() {
        Schema reference = schema(
                1,
                ReferenceOntologyParityTest.deliverable(),
                ReferenceOntologyParityTest.workItem(),
                ReferenceOntologyParityTest.task(),
                ReferenceOntologyParityTest.project(),
                ReferenceOntologyParityTest.person());

        Set<Difference> differences = new TreeSet<>(Comparator.comparing(Difference::toString));
        for (String root : List.of("Deliverable", "WorkItem")) {
            String rootIri = SchemaFixtures.iri(root);
            Map<String, Answer> prototype = prototypeAnswers(reference, rootIri);
            Map<String, Answer> released = releasedAnswers(reference, rootIri);

            assertThat(released.keySet()).as("union of %s", root).isEqualTo(prototype.keySet());
            assertThat(released.values())
                    .as("sibling conflicts in the released union of %s", root)
                    .noneMatch(answer -> !answer.sort() && !answer.filter() && !answer.facet());
            for (String iri : prototype.keySet()) {
                Answer p = prototype.get(iri);
                Answer r = released.get(iri);
                addIfDiffers(differences, root, iri, "sort", p.sort(), r.sort());
                addIfDiffers(differences, root, iri, "filter", p.filter(), r.filter());
                addIfDiffers(differences, root, iri, "facet", p.facet(), r.facet());
            }
        }

        assertThat(differences).containsExactlyInAnyOrderElementsOf(EXPECTED);
    }

    private static void addIfDiffers(
            Set<Difference> differences, String root, String iri, String capability, boolean p, boolean r) {
        if (p != r) {
            differences.add(new Difference(root, iri, capability, p, r));
        }
    }

    /**
     * The prototype's answer for each IRI of the root's union. The index is built as {@code propertyIndex} builds
     * it, and the test asserts that the copy it keeps does not change any answer.
     */
    private static Map<String, Answer> prototypeAnswers(Schema schema, String rootIri) {
        SchemaView view = new SchemaView(schema);
        TypeDefinition root = view.type(rootIri).orElseThrow();
        Set<String> covered =
                view.typeAndSubtypes(root).stream().map(TypeDefinition::iri).collect(Collectors.toSet());
        Map<String, PropertyDefinition> index = new HashMap<>();
        Map<String, Set<List<Object>>> inputsPerIri = new TreeMap<>();
        for (TypeDefinition type : schema.types()) {
            if (covered.contains(type.iri())) {
                for (PropertyDefinition property : type.properties()) {
                    index.put(property.iri(), property);
                    inputsPerIri
                            .computeIfAbsent(property.iri(), iri -> new HashSet<>())
                            .add(ruleInputs(property));
                }
            }
        }
        for (Map.Entry<String, Set<List<Object>>> copies : inputsPerIri.entrySet()) {
            assertThat(copies.getValue())
                    .as("the winning copy of %s does not change any answer", copies.getKey())
                    .hasSize(1);
        }

        Map<String, Answer> answers = new TreeMap<>();
        for (PropertyDefinition property : index.values()) {
            String slug = localName(property.iri());
            boolean derivedNotMaterialised = DERIVED_MATERIALISED.containsKey(slug) && !DERIVED_MATERIALISED.get(slug);
            boolean usable = isScalar(property) && !derivedNotMaterialised;
            answers.put(slug, new Answer(usable, usable, usable && property.facet()));
        }
        return answers;
    }

    /** The copy inputs the prototype's rules read: scalar-ness and the facet flag. */
    private static List<Object> ruleInputs(PropertyDefinition property) {
        return List.of(isScalar(property), property.facet());
    }

    /** The prototype's scalar check, lines 445 to 448: max present and equal to 1. */
    private static boolean isScalar(PropertyDefinition property) {
        return property.cardinality().max().isPresent()
                && property.cardinality().max().getAsInt() == 1;
    }

    private static Map<String, Answer> releasedAnswers(Schema schema, String rootIri) {
        FieldScope scope = ReferenceOntologyParityTest.resolve(schema, rootIri);
        Map<String, Answer> answers = new TreeMap<>();
        for (Field field : scope.facets()) {
            answers.put(
                    field.path().asSlugPath(),
                    new Answer(
                            field.capabilities().sortable(),
                            field.capabilities().filterable(),
                            field.capabilities().facetable()));
        }
        return answers;
    }

    private static String localName(String iri) {
        return iri.substring(SchemaFixtures.NS.length());
    }
}
