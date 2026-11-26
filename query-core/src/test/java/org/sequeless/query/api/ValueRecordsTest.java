package org.sequeless.query.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sequeless.query.testsupport.QueryFixtures.path;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.query.field.api.FieldPath;

class ValueRecordsTest {

    private static final ObjectId ID = new ObjectId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final Audit AUDIT = new Audit(Instant.EPOCH, "creator", Instant.EPOCH, "updater");
    private static final Map<PropertyRef, Value> NO_PROPERTIES = Map.of();

    private static ObjectView view(long version) {
        return new ObjectView(
                ID,
                new TypeRef("urn:class:Invoice"),
                new TenantId("tenant-1"),
                version,
                Optional.empty(),
                NO_PROPERTIES,
                AUDIT,
                false);
    }

    @Test
    void typeRefRejectsNullIri() {
        assertThatNullPointerException().isThrownBy(() -> new TypeRef(null));
    }

    @Test
    void typeRefRejectsBlankIri() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TypeRef("  ")).withMessage("iri must not be blank");
    }

    @Test
    void propertyRefRejectsNullIri() {
        assertThatNullPointerException().isThrownBy(() -> new PropertyRef(null));
    }

    @Test
    void propertyRefRejectsBlankIri() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PropertyRef(""));
    }

    @Test
    void objectViewRejectsVersionZero() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> view(0))
                .withMessage("ObjectView version must be at least 1");
    }

    @Test
    void objectViewAcceptsVersionOne() {
        assertThat(view(1).version()).isEqualTo(1L);
    }

    @Test
    void objectViewRejectsNullReferenceComponents() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ObjectView(
                        null,
                        new TypeRef("urn:class:Invoice"),
                        new TenantId("tenant-1"),
                        1,
                        Optional.empty(),
                        NO_PROPERTIES,
                        AUDIT,
                        false));
        assertThatNullPointerException()
                .isThrownBy(() -> new ObjectView(
                        ID, null, new TenantId("tenant-1"), 1, Optional.empty(), NO_PROPERTIES, AUDIT, false));
        assertThatNullPointerException()
                .isThrownBy(() -> new ObjectView(
                        ID, new TypeRef("urn:class:Invoice"), null, 1, Optional.empty(), NO_PROPERTIES, AUDIT, false));
        assertThatNullPointerException()
                .isThrownBy(() -> new ObjectView(
                        ID,
                        new TypeRef("urn:class:Invoice"),
                        new TenantId("tenant-1"),
                        1,
                        null,
                        NO_PROPERTIES,
                        AUDIT,
                        false));
        assertThatNullPointerException()
                .isThrownBy(() -> new ObjectView(
                        ID,
                        new TypeRef("urn:class:Invoice"),
                        new TenantId("tenant-1"),
                        1,
                        Optional.empty(),
                        NO_PROPERTIES,
                        null,
                        false));
    }

    @Test
    void objectViewCopiesItsPropertiesAsUnmodifiable() {
        Map<PropertyRef, Value> mutable = new HashMap<>();
        mutable.put(new PropertyRef("urn:prop:status"), Value.text("open"));
        ObjectView view = new ObjectView(
                ID,
                new TypeRef("urn:class:Invoice"),
                new TenantId("tenant-1"),
                1,
                Optional.empty(),
                mutable,
                AUDIT,
                false);
        mutable.clear();
        assertThat(view.properties()).containsOnlyKeys(new PropertyRef("urn:prop:status"));
        assertThatThrownBy(() -> view.properties().put(new PropertyRef("urn:prop:x"), Value.text("y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void auditRejectsNullComponents() {
        assertThatNullPointerException().isThrownBy(() -> new Audit(null, "c", Instant.EPOCH, "u"));
        assertThatNullPointerException().isThrownBy(() -> new Audit(Instant.EPOCH, null, Instant.EPOCH, "u"));
        assertThatNullPointerException().isThrownBy(() -> new Audit(Instant.EPOCH, "c", null, "u"));
        assertThatNullPointerException().isThrownBy(() -> new Audit(Instant.EPOCH, "c", Instant.EPOCH, null));
    }

    @Test
    void pageRejectsNegativeNumber() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Page(-1, 10))
                .withMessage("Page number must not be negative");
    }

    @Test
    void pageRejectsSizeZero() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Page(0, 0))
                .withMessage("Page size must be between 1 and 500");
    }

    @Test
    void pageRejectsSizeAboveFiveHundred() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Page(0, 501))
                .withMessage("Page size must be between 1 and 500");
    }

    @Test
    void pageAcceptsTheSizeBounds() {
        assertThat(new Page(0, 1).size()).isEqualTo(1);
        assertThat(new Page(3, 500).size()).isEqualTo(500);
    }

    @Test
    void sortRejectsNullComponents() {
        FieldPath path = path("urn:ont:status");
        assertThatNullPointerException().isThrownBy(() -> new Sort(null, Direction.ASC));
        assertThatNullPointerException().isThrownBy(() -> new Sort(path, null));
    }

    @Test
    void readRequestRejectsNullId() {
        assertThatNullPointerException().isThrownBy(() -> new ReadRequest(null));
    }

    @Test
    void facetBucketRejectsNegativeCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FacetBucket("open", -1))
                .withMessage("FacetBucket count must not be negative");
    }

    @Test
    void facetBucketRejectsNullValue() {
        assertThatNullPointerException().isThrownBy(() -> new FacetBucket(null, 1));
    }

    @Test
    void browseQueryRejectsNullFilterAndPage() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BrowseQuery(null, Optional.empty(), List.of(), new Page(0, 10), List.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> new BrowseQuery(Optional.empty(), Optional.empty(), List.of(), null, List.of()));
    }

    @Test
    void browseQueryCopiesItsLists() {
        List<Sort> sorts = new ArrayList<>();
        sorts.add(new Sort(path("urn:ont:status"), Direction.DESC));
        BrowseQuery query = new BrowseQuery(Optional.empty(), Optional.of("text"), sorts, new Page(0, 10), List.of());
        sorts.clear();
        assertThat(query.sorts()).hasSize(1);
        assertThat(query.text()).contains("text");
    }

    @Test
    void browseResultRejectsNegativeTotal() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BrowseResult(List.of(), -1, Map.of()))
                .withMessage("BrowseResult total must not be negative");
    }

    @Test
    void browseResultKeepsFacetOrderAndIsUnmodifiable() {
        FieldPath status = path("urn:ont:status");
        FieldPath region = path("urn:ont:region");
        LinkedHashMap<FieldPath, List<FacetBucket>> facets = new LinkedHashMap<>();
        facets.put(region, List.of(new FacetBucket("eu", 2)));
        facets.put(status, List.of(new FacetBucket("open", 1)));
        BrowseResult result = new BrowseResult(List.of(), 0, facets);
        assertThat(result.facets().keySet()).containsExactly(region, status);
        assertThatThrownBy(() -> result.facets().put(path("urn:ont:x"), List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void indexPreparationResultEmptyHasNoFields() {
        assertThat(IndexPreparationResult.EMPTY.newlyRegistered()).isEmpty();
    }

    @Test
    void indexPreparationResultRejectsNullList() {
        assertThatNullPointerException().isThrownBy(() -> new IndexPreparationResult(null));
    }

    @Test
    void indexPreparationResultCopiesItsList() {
        ArrayList<FieldPath> paths = new ArrayList<>();
        paths.add(path("urn:ont:status"));
        IndexPreparationResult result = new IndexPreparationResult(paths);
        paths.clear();
        assertThat(result.newlyRegistered()).hasSize(1);
    }
}
