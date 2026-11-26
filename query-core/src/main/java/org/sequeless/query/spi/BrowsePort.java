package org.sequeless.query.spi;

import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.BrowseResult;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.field.api.FieldScope;

/**
 * The outbound port that runs filtered, sorted, paged, faceted and free-text browse queries over
 * stored objects, returning each matching object as an {@link ObjectView}. Exactly one
 * implementation is wired into the running application at a time; use cases never choose between
 * implementations themselves.
 *
 * <p>This javadoc is the full behavioural contract every implementation must satisfy. It is not
 * advisory: the query-testkit's {@code BrowseContract} asserts every clause of it mechanically
 * against any port passed to it, and any adapter's own test suite is expected to extend that
 * contract. An implementation that violates a clause here is not a valid adapter, regardless of
 * what its own tests claim.
 *
 * <ul>
 *   <li><b>Null handling.</b> Every argument to every method on this interface must be non-{@code
 *       null}. A conforming implementation throws {@link NullPointerException} when any argument
 *       is {@code null}.
 *   <li><b>Tenant scoping.</b> {@link #browse} is scoped to {@code scope.request().tenantId()}: an
 *       object created under one tenant is invisible to a query made on a {@link FieldScope} whose
 *       request names a different tenant, as if it did not exist. The browse takes no
 *       authorization {@code Scope}; the tenant is the one the scope was resolved for.
 *   <li><b>Soft-delete filtering.</b> Soft-deleted objects are never returned by {@link #browse}.
 *       The port has no option to include them.
 *   <li><b>Candidates.</b> Only objects whose type is in {@code scope.concreteTypes()} are
 *       matched: the root and its concrete subtypes. An empty set gives an empty result with no
 *       error. The port does no hierarchy reasoning.
 *   <li><b>Result order.</b> Results are totally ordered, so pages over unchanged data never
 *       overlap or skip. With a non-empty {@code query.sorts()}: each sort key in list order, then
 *       {@code id} ascending, whatever the keys' directions. Otherwise, with {@code query.text()}
 *       present: relevance, best first, then {@code createdAt} ascending, then {@code id}
 *       ascending. Otherwise: {@code createdAt} ascending, then {@code id} ascending. An object
 *       with no value for a sort key sorts last when the key is ascending and first when it is
 *       descending. {@code createdAt} is the audit creation instant and compares as stored, at the
 *       precision the adapter keeps. Ids compare in unsigned byte order of the 16-byte UUID, most
 *       significant byte first, which equals the order of the lowercase canonical strings; this is
 *       not {@link java.util.UUID#compareTo}, which is signed. A sort key whose Field is of type
 *       {@code OBJECT_REF} orders by target id in the same order.
 *       The query-testkit's {@code BrowseContract} asserts each id tie-break with fixed ids.
 *   <li><b>Page overrun is empty, never a throw.</b> A {@link org.sequeless.query.api.Page} whose
 *       number is past the last page returns a {@link BrowseResult} with empty {@link
 *       BrowseResult#items()} and the correct {@link BrowseResult#total()}.
 *   <li><b>Facet counting is "conventional multi-select."</b> For a facet on path {@code P}, the
 *       counts are computed with the filter minus its top-level AND conjuncts that are a
 *       {@code FieldFilter} on {@code P}, compared by the Q7 key (spec 14 section 6.5). Every other
 *       conjunct is kept whole, including any {@code OrFilter} or {@code AnyFilter} that mentions
 *       {@code P}. A filter that is not an {@code AndFilter} counts as one conjunct; a
 *       {@code FieldFilter} on {@code P} is removed, leaving match-all. The free-text condition
 *       narrows every facet.
 *   <li><b>Facet bucket order.</b> For each facet path, the buckets are sorted by count, descending,
 *       then by bucket value in Unicode code point order. An object with no value for a facet
 *       contributes to no bucket of that facet.
 *   <li><b>Reference facet buckets.</b> A facet on a Field of type {@code OBJECT_REF} buckets each
 *       object by the text of its target's {@link
 *       org.sequeless.kernel.values.ObjectId}, the canonical lowercase UUID string. A target that is
 *       missing, or soft-deleted, or belongs to another tenant, contributes to no bucket: the buckets
 *       are an inner join on the visible targets. The total is not affected, since it counts the
 *       matching objects. The bucket order is the scalar rule above; id text is ASCII, so code point
 *       order and {@link String#compareTo} agree on it.
 *   <li><b>Free text reads the root scope.</b> {@link BrowseQuery#text()} is matched against the
 *       searchable Fields of the root scope only: the direct, single-segment Fields of {@code
 *       scope.facets()} that are {@code searchable}. Traversal Fields that an expansion added to the
 *       scope are not searched. The text applies to every object in scope, concrete subtype instances
 *       included. A property that is searchable only on a subtype is not searched. This is a known
 *       limit: the port takes no union of searchable Fields across the concrete types.
 *   <li><b>Unsupported query shapes are a typed rejection.</b> A port that does not evaluate a filter,
 *       sort or facet shape fails with {@link InvalidQueryException}, listing each problem. The rule
 *       covers only the shapes listed in the {@code @throws} clause of {@link #browse}: a filter node
 *       other than {@code AndFilter}, {@code OrFilter} or {@code FieldFilter} (such as {@code AnyFilter});
 *       a traversal path used as a filter, sort or facet path; and the {@code like} and {@code not like}
 *       operators. Operand and value errors are outside the rule. The query-testkit's {@code
 *       BrowseContract} asserts the rejection for each shape listed there.
 * </ul>
 */
public interface BrowsePort {

    /**
     * Runs a single filtered, sorted, paged, faceted and optionally free-text query. See the
     * interface-level javadoc for the full contract this method must satisfy, including tenant
     * scoping, soft-delete filtering, the default sort order used when {@link BrowseQuery#sorts()}
     * is empty, page-overrun behaviour, and the "conventional multi-select" facet counting rule.
     *
     * <p>The filter, sort and facet paths have already been checked against {@code scope} by the
     * caller: the port may rely on each of them naming a Field of the scope with the capability
     * the query needs. The shape rules of the {@code @throws} clause still apply to them.
     *
     * @param scope the resolved Field scope; its request names the tenant the query runs for;
     *     must not be {@code null}
     * @param query the filter, text, sort, paging and facet request to run; must not be {@code
     *     null}
     * @return a non-{@code null} result containing the matching page of objects, the total match
     *     count, and the computed facet buckets
     * @throws NullPointerException if any argument is {@code null}
     * @throws InvalidQueryException if the query has a filter, sort or facet shape this port does not
     *     evaluate: a filter node other than {@code AndFilter}, {@code OrFilter} or {@code FieldFilter},
     *     a traversal path used as a filter, sort or facet path, or the {@code like} and {@code not like}
     *     operators (see the interface-level javadoc)
     */
    BrowseResult browse(FieldScope scope, BrowseQuery query);
}
