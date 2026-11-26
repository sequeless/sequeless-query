package org.sequeless.query.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.PathSegment;

/**
 * The one place the {@code FieldRegistry} key of a traversal path is built and read (spec 14,
 * section 6.5).
 *
 * <p>A Field's key is the IRIs of its segments joined by {@code |} (U+007C), with no spaces, in path
 * order. A single-segment key is the bare property IRI, so a direct Field keeps the key it always
 * had (spec 11, decision 5). The postgres adapter reads keys through {@link #decode(String)} and
 * escapes each IRI as a literal.
 */
public final class FieldKeys {

    private static final String SEPARATOR = "|";
    private static final String SEPARATOR_REGEX = "\\|";

    private FieldKeys() {}

    /**
     * Builds the registry key of {@code path}: the segment IRIs joined by {@code |}.
     *
     * @param path the path to encode; never {@code null}
     * @return the IRIs of {@code path.segments()} in order, joined by {@code |} with no spaces
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public static String encode(FieldPath path) {
        Objects.requireNonNull(path, "path must not be null");
        return path.segments().stream().map(PathSegment::iri).collect(Collectors.joining(SEPARATOR));
    }

    /**
     * Reads a registry key back into its segment IRIs, the inverse of {@link #encode(FieldPath)}.
     *
     * @param key the key to split; must not be {@code null}, blank, contain an empty token, or
     *     contain whitespace in any token
     * @return the IRIs of the key, in order; never {@code null}, unmodifiable
     * @throws IllegalArgumentException if {@code key} is {@code null}, blank, has an empty or
     *     blank token between separators, or has a token that contains whitespace
     */
    public static List<String> decode(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be null or blank");
        }
        List<String> iris = Arrays.asList(key.split(SEPARATOR_REGEX, -1));
        for (String iri : iris) {
            if (iri.isBlank()) {
                throw new IllegalArgumentException("key '" + key + "' has an empty token");
            }
            if (iri.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("key '" + key + "' has a token with whitespace");
            }
        }
        return List.copyOf(iris);
    }
}
