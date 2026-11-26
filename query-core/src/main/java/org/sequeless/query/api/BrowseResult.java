package org.sequeless.query.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sequeless.query.field.api.FieldPath;

/**
 * The answer to a browse: the matching page of objects, the total number of matches, and the
 * facet buckets counted for each requested facet.
 *
 * @param items the objects on the requested page, in result order; never {@code null}, copied
 * @param total the number of objects matching the query, across all pages; never negative
 * @param facets the buckets for each facet path, in the order the facets were requested; never
 *     {@code null}, unmodifiable
 */
public record BrowseResult(List<ObjectView> items, long total, Map<FieldPath, List<FacetBucket>> facets) {

    /**
     * Validates {@code total}, copies {@code items}, and copies {@code facets} into an
     * unmodifiable {@link LinkedHashMap} that keeps its key order.
     *
     * @throws IllegalArgumentException if {@code total} is negative
     */
    public BrowseResult {
        items = List.copyOf(items); // null-checks the list and every element
        if (total < 0) {
            throw new IllegalArgumentException("BrowseResult total must not be negative");
        }
        Objects.requireNonNull(facets, "facets must not be null");
        Map<FieldPath, List<FacetBucket>> copy = new LinkedHashMap<>();
        for (Map.Entry<FieldPath, List<FacetBucket>> entry : facets.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), "facet path must not be null"),
                    List.copyOf(entry.getValue()));
        }
        facets = Collections.unmodifiableMap(copy);
    }
}
