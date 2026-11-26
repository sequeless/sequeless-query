package org.sequeless.query.field.testfixtures;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.Cardinality;
import org.sequeless.query.field.api.ConceptSchemeRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.InferenceSupport;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.OperatorRestriction;

/**
 * One version's definition of one scalar property on a {@link ClassFixture}.
 *
 * <p><b>{@link #definedInVersion()} is singular</b>, unlike {@link Field#definedInVersions()} on
 * the published contract type, and that is deliberate: this row models a single version's
 * definition, matching {@code FieldMerger.merge}'s own input contract exactly — every {@code
 * Field} it is handed must carry exactly one entry in {@code definedInVersions}. A class's whole
 * {@code List<PropertyFixture>} therefore converts straight into {@code FieldMerger.merge}'s input
 * with no reshaping: one fixture row becomes one per-version {@code Field}.
 *
 * <p>Two rows sharing the same {@link #id()} but a different {@link #slug()}, {@link #type()}, or {@link
 * #definedInVersion()} is exactly how a fixture expresses a slug rename or a cross-version type conflict — the merge
 * then reconciles them by identity.
 *
 * @param id the durable identity of this property; never {@code null}
 * @param iri the property's IRI, stable across a slug rename; never {@code null} or blank, and never containing '|'
 * @param slug the current short name of this property in this version; never {@code null} or
 *     blank
 * @param type this property's type in this version; never {@code null}
 * @param cardinality this property's cardinality in this version; never {@code null}
 * @param capabilities this property's capabilities in this version; never {@code null}
 * @param nativeDatatypeIri this property's native datatype IRI in this version; may be {@code
 *     null}
 * @param conceptScheme the concept scheme this property draws from in this version; never {@code
 *     null} as a wrapper, use {@link Optional#empty()} when there is none
 * @param restriction this property's operator restriction in this version; never {@code null}
 * @param inference this property's inference support in this version; never {@code null}
 * @param definedInVersion the single ontology version this row defines the property for; never
 *     {@code null}
 */
public record PropertyFixture(
        UUID id,
        String iri,
        String slug,
        FieldType type,
        Cardinality cardinality,
        Capabilities capabilities,
        String nativeDatatypeIri,
        Optional<ConceptSchemeRef> conceptScheme,
        OperatorRestriction restriction,
        InferenceSupport inference,
        OntologyVersionRef definedInVersion) {

    /** Validates every required field is present. */
    public PropertyFixture {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(iri, "iri must not be null");
        if (iri.isBlank()) {
            throw new IllegalArgumentException("iri must not be blank");
        }
        if (iri.contains("|")) {
            throw new IllegalArgumentException("iri must not contain '|'");
        }
        Objects.requireNonNull(slug, "slug must not be null");
        if (slug.isBlank()) {
            throw new IllegalArgumentException("slug must not be blank");
        }
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(cardinality, "cardinality must not be null");
        Objects.requireNonNull(capabilities, "capabilities must not be null");
        // nativeDatatypeIri may be null: not every ontology exposes one.
        Objects.requireNonNull(conceptScheme, "conceptScheme must not be null (use Optional.empty())");
        Objects.requireNonNull(restriction, "restriction must not be null");
        Objects.requireNonNull(inference, "inference must not be null");
        Objects.requireNonNull(definedInVersion, "definedInVersion must not be null");
    }
}
