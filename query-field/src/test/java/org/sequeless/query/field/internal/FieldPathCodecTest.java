package org.sequeless.query.field.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.LocalizedText;
import org.sequeless.query.field.api.PathSegment;

class FieldPathCodecTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID ADDRESS_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CITY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    private static ClassRef classRef(String slug) {
        return new ClassRef(UUID.nameUUIDFromBytes(slug.getBytes()), slug, "urn:class:" + slug, label(slug));
    }

    private static LocalizedText label(String value) {
        return new LocalizedText(value, "en");
    }

    private static FieldPath traversingPath() {
        return new FieldPath(List.of(
                new PathSegment(CUSTOMER_ID, "customer", classRef("Customer")),
                new PathSegment(ADDRESS_ID, "address", classRef("Address")),
                new PathSegment(CITY_ID, "city", null)));
    }

    @Test
    void formatIdsJoinsPropertyIdsWithSlash() {
        assertThat(FieldPathCodec.formatIds(traversingPath()))
                .isEqualTo(CUSTOMER_ID + "/" + ADDRESS_ID + "/" + CITY_ID);
    }

    @Test
    void parseIdsSplitsOnSlashAndParsesEachUuid() {
        String idPath = CUSTOMER_ID + "/" + ADDRESS_ID + "/" + CITY_ID;

        assertThat(FieldPathCodec.parseIds(idPath)).containsExactly(CUSTOMER_ID, ADDRESS_ID, CITY_ID);
    }

    @Test
    void formatIdsAndParseIdsRoundTripPreservesIdentity() {
        FieldPath path = traversingPath();

        List<UUID> roundTripped = FieldPathCodec.parseIds(FieldPathCodec.formatIds(path));

        assertThat(roundTripped).isEqualTo(path.asIdPath());
    }

    @Test
    void parseIdsRejectsBlankInput() {
        assertThatThrownBy(() -> FieldPathCodec.parseIds("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void parseIdsRejectsBlankSegment() {
        assertThatThrownBy(() -> FieldPathCodec.parseIds(CUSTOMER_ID + "//" + CITY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank segment");
    }

    @Test
    void parseIdsRejectsMalformedUuidSegment() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> FieldPathCodec.parseIds("not-a-uuid"));
    }

    @Test
    void formatSlugDelegatesToAsSlugPath() {
        FieldPath path = traversingPath();

        assertThat(FieldPathCodec.formatSlug(path)).isEqualTo(path.asSlugPath());
    }

    @Test
    void splitSlugSplitsOnDots() {
        assertThat(FieldPathCodec.splitSlug("customer.address.city")).containsExactly("customer", "address", "city");
    }

    @Test
    void splitSlugRejectsBlankInput() {
        assertThatThrownBy(() -> FieldPathCodec.splitSlug("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void splitSlugRejectsLeadingDot() {
        assertThatThrownBy(() -> FieldPathCodec.splitSlug(".customer.city"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank segment");
    }

    @Test
    void splitSlugRejectsTrailingDot() {
        assertThatThrownBy(() -> FieldPathCodec.splitSlug("customer.city."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank segment");
    }

    @Test
    void splitSlugRejectsDoubledDot() {
        assertThatThrownBy(() -> FieldPathCodec.splitSlug("customer..city"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank segment");
    }

    @Test
    void splitSlugOfFormatSlugRecoversSegmentSlugs() {
        FieldPath path = traversingPath();

        List<String> recovered = FieldPathCodec.splitSlug(FieldPathCodec.formatSlug(path));

        assertThat(recovered).containsExactly("customer", "address", "city");
    }
}
