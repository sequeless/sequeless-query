package org.sequeless.query.api;

import java.util.Optional;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.OntologyVersionRef;

/**
 * The host-facing Query side: browse, read and index preparation. Use cases behind this interface
 * call the driven ports in {@code org.sequeless.query.spi}.
 */
public interface QueryService {

    /**
     * Resolves the Field scope the request describes, checks the query against it, and runs the
     * browse through the browse port.
     *
     * @param request who is asking, from which tenant, rooted at which class, and under which
     *     version policy; the scope is resolved from it; never {@code null}
     * @param query the filter, text, sorts, page and facets to run; never {@code null}
     * @return the matching page, the total and the facet buckets; never {@code null}
     * @throws InvalidQueryException if the filter is rejected by the filter validator, a sort path
     *     is not a sortable Field of the scope, or a facet path is not a facetable Field of the
     *     scope; the browse port is not called
     * @throws org.sequeless.query.field.api.FieldResolutionException if the scope cannot be
     *     resolved from {@code request}
     * @throws NullPointerException if an argument is {@code null}
     */
    BrowseResult browse(FieldScopeRequest request, BrowseQuery query);

    /**
     * Reads one object by id, within the tenant and principal of {@code scope}.
     *
     * @param scope the tenant and principal the read is made on behalf of; never {@code null}
     * @param request the identity of the object to read; never {@code null}
     * @return the object's view, or {@link Optional#empty()} when it does not exist, is
     *     soft-deleted, or belongs to another tenant
     * @throws NullPointerException if an argument is {@code null}
     */
    Optional<ObjectView> read(Scope scope, ReadRequest request);

    /**
     * Prepares the index structures the tenant's root classes need for the given ontology version.
     *
     * <p><b>Version guard.</b> The use case resolves each root class of the tenant
     * through {@code FieldSource}. If any resolved scope does not list {@code version} among its
     * ontology versions, a newer activation has superseded this call: the use case returns {@link
     * IndexPreparationResult#EMPTY} and does not call the port.
     *
     * <p>The guard reads {@code ontologyVersions} as the {@code FieldSource} reports it. Under
     * {@code VersionPolicy.Union}, the source reports only the active version, so a scope that does
     * not list {@code version} means that version is no longer active.
     *
     * <p><b>Roots.</b> An empty root list still calls the port, with an empty map of root fields. A
     * root class listed twice is rejected before any scope is resolved.
     *
     * <p><b>Done or durably accepted.</b> When the guard passes, the call returns once
     * the preparation is done or durably accepted. A repeat call with the same version returns an
     * empty {@code newlyRegistered}.
     *
     * @param tenant the tenant whose root classes are prepared; never {@code null}
     * @param version the ontology version the host has just activated; never {@code null}
     * @return what the port newly registered, or {@link IndexPreparationResult#EMPTY} when the
     *     version guard finds the version superseded; never {@code null}
     * @throws IllegalArgumentException if the tenant's root class list names the same class twice
     * @throws org.sequeless.query.field.api.FieldResolutionException if the tenant or a root class
     *     cannot be resolved
     * @throws NullPointerException if an argument is {@code null}
     */
    IndexPreparationResult prepare(TenantId tenant, OntologyVersionRef version);
}
