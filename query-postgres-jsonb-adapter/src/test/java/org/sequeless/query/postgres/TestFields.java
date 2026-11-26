package org.sequeless.query.postgres;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.Cardinality;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.InferenceSupport;
import org.sequeless.query.field.api.LocalizedText;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.OperatorRestriction;
import org.sequeless.query.field.api.PathSegment;

/** Hand-built Fields for the adapter's unit tests. The IRI is also the slug, which the Field does not need to be. */
final class TestFields {

    private static final OntologyVersionRef V1 = new OntologyVersionRef(1);
    private static final Capabilities ALL = new Capabilities(true, true, true, true);

    private TestFields() {}

    /** A direct Field (one segment) whose property IRI is {@code iri}, with every capability. */
    static Field direct(String iri, FieldType type) {
        return direct(iri, type, ALL);
    }

    /** A direct Field (one segment) whose property IRI is {@code iri}, with the given capabilities. */
    static Field direct(String iri, FieldType type, Capabilities capabilities) {
        return withPath(new FieldPath(List.of(segment(iri, null))), type, capabilities);
    }

    /** A traversal Field through the given property IRIs; every segment but the last lands on a class. */
    static Field traversal(String... iris) {
        List<PathSegment> segments = new ArrayList<>();
        for (int i = 0; i < iris.length; i++) {
            ClassRef target = i < iris.length - 1 ? classRef("urn:class:" + iris[i]) : null;
            segments.add(segment(iris[i], target));
        }
        return withPath(new FieldPath(segments), FieldType.STRING, ALL);
    }

    private static Field withPath(FieldPath path, FieldType type, Capabilities capabilities) {
        return new Field(
                path,
                type,
                null,
                Cardinality.SINGLE,
                capabilities,
                new LocalizedText(path.asSlugPath(), "en"),
                null,
                Optional.empty(),
                OperatorRestriction.unrestricted(),
                InferenceSupport.ASSERTED_ONLY,
                Set.of(V1),
                Optional.empty());
    }

    private static PathSegment segment(String iri, ClassRef targetClass) {
        return new PathSegment(UUID.nameUUIDFromBytes(iri.getBytes(StandardCharsets.UTF_8)), iri, iri, targetClass);
    }

    static ClassRef classRef(String iri) {
        return new ClassRef(
                UUID.nameUUIDFromBytes(iri.getBytes(StandardCharsets.UTF_8)), iri, iri, new LocalizedText(iri, "en"));
    }
}
