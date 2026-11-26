package org.sequeless.query.field.api;

import java.util.Objects;

/**
 * One version's incompatible definition of a facet that is otherwise the same property (same
 * UUID) across the ontology versions in scope.
 *
 * <p>A merge conflict arises when two versions in scope define the same property with an incompatible {@link #type()}
 * or {@link #cardinality()} — for example, v1 typed {@code issuedAt} as {@code STRING} and v2 corrected it to {@code
 * DATETIME}. Each such version's definition is captured as one {@code ConflictingDefinition}, and the full set is
 * carried on {@link FieldConflict#definitions()}.
 *
 * @param version the ontology version that produced this definition; never {@code null}
 * @param type the {@link FieldType} this version assigned to the property; never {@code null}
 * @param cardinality the {@link Cardinality} this version assigned to the property; never {@code
 *     null}
 */
public record ConflictingDefinition(OntologyVersionRef version, FieldType type, Cardinality cardinality) {

    /** Validates that the version is identified and both the type and cardinality are present. */
    public ConflictingDefinition {
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(cardinality, "cardinality must not be null");
    }
}
