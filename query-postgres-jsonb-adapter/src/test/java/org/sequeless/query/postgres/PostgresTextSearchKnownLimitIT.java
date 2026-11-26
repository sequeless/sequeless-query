package org.sequeless.query.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.query.api.Audit;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.Page;
import org.sequeless.query.api.PropertyRef;
import org.sequeless.query.api.TypeRef;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.testkit.QueryFixtures;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * Pins a documented difference from the in-memory reference, which is not a testkit contract case (spec 14, Q22 and Q29).
 *
 * <p>Postgres text search reads {@code sq_object.search_vector}. That column is filled, for each row, from every property
 * the tenant registers as searchable, whatever class the property belongs to. The in-memory reference reads only the
 * searchable Fields of the scope's root. So a property that is searchable on another root class of the same tenant is
 * matched here on an object of the scope's type, and the reference does not match it. This test pins the Postgres answer,
 * so that a change to either side shows.
 *
 * <p>The Project's {@code projectName} is searchable on the Project root of the testkit scenario, and the Task root
 * does not have it. A Task that carries a value under that IRI is therefore found by a search for the project's name.
 */
class PostgresTextSearchKnownLimitIT extends AbstractPostgresIT {

    /** The testkit scenario's IRIs: the Task's title, and the Project's name, which the Task scope does not have. */
    private static final String TITLE_IRI = "https://example.org/query-testkit#title";

    private static final String PROJECT_NAME_IRI = "https://example.org/query-testkit#projectName";
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void textSearchOnATaskMatchesAPropertySearchableOnAnotherRootOfTheTenant() {
        FieldScope taskScope = QueryFixtures.taskScope(QueryFixtures.TENANT);
        prepareIndexes();
        ObjectId withProjectName = ObjectId.random();
        ObjectId withoutProjectName = ObjectId.random();
        seedTask(
                withProjectName,
                taskScope,
                Map.of(
                        new PropertyRef(TITLE_IRI), Value.text("Quarterly review"),
                        new PropertyRef(PROJECT_NAME_IRI), Value.text("Budget project")));
        seedTask(withoutProjectName, taskScope, Map.of(new PropertyRef(TITLE_IRI), Value.text("Quarterly review")));

        List<ObjectId> found = new PostgresBrowsePort(jdbc())
                .browse(taskScope, textQuery("budget")).items().stream()
                        .map(ObjectView::id)
                        .toList();

        // The Postgres answer: the task that carries the project's name is found. The reference finds neither.
        assertThat(found).containsExactly(withProjectName);
    }

    private static void prepareIndexes() {
        new PostgresIndexPreparation(jdbc(), new DataSourceTransactionManager(dataSource))
                .prepare(
                        QueryFixtures.TENANT,
                        new OntologyVersionRef(1),
                        QueryFixtures.rootFields(QueryFixtures.TENANT));
    }

    private static void seedTask(ObjectId id, FieldScope taskScope, Map<PropertyRef, Value> properties) {
        new PostgresObjectSeeder(jdbc())
                .seed(new ObjectView(
                        id,
                        new TypeRef(taskScope.root().iri()),
                        QueryFixtures.TENANT,
                        1,
                        Optional.empty(),
                        properties,
                        new Audit(CREATED, "tester", CREATED, "tester"),
                        false));
    }

    private static BrowseQuery textQuery(String text) {
        return new BrowseQuery(Optional.empty(), Optional.of(text), List.of(), new Page(0, 20), List.of());
    }

    private static JdbcClient jdbc() {
        return JdbcClient.create(dataSource);
    }
}
