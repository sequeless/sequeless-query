package org.sequeless.query.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.sequeless.filter.api.AndFilter;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.api.OrFilter;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.BoolValue;
import org.sequeless.kernel.values.DateTimeValue;
import org.sequeless.kernel.values.DateValue;
import org.sequeless.kernel.values.DecimalValue;
import org.sequeless.kernel.values.IntegerValue;
import org.sequeless.kernel.values.ListValue;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ReferenceValue;
import org.sequeless.kernel.values.TextValue;
import org.sequeless.kernel.values.Value;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.BrowseResult;
import org.sequeless.query.api.Direction;
import org.sequeless.query.api.FacetBucket;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.PropertyRef;
import org.sequeless.query.api.Sort;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.spi.BrowsePort;

/**
 * An in-memory {@link BrowsePort}, the reference {@link BrowseContract} is proven against. It is a
 * query engine over the {@link InMemoryObjectStore}: it applies tenant scoping, soft-delete
 * filtering, the candidate types of the scope, the filter, the free-text condition, the sort and
 * default order, paging, and the conventional multi-select facet rule, each as {@link BrowsePort}'s
 * javadoc describes them.
 *
 * <p>The filter is evaluated over {@code AndFilter}, {@code OrFilter} and {@code FieldFilter} nodes,
 * on direct properties, with these operators (filter-core's default names, as canonical names):
 * {@code exists}, {@code does not exist}, {@code is}, {@code is not}, {@code is greater than}, {@code
 * is greater than or equal to}, {@code is less than}, {@code is less than or equal to}, {@code is
 * in}, {@code contains} and {@code starts with}.
 *
 * <p>Free text requires every query token to occur in the searchable text of the object, and ranks
 * by the number of occurrences, best first. The searchable text is read from the root's own
 * searchable Fields: the single-segment Fields of the scope that are {@code searchable}. A traversal
 * Field that an expansion added to the scope is not searched. A property that is searchable only on
 * a concrete subtype is not searched either (spec 14, Q22).
 *
 * <p>Before any object is read, the query is checked, and every problem is reported together in one
 * {@link InvalidQueryException} (spec 14, Q23). The query is rejected when it names:
 *
 * <ul>
 *   <li>a filter node other than {@code AndFilter}, {@code OrFilter} or {@code FieldFilter}, such as
 *       {@code AnyFilter};
 *   <li>a {@code FieldFilter} on a traversal path (more than one segment, keyed with {@code |});
 *   <li>an operator outside the evaluated list above, such as {@code like} and {@code not like}, or
 *       one that filter-core does not know;
 *   <li>a sort key or a facet path that is a traversal path.
 * </ul>
 *
 * <p>Two things are not evaluated for the values they hold, and fail with {@link
 * UnsupportedOperationException} when a stored value of that kind is compared, sorted or bucketed:
 * list-valued properties, and any value kind the reference does not compare. No contract clause
 * needs them. An operand whose JSON type does not fit the stored value, or an {@code is in} operand
 * that is not an array, fails with {@link IllegalArgumentException}.
 *
 * <p>Reference values bucket by their target's id text, and a bucket counts only a target that is
 * visible in the tenant: stored and not soft-deleted. A missing or soft-deleted target contributes
 * to no bucket (spec 14, Q21).
 *
 * <p>Ids, and reference values used as sort keys or facet keys, compare in {@link BrowsePort}'s
 * unsigned order (see {@code compareIds}), never by the signed {@link UUID#compareTo}.
 */
public final class InMemoryBrowsePort implements BrowsePort {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final OperatorRegistry OPERATORS = OperatorRegistry.defaults();

    /** The canonical operator names this port evaluates. Any other operator is rejected before a read. */
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

    private static final Comparator<ObjectView> CREATED_AT_THEN_ID = Comparator.comparing(
                    (ObjectView view) -> view.audit().createdAt())
            .thenComparing(view -> view.id().value(), InMemoryBrowsePort::compareIds);

    private static final Comparator<ObjectView> ID =
            Comparator.comparing((ObjectView view) -> view.id().value(), InMemoryBrowsePort::compareIds);

    private final InMemoryObjectStore store;

    /**
     * @param store the store to query; must not be {@code null}
     */
    public InMemoryBrowsePort(InMemoryObjectStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    @Override
    public BrowseResult browse(FieldScope scope, BrowseQuery query) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(query, "query must not be null");
        rejectUnsupportedShapes(query);

        Scan scan = new Scan(scope, query, store.all());
        List<ObjectView> ordered = scan.matching(Optional.empty()).stream()
                .sorted(scan.comparator())
                .toList();

        long from = (long) query.page().number() * query.page().size();
        int start = (int) Math.min(from, ordered.size());
        int end = (int) Math.min(from + query.page().size(), ordered.size());

        Map<FieldPath, List<FacetBucket>> facets = new LinkedHashMap<>();
        for (FieldPath facet : query.facets()) {
            facets.put(facet, scan.buckets(facet));
        }
        return new BrowseResult(ordered.subList(start, end), ordered.size(), facets);
    }

    /**
     * One browse's inputs, with the candidate objects, the visible targets and the searchable
     * properties worked out once: tenant, not soft-deleted, and (for candidates) a type in {@link
     * FieldScope#concreteTypes()}.
     */
    private static final class Scan {

        private final BrowseQuery query;
        private final List<ObjectView> candidates;
        private final Set<ObjectId> visibleTargets;
        private final List<String> searchableIris;
        private final List<String> queryTokens;

        Scan(FieldScope scope, BrowseQuery query, List<ObjectView> stored) {
            this.query = query;
            TenantId tenant = scope.request().tenantId();
            Set<String> types =
                    scope.concreteTypes().stream().map(ClassRef::iri).collect(Collectors.toSet());
            List<ObjectView> visible = stored.stream()
                    .filter(view -> view.tenant().equals(tenant))
                    .filter(view -> !view.deleted())
                    .toList();
            this.candidates = visible.stream()
                    .filter(view -> types.contains(view.type().iri()))
                    .toList();
            this.visibleTargets = visible.stream().map(ObjectView::id).collect(Collectors.toSet());
            // A traversal Field has more than one segment. Expansion adds those to the scope, and they are not root
            // Fields.
            this.searchableIris = scope.facets().stream()
                    .filter(field -> field.capabilities().searchable())
                    .filter(field -> field.path().segments().size() == 1)
                    .map(field -> iriOf(field.path()))
                    .toList();
            this.queryTokens = query.text().map(InMemoryBrowsePort::tokens).orElse(List.of());
        }

        /**
         * The candidates that pass the text condition and the filter. For a facet, {@code excludedKey}
         * names the path whose own top-level conjuncts are left out of the filter.
         */
        List<ObjectView> matching(Optional<String> excludedKey) {
            return candidates.stream().filter(view -> passes(view, excludedKey)).toList();
        }

        Comparator<ObjectView> comparator() {
            if (!query.sorts().isEmpty()) {
                Comparator<ObjectView> bySorts = null;
                for (Sort sort : query.sorts()) {
                    Comparator<ObjectView> next = sortBy(sort);
                    bySorts = bySorts == null ? next : bySorts.thenComparing(next);
                }
                return bySorts.thenComparing(ID);
            }
            if (query.text().isPresent()) {
                return Comparator.comparingLong((ObjectView view) -> score(view))
                        .reversed()
                        .thenComparing(CREATED_AT_THEN_ID);
            }
            return CREATED_AT_THEN_ID;
        }

        /** Facet buckets for one path: counted over the filter minus that path's own conjuncts. */
        List<FacetBucket> buckets(FieldPath facet) {
            String iri = iriOf(facet);
            PropertyRef property = new PropertyRef(iri);
            Map<String, Long> counts = new HashMap<>();
            for (ObjectView view : matching(Optional.of(iri))) {
                Value value = view.properties().get(property);
                if (value != null && isVisibleTarget(value)) {
                    counts.merge(lexical(value), 1L, Long::sum);
                }
            }
            return counts.entrySet().stream()
                    .map(entry -> new FacetBucket(entry.getKey(), entry.getValue()))
                    .sorted(Comparator.comparingLong((FacetBucket bucket) -> bucket.count())
                            .reversed()
                            .thenComparing(FacetBucket::value, InMemoryBrowsePort::compareByCodePoint))
                    .toList();
        }

        /** A reference counts only when its target is visible in the tenant; any other value always counts. */
        private boolean isVisibleTarget(Value value) {
            return !(value instanceof ReferenceValue reference) || visibleTargets.contains(reference.target());
        }

        private boolean passes(ObjectView view, Optional<String> excludedKey) {
            if (query.text().isPresent() && score(view) < 0) {
                return false;
            }
            if (query.filter().isEmpty()) {
                return true;
            }
            FilterNode filter = query.filter().get();
            if (excludedKey.isEmpty()) {
                return matches(filter, view);
            }
            // A filter that is not an AndFilter is one conjunct. A FieldFilter on the excluded path is
            // removed, which leaves match-all. Every other conjunct is kept whole.
            for (FilterNode conjunct : conjuncts(filter)) {
                if (conjunct instanceof FieldFilter field && field.path().equals(excludedKey.get())) {
                    continue;
                }
                if (!matches(conjunct, view)) {
                    return false;
                }
            }
            return true;
        }

        /** Every query token must occur; the score is the total number of occurrences. -1 is no match. */
        long score(ObjectView view) {
            if (queryTokens.isEmpty()) {
                return -1;
            }
            List<String> document = new ArrayList<>();
            for (String iri : searchableIris) {
                if (view.properties().get(new PropertyRef(iri)) instanceof TextValue text) {
                    document.addAll(tokens(text.value()));
                }
            }
            long total = 0;
            for (String token : queryTokens) {
                long occurrences = document.stream().filter(token::equals).count();
                if (occurrences == 0) {
                    return -1;
                }
                total += occurrences;
            }
            return total;
        }
    }

    /**
     * Rejects, with one {@link InvalidQueryException} that lists every problem, each shape this port
     * does not evaluate. Nothing is read when the query is rejected.
     */
    private static void rejectUnsupportedShapes(BrowseQuery query) {
        List<String> problems = new ArrayList<>();
        query.filter().ifPresent(filter -> collectFilterProblems(filter, problems));
        for (Sort sort : query.sorts()) {
            collectPathProblem(sort.path(), "sort", problems);
        }
        for (FieldPath facet : query.facets()) {
            collectPathProblem(facet, "facet", problems);
        }
        if (!problems.isEmpty()) {
            throw new InvalidQueryException(problems);
        }
    }

    private static void collectFilterProblems(FilterNode node, List<String> problems) {
        switch (node) {
            case AndFilter and -> and.operands().forEach(operand -> collectFilterProblems(operand, problems));
            case OrFilter or -> or.operands().forEach(operand -> collectFilterProblems(operand, problems));
            case FieldFilter field -> collectFieldFilterProblems(field, problems);
            default -> problems.add(node.getClass().getSimpleName() + " is not evaluated by the in-memory browse port");
        }
    }

    private static void collectFieldFilterProblems(FieldFilter filter, List<String> problems) {
        if (filter.path().contains("|")) {
            problems.add("traversal path " + filter.path() + " is not evaluated by the in-memory browse port");
        }
        Optional<OperatorDefinition> operator = OPERATORS.findByCanonicalOrAlias(filter.op());
        if (operator.isEmpty()) {
            problems.add("unknown operator '" + filter.op() + "' on '" + filter.path() + "'");
        } else if (!EVALUATED_OPERATORS.contains(operator.get().getCanonicalName())) {
            problems.add("operator '" + operator.get().getCanonicalName()
                    + "' is not evaluated by the in-memory browse port on '" + filter.path() + "'");
        }
    }

    private static void collectPathProblem(FieldPath path, String role, List<String> problems) {
        if (path.segments().size() != 1) {
            problems.add(role + " on traversal path " + path.asSlugPath()
                    + " is not evaluated by the in-memory browse port");
        }
    }

    /** Ascending puts a missing value last and descending puts it first. */
    private static Comparator<ObjectView> sortBy(Sort sort) {
        PropertyRef property = new PropertyRef(iriOf(sort.path()));
        Comparator<Value> values = Comparator.nullsLast(InMemoryBrowsePort::compareValues);
        Comparator<ObjectView> ascending =
                Comparator.comparing((ObjectView view) -> view.properties().get(property), values);
        return sort.direction() == Direction.ASC ? ascending : ascending.reversed();
    }

    private static boolean matches(FilterNode node, ObjectView view) {
        return switch (node) {
            case AndFilter and -> and.operands().stream().allMatch(operand -> matches(operand, view));
            case OrFilter or -> or.operands().stream().anyMatch(operand -> matches(operand, view));
            case FieldFilter field -> fieldMatches(field, view);
            default -> throw new IllegalStateException("filter node was not checked before the read: " + node);
        };
    }

    private static List<FilterNode> conjuncts(FilterNode node) {
        return node instanceof AndFilter and ? and.operands() : List.of(node);
    }

    /** The FieldFilter's path is a direct property IRI: traversal paths were rejected before the read. */
    private static boolean fieldMatches(FieldFilter filter, ObjectView view) {
        Value actual = view.properties().get(new PropertyRef(filter.path()));
        String operator = canonicalOperator(filter.op());
        return switch (operator) {
            case "exists" -> actual != null;
            case "does not exist" -> actual == null;
            case "is" -> actual != null && compareToOperand(actual, filter.value()) == 0;
            case "is not" -> actual == null || compareToOperand(actual, filter.value()) != 0;
            case "is greater than" -> actual != null && compareToOperand(actual, filter.value()) > 0;
            case "is greater than or equal to" -> actual != null && compareToOperand(actual, filter.value()) >= 0;
            case "is less than" -> actual != null && compareToOperand(actual, filter.value()) < 0;
            case "is less than or equal to" -> actual != null && compareToOperand(actual, filter.value()) <= 0;
            case "is in" -> actual != null && inList(actual, filter.value());
            case "contains" ->
                actual instanceof TextValue text && lower(text.value()).contains(lower(text(filter.value())));
            case "starts with" ->
                actual instanceof TextValue text && lower(text.value()).startsWith(lower(text(filter.value())));
            default -> throw new IllegalStateException("operator was not checked before the read: " + operator);
        };
    }

    private static int compareToOperand(Value actual, JsonNode operand) {
        return compare(comparable(actual), operand(actual, operand));
    }

    private static boolean inList(Value actual, JsonNode list) {
        if (!list.isArray()) {
            throw new IllegalArgumentException("'is in' needs a list of values");
        }
        for (JsonNode element : list) {
            if (compare(comparable(actual), operand(actual, element)) == 0) {
                return true;
            }
        }
        return false;
    }

    /** Reads a JSON operand as the Java type of the stored value it is compared with. */
    private static Object operand(Value actual, JsonNode json) {
        if (json == null || json.isNull() || json.isMissingNode()) {
            throw new IllegalArgumentException("this operator needs a value");
        }
        return switch (actual) {
            case TextValue _ -> text(json);
            case IntegerValue _, DecimalValue _ -> number(json);
            case BoolValue _ -> {
                if (!json.isBoolean()) {
                    throw new IllegalArgumentException("expected a boolean operand");
                }
                yield json.booleanValue();
            }
            case DateValue _ -> LocalDate.parse(text(json));
            case DateTimeValue _ -> Instant.parse(text(json));
            case ReferenceValue _ -> ObjectId.parse(text(json)).value();
            default ->
                throw new UnsupportedOperationException(
                        "the in-memory browse port does not compare values of this kind: " + actual);
        };
    }

    private static Object comparable(Value value) {
        return switch (value) {
            case TextValue text -> text.value();
            case IntegerValue integer -> BigDecimal.valueOf(integer.value());
            case DecimalValue decimal -> decimal.value();
            case BoolValue bool -> bool.value();
            case DateValue date -> date.value();
            case DateTimeValue instant -> instant.value();
            case ReferenceValue reference -> reference.target().value();
            case ListValue _ ->
                throw new UnsupportedOperationException("list values are not compared by the in-memory browse port");
            default -> throw new UnsupportedOperationException("the in-memory browse port does not compare " + value);
        };
    }

    private static int compareValues(Value left, Value right) {
        return compare(comparable(left), comparable(right));
    }

    private static int compare(Object left, Object right) {
        if (left instanceof BigDecimal leftNumber && right instanceof BigDecimal rightNumber) {
            return leftNumber.compareTo(rightNumber);
        }
        if (left instanceof UUID leftId && right instanceof UUID rightId) {
            return compareIds(leftId, rightId);
        }
        if (left instanceof Comparable<?> && left.getClass() == right.getClass()) {
            return compareUnchecked(left, right);
        }
        throw new IllegalArgumentException("Cannot order " + left + " against " + right);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareUnchecked(Object left, Object right) {
        return ((Comparable) left).compareTo(right);
    }

    /** BrowsePort's id order: unsigned, most significant half first. */
    private static int compareIds(UUID left, UUID right) {
        int high = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    /** Unicode code point order, which equals the UTF-8 byte order. */
    private static int compareByCodePoint(String left, String right) {
        return Arrays.compare(left.codePoints().toArray(), right.codePoints().toArray());
    }

    private static String lexical(Value value) {
        return switch (value) {
            case TextValue text -> text.value();
            case IntegerValue integer -> Long.toString(integer.value());
            case DecimalValue decimal -> decimal.value().toPlainString();
            case BoolValue bool -> Boolean.toString(bool.value());
            case DateValue date -> date.value().toString();
            case DateTimeValue instant -> instant.value().toString();
            case ReferenceValue reference -> reference.target().value().toString();
            case ListValue _ ->
                throw new UnsupportedOperationException(
                        "facets over list-valued properties are not evaluated by the in-memory browse port");
            default -> throw new UnsupportedOperationException("the in-memory browse port does not bucket " + value);
        };
    }

    private static String canonicalOperator(String op) {
        return OPERATORS
                .findByCanonicalOrAlias(op)
                .map(OperatorDefinition::getCanonicalName)
                .orElseThrow(() -> new IllegalArgumentException("unknown operator: " + op));
    }

    private static String text(JsonNode json) {
        if (!json.isTextual()) {
            throw new IllegalArgumentException("expected a text operand");
        }
        return json.asText();
    }

    private static BigDecimal number(JsonNode json) {
        if (!json.isNumber()) {
            throw new IllegalArgumentException("expected a numeric operand");
        }
        return json.decimalValue();
    }

    /**
     * The direct property a single-segment path names. Traversal paths are rejected before a read, so
     * a multi-segment path reaching here is a bug in this class, and it fails rather than guessing.
     */
    private static String iriOf(FieldPath path) {
        if (path.segments().size() != 1) {
            throw new IllegalStateException("the in-memory browse port reads direct paths only: " + path.asSlugPath());
        }
        return path.segments().get(0).iri();
    }

    private static List<String> tokens(String text) {
        return Arrays.stream(NON_WORD.split(lower(text)))
                .filter(token -> !token.isEmpty())
                .toList();
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
