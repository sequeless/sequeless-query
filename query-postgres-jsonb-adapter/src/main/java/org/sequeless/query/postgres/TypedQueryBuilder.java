package org.sequeless.query.postgres;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sequeless.filter.api.OperatorDefinition;
import org.sequeless.filter.sql.api.SqlFragment;
import org.sequeless.filter.sql.spi.QueryBuilder;
import org.sequeless.query.field.api.FieldType;

/**
 * A {@link QueryBuilder} that coerces each bind value to the Java type of its Field, then delegates the rendering
 * (spec 14, section 6.4). The translator passes values as plain JSON-derived Java types, so {@code 2026-02-01} arrives
 * as a {@link String}. The expression it compares against needs a {@code date}, so {@link ValueConverter} converts it.
 *
 * <p>The Field is recognised by its expression text. {@link FieldExpressions#sqlOf} gives one text per Field, so
 * {@code typeBySql} maps each text to its type. A Field whose text is not in the map is a programming error.
 */
final class TypedQueryBuilder implements QueryBuilder {

    private final QueryBuilder delegate;
    private final Map<String, FieldType> typeBySql;

    /**
     * @param delegate the vendor builder that renders the SQL; must not be {@code null}
     * @param typeBySql the type of each Field expression text the filter may name; must not be {@code null}
     */
    TypedQueryBuilder(QueryBuilder delegate, Map<String, FieldType> typeBySql) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.typeBySql = Map.copyOf(typeBySql);
    }

    @Override
    public SqlFragment binary(org.sequeless.filter.sql.api.Field field, OperatorDefinition operator, Object value) {
        return delegate.binary(field, operator, convert(field, value));
    }

    @Override
    public SqlFragment unary(org.sequeless.filter.sql.api.Field field, OperatorDefinition operator) {
        return delegate.unary(field, operator);
    }

    @Override
    public SqlFragment and(List<SqlFragment> operands) {
        return delegate.and(operands);
    }

    @Override
    public SqlFragment or(List<SqlFragment> operands) {
        return delegate.or(operands);
    }

    private Object convert(org.sequeless.filter.sql.api.Field field, Object value) {
        if (!(field instanceof org.sequeless.filter.sql.api.Field.Expression expression)) {
            throw new IllegalStateException("the postgres filter names expressions only, not columns");
        }
        String sql = expression.expression().sql();
        FieldType type = typeBySql.get(sql);
        if (type == null) {
            throw new IllegalStateException("no Field of the scope has the expression " + sql);
        }
        return ValueConverter.toBindValue(type, value);
    }
}
