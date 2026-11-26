package org.sequeless.query.field.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Thrown when a facet scope, expansion, or path description cannot be resolved.
 *
 * <p>Per field-contract.md section 2.7, resolution never returns a partial or silently-narrowed answer: it
 * either succeeds or throws. Returning a shortened facet list on failure would leave the caller
 * unable to tell "this class genuinely has three filterable properties" apart from "something went
 * wrong and you are seeing three of thirty", and a filter builder rendered from the latter is
 * worse than an error message.
 *
 * <p>The failure therefore carries a {@link FieldErrorCode} the caller can branch on and localize,
 * rather than only a message string. It also carries the {@link FieldPath} and {@link ClassRef}
 * involved where they are known, so a UI can point at the offending segment instead of rejecting
 * the whole expression.
 *
 * <p>{@code equals} and {@code hashCode} are deliberately not overridden. Two separate failures
 * carrying the same code are distinct events, and identity semantics inherited from
 * {@link Throwable} are the correct model for that.
 */
public final class FieldResolutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Why resolution failed; never {@code null}. */
    private final FieldErrorCode code;

    private final transient Optional<FieldPath> path;
    private final transient Optional<ClassRef> classRef;

    /**
     * Creates a failure with no known path or class — used for whole-request failures such as
     * {@link FieldErrorCode#UNKNOWN_TENANT} or {@link FieldErrorCode#EMPTY_INTERSECTION}.
     *
     * @param code why resolution failed; never {@code null}
     */
    public FieldResolutionException(FieldErrorCode code) {
        this(code, Optional.empty(), Optional.empty());
    }

    /**
     * Creates a failure at a known path — used for path-shaped failures such as
     * {@link FieldErrorCode#UNKNOWN_PATH} or {@link FieldErrorCode#PATH_NOT_FILTERABLE}.
     *
     * @param code why resolution failed; never {@code null}
     * @param path where it went wrong; never {@code null}
     */
    public FieldResolutionException(FieldErrorCode code, FieldPath path) {
        this(code, Optional.of(Objects.requireNonNull(path, "path must not be null")), Optional.empty());
    }

    /**
     * Creates a failure at a known class — used for class-shaped failures such as
     * {@link FieldErrorCode#UNKNOWN_CLASS} or {@link FieldErrorCode#CLASS_NOT_REACHABLE}.
     *
     * @param code why resolution failed; never {@code null}
     * @param classRef the class involved; never {@code null}
     */
    public FieldResolutionException(FieldErrorCode code, ClassRef classRef) {
        this(code, Optional.empty(), Optional.of(Objects.requireNonNull(classRef, "classRef must not be null")));
    }

    /**
     * Creates a failure with both a path and a class — used where the offending segment and the
     * class it reaches are both meaningful, such as {@link FieldErrorCode#CYCLE_DETECTED} or
     * {@link FieldErrorCode#PRINCIPAL_DENIED}.
     *
     * @param code why resolution failed; never {@code null}
     * @param path where it went wrong; never {@code null}
     * @param classRef the class involved; never {@code null}
     */
    public FieldResolutionException(FieldErrorCode code, FieldPath path, ClassRef classRef) {
        this(
                code,
                Optional.of(Objects.requireNonNull(path, "path must not be null")),
                Optional.of(Objects.requireNonNull(classRef, "classRef must not be null")));
    }

    private FieldResolutionException(FieldErrorCode code, Optional<FieldPath> path, Optional<ClassRef> classRef) {
        super(buildMessage(Objects.requireNonNull(code, "code must not be null"), path, classRef));
        this.code = code;
        this.path = path;
        this.classRef = classRef;
    }

    private static String buildMessage(FieldErrorCode code, Optional<FieldPath> path, Optional<ClassRef> classRef) {
        StringBuilder message = new StringBuilder(code.name());
        path.ifPresent(p -> message.append(" at path ").append(p.asSlugPath()));
        classRef.ifPresent(c -> message.append(" for class ").append(c.slug()));
        return message.toString();
    }

    /**
     * Why resolution failed, as a constant the caller can branch on and localize.
     *
     * @return the error code; never {@code null}
     */
    public FieldErrorCode code() {
        return code;
    }

    /**
     * Where resolution went wrong, when a specific path was involved.
     *
     * @return the offending path, or empty for whole-request failures
     */
    public Optional<FieldPath> path() {
        return path;
    }

    /**
     * The class involved, when one was identified.
     *
     * @return the class, or empty when no single class is implicated
     */
    public Optional<ClassRef> classRef() {
        return classRef;
    }
}
