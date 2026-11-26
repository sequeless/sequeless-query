package org.sequeless.query.postgres;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.filter.sql.api.FieldResolver;
import org.sequeless.query.api.FieldKey;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.field.api.Field;

/**
 * Resolves a filter's field key to the SQL expression of its Field, for filter-sql-adapter's translator (spec 14,
 * section 6.4). The key is the Field's {@link FieldKey} (the bare IRI of a direct Field), which is the path the
 * filter's FieldRegistry validated.
 *
 * <p>A key with a {@code |} names a traversal, and is rejected with {@link InvalidQueryException} that names the key
 * (Q23). The translator calls this once per path, so it reads a map and builds nothing else.
 */
public final class PostgresFieldResolver implements FieldResolver {

    private final Map<String, Field> fieldsByKey;

    /**
     * @param fields the Fields a filter may name, usually the scope's Fields; must not be {@code null}, nor contain
     *     {@code null}, and no two Fields may share a key
     * @throws NullPointerException if {@code fields} or one of its elements is {@code null}
     * @throws IllegalArgumentException if two Fields share a key
     */
    public PostgresFieldResolver(Collection<Field> fields) {
        Objects.requireNonNull(fields, "fields must not be null");
        Map<String, Field> byKey = new HashMap<>();
        for (Field field : fields) {
            Objects.requireNonNull(field, "fields must not contain null");
            String key = FieldKey.encode(field.path());
            if (byKey.put(key, field) != null) {
                throw new IllegalArgumentException("two Fields share the key '" + key + "'");
            }
        }
        this.fieldsByKey = Map.copyOf(byKey);
    }

    /**
     * @param path the FieldRegistry key of the filter path; must not be {@code null}
     * @return the Field's expression, or empty when no Field has this key
     * @throws InvalidQueryException if the key is a traversal, or the Field's type has no expression
     */
    @Override
    public Optional<org.sequeless.filter.sql.api.Field> resolve(String path) {
        Objects.requireNonNull(path, "path must not be null");
        if (path.indexOf('|') >= 0) {
            throw new InvalidQueryException(
                    List.of("traversal path '" + path + "' cannot be used as a filter: only a direct Field can"));
        }
        Field field = fieldsByKey.get(path);
        if (field == null) {
            return Optional.empty();
        }
        return Optional.of(new org.sequeless.filter.sql.api.Field.Expression(FieldExpressions.sqlExpressionOf(field)));
    }
}
