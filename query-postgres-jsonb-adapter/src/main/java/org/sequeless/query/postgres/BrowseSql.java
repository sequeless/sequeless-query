package org.sequeless.query.postgres;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.sequeless.filter.api.AndFilter;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.OperatorRegistry;
import org.sequeless.filter.sql.api.DefaultFilterQueryTranslator;
import org.sequeless.filter.sql.api.FilterQueryTranslator;
import org.sequeless.filter.sql.api.SqlFragment;
import org.sequeless.filter.sql.postgres.PostgresQueryBuilder;
import org.sequeless.filter.sql.spi.QueryBuilder;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.Direction;
import org.sequeless.query.api.FieldKey;
import org.sequeless.query.api.FieldRegistries;
import org.sequeless.query.api.Sort;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.FieldType;

/**
 * The SQL of one browse over {@code sq_object}, built from one scope and one query.
 *
 * <p>The candidates are the fixed conditions (the tenant, the scope's concrete types, soft delete) and the free-text
 * match. The filter is translated by filter-sql's translator, against the scope's filterable Fields, and its values are
 * coerced to each Field's type. Every fragment has positional {@code ?} parameters, bound in the order they appear.
 * The Field expressions and the {@code id} come from {@link FieldExpressions} and the literal column names, never from
 * a bound value.
 *
 * <p>{@code scope.concreteTypes()} may be empty. The port then runs no statement, but it still builds this, so that the
 * sort and facet keys are checked.
 */
final class BrowseSql {

    private static final FilterQueryTranslator TRANSLATOR = new DefaultFilterQueryTranslator();
    private static final OperatorRegistry OPERATORS = OperatorRegistry.defaults();

    private static final String TEXT_MATCH = "search_vector @@ plainto_tsquery('simple', ?)";
    private static final String TEXT_RANK = "ts_rank(search_vector, plainto_tsquery('simple', ?))";

    private final BrowseQuery query;
    private final String tenant;
    private final List<String> types;
    private final Map<String, Field> fieldsByKey;
    private final FieldRegistry registry;
    private final PostgresFieldResolver resolver;
    private final QueryBuilder builder;

    /**
     * @param scope the resolved scope; must not be {@code null}; its concrete types may be empty
     * @param query the query to build SQL for; must not be {@code null}, and already checked by {@link BrowseValidation}
     */
    BrowseSql(FieldScope scope, BrowseQuery query) {
        this.query = query;
        this.tenant = scope.request().tenantId().value();
        this.types = scope.concreteTypes().stream().map(ClassRef::iri).sorted().toList();
        this.fieldsByKey = new HashMap<>();
        Map<String, FieldType> typeBySql = new HashMap<>();
        for (Field field : scope.facets()) {
            fieldsByKey.put(FieldKey.encode(field.path()), field);
            if (!field.path().isTraversing() && FieldExpressions.supports(field.type())) {
                typeBySql.put(FieldExpressions.sqlOf(field), field.type());
            }
        }
        this.registry = FieldRegistries.of(scope);
        this.resolver = new PostgresFieldResolver(scope.facets());
        this.builder = new TypedQueryBuilder(new PostgresQueryBuilder(), typeBySql);
    }

    /** The WHERE condition of the items and the total: the candidates and the whole filter. */
    SqlFragment matching() {
        SqlFragment base = base();
        return query.filter().map(filter -> all(base, translate(filter))).orElse(base);
    }

    /**
     * The ORDER BY terms, without the keyword. Each sort key is {@code expression ASC|DESC}, then {@code id}. With no
     * sort, free text ranks first, then {@code created_at} and {@code id}; without text, {@code created_at} then {@code
     * id}. An ascending key puts a missing value last and a descending one puts it first.
     */
    SqlFragment orderBy() {
        List<String> terms = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        if (!query.sorts().isEmpty()) {
            for (Sort sort : query.sorts()) {
                Field field = fieldFor(sort.path());
                // Text keys compare by code point, as the facet buckets do.
                String expression = FieldExpressions.sqlOf(field) + (isText(field.type()) ? " COLLATE \"C\"" : "");
                terms.add(expression + (sort.direction() == Direction.ASC ? " ASC NULLS LAST" : " DESC NULLS FIRST"));
            }
        } else {
            if (query.text().isPresent()) {
                terms.add(TEXT_RANK + " DESC");
                parameters.add(query.text().get());
            }
            terms.add("created_at ASC");
        }
        terms.add("id ASC");
        return new SqlFragment(String.join(", ", terms), parameters);
    }

    /**
     * The bucket query of one facet, as {@code (bucket_value, bucket_count)} rows in bucket order.
     * The counts use the candidates with the facet's own top-level conjuncts removed. A scalar bucket keeps only
     * present values. A reference bucket keeps only targets visible in the tenant, which is an inner join by
     * subquery, so the count of the objects still reaches the total. A DATE or DATETIME bucket is the value's text as the
     * in-memory reference writes it, from {@link FieldExpressions#bucketTextOf}.
     */
    SqlFragment bucketsOf(FieldPath facet) {
        Field field = fieldFor(facet);
        String expression = FieldExpressions.sqlOf(field);
        SqlFragment matching = facetMatching(facet);
        List<Object> parameters = new ArrayList<>(matching.parameters());
        String present;
        if (field.type() == FieldType.OBJECT_REF) {
            present = "(" + expression + ") IN (SELECT id FROM sq_object WHERE tenant_id = ? AND deleted_at IS NULL)";
            parameters.add(tenant);
        } else {
            present = "(" + expression + ") IS NOT NULL";
        }
        String sql = "SELECT bucket_value, bucket_count FROM (SELECT " + FieldExpressions.bucketTextOf(field)
                + " AS bucket_value, count(*) AS bucket_count FROM sq_object WHERE " + matching.sql() + " AND "
                + present + " GROUP BY 1) AS buckets ORDER BY bucket_count DESC, bucket_value COLLATE \"C\" ASC";
        return new SqlFragment(sql, parameters);
    }

    /** The candidates of a facet's count: the candidates with the filter minus the facet's own top-level conjuncts. */
    private SqlFragment facetMatching(FieldPath facet) {
        SqlFragment base = base();
        return filterWithout(FieldKey.encode(facet))
                .map(node -> all(base, translate(node)))
                .orElse(base);
    }

    /**
     * The filter minus its top-level AND conjuncts that are a {@code FieldFilter} on {@code key}. A filter that is not an
     * {@code AndFilter} is one conjunct. Empty means match-all (Q14).
     */
    private Optional<FilterNode> filterWithout(String key) {
        if (query.filter().isEmpty()) {
            return Optional.empty();
        }
        FilterNode filter = query.filter().get();
        List<FilterNode> kept = (filter instanceof AndFilter and ? and.operands() : List.of(filter))
                .stream().filter(conjunct -> !isFieldOn(conjunct, key)).toList();
        if (kept.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(kept.size() == 1 ? kept.get(0) : new AndFilter(kept));
    }

    private static boolean isFieldOn(FilterNode node, String key) {
        return node instanceof FieldFilter field && field.path().equals(key);
    }

    /** The fixed conditions and the free-text match. */
    private SqlFragment base() {
        List<Object> parameters = new ArrayList<>();
        parameters.add(tenant);
        parameters.addAll(types);
        String fixed = "tenant_id = ? AND type_iri IN ("
                + String.join(", ", types.stream().map(type -> "?").toList()) + ") AND deleted_at IS NULL";
        SqlFragment fixedFragment = new SqlFragment(fixed, parameters);
        return query.text()
                .map(text -> all(fixedFragment, new SqlFragment(TEXT_MATCH, Arrays.asList(text))))
                .orElse(fixedFragment);
    }

    private SqlFragment translate(FilterNode node) {
        return TRANSLATOR.translate(node, resolver, registry, OPERATORS, builder);
    }

    private Field fieldFor(FieldPath path) {
        String key = FieldKey.encode(path);
        Field field = fieldsByKey.get(key);
        if (field == null) {
            throw new IllegalArgumentException("'" + key + "' is not a Field of the scope");
        }
        return field;
    }

    /** Each part in parentheses, joined by AND, with the parameters in the same order. */
    private static SqlFragment all(SqlFragment... parts) {
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>();
        for (SqlFragment part : parts) {
            if (!sql.isEmpty()) {
                sql.append(" AND ");
            }
            sql.append('(').append(part.sql()).append(')');
            parameters.addAll(part.parameters());
        }
        return new SqlFragment(sql.toString(), parameters);
    }

    private static boolean isText(FieldType type) {
        return switch (type) {
            case STRING, LANG_STRING, TIME, DURATION, IRI_REF, CONCEPT_REF -> true;
            default -> false;
        };
    }
}
