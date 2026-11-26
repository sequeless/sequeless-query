package org.sequeless.query.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.Filters;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.Direction;
import org.sequeless.query.api.FieldKey;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.api.Page;
import org.sequeless.query.api.Sort;
import org.sequeless.query.field.api.FieldPath;

/**
 * The shapes {@link BrowseValidation} rejects, checked without a database. Each rejection names the key it
 * refuses, so an operator problem and a traversal problem both carry the key the contract asserts.
 */
class BrowseValidationTest {

    private static final FieldPath TRAVERSAL =
            TestFields.traversal("urn:ex:belongsTo", "urn:ex:status").path();
    private static final String TRAVERSAL_KEY = FieldKey.encode(TRAVERSAL);
    private static final String DIRECT_KEY = "urn:ex:title";
    private static final Page PAGE = new Page(0, 20);

    @Test
    void aTraversalFilterIsRejectedNamingItsKey() {
        assertRejectedNamingKey(
                query(Optional.of(Filters.field(TRAVERSAL_KEY, "is", text("x"))), List.of(), List.of()), TRAVERSAL_KEY);
    }

    @Test
    void aTraversalSortIsRejectedNamingItsKey() {
        assertRejectedNamingKey(
                query(Optional.empty(), List.of(new Sort(TRAVERSAL, Direction.ASC)), List.of()), TRAVERSAL_KEY);
    }

    @Test
    void aTraversalFacetIsRejectedNamingItsKey() {
        assertRejectedNamingKey(query(Optional.empty(), List.of(), List.of(TRAVERSAL)), TRAVERSAL_KEY);
    }

    @Test
    void anOperatorTheAdapterDoesNotEvaluateIsRejectedNamingItsKey() {
        assertRejectedNamingKey(
                query(Optional.of(Filters.field(DIRECT_KEY, "like", text("A%"))), List.of(), List.of()), DIRECT_KEY);
    }

    @Test
    void anUnknownOperatorIsRejectedNamingItsKey() {
        assertRejectedNamingKey(
                query(Optional.of(Filters.field(DIRECT_KEY, "no such operator", text("A"))), List.of(), List.of()),
                DIRECT_KEY);
    }

    private static void assertRejectedNamingKey(BrowseQuery query, String key) {
        assertThatThrownBy(() -> BrowseValidation.rejectUnsupportedShapes(query))
                .isInstanceOfSatisfying(
                        InvalidQueryException.class,
                        error -> assertThat(error.problems())
                                .anySatisfy(problem -> assertThat(problem).contains(key)));
    }

    private static BrowseQuery query(Optional<FilterNode> filter, List<Sort> sorts, List<FieldPath> facets) {
        return new BrowseQuery(filter, Optional.empty(), sorts, PAGE, facets);
    }

    private static JsonNode text(String value) {
        return JsonNodeFactory.instance.textNode(value);
    }
}
