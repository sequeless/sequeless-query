package org.sequeless.query.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Who created and last changed an object, and when.
 *
 * @param createdAt when the object was created; never {@code null}
 * @param createdBy who created the object; never {@code null}
 * @param updatedAt when the object was last changed; never {@code null}
 * @param updatedBy who last changed the object; never {@code null}
 */
public record Audit(Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {

    /** Validates every component is present. */
    public Audit {
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        Objects.requireNonNull(updatedBy, "updatedBy must not be null");
    }
}
