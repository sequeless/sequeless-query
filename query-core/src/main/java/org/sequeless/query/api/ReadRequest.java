package org.sequeless.query.api;

import java.util.Objects;
import org.sequeless.kernel.values.ObjectId;

/**
 * A request to read one object by id.
 *
 * @param id the identity of the object to read; never {@code null}
 */
public record ReadRequest(ObjectId id) {

    /** Validates {@code id} is present. */
    public ReadRequest {
        Objects.requireNonNull(id, "id must not be null");
    }
}
