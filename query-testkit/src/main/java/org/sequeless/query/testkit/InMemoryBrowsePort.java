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
 * <p>Filters are evaluated over the operators filter-core's default registry names, on direct
 * properties. Free text requires every query token to occur in the object's searchable text, and
 * ranks by the number of occurrences, best first. The searchable text is the text of the scope's
 * searchable root Fields.
 *
 * <p>Three things are not evaluated, and fail with {@link UnsupportedOperationException} rather than
 * answering wrongly: traversal paths (more than one segment), {@code AnyFilter}, and the list-valued
 * operators and orderings. No contract clause needs them.
 *
 * <p>Ids, and reference values used as sort keys or facet keys, compare in {@link BrowsePort}'s
 * unsigned order (see {@code compareIds}), never by the signed {@link UUID#compareTo}.
 */
public final class InMemoryBrowsePort implements BrowsePort {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final OperatorRegistry OPERATORS = OperatorRegistry.defaults();

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
     * One browse's inputs, with the candidate objects and the searchable properties worked out once:
     * tenant, not soft-deleted, and a type in {@link FieldScope#concreteTypes()}.
     */
    private static final class Scan {

        private final BrowseQuery query;
        private final List<ObjectView> candidates;
        private final List<String> searchableIris;
        private final List<String> queryTokens;

        Scan(FieldScope scope, BrowseQuery query, List<ObjectView> stored) {
            this.query = query;
            TenantId tenant = scope.request().tenantId();
            Set<String> types =
                    scope.concreteTypes().stream().map(ClassRef::iri).collect(Collectors.toSet());
            this.candidates = stored.stream()
                    .filter(view -> view.tenant().equals(tenant))
                    .filter(view -> !view.deleted())
                    .filter(view -> types.contains(view.type().iri()))
                    .toList();
            this.searchableIris = scope.facets().stream()
                    .filter(field -> field.capabilities().searchable())
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
                if (value != null) {
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
            // removed, which leaves match-all.
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
            default ->
                throw new UnsupportedOperationException(
                        "the in-memory browse port does not evaluate this filter node: " + node);
        };
    }

    private static List<FilterNode> conjuncts(FilterNode node) {
        return node instanceof AndFilter and ? and.operands() : List.of(node);
    }

    private static boolean fieldMatches(FieldFilter filter, ObjectView view) {
        Value actual = view.properties().get(new PropertyRef(singleIri(filter.path())));
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
            default ->
                throw new UnsupportedOperationException(
                        "the in-memory browse port does not evaluate the operator '" + operator + "'");
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

    /** The direct property a single-segment path names. */
    private static String iriOf(FieldPath path) {
        if (path.segments().size() != 1) {
            throw new UnsupportedOperationException(
                    "the in-memory browse port evaluates direct paths only: " + path.asSlugPath());
        }
        return path.segments().get(0).iri();
    }

    /** The property a FieldFilter path names; a traversal key carries {@code |} and is not evaluated. */
    private static String singleIri(String key) {
        if (key.contains("|")) {
            throw new UnsupportedOperationException("the in-memory browse port evaluates direct paths only: " + key);
        }
        return key;
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
