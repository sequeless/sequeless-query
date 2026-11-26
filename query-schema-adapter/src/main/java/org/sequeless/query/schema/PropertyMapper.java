package org.sequeless.query.schema;

import java.util.List;
import java.util.Optional;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.RelationshipDefinition;
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
     * @param path the path the Field is reached by; the caller decides it, so expansions can prefix it
     * @param property the property the Field describes
     * @return the Field for {@code property} at {@code path}
     */
    Field field(FieldPath path, PropertyDefinition property) {
        return new Field(
                path,
                fieldType(property),
                nativeDatatypeIri(property),
                cardinality(property.cardinality()),
                capabilities(property),
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
     * The capability defaults (Navigator decision Q16). Every property filters, facet and search follow
     * the property's own flags, and sorting is off. Change a default here and nowhere else.
     */
    private static Capabilities capabilities(PropertyDefinition property) {
        return new Capabilities(true, property.facet(), property.searchable(), false);
    }

    /** An attribute maps by its datatype; a relationship is an object reference (Navigator decision Q18). */
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
