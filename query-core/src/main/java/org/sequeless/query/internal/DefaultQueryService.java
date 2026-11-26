package org.sequeless.query.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.sequeless.filter.api.FilterValidator;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.BrowseResult;
import org.sequeless.query.api.FieldRegistries;
import org.sequeless.query.api.IndexPreparationResult;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.QueryService;
import org.sequeless.query.api.ReadRequest;
import org.sequeless.query.api.Sort;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.TraversalConfig;
import org.sequeless.query.field.api.VersionPolicy;
import org.sequeless.query.field.spi.FieldSource;
import org.sequeless.query.spi.BrowsePort;
import org.sequeless.query.spi.IndexPreparationPort;
import org.sequeless.query.spi.ReadPort;

/**
 * The Query side's use cases: browse, read and index preparation, over the driven ports.
 *
 * <p>Browse resolves the scope from the request, checks the query against that scope, and only then
 * calls the browse port. Index preparation applies the version guard of ADR 0002 before it calls the
 * port.
 */
public final class DefaultQueryService implements QueryService {

    private final FieldSource fieldSource;
    private final BrowsePort browsePort;
    private final ReadPort readPort;
    private final IndexPreparationPort indexPreparationPort;

    /**
     * Creates the service over the given ports.
     *
     * @param fieldSource the Field contract the scopes and root classes come from; never {@code null}
     * @param browsePort the browse port; never {@code null}
     * @param readPort the read port; never {@code null}
     * @param indexPreparationPort the index preparation port; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultQueryService(
            FieldSource fieldSource,
            BrowsePort browsePort,
            ReadPort readPort,
            IndexPreparationPort indexPreparationPort) {
        this.fieldSource = Objects.requireNonNull(fieldSource, "fieldSource must not be null");
        this.browsePort = Objects.requireNonNull(browsePort, "browsePort must not be null");
        this.readPort = Objects.requireNonNull(readPort, "readPort must not be null");
        this.indexPreparationPort =
                Objects.requireNonNull(indexPreparationPort, "indexPreparationPort must not be null");
    }

    @Override
    public BrowseResult browse(FieldScopeRequest request, BrowseQuery query) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(query, "query must not be null");
        FieldScope scope = fieldSource.resolve(request);
        List<String> problems = problemsOf(scope, query);
        if (!problems.isEmpty()) {
            throw new InvalidQueryException(problems);
        }
        return browsePort.browse(scope, query);
    }

    @Override
    public Optional<ObjectView> read(Scope scope, ReadRequest request) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(request, "request must not be null");
        return readPort.read(scope, request);
    }

    @Override
    public IndexPreparationResult prepare(TenantId tenant, OntologyVersionRef version) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(version, "version must not be null");
        List<ClassRef> roots = fieldSource.rootClasses(tenant);
        rejectDuplicateRoots(roots);
        Map<ClassRef, List<Field>> rootFields = new LinkedHashMap<>();
        for (ClassRef root : roots) {
            FieldScope rootScope = fieldSource.resolve(new FieldScopeRequest(
                    tenant,
                    root,
                    new VersionPolicy.Union(),
                    TraversalConfig.defaults(),
                    Optional.empty(),
                    Optional.empty()));
            if (!rootScope.root().equals(root)) {
                throw new IllegalStateException("resolved root does not match the listed root: " + root.iri());
            }
            if (!rootScope.ontologyVersions().contains(version)) {
                // ADR 0002: a newer activation has superseded this call; do nothing.
                return IndexPreparationResult.EMPTY;
            }
            rootFields.put(root, rootScope.facets());
        }
        return indexPreparationPort.prepare(tenant, version, Collections.unmodifiableMap(rootFields));
    }

    /** Rejects a root class listed twice, before any scope is resolved, so no root entry is overwritten. */
    private static void rejectDuplicateRoots(List<ClassRef> roots) {
        Set<ClassRef> seen = new HashSet<>();
        for (ClassRef root : roots) {
            if (!seen.add(root)) {
                throw new IllegalArgumentException("duplicate root class: " + root.iri());
            }
        }
    }

    /** Checks the query against the scope, in the order filter, sorts, facets. */
    private static List<String> problemsOf(FieldScope scope, BrowseQuery query) {
        List<String> problems = new ArrayList<>();
        if (query.filter().isPresent()) {
            FilterValidator.validate(query.filter().get(), OperatorRegistry.defaults(), FieldRegistries.of(scope))
                    .forEach(violation -> problems.add(violation.message()));
        }
        for (Sort sort : query.sorts()) {
            if (!hasCapability(scope, sort.path(), Capabilities::sortable)) {
                problems.add("sort path '%s' is not a sortable field of this scope"
                        .formatted(sort.path().asSlugPath()));
            }
        }
        for (FieldPath facet : query.facets()) {
            if (!hasCapability(scope, facet, Capabilities::facetable)) {
                problems.add("facet path '%s' is not a facetable field of this scope".formatted(facet.asSlugPath()));
            }
        }
        return List.copyOf(problems);
    }

    private static boolean hasCapability(FieldScope scope, FieldPath path, Predicate<Capabilities> capability) {
        return scope.facets().stream()
                .filter(field -> field.path().equals(path))
                .anyMatch(field -> capability.test(field.capabilities()));
    }
}
