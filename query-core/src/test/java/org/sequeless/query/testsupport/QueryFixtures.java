package org.sequeless.query.testsupport;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import org.sequeless.query.field.api.TraversalConfig;
import org.sequeless.query.field.api.VersionPolicy;

/** Hand-built Field-contract values for the query-core tests. Not a published test kit. */
public final class QueryFixtures {

    public static final TenantId TENANT = new TenantId("tenant-1");
    public static final OntologyVersionRef V1 = new OntologyVersionRef(1);
    public static final OntologyVersionRef V2 = new OntologyVersionRef(2);

    public static final Capabilities FILTER_ONLY = new Capabilities(true, false, false, false);
    public static final Capabilities FILTER_SORT = new Capabilities(true, false, false, true);
    public static final Capabilities FILTER_FACET_SORT = new Capabilities(true, true, false, true);

    private QueryFixtures() {}

    /** A class reference whose IRI is {@code urn:class:<slug>}. */
    public static ClassRef classRef(String slug) {
        return new ClassRef(
                UUID.nameUUIDFromBytes(slug.getBytes(StandardCharsets.UTF_8)),
                slug,
                "urn:class:" + slug,
                new LocalizedText(slug, "en"));
    }

    /**
     * A path through the given property IRIs. Every segment but the last lands on a made-up class,
     * as a traversal requires. Each slug is the IRI after its last colon.
     */
    public static FieldPath path(String... iris) {
        List<PathSegment> segments = new ArrayList<>();
        for (int i = 0; i < iris.length; i++) {
            ClassRef target = i < iris.length - 1 ? classRef("Target" + i) : null;
            UUID propertyId = UUID.nameUUIDFromBytes(iris[i].getBytes(StandardCharsets.UTF_8));
            segments.add(new PathSegment(propertyId, iris[i], slugOf(iris[i]), target));
        }
        return new FieldPath(segments);
    }

    /** A Field with a label, no description and no restriction, defined in version 1. */
    public static Field field(FieldPath path, FieldType type, Capabilities capabilities) {
        return new Field(
                path,
                type,
                null,
                Cardinality.SINGLE,
                capabilities,
                new LocalizedText(slugOf(path.asSlugPath()), "en"),
                null,
                Optional.empty(),
                OperatorRestriction.unrestricted(),
                InferenceSupport.ASSERTED_ONLY,
                Set.of(V1),
                Optional.empty());
    }

    /** A request for the tenant, rooted at {@code root}, under the union policy and defaults. */
    public static FieldScopeRequest request(ClassRef root) {
        return new FieldScopeRequest(
                TENANT,
                root,
                new VersionPolicy.Union(),
                TraversalConfig.defaults(),
                Optional.empty(),
                Optional.empty());
    }

    /** A scope rooted at {@code root} that lists {@code facets} and is defined in version 1. */
    public static FieldScope scope(ClassRef root, List<Field> facets) {
        return scope(root, facets, Set.of(V1));
    }

    /** A scope rooted at {@code root} that lists {@code facets} and the given ontology versions. */
    public static FieldScope scope(ClassRef root, List<Field> facets, Set<OntologyVersionRef> versions) {
        return new FieldScope(root, facets, List.of(), "stamp-test", versions, Set.of(root), request(root));
    }

    private static String slugOf(String iri) {
        return iri.substring(iri.lastIndexOf(':') + 1);
    }
}
