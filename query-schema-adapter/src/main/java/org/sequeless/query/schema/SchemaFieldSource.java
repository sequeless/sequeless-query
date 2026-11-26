package org.sequeless.query.schema;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.RelationshipDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.ontology.schema.spi.NoConsistentOntologyException;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldErrorCode;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldResolutionException;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.PathSegment;
import org.sequeless.query.field.api.Relationship;
import org.sequeless.query.field.api.VersionPolicy;
import org.sequeless.query.field.internal.StampCalculator;
import org.sequeless.query.field.spi.FieldSource;

/**
 * A {@link FieldSource} that answers from the active Schema of a tenant, as {@link SchemaSource} reads
 * it. Every call reads the Schema afresh, so a new activation is visible on the next call.
 *
 * <p>The Schema says nothing about walkability or readability, so every relationship is walkable and
 * no principal is denied. Only this module calls {@link SchemaSource}; the Spring Boot autoconfiguration
 * names its type only to wire this class.
 *
 * <p>{@link #expand}, like {@link #resolve} and {@link #describe}, throws {@code VERSION_NOT_FOUND} for a
 * {@link VersionPolicy.Pinned} version that is not the active one.
 *
 * <p>An IRI containing '|' is rejected by PathSegment and Relationship with IllegalArgumentException, which
 * aborts the call for that class.
 *
 * <p>A tenant with no consistent active Ontology gives {@code UNKNOWN_TENANT}: {@link SchemaSource}
 * throws {@link NoConsistentOntologyException} for it, and every method translates that exception.
 * A tenant that {@link SchemaSource} does not know at all is whatever {@link SchemaSource} does; this
 * module does not change that case, and any other exception it throws propagates unchanged.
 */
public final class SchemaFieldSource implements FieldSource {

    private final SchemaSource schemaSource;

    /**
     * @param schemaSource the port that reads the active Schema of a tenant; never {@code null}
     */
    public SchemaFieldSource(SchemaSource schemaSource) {
        this.schemaSource = Objects.requireNonNull(schemaSource, "schemaSource must not be null");
    }

    @Override
    public FieldScope resolve(FieldScopeRequest request) {
        SchemaView view = view(request.tenantId());
        TypeDefinition root = root(view, request.root());
        requireActiveVersion(view, request.versionPolicy());
        PropertyMapper mapper = new PropertyMapper(view);
        // A root covers its own properties and every transitive subtype's, abstract ones included.
        List<TypeDefinition> covered = view.typeAndSubtypes(root);
        List<Field> facets = mapper.unionFields(covered);
        Set<ClassRef> concreteTypes = view.concreteTypes(root);
        String stamp = StampCalculator.stamp(request, view.versions(), concreteTypes);
        return new FieldScope(
                view.classRef(root),
                facets,
                mapper.unionRelationships(covered),
                stamp,
                view.versions(),
                concreteTypes,
                request);
    }

    @Override
    public FieldScope expand(FieldScope scope, FieldPath relationship) {
        FieldScopeRequest request = scope.request();
        SchemaView view = view(request.tenantId());
        TypeDefinition start = view.type(scope.root().iri())
                .orElseThrow(() -> new FieldResolutionException(FieldErrorCode.UNKNOWN_CLASS, scope.root()));
        requireActiveVersion(view, request.versionPolicy());
        PropertyMapper mapper = new PropertyMapper(view);
        PathWalk walk = new PathWalk(view, start, request.traversal(), relationship);
        List<TypeDefinition> covered = view.typeAndSubtypes(start);

        List<PathSegment> segments = relationship.segments();
        for (int index = 0; index < segments.size(); index++) {
            PathSegment segment = segments.get(index);
            List<PropertyDefinition> definitions = definitionsAt(walk, mapper, covered, index, segment);
            // A segment with no target class is a literal end, which cannot be walked into.
            if (definitions.isEmpty() || segment.targetClass() == null) {
                throw new FieldResolutionException(FieldErrorCode.UNKNOWN_PATH, relationship);
            }
            // Definitions that disagree name no single target, so no route can be walked through them.
            if (!PropertyMapper.agree(definitions)) {
                throw new FieldResolutionException(
                        FieldErrorCode.CLASS_NOT_REACHABLE, relationship, segment.targetClass());
            }
            if (!(definitions.get(0) instanceof RelationshipDefinition edge)) {
                throw new FieldResolutionException(FieldErrorCode.UNKNOWN_PATH, relationship);
            }
            // A root hop names its target too. A caller target that is not the edge's target is refused before the
            // walk enters anything. Deeper hops are not compared; that is a pre-existing gap.
            if (index == 0 && !segment.targetClass().iri().equals(edge.targetTypeIri())) {
                throw new FieldResolutionException(
                        FieldErrorCode.CLASS_NOT_REACHABLE, relationship, segment.targetClass());
            }
            walk.checkRevisit(view.targetRef(edge.targetTypeIri()));
            walk.checkDepth(index);
            walk.enter(edge);
        }

        TypeDefinition terminal = walk.current();
        List<Field> added = terminal.properties().stream()
                .map(property -> mapper.field(extend(segments, mapper.segment(property)), property))
                .toList();
        List<Field> facets = new ArrayList<>(scope.facets());
        facets.addAll(added);
        List<Relationship> relationships = new ArrayList<>(scope.relationships());
        relationships.addAll(mapper.relationships(terminal.properties()));
        Set<OntologyVersionRef> versions = new LinkedHashSet<>(scope.ontologyVersions());
        versions.addAll(view.versions());
        // The stamp depends on versions, traversal and concrete types, never on how far the walk went.
        return new FieldScope(
                scope.root(), facets, relationships, scope.stamp(), versions, scope.concreteTypes(), request);
    }

    @Override
    public Field describe(FieldScopeRequest request, FieldPath path) {
        SchemaView view = view(request.tenantId());
        TypeDefinition root = root(view, request.root());
        requireActiveVersion(view, request.versionPolicy());
        PropertyMapper mapper = new PropertyMapper(view);
        PathWalk walk = new PathWalk(view, root, request.traversal(), path);
        List<TypeDefinition> covered = view.typeAndSubtypes(root);

        List<PathSegment> segments = path.segments();
        int last = segments.size() - 1;
        for (int index = 0; index <= last; index++) {
            PathSegment segment = segments.get(index);
            boolean terminal = index == last;
            List<PropertyDefinition> definitions = definitionsAt(walk, mapper, covered, index, segment);
            if (definitions.isEmpty()) {
                throw terminal
                        ? new FieldResolutionException(FieldErrorCode.UNKNOWN_PATH, path)
                        : new FieldResolutionException(FieldErrorCode.CLASS_NOT_REACHABLE, path, segment.targetClass());
            }
            PropertyDefinition property = definitions.get(0);
            // Definitions that disagree name no single kind or target, so no route can be walked through them.
            boolean walkable = PropertyMapper.agree(definitions) && property instanceof RelationshipDefinition;
            if (!terminal && !walkable) {
                throw new FieldResolutionException(FieldErrorCode.CLASS_NOT_REACHABLE, path, segment.targetClass());
            }
            if (!terminal) {
                // Only a hop that walks into a relationship is guarded; the terminal segment is never walked into.
                RelationshipDefinition relationship = (RelationshipDefinition) property;
                // A root hop names its target too; a null target is not compared here, as before. Deeper hops are
                // not compared, which is a pre-existing gap.
                if (index == 0
                        && segment.targetClass() != null
                        && !segment.targetClass().iri().equals(relationship.targetTypeIri())) {
                    throw new FieldResolutionException(FieldErrorCode.CLASS_NOT_REACHABLE, path, segment.targetClass());
                }
                walk.checkRevisit(view.targetRef(relationship.targetTypeIri()));
                walk.checkDepth(index);
                walk.enter(relationship);
                continue;
            }
            // A root hop keeps the caller's path when its definitions agree, as origin/main does; a disagreeing
            // root property is listed with every capability false, so it is refused here as well.
            Field field = index == 0 ? mapper.unionField(path, definitions) : mapper.field(path, property);
            if (!field.capabilities().filterable()) {
                throw new FieldResolutionException(FieldErrorCode.PATH_NOT_FILTERABLE, path);
            }
            return field;
        }
        // Unreachable: FieldPath's constructor guarantees at least one segment, so the terminal hop returns.
        throw new IllegalStateException("path must have at least one segment");
    }

    @Override
    public List<ClassRef> rootClasses(TenantId tenant) {
        SchemaView view = view(tenant);
        return view.types().stream()
                .sorted(Comparator.comparing(TypeDefinition::iri))
                .map(view::classRef)
                .toList();
    }

    /** Every method reads the Schema through here, so the translation to UNKNOWN_TENANT lives in one place. */
    private SchemaView view(TenantId tenant) {
        Schema schema;
        try {
            schema = schemaSource.schema(tenant);
        } catch (NoConsistentOntologyException error) {
            FieldResolutionException unknown = new FieldResolutionException(FieldErrorCode.UNKNOWN_TENANT);
            unknown.initCause(error);
            throw unknown;
        }
        return new SchemaView(schema);
    }

    /**
     * The definitions a path segment names on the class the walk is at, empty when none does. The root hop
     * (index 0) reads the union of the root's and its subtypes' definitions, as resolve() does, so
     * describe() and expand() agree with resolve() on a root's properties. A deeper hop reads only the class
     * the walk has entered.
     */
    private static List<PropertyDefinition> definitionsAt(
            PathWalk walk, PropertyMapper mapper, List<TypeDefinition> covered, int index, PathSegment segment) {
        return index == 0
                ? mapper.groupNamed(covered, segment)
                : walk.find(segment).stream().toList();
    }

    private static TypeDefinition root(SchemaView view, ClassRef root) {
        return view.type(root.iri())
                .orElseThrow(() -> new FieldResolutionException(FieldErrorCode.UNKNOWN_CLASS, root));
    }

    /** A pinned version must be the active one. Union and intersection both resolve against the one Schema. */
    private static void requireActiveVersion(SchemaView view, VersionPolicy policy) {
        if (policy instanceof VersionPolicy.Pinned(OntologyVersionRef requested) && !requested.equals(view.version())) {
            throw new FieldResolutionException(FieldErrorCode.VERSION_NOT_FOUND);
        }
    }

    /** The path of a field reached through {@code prefix}: the relationship's segments, then {@code last}. */
    private static FieldPath extend(List<PathSegment> prefix, PathSegment last) {
        List<PathSegment> segments = new ArrayList<>(prefix);
        segments.add(last);
        return new FieldPath(segments);
    }
}
