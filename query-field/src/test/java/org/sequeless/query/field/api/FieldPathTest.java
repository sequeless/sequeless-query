package org.sequeless.query.field.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FieldPathTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final String CUSTOMER_IRI = "urn:ont:property:Invoice.customer";
    private static final UUID ADDRESS_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final String ADDRESS_IRI = "urn:ont:property:Customer.address";
    private static final UUID CITY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final String CITY_IRI = "urn:ont:property:Customer.city";

    private static ClassRef classRef(String slug) {
        return new ClassRef(UUID.nameUUIDFromBytes(slug.getBytes()), slug, "urn:class:" + slug, label(slug));
    }

    private static LocalizedText label(String value) {
        return new LocalizedText(value, "en");
    }

    @Test
    void classRefRejectsBlankSlug() {
        assertThatThrownBy(() -> new ClassRef(CUSTOMER_ID, "  ", "urn:class:Customer", label("Customer")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug");
    }

    @Test
    void classRefRejectsNullId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new ClassRef(null, "Customer", "urn:class:Customer", label("Customer")));
    }

    @Test
    void pathSegmentAllowsNullTargetClassForTerminalSegment() {
        PathSegment terminal = new PathSegment(CITY_ID, CITY_IRI, "city", null);

        assertThat(terminal.targetClass()).isNull();
        assertThat(terminal.slug()).isEqualTo("city");
    }

    @Test
    void facetPathRejectsEmptySegments() {
        assertThatThrownBy(() -> new FieldPath(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void facetPathRejectsNonTerminalSegmentWithoutTargetClass() {
        // 'customer' is followed by 'city', so it must lead somewhere — but it has no targetClass.
        List<PathSegment> segments = List.of(
                new PathSegment(CUSTOMER_ID, CUSTOMER_IRI, "customer", null),
                new PathSegment(CITY_ID, CITY_IRI, "city", null));

        assertThatThrownBy(() -> new FieldPath(segments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetClass");
    }

    @Test
    void asSlugPathJoinsSegmentSlugsWithDots() {
        FieldPath path = new FieldPath(List.of(
                new PathSegment(CUSTOMER_ID, CUSTOMER_IRI, "customer", classRef("Customer")),
                new PathSegment(ADDRESS_ID, ADDRESS_IRI, "address", classRef("Address")),
                new PathSegment(CITY_ID, CITY_IRI, "city", null)));

        assertThat(path.asSlugPath()).isEqualTo("customer.address.city");
    }

    @Test
    void asIdPathReturnsPropertyIdsInOrder() {
        FieldPath path = new FieldPath(List.of(
                new PathSegment(CUSTOMER_ID, CUSTOMER_IRI, "customer", classRef("Customer")),
                new PathSegment(CITY_ID, CITY_IRI, "city", null)));

        assertThat(path.asIdPath()).containsExactly(CUSTOMER_ID, CITY_ID);
    }

    @Test
    void asIdPathIsImmutable() {
        FieldPath path = new FieldPath(List.of(new PathSegment(CITY_ID, CITY_IRI, "city", null)));
        List<UUID> ids = path.asIdPath();

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> ids.add(CUSTOMER_ID));
    }

    @Test
    void isTraversingIsFalseForSingleSegmentPath() {
        FieldPath path = new FieldPath(List.of(new PathSegment(CITY_ID, CITY_IRI, "city", null)));

        assertThat(path.isTraversing()).isFalse();
    }

    @Test
    void isTraversingIsTrueForMultiSegmentPath() {
        FieldPath path = new FieldPath(List.of(
                new PathSegment(CUSTOMER_ID, CUSTOMER_IRI, "customer", classRef("Customer")),
                new PathSegment(CITY_ID, CITY_IRI, "city", null)));

        assertThat(path.isTraversing()).isTrue();
    }

    @Test
    void segmentIrisFollowPathOrder() {
        FieldPath path = new FieldPath(List.of(
                new PathSegment(CUSTOMER_ID, CUSTOMER_IRI, "customer", classRef("Customer")),
                new PathSegment(CITY_ID, CITY_IRI, "city", null)));

        List<String> iris = path.segments().stream().map(PathSegment::iri).toList();

        assertThat(iris).containsExactly(CUSTOMER_IRI, CITY_IRI);
    }

    @Test
    void depthEqualsSegmentCount() {
        FieldPath path = new FieldPath(List.of(
                new PathSegment(CUSTOMER_ID, CUSTOMER_IRI, "customer", classRef("Customer")),
                new PathSegment(ADDRESS_ID, ADDRESS_IRI, "address", classRef("Address")),
                new PathSegment(CITY_ID, CITY_IRI, "city", null)));

        assertThat(path.depth()).isEqualTo(3);
    }

    @Test
    void facetPathDefensiveCopyIsolatesFromSourceMutation() {
        // A mutable source is essential here: List.copyOf returns its argument unchanged when the
        // argument is already immutable, so passing List.of(...) would prove nothing.
        List<PathSegment> source = new ArrayList<>(List.of(new PathSegment(CITY_ID, CITY_IRI, "city", null)));
        FieldPath path = new FieldPath(source);

        source.add(new PathSegment(CUSTOMER_ID, CUSTOMER_IRI, "customer", classRef("Customer")));

        assertThat(path.segments()).hasSize(1);
        assertThat(path.asSlugPath()).isEqualTo("city");
    }

    @Test
    void facetTypeHasFifteenConstants() {
        assertThat(FieldType.values()).hasSize(15);
        assertThat(FieldType.values()).contains(FieldType.UNKNOWN);
    }

    @Test
    void facetErrorCodeHasTwelveConstants() {
        assertThat(FieldErrorCode.values()).hasSize(12);
    }
}
