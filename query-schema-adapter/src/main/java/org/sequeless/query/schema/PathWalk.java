package org.sequeless.query.schema;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.RelationshipDefinition;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.FieldErrorCode;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldResolutionException;
import org.sequeless.query.field.api.PathSegment;
import org.sequeless.query.field.api.TraversalConfig;

/**
 * Walks a {@link FieldPath} hop by hop over one Schema, applying the traversal guards of {@code FieldSource}. Cycle
 * detection is per path: the walk remembers only the classes on its own route, starting from the root.
 *
 * <p>The walk does not decide which error a missing segment raises, because that depends on the
 * caller and the segment's position. The caller decides that, then calls {@link #enter} to move on.
 */
final class PathWalk {

    private final SchemaView view;
    private final TraversalConfig traversal;
    private final FieldPath path;
    private final Set<UUID> visited = new HashSet<>();
    private TypeDefinition current;

    /**
     * @param view the Schema being walked
     * @param root the class the walk starts from
     * @param traversal the guards to apply
     * @param path the path reported in any error this walk raises
     */
    PathWalk(SchemaView view, TypeDefinition root, TraversalConfig traversal, FieldPath path) {
        this.view = view;
        this.traversal = traversal;
        this.path = path;
        this.current = root;
        this.visited.add(Iris.id(root.iri()));
    }

    /** @return the class the walk is at, after the hops taken so far */
    TypeDefinition current() {
        return current;
    }

    /**
     * @param segment a segment of the walked path
     * @return the property the segment names on the current class, or empty if there is none
     */
    Optional<PropertyDefinition> find(PathSegment segment) {
        return current.properties().stream()
                .filter(property -> Iris.id(property.iri()).equals(segment.propertyId()))
                .findFirst();
    }

    /**
     * Guard 3: the hop at {@code index} (zero-based) must not go deeper than {@code maxDepth}.
     *
     * @param index the position of the hop in the path
     * @throws FieldResolutionException with {@code DEPTH_EXCEEDED} when the hop is too deep
     */
    void checkDepth(int index) {
        if (traversal.maxDepth().isPresent() && index + 1 > traversal.maxDepth().getAsInt()) {
            throw new FieldResolutionException(FieldErrorCode.DEPTH_EXCEEDED, path);
        }
    }

    /**
     * Guard 2: when classes may not be revisited, a relationship may not re-enter a class already on
     * this route.
     *
     * @param target the class the relationship points to
     * @throws FieldResolutionException with {@code CYCLE_DETECTED} when the route would loop
     */
    void checkRevisit(ClassRef target) {
        if (traversal.noRevisitClasses() && visited.contains(target.id())) {
            throw new FieldResolutionException(FieldErrorCode.CYCLE_DETECTED, path, target);
        }
    }

    /**
     * Moves the walk into a relationship's target class.
     *
     * @param relationship a relationship of the current class, already checked by the caller
     * @throws FieldResolutionException with {@code CLASS_NOT_REACHABLE} when the Schema has no target
     *     type to walk into
     */
    void enter(RelationshipDefinition relationship) {
        TypeDefinition target = view.type(relationship.targetTypeIri())
                .orElseThrow(() -> new FieldResolutionException(
                        FieldErrorCode.CLASS_NOT_REACHABLE, path, view.targetRef(relationship.targetTypeIri())));
        visited.add(Iris.id(target.iri()));
        current = target;
    }
}
