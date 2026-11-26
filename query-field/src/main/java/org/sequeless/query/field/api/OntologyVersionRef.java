package org.sequeless.query.field.api;

/**
 * One version of an ontology activation, identified by its positive ordinal.
 *
 * <p>Every ontology version identifier in the Field contract is this type rather than a string, so
 * that versions compare and sort numerically (see {@code StampCalculator}). The value is at least 1.
 *
 * @param value the version number; never less than 1
 * @throws IllegalArgumentException if {@code value} is less than 1
 */
public record OntologyVersionRef(int value) {

    /** Validates that the version number is at least 1. */
    public OntologyVersionRef {
        if (value < 1) {
            throw new IllegalArgumentException("value must be at least 1 but was " + value);
        }
    }
}
