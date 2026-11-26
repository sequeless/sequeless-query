package org.sequeless.query.api;

import java.util.List;

/**
 * Thrown by {@link QueryService#browse} when a {@link BrowseQuery} names something the scope does
 * not allow: a filter the validator rejects, a sort on a Field that is not sortable, or a facet on
 * a Field that is not facetable.
 *
 * <p>The message is {@code "invalid query: "} followed by the problems joined by {@code "; "}.
 */
public final class InvalidQueryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<String> problems;

    /**
     * Creates the exception for the given problems.
     *
     * @param problems one human-readable message per problem; never {@code null}, copied
     */
    public InvalidQueryException(List<String> problems) {
        super(messageFor(problems));
        this.problems = List.copyOf(problems);
    }

    /**
     * The problems that made the query invalid.
     *
     * @return the problems, in the order they were found; never {@code null}, unmodifiable
     */
    public List<String> problems() {
        return problems;
    }

    private static String messageFor(List<String> problems) {
        return "invalid query: " + String.join("; ", List.copyOf(problems));
    }
}
