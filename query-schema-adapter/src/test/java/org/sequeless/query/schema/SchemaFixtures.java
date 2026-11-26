package org.sequeless.query.schema;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Cardinality;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.RelationshipDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.LocalizedText;
import org.sequeless.query.field.api.PathSegment;
import org.sequeless.query.field.api.TraversalConfig;
import org.sequeless.query.field.api.VersionPolicy;

/**
 * Builders for the schema-contract records the tests need. Every name is a local name under
 * {@link #NS}, so an IRI is {@code NS + localName} and a slug is the local name itself.
 */
final class SchemaFixtures {

    static final String NS = "http://example.org/onto#";
    static final TenantId TENANT = new TenantId("tenant-a");
    static final TenantId OTHER_TENANT = new TenantId("tenant-b");

    private SchemaFixtures() {}

    static String iri(String localName) {
        return NS + localName;
    }

    /** The id rule under test, written out here rather than called from production code. */
    static UUID idOf(String iri) {
        return UUID.nameUUIDFromBytes(iri.getBytes(StandardCharsets.UTF_8));
    }

    /** The label a type or property carries in these fixtures. */
    static String label(String localName) {
        return localName + " label";
    }

    static Schema schema(int version, TypeDefinition... types) {
        return new Schema(
                new OntologyVersion(version), "http://example.org/onto", Optional.empty(), Map.of(), List.of(types));
    }

    static TypeDefinition type(
            String localName, boolean isAbstract, List<String> superTypes, PropertyDefinition... properties) {
        return new TypeDefinition(
                iri(localName),
                label(localName),
                superTypes.stream().map(SchemaFixtures::iri).toList(),
                List.of(properties),
                DisplayHints.none(),
                isAbstract);
    }

    static TypeDefinition concreteType(String localName, PropertyDefinition... properties) {
        return type(localName, false, List.of(), properties);
    }

    static TypeDefinition subtype(String localName, boolean isAbstract, String superType) {
        return type(localName, isAbstract, List.of(superType));
    }

    static AttributeDefinition attribute(String localName, Datatype datatype, boolean facet, boolean searchable) {
        return attribute(localName, datatype, Cardinality.optional(), facet, searchable);
    }

    static AttributeDefinition attribute(
            String localName, Datatype datatype, Cardinality cardinality, boolean facet, boolean searchable) {
        return new AttributeDefinition(
                iri(localName),
                label(localName),
                cardinality,
                facet,
                false,
                searchable,
                false,
                false,
                DisplayHints.none(),
                datatype);
    }

    static RelationshipDefinition relationship(String localName, String targetLocalName) {
        return relationship(localName, targetLocalName, Cardinality.optional());
    }

    static RelationshipDefinition relationship(String localName, String targetLocalName, Cardinality cardinality) {
        return new RelationshipDefinition(
                iri(localName),
                label(localName),
                cardinality,
                false,
                false,
                false,
                false,
                false,
                DisplayHints.none(),
                iri(targetLocalName),
                Optional.empty(),
                false);
    }

    /** A class reference built the way the adapter builds one: id from the IRI, slug, label. */
    static ClassRef classRef(String localName) {
        String iri = iri(localName);
        return new ClassRef(idOf(iri), localName, iri, new LocalizedText(label(localName), "und"));
    }

    static FieldScopeRequest request(
            TenantId tenant, String rootLocalName, VersionPolicy policy, TraversalConfig traversal) {
        return new FieldScopeRequest(
                tenant, classRef(rootLocalName), policy, traversal, Optional.empty(), Optional.empty());
    }

    static FieldScopeRequest request(String rootLocalName) {
        return request(TENANT, rootLocalName, new VersionPolicy.Union(), TraversalConfig.defaults());
    }

    /**
     * A path segment for {@code localName}. Pass a target local name for a relationship, or {@code null}
     * for an attribute.
     */
    static PathSegment segment(String localName, String targetLocalName) {
        String iri = iri(localName);
        return new PathSegment(idOf(iri), iri, localName, targetLocalName == null ? null : classRef(targetLocalName));
    }

    static FieldPath path(PathSegment... segments) {
        return new FieldPath(List.of(segments));
    }
}
