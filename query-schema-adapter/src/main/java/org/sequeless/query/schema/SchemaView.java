package org.sequeless.query.schema;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.OntologyVersionRef;

/**
 * One activation's Schema, indexed for the adapter: its version, its types by IRI, and the class
 * references and concrete-type set derived from them. Built fresh for each call, so it never outlives
 * the Schema it was read from.
 */
final class SchemaView {

    private final OntologyVersionRef version;
    private final List<TypeDefinition> types;
    private final Map<String, TypeDefinition> typesByIri;

    SchemaView(Schema schema) {
        this.version = new OntologyVersionRef(schema.ontologyVersion().value());
        this.types = schema.types();
        // Schema rejects duplicate short names, so no two types share an IRI.
        Map<String, TypeDefinition> byIri = new HashMap<>();
        for (TypeDefinition type : types) {
            byIri.put(type.iri(), type);
        }
        this.typesByIri = Map.copyOf(byIri);
    }

    /** @return the active ontology version, the only one this adapter serves */
    OntologyVersionRef version() {
        return version;
    }

    /** @return the single-element set of the active version, as scopes and Fields carry it */
    Set<OntologyVersionRef> versions() {
        return Set.of(version);
    }

    /** @return every type in the Schema, in the Schema's own order */
    List<TypeDefinition> types() {
        return types;
    }

    /**
     * @param iri the type's IRI; never {@code null}
     * @return the type with that IRI, or empty when the Schema has none
     */
    Optional<TypeDefinition> type(String iri) {
        return Optional.ofNullable(typesByIri.get(iri));
    }

    /**
     * @param type a type of this Schema
     * @return the class reference of {@code type}, labelled with the type's own label
     */
    ClassRef classRef(TypeDefinition type) {
        return new ClassRef(Iris.id(type.iri()), Iris.slug(type.iri()), type.iri(), Iris.label(type.label()));
    }

    /**
     * The class reference of a relationship's target. The target may name a type the Schema does not
     * carry; its label is then its local name.
     *
     * @param targetIri the target type's IRI; never {@code null}
     * @return the class reference of that type
     */
    ClassRef targetRef(String targetIri) {
        return type(targetIri)
                .map(this::classRef)
                .orElseGet(() -> new ClassRef(
                        Iris.id(targetIri), Iris.slug(targetIri), targetIri, Iris.label(Iris.slug(targetIri))));
    }

    /**
     * The root and every concrete subtype of it. The root is included only
     * when it is concrete itself. An abstract root with no concrete subtypes yields an empty set.
     *
     * @param root a type of this Schema
     * @return the concrete types a Browse of {@code root} covers, in no particular order
     */
    Set<ClassRef> concreteTypes(TypeDefinition root) {
        Set<ClassRef> concrete = new LinkedHashSet<>();
        for (TypeDefinition type : typeAndSubtypes(root)) {
            if (!type.isAbstract()) {
                concrete.add(classRef(type));
            }
        }
        return concrete;
    }

    /**
     * The root and every transitive subtype of it, abstract ones included: the walk {@link
     * #concreteTypes} filters, before the filter. The root comes first, then its subtypes in the
     * Schema's own order.
     *
     * @param root a type of this Schema
     * @return the root and every type that reaches it through superTypes, each once
     */
    List<TypeDefinition> typeAndSubtypes(TypeDefinition root) {
        List<TypeDefinition> covered = new ArrayList<>();
        covered.add(root);
        for (TypeDefinition type : types) {
            if (!type.iri().equals(root.iri()) && reaches(type, root.iri())) {
                covered.add(type);
            }
        }
        return covered;
    }

    /** Whether {@code ancestorIri} is reached from {@code type} by following superTypes transitively. */
    private boolean reaches(TypeDefinition type, String ancestorIri) {
        Deque<String> pending = new ArrayDeque<>(type.superTypes());
        Set<String> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            String iri = pending.pop();
            if (iri.equals(ancestorIri)) {
                return true;
            }
            if (seen.add(iri)) {
                type(iri).ifPresent(parent -> pending.addAll(parent.superTypes()));
            }
        }
        return false;
    }
}
