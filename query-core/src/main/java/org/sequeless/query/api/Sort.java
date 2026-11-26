package org.sequeless.query.api;

import java.util.Objects;
import org.sequeless.query.field.api.FieldPath;

/**
 * One sort key of a browse: a sortable Field of the scope and the order to sort it in.
 *
 * @param path the path of the Field to sort by; never {@code null}
 * @param direction the order; never {@code null}
 */
public record Sort(FieldPath path, Direction direction) {

    /** Validates both components are present. */
    public Sort {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
    }
}
