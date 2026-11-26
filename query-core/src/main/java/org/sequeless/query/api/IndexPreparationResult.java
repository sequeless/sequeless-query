package org.sequeless.query.api;

import java.util.List;
import org.sequeless.query.field.api.FieldPath;

/**
 * What one index preparation registered: the searchable Fields that were newly registered for
 * the tenant (ADR 0002). The host runs the {@code search_vector} backfill when the list is not
 * empty.
 *
 * @param newlyRegistered the Fields newly registered by this call, in deterministic order; never
 *     {@code null}, copied, empty when nothing new was registered
 */
public record IndexPreparationResult(List<FieldPath> newlyRegistered) {

    /** The result of a call that registered nothing new. */
    public static final IndexPreparationResult EMPTY = new IndexPreparationResult(List.of());

    /** Copies {@code newlyRegistered}, which also null-checks the list and every element. */
    public IndexPreparationResult {
        newlyRegistered = List.copyOf(newlyRegistered);
    }
}
