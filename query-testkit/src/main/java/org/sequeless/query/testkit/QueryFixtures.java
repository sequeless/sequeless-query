package org.sequeless.query.testkit;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.Cardinality;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.InferenceSupport;
import org.sequeless.query.field.api.LocalizedText;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.OperatorRestriction;
import org.sequeless.query.field.api.PathSegment;
import org.sequeless.query.field.api.Relationship;
import org.sequeless.query.field.api.TraversalConfig;
import org.sequeless.query.field.api.VersionPolicy;
import org.sequeless.query.field.testfixtures.ClassFixture;
import org.sequeless.query.field.testfixtures.ContractFixture;
import org.sequeless.query.field.testfixtures.InMemoryFieldSource;
import org.sequeless.query.field.testfixtures.PropertyFixture;
import org.sequeless.query.field.testfixtures.RelationshipFixture;

/**
 * The scenario the Browse and Index contracts run over: three classes, Task, Project and Person, with
 * the properties and the two walkable relationships the contracts filter, sort, facet and search on.
 * Scopes are resolved through {@link InMemoryFieldSource}, so the Fields are the ones the Field
 * contract produces, not hand-built records.
 */
final class QueryFixtures {

    static final TenantId TENANT = new TenantId("query-testkit");
    static final TenantId OTHER_TENANT = new TenantId("other-tenant");
    static final OntologyVersionRef V1 = new OntologyVersionRef(1);

    static final String TASK = "task";
    static final String PROJECT = "project";
    static final String PERSON = "person";

    static final String TASK_IRI = "https://example.org/query-testkit#Task";
    static final String PROJECT_IRI = "https://example.org/query-testkit#Project";
    static final String PERSON_IRI = "https://example.org/query-testkit#Person";

    static final String TITLE_IRI = "https://example.org/query-testkit#title";
    static final String DESCRIPTION_IRI = "https://example.org/query-testkit#description";
    static final String STATUS_IRI = "https://example.org/query-testkit#status";
    static final String PRIORITY_IRI = "https://example.org/query-testkit#priority";
    static final String DUE_DATE_IRI = "https://example.org/query-testkit#dueDate";
    static final String ASSIGNED_TO_IRI = "https://example.org/query-testkit#assignedTo";
    static final String BELONGS_TO_PROJECT_IRI = "https://example.org/query-testkit#belongsToProject";
    static final String PROJECT_NAME_IRI = "https://example.org/query-testkit#projectName";
    static final String PROJECT_STATUS_IRI = "https://example.org/query-testkit#projectStatus";
    static final String PERSON_NAME_IRI = "https://example.org/query-testkit#personName";
    static final String PERSON_EMAIL_IRI = "https://example.org/query-testkit#personEmail";

    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID PERSON_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    private static final Capabilities FILTERABLE = new Capabilities(true, false, false, false);
    private static final Capabilities FILTERABLE_SORTABLE = new Capabilities(true, false, false, true);
    private static final Capabilities FILTERABLE_SEARCHABLE = new Capabilities(true, false, true, false);
    private static final Capabilities FILTERABLE_FACETABLE_SORTABLE = new Capabilities(true, true, false, true);
    private static final Capabilities FILTERABLE_FACETABLE = new Capabilities(true, true, false, false);

    private QueryFixtures() {}

    /**
     * The scenario, owned by {@code tenant}. The contracts use {@link #TENANT} and {@link #OTHER_TENANT}.
     *
     * @param tenant the tenant the classes belong to
     * @return the fixture
     */
    static ContractFixture fixture(TenantId tenant) {
        ClassFixture task = new ClassFixture(
                TASK_ID,
                TASK,
                TASK_IRI,
                Set.of(),
                List.of(
                        property(
                                UUID.fromString("00000000-0000-0000-0000-0000000000d1"),
                                TITLE_IRI,
                                "title",
                                FieldType.STRING,
                                FILTERABLE_SEARCHABLE),
                        property(
                                UUID.fromString("00000000-0000-0000-0000-0000000000d2"),
                                DESCRIPTION_IRI,
                                "description",
                                FieldType.STRING,
                                FILTERABLE_SEARCHABLE),
                        property(
                                UUID.fromString("00000000-0000-0000-0000-0000000000d3"),
                                STATUS_IRI,
                                "status",
                                FieldType.STRING,
                                FILTERABLE_FACETABLE_SORTABLE),
                        property(
                                UUID.fromString("00000000-0000-0000-0000-0000000000d4"),
                                PRIORITY_IRI,
                                "priority",
                                FieldType.INTEGER,
                                FILTERABLE_SORTABLE),
                        property(
                                UUID.fromString("00000000-0000-0000-0000-0000000000d5"),
                                DUE_DATE_IRI,
                                "dueDate",
                                FieldType.DATE,
                                FILTERABLE_SORTABLE)),
                List.of(
                        relationship(
                                UUID.fromString("00000000-0000-0000-0000-0000000000e1"),
                                ASSIGNED_TO_IRI,
                                "assignedTo",
                                PERSON),
                        relationship(
                                UUID.fromString("00000000-0000-0000-0000-0000000000e2"),
                                BELONGS_TO_PROJECT_IRI,
                                "belongsToProject",
                                PROJECT)));
        ClassFixture project = new ClassFixture(
                PROJECT_ID,
                PROJECT,
                PROJECT_IRI,
                Set.of(),
                List.of(
                        property(
                                UUID.fromString("00000000-0000-0000-0000-0000000000f1"),
                                PROJECT_NAME_IRI,
                                "name",
                                FieldType.STRING,
                                FILTERABLE_SEARCHABLE),
                        property(
                                UUID.fromString("00000000-0000-0000-0000-0000000000f2"),
                                PROJECT_STATUS_IRI,
                                "status",
                                FieldType.STRING,
                                FILTERABLE_FACETABLE)),
                List.of());
        ClassFixture person = new ClassFixture(
                PERSON_ID,
                PERSON,
                PERSON_IRI,
                Set.of(),
                List.of(
                        property(
                                UUID.fromString("00000000-0000-0000-0000-0000000001a1"),
                                PERSON_NAME_IRI,
                                "name",
                                FieldType.STRING,
                                FILTERABLE_SEARCHABLE),
                        property(
                                UUID.fromString("00000000-0000-0000-0000-0000000001a2"),
                                PERSON_EMAIL_IRI,
                                "email",
                                FieldType.STRING,
                                FILTERABLE)),
                List.of());
        return new ContractFixture(tenant, List.of(V1), List.of(task, project, person));
    }

    /**
     * The Task scope for {@code tenant}: candidates are Task only.
     *
     * @param tenant the tenant the scope is resolved for
     * @return the resolved scope
     */
    static FieldScope taskScope(TenantId tenant) {
        return resolve(tenant, TASK);
    }

    /**
     * The Task scope widened to Task and Project, as a union of types.
     *
     * @param tenant the tenant the scope is resolved for
     * @return the resolved scope whose concrete types are Task and Project
     */
    static FieldScope taskAndProjectScope(TenantId tenant) {
        FieldScope scope = taskScope(tenant);
        return withConcreteTypes(scope, Set.of(scope.root(), classRef(fixture(tenant), PROJECT)));
    }

    /**
     * Resolves the scope rooted at the named class of the fixture for {@code tenant}.
     *
     * @param tenant the tenant the scope is resolved for
     * @param rootSlug the slug of the root class
     * @return the resolved scope
     */
    static FieldScope resolve(TenantId tenant, String rootSlug) {
        ContractFixture fixture = fixture(tenant);
        return InMemoryFieldSource.from(fixture).resolve(request(tenant, classRef(fixture, rootSlug)));
    }

    /**
     * A ClassRef for the named class of {@code fixture}, carrying the fixture's own id and IRI.
     *
     * @param fixture the scenario
     * @param slug the class slug
     * @return the class reference
     */
    static ClassRef classRef(ContractFixture fixture, String slug) {
        ClassFixture classFixture = fixture.classNamed(slug);
        return new ClassRef(classFixture.id(), classFixture.slug(), classFixture.iri(), new LocalizedText(slug, "en"));
    }

    /**
     * The FieldPath of a direct facet Field of the scope.
     *
     * @param scope the scope
     * @param slug the Field's slug
     * @return the path
     */
    static FieldPath facetPath(FieldScope scope, String slug) {
        return scope.facets().stream()
                .map(Field::path)
                .filter(path -> path.asSlugPath().equals(slug))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no facet named " + slug));
    }

    /**
     * The FieldPath of a walkable relationship of the scope: one segment that lands on a class.
     *
     * @param scope the scope
     * @param slug the relationship's slug
     * @return the path
     */
    static FieldPath relationshipPath(FieldScope scope, String slug) {
        Relationship relationship = scope.relationships().stream()
                .filter(candidate -> candidate.slug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no relationship named " + slug));
        return new FieldPath(List.of(new PathSegment(
                relationship.propertyId(), relationship.iri(), relationship.slug(), relationship.targetClass())));
    }

    static FieldScope withConcreteTypes(FieldScope scope, Set<ClassRef> concreteTypes) {
        return new FieldScope(
                scope.root(),
                scope.facets(),
                scope.relationships(),
                scope.stamp(),
                scope.ontologyVersions(),
                concreteTypes,
                scope.request());
    }

    private static FieldScopeRequest request(TenantId tenant, ClassRef root) {
        return new FieldScopeRequest(
                tenant,
                root,
                new VersionPolicy.Union(),
                TraversalConfig.defaults(),
                Optional.empty(),
                Optional.empty());
    }

    private static PropertyFixture property(
            UUID id, String iri, String slug, FieldType type, Capabilities capabilities) {
        return new PropertyFixture(
                id,
                iri,
                slug,
                type,
                Cardinality.SINGLE,
                capabilities,
                null,
                Optional.empty(),
                OperatorRestriction.unrestricted(),
                InferenceSupport.ASSERTED_ONLY,
                V1);
    }

    private static RelationshipFixture relationship(UUID id, String iri, String slug, String targetClassSlug) {
        return new RelationshipFixture(id, iri, slug, targetClassSlug, Cardinality.SINGLE, true, Set.of(V1));
    }
}
