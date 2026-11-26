package org.sequeless.query.schema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.RelationshipDefinition;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.Cardinality;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.InferenceSupport;
import org.sequeless.query.field.api.OperatorRestriction;
import org.sequeless.query.field.api.PathSegment;
import org.sequeless.query.field.api.Relationship;

/**
 * Maps one Schema property to the Field contract: its {@link Field}, its single-hop path, its
 * {@link Relationship} record, and its capabilities. Every mapping rule for a property lives here.
 */
final class PropertyMapper {

    private final SchemaView view;

    PropertyMapper(SchemaView view) {
        this.view = view;
    }

    /**
     * @param property a property of a type in the view's Schema
     * @return the single-segment path that names {@code property} from its own class
     */
    FieldPath path(PropertyDefinition property) {
        return new FieldPath(List.of(segment(property)));
    }

    /**
     * @param property a property of a type in the view's Schema
     * @return the path segment for {@code property}; its target class is set only for a relationship
     */
    PathSegment segment(PropertyDefinition property) {
        return new PathSegment(Iris.id(property.iri()), property.iri(), Iris.slug(property.iri()), targetOf(property));
    }

    /**
     * The Field of one definition. Its label is that definition's own; a union of agreeing definitions
     * (see {@link #unionField(FieldPath, List)}) takes the label of the first definition in root-first order.
     *
     * @param path the path the Field is reached by; the caller decides it, so expansions can prefix it
     * @param property the property the Field describes
     * @return the Field for {@code property} at {@code path}
     */
    Field field(FieldPath path, PropertyDefinition property) {
        return field(path, property, capabilities(List.of(property)));
    }

    private Field field(FieldPath path, PropertyDefinition property, Capabilities capabilities) {
        return new Field(
                path,
                fieldType(property),
                nativeDatatypeIri(property),
                cardinality(property.cardinality()),
                capabilities,
                Iris.label(property.label()),
                // DisplayHints carries no description, so the Field's description is blank.
                Iris.label(""),
                Optional.empty(),
                OperatorRestriction.unrestricted(),
                InferenceSupport.MAY_BE_INFERRED,
                view.versions(),
                Optional.empty());
    }

    /**
     * The Fields a root's FieldScope carries: one per property IRI declared by the root or by any of its
     * transitive subtypes. The root's own properties come first, in declaration order, then the
     * subtype-only ones. A property the root declares is the root's copy alone (see {@link #groupByIri}); the
     * conflict rule for a subtype-only property is on {@link #unionField}.
     *
     * @param covered the root and its transitive subtypes, as {@link SchemaView#typeAndSubtypes} returns them
     * @return one Field per property IRI, in first-declared order
     */
    List<Field> unionFields(List<TypeDefinition> covered) {
        return groupByIri(covered).stream().map(group -> unionField(group)).toList();
    }

    /**
     * The Relationships a root's FieldScope walks: one per group, as {@link #groupByIri} makes it, that agrees. A
     * relationship whose declarations conflict is left out, because no single target can be walked into. Its
     * Field is still listed by {@link #unionFields}, with every capability false.
     *
     * <p>An agreeing relationship takes its label from the first definition in root-first order, the same
     * rule {@link #unionField(FieldPath, List)} applies to its Field.
     *
     * @param covered the root and its transitive subtypes, as {@link SchemaView#typeAndSubtypes} returns them
     * @return the edge record of every agreeing relationship, in first-declared order
     */
    List<Relationship> unionRelationships(List<TypeDefinition> covered) {
        return groupByIri(covered).stream()
                .filter(PropertyMapper::agree)
                .map(group -> group.get(0))
                .filter(RelationshipDefinition.class::isInstance)
                .map(RelationshipDefinition.class::cast)
                .map(this::relationship)
                .toList();
    }

    /**
     * The conflict rule for a property declared by more than one covered type.
     *
     * <p>The definitions agree when they share the kind (attribute or relationship), the datatype or the
     * target type, and the cardinality. Agreeing definitions give one Field: filterable as always, facetable
     * and searchable only when every definition allows it, and sortable by the Q-sort rule, which the
     * agreeing cardinality makes the same for all of them.
     *
     * <p>Disagreeing definitions never pick one. The Field is still listed, with all four capabilities false,
     * type {@link FieldType#UNKNOWN}, cardinality {@link Cardinality#MANY} (it may hold several values), no
     * native datatype, and a path whose segment names no target class. {@link Field#conflict()} stays empty:
     * a {@link org.sequeless.query.field.api.FieldConflict} is keyed by ontology version, and this
     * disagreement is between types of one version, so it would misstate what the conflict is.
     *
     * <p>An agreeing group takes its label from the first definition in root-first order, and its path is
     * {@code path}, which the caller chose. This method does not change the label rule; it only states it.
     *
     * @param path the path the Field is reached by, used when the definitions agree
     * @param group the definitions of one property, root-first; never empty
     * @return the Field of the group
     */
    Field unionField(FieldPath path, List<PropertyDefinition> group) {
        PropertyDefinition first = group.get(0);
        if (agree(group)) {
            return field(path, first, capabilities(group));
        }
        PathSegment segment = new PathSegment(Iris.id(first.iri()), first.iri(), Iris.slug(first.iri()), null);
        return new Field(
                new FieldPath(List.of(segment)),
                FieldType.UNKNOWN,
                null,
                Cardinality.MANY,
                new Capabilities(false, false, false, false),
                Iris.label(first.label()),
                Iris.label(""),
                Optional.empty(),
                OperatorRestriction.unrestricted(),
                InferenceSupport.MAY_BE_INFERRED,
                view.versions(),
                Optional.empty());
    }

    /**
     * The Field of a group as a root's FieldScope lists it: at the group's own canonical path (see
     * {@link #unionField(FieldPath, List)}).
     *
     * @param group the definitions of one property, root-first; never empty
     * @return the Field of the group
     */
    Field unionField(List<PropertyDefinition> group) {
        return unionField(path(group.get(0)), group);
    }

    /** Whether every definition of one property matches the first one in kind, datatype or target, and cardinality. */
    static boolean agree(List<PropertyDefinition> group) {
        PropertyDefinition first = group.get(0);
        return group.stream().allMatch(other -> sameDefinition(first, other));
    }

    private static boolean sameDefinition(PropertyDefinition a, PropertyDefinition b) {
        return switch (a) {
            case AttributeDefinition attribute ->
                b instanceof AttributeDefinition other
                        && attribute.datatype() == other.datatype()
                        && attribute.cardinality().equals(other.cardinality());
            case RelationshipDefinition relationship ->
                b instanceof RelationshipDefinition other
                        && relationship.targetTypeIri().equals(other.targetTypeIri())
                        && relationship.cardinality().equals(other.cardinality());
        };
    }

    /**
     * The definitions, across the covered types, of the property a root-hop segment names: the same group
     * {@link #unionFields} makes for it. Empty when no covered type declares the property.
     *
     * @param covered the root and its transitive subtypes, as {@link SchemaView#typeAndSubtypes} returns them
     * @param segment a segment of a path rooted at the covered root
     * @return the definitions of that property, in first-declared order, or empty
     */
    List<PropertyDefinition> groupNamed(List<TypeDefinition> covered, PathSegment segment) {
        return groupByIri(covered).stream()
                .filter(group -> Iris.id(group.get(0).iri()).equals(segment.propertyId()))
                .findFirst()
                .orElse(List.of());
    }

    /**
     * The property definitions the root's scope groups, by property IRI, in first-declared order. The root wins: an IRI
     * the root's own list declares, inherited copies included, is grouped from the root's copy alone, so a subtype's
     * copy of it, even a narrowed one, is not part of the scope. Only an IRI the root does not declare is grouped
     * across the subtypes, where its copies must agree to be usable. {@code covered} comes root first, as {@link
     * SchemaView#typeAndSubtypes} returns it.
     */
    private static List<List<PropertyDefinition>> groupByIri(List<TypeDefinition> covered) {
        TypeDefinition root = covered.get(0);
        Map<String, List<PropertyDefinition>> groups = new LinkedHashMap<>();
        Set<String> rootIris = new HashSet<>();
        for (PropertyDefinition property : root.properties()) {
            rootIris.add(property.iri());
            groups.computeIfAbsent(property.iri(), iri -> new ArrayList<>()).add(property);
        }
        for (TypeDefinition subtype : covered.subList(1, covered.size())) {
            for (PropertyDefinition property : subtype.properties()) {
                if (!rootIris.contains(property.iri())) {
                    groups.computeIfAbsent(property.iri(), iri -> new ArrayList<>())
                            .add(property);
                }
            }
        }
        return new ArrayList<>(groups.values());
    }

    /**
     * @param relationship a relationship property of a type in the view's Schema
     * @return the edge record that expansion walks {@code relationship} by
     */
    Relationship relationship(RelationshipDefinition relationship) {
        return new Relationship(
                Iris.id(relationship.iri()),
                relationship.iri(),
                Iris.slug(relationship.iri()),
                Iris.label(relationship.label()),
                view.targetRef(relationship.targetTypeIri()),
                cardinality(relationship.cardinality()),
                view.versions());
    }

    /**
     * @param properties the properties of one class, in declaration order
     * @return the edge record of every relationship among them, in the same order
     */
    List<Relationship> relationships(List<PropertyDefinition> properties) {
        return properties.stream()
                .filter(RelationshipDefinition.class::isInstance)
                .map(RelationshipDefinition.class::cast)
                .map(this::relationship)
                .toList();
    }

    /**
     * The capability defaults. Every property filters, and facet and search follow the property's own
     * flags. A property is sortable when it holds at most one value per object (cardinality max is
     * present and equal to 1). This overrides an earlier rule for sort only, decided for the
     * sequeless-prototype cut-over. Change a default here and nowhere else.
     *
     * <p>Over several agreeing definitions (subtypes), facet and search hold only when every
     * definition allows them, so no subtype's flag is silently overridden by another's. Sort needs no such
     * rule: the definitions agree on cardinality, so the first one's cardinality answers for all of them.
     *
     * @param definitions the definitions of one property, all of the same kind, datatype or target and
     *     cardinality; never empty
     */
    private static Capabilities capabilities(List<PropertyDefinition> definitions) {
        boolean facet = definitions.stream().allMatch(PropertyDefinition::facet);
        boolean searchable = definitions.stream().allMatch(PropertyDefinition::searchable);
        return new Capabilities(true, facet, searchable, isSortable(definitions.get(0)));
    }

    private static boolean isSortable(PropertyDefinition property) {
        OptionalInt max = property.cardinality().max();
        return max.isPresent() && max.getAsInt() == 1;
    }

    /** An attribute maps by its datatype; a relationship is an object reference. */
    static FieldType fieldType(PropertyDefinition property) {
        return switch (property) {
            case AttributeDefinition attribute -> fieldType(attribute.datatype());
            case RelationshipDefinition relationship -> FieldType.OBJECT_REF;
        };
    }

    /** One arm per {@link Datatype} constant and no default, so a new constant fails to compile here. */
    static FieldType fieldType(Datatype datatype) {
        return switch (datatype) {
            case STRING -> FieldType.STRING;
            case BOOLEAN -> FieldType.BOOLEAN;
            case INTEGER, LONG -> FieldType.INTEGER;
            case DECIMAL, DOUBLE -> FieldType.DECIMAL;
            case DATE -> FieldType.DATE;
            case TIME -> FieldType.TIME;
            case DATE_TIME -> FieldType.DATETIME;
            case DURATION -> FieldType.DURATION;
            case ANY_URI -> FieldType.IRI_REF;
        };
    }

    /**
     * A property that holds at most one value is {@link Cardinality#SINGLE}. An unbounded one, or one
     * that may hold several, is {@link Cardinality#MANY}.
     */
    static Cardinality cardinality(org.sequeless.ontology.schema.api.Cardinality schemaCardinality) {
        boolean atMostOne =
                schemaCardinality.max().isPresent() && schemaCardinality.max().getAsInt() <= 1;
        return atMostOne ? Cardinality.SINGLE : Cardinality.MANY;
    }

    /** The XSD datatype of an attribute. A relationship has no datatype, so none. */
    private static String nativeDatatypeIri(PropertyDefinition property) {
        return switch (property) {
            case AttributeDefinition attribute -> attribute.datatype().xsdIri();
            case RelationshipDefinition relationship -> null;
        };
    }

    /** The target class of a relationship. An attribute ends in a literal value, so it has none. */
    private ClassRef targetOf(PropertyDefinition property) {
        if (property instanceof RelationshipDefinition relationship) {
            return view.targetRef(relationship.targetTypeIri());
        }
        return null;
    }
}
