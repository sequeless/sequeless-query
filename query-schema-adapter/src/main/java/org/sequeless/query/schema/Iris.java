package org.sequeless.query.schema;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.sequeless.query.field.api.LocalizedText;

/**
 * Identity and naming rules shared by every mapping in this module.
 *
 * <p>The IRI is the identity the Query side keys on. The id of a property or a
 * class is a name-based UUID of its IRI, so the same IRI yields the same id in every activation.
 */
final class Iris {

    /** The Schema carries no language tags, so every label is tagged BCP 47 "undetermined". */
    private static final String UNDETERMINED = "und";

    private Iris() {}

    /**
     * @param iri the property's or class's IRI; never {@code null}
     * @return the name-based UUID of {@code iri}, stable across activations
     */
    static UUID id(String iri) {
        return UUID.nameUUIDFromBytes(iri.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The local name: the part of {@code iri} after its last {@code '#'}, {@code '/'} or {@code ':'}.
     * When that part is empty, the whole IRI is the slug, so a slug is never blank. The cut at the last
     * {@code '#'}, {@code '/'} or {@code ':'} means two IRIs can share a slug, so a slug is
     * presentation-only; identity is the IRI.
     *
     * @param iri the IRI; never {@code null} or blank
     * @return the slug of {@code iri}; never blank
     */
    static String slug(String iri) {
        int cut = Math.max(iri.lastIndexOf('#'), Math.max(iri.lastIndexOf('/'), iri.lastIndexOf(':')));
        String localName = iri.substring(cut + 1);
        return localName.isEmpty() ? iri : localName;
    }

    /**
     * @param text the human-facing text; never {@code null}
     * @return {@code text} tagged with the undetermined language
     */
    static LocalizedText label(String text) {
        return new LocalizedText(text, UNDETERMINED);
    }
}
