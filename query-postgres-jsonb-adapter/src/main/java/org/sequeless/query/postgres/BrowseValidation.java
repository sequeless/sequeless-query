package org.sequeless.query.postgres;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.sequeless.filter.api.AndFilter;
import org.sequeless.filter.api.AnyFilter;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.api.OrFilter;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.FieldKey;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.api.Sort;
import org.sequeless.query.field.api.FieldPath;

/**
 * The shapes this adapter does not evaluate, rejected before any SQL is built (spec 14, Q23). The rule is the one
 * {@code BrowsePort} states and the in-memory reference enforces: an {@code AnyFilter}, a traversal path used as a
 * filter, sort or facet key, and an operator outside the evaluated set (the {@code like} and {@code not like} pair,
 * and any operator filter-core does not know). Every problem is collected and named in one
 * {@link InvalidQueryException}. Operand and value errors are not checked here.
 *
 * <p>The evaluated set is the in-memory reference's: the operators {@code PostgresQueryBuilder} renders for them
 * ({@code IS}, {@code IS NOT}, the four comparisons, {@code IS IN}, {@code CONTAINS}, {@code STARTS WITH},
 * {@code EXISTS} and {@code DOES NOT EXIST}).
 */
final class BrowseValidation {

    private static final OperatorRegistry OPERATORS = OperatorRegistry.defaults();

    private static final Set<String> EVALUATED_OPERATORS = Set.of(
            "exists",
            "does not exist",
            "is",
            "is not",
            "is greater than",
            "is greater than or equal to",
            "is less than",
            "is less than or equal to",
            "is in",
            "contains",
            "starts with");

    private BrowseValidation() {}

    /**
     * @param query the query to check; must not be {@code null}
     * @throws InvalidQueryException listing each shape this adapter does not evaluate, naming its key or node
     */
    static void rejectUnsupportedShapes(BrowseQuery query) {
        List<String> problems = new ArrayList<>();
        query.filter().ifPresent(filter -> collectFilter(filter, problems));
        for (Sort sort : query.sorts()) {
            collectPath(sort.path(), "sort", problems);
        }
        for (FieldPath facet : query.facets()) {
            collectPath(facet, "facet", problems);
        }
        if (!problems.isEmpty()) {
            throw new InvalidQueryException(problems);
        }
    }

    private static void collectFilter(FilterNode node, List<String> problems) {
        switch (node) {
            case AndFilter and -> and.operands().forEach(operand -> collectFilter(operand, problems));
            case OrFilter or -> or.operands().forEach(operand -> collectFilter(operand, problems));
            case FieldFilter field -> collectFieldFilter(field, problems);
            case AnyFilter any ->
                problems.add("AnyFilter (any '" + any.op() + "') is not evaluated by the postgres browse adapter");
        }
    }

    private static void collectFieldFilter(FieldFilter filter, List<String> problems) {
        if (FieldKey.isTraversal(filter.path())) {
            problems.add("traversal path " + filter.path() + " cannot be used as a filter: only a direct Field can");
        }
        Optional<OperatorDefinition> operator = OPERATORS.findByCanonicalOrAlias(filter.op());
        if (operator.isEmpty()) {
            problems.add("unknown operator '" + filter.op() + "'");
        } else if (!EVALUATED_OPERATORS.contains(operator.get().getCanonicalName())) {
            problems.add("operator '" + operator.get().getCanonicalName()
                    + "' is not evaluated by the postgres browse adapter");
        }
    }

    private static void collectPath(FieldPath path, String role, List<String> problems) {
        if (path.isTraversing()) {
            problems.add(role + " on traversal path " + FieldKey.encode(path)
                    + " is not evaluated by the postgres browse adapter");
        }
    }
}
