package org.sequeless.query.api;

import java.util.Objects;

/**
 * The IRI of an ontology class, as an object view reports the type of the object it describes.
 *
 * @param iri the class IRI; never {@code null} or blank
 */
public record TypeRef(String iri) {

    /**
     * Validates {@code iri} is present and not blank.
     *
     * <p>A null IRI throws NullPointerException, where the prototype threw IllegalArgumentException; the null check is the repository's convention.
     *
     * @throws NullPointerException if iri is null
     * @throws IllegalArgumentException if iri is blank
     */
    public TypeRef {
        Objects.requireNonNull(iri, "iri must not be null");
        if (iri.isBlank()) {
            throw new IllegalArgumentException("iri must not be blank");
        }
    }
}
