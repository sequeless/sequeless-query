package org.sequeless.query.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.query.field.api.FieldPath;

/**
 * What a browse asks for: an optional filter, an optional free-text search, the sort keys, the
 * page, and the facets to count.
 *
 * <p>The filter, the sort paths and the facet paths name Fields of the {@code FieldScope} the
 * browse runs against. {@link org.sequeless.query.api.QueryService#browse} rejects a query that
 * names a Field the scope does not allow.
 *
 * @param filter the filter to apply, when there is one; never {@code null} as a wrapper, use
 *     {@link Optional#empty()} when there is none
 * @param text the free-text search, when there is one; never {@code null} as a wrapper
 * @param sorts the sort keys, most significant first; never {@code null}, copied
 * @param page the page to return; never {@code null}
 * @param facets the paths to count buckets for; never {@code null}, copied
 */
public record BrowseQuery(
        Optional<FilterNode> filter, Optional<String> text, List<Sort> sorts, Page page, List<FieldPath> facets) {

    /**
     * Validates every component is present and copies {@code sorts} and {@code facets}.
     */
    public BrowseQuery {
        Objects.requireNonNull(filter, "filter must not be null (use Optional.empty())");
        Objects.requireNonNull(text, "text must not be null (use Optional.empty())");
        sorts = List.copyOf(sorts); // null-checks the list and every element
        Objects.requireNonNull(page, "page must not be null");
        facets = List.copyOf(facets); // null-checks the list and every element
    }
}
