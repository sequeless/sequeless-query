package org.sequeless.query.api;

import java.util.Objects;

/**
 * One bucket of a facet: a value and the number of matching objects that hold it. The name is
 * kept from the prototype.
 *
 * @param value the facet value; never {@code null}
 * @param count the number of matching objects holding the value; never negative
 */
public record FacetBucket(String value, long count) {

    /**
     * Validates {@code value} is present and {@code count} is not negative.
     *
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public FacetBucket {
        Objects.requireNonNull(value, "value must not be null");
        if (count < 0) {
            throw new IllegalArgumentException("FacetBucket count must not be negative");
        }
    }
}
