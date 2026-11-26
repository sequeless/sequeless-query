package org.sequeless.query.api;

import java.util.List;
import org.sequeless.query.field.api.FieldPath;

/**
 * What one index preparation registered: the search registrations newly added for the tenant by
 * that call (ADR 0002). Only searchable Fields are listed, one per distinct search key (the
 * FieldRegistry key of spec 14, section 6.5), sorted by that key in {@link String#compareTo} order.
 * Expression-index registrations are not listed. A searchable Field is listed whatever its type,
 * DATE and DATETIME included, because search registration is by capability. The host runs the
 * {@code search_vector} backfill when the list is not empty.
 *
 * @param newlyRegistered the searchable Fields whose search registration this call added, one per
 *     search key, in key order; never {@code null}, copied, empty when nothing new was registered
 */
public record IndexPreparationResult(List<FieldPath> newlyRegistered) {

    /** The result of a call that registered nothing new. */
    public static final IndexPreparationResult EMPTY = new IndexPreparationResult(List.of());

    /** Copies {@code newlyRegistered}, which also null-checks the list and every element. */
    public IndexPreparationResult {
        newlyRegistered = List.copyOf(newlyRegistered);
    }
}
