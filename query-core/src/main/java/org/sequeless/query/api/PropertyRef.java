package org.sequeless.query.api;

import java.util.Objects;

/**
 * The IRI of a property, as a key of an object view's {@code properties}. A property key is one
 * IRI. A traversal key is never a {@code PropertyRef}: it is the {@code |}-joined encoding of
 * spec 14, section 6.5.
 *
 * @param iri the property IRI; never {@code null} or blank
 */
public record PropertyRef(String iri) {

    /**
     * Validates {@code iri} is present and not blank.
     *
     * <p>A null IRI throws NullPointerException, where the prototype threw IllegalArgumentException; the null check is the repository's convention.
     *
     * @throws NullPointerException if iri is null
     * @throws IllegalArgumentException if iri is blank
     */
    public PropertyRef {
        Objects.requireNonNull(iri, "iri must not be null");
        if (iri.isBlank()) {
            throw new IllegalArgumentException("iri must not be blank");
        }
    }
}
