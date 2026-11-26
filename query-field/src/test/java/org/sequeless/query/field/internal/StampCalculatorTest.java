package org.sequeless.query.field.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.LocalizedText;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.StampMode;
import org.sequeless.query.field.api.TraversalConfig;
import org.sequeless.query.field.api.VersionPolicy;

class StampCalculatorTest {

    private static final UUID ROOT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ROOT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final OntologyVersionRef V1 = new OntologyVersionRef(1);
    private static final OntologyVersionRef V2 = new OntologyVersionRef(2);
    private static final OntologyVersionRef V3 = new OntologyVersionRef(3);
    private static final UUID SUBTYPE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    /** The root alone: the concrete types of a root with no concrete subtypes. */
    private static final Set<ClassRef> ROOT_ONLY = Set.of(classRef(ROOT_ID, "Invoice"));
    /** The root and one concrete subtype, which must stamp differently from {@link #ROOT_ONLY}. */
    private static final Set<ClassRef> ROOT_AND_SUBTYPE =
            Set.of(classRef(ROOT_ID, "Invoice"), classRef(SUBTYPE_ID, "CreditNote"));

    private static ClassRef classRef(UUID id, String slug) {
        return new ClassRef(id, slug, "urn:class:" + slug, new LocalizedText(slug, "en"));
    }

    private static FieldScopeRequest request(
            String tenantId,
            UUID rootId,
            VersionPolicy versionPolicy,
            TraversalConfig traversal,
            Optional<Principal> principal,
            Optional<Locale> locale) {
        return new FieldScopeRequest(
                new TenantId(tenantId), classRef(rootId, "Invoice"), versionPolicy, traversal, principal, locale);
    }

    private static FieldScopeRequest fixedInputRequest() {
        return request(
                "acme",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.CONTENT_HASH, false),
                Optional.of(new Principal("u-42", Set.of("analyst", "admin"))),
                Optional.empty());
    }

    @Test
    void versionOnlySortsAndJoinsVersions() {
        FieldScopeRequest request = request(
                "acme",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.VERSION_ONLY, false),
                Optional.empty(),
                Optional.empty());

        String stamp = StampCalculator.stamp(request, Set.of(V2, V1), Set.of());

        assertThat(stamp).isEqualTo("1,2");
    }

    @Test
    void versionOnlySortsVersionsNumericallyNotAsStrings() {
        FieldScopeRequest request = request(
                "acme",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.VERSION_ONLY, false),
                Optional.empty(),
                Optional.empty());

        String stamp = StampCalculator.stamp(request, Set.of(new OntologyVersionRef(10), V2, V1), Set.of());

        assertThat(stamp).isEqualTo("1,2,10");
    }

    @Test
    void versionOnlyIsOrderIndependent() {
        FieldScopeRequest request = request(
                "acme",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.VERSION_ONLY, false),
                Optional.empty(),
                Optional.empty());

        String forward = StampCalculator.stamp(request, Set.of(V1, V2, V3), Set.of());
        String backward = StampCalculator.stamp(request, Set.of(V3, V2, V1), Set.of());

        assertThat(forward).isEqualTo(backward);
    }

    @Test
    void versionOnlyProducesUnversionedSentinelForEmptyVersions() {
        FieldScopeRequest request = request(
                "acme",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.VERSION_ONLY, false),
                Optional.empty(),
                Optional.empty());

        String stamp = StampCalculator.stamp(request, Set.of(), Set.of());

        assertThat(stamp).isEqualTo("unversioned");
    }

    @Test
    void contentHashMatchesKnownDigestForFixedInput() {
        String stamp = StampCalculator.stamp(fixedInputRequest(), Set.of(V1, V2), ROOT_ONLY);

        // The digest changed for the concreteTypes addition (Q15).
        assertThat(stamp).isEqualTo("sha256:3b1bcfa37031c255f2c646d9ab27d5ff6d9074e7d13c2716f1244ece9e70b788");
    }

    @Test
    void contentHashIsOrderIndependentForVersionsSet() {
        String forward = StampCalculator.stamp(fixedInputRequest(), Set.of(V1, V2), ROOT_ONLY);
        String backward = StampCalculator.stamp(fixedInputRequest(), Set.of(V2, V1), ROOT_ONLY);

        assertThat(forward).isEqualTo(backward);
    }

    @Test
    void contentHashIsOrderIndependentForVersionsTenAndNine() {
        Set<OntologyVersionRef> tenThenNine =
                new LinkedHashSet<>(List.of(new OntologyVersionRef(10), new OntologyVersionRef(9)));
        Set<OntologyVersionRef> nineThenTen =
                new LinkedHashSet<>(List.of(new OntologyVersionRef(9), new OntologyVersionRef(10)));

        String forward = StampCalculator.stamp(fixedInputRequest(), tenThenNine, ROOT_ONLY);
        String backward = StampCalculator.stamp(fixedInputRequest(), nineThenTen, ROOT_ONLY);

        assertThat(forward).isEqualTo(backward);
    }

    @Test
    void contentHashIsOrderIndependentForPrincipalRoles() {
        FieldScopeRequest forwardRequest = request(
                "acme",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.CONTENT_HASH, false),
                Optional.of(new Principal("u-42", Set.of("analyst", "admin"))),
                Optional.empty());
        FieldScopeRequest backwardRequest = request(
                "acme",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.CONTENT_HASH, false),
                Optional.of(new Principal("u-42", Set.of("admin", "analyst"))),
                Optional.empty());

        String forward = StampCalculator.stamp(forwardRequest, Set.of(V1), ROOT_ONLY);
        String backward = StampCalculator.stamp(backwardRequest, Set.of(V1), ROOT_ONLY);

        assertThat(forward).isEqualTo(backward);
    }

    @Test
    void contentHashDiffersWhenTenantDiffers() {
        FieldScopeRequest acme = fixedInputRequest();
        FieldScopeRequest other = request(
                "globex",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.CONTENT_HASH, false),
                Optional.of(new Principal("u-42", Set.of("analyst", "admin"))),
                Optional.empty());

        OntologyVersionRef versions = V1;
        assertThat(StampCalculator.stamp(acme, Set.of(versions), ROOT_ONLY))
                .isNotEqualTo(StampCalculator.stamp(other, Set.of(versions), ROOT_ONLY));
    }

    @Test
    void contentHashDiffersWhenRootClassDiffers() {
        FieldScopeRequest first = fixedInputRequest();
        FieldScopeRequest second = request(
                "acme",
                OTHER_ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.CONTENT_HASH, false),
                Optional.of(new Principal("u-42", Set.of("analyst", "admin"))),
                Optional.empty());

        OntologyVersionRef versions = V1;
        assertThat(StampCalculator.stamp(first, Set.of(versions), ROOT_ONLY))
                .isNotEqualTo(StampCalculator.stamp(second, Set.of(versions), ROOT_ONLY));
    }

    @Test
    void contentHashDiffersWhenVersionPolicyDiffers() {
        FieldScopeRequest union = fixedInputRequest();
        FieldScopeRequest pinned = request(
                "acme",
                ROOT_ID,
                new VersionPolicy.Pinned(V1),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.CONTENT_HASH, false),
                Optional.of(new Principal("u-42", Set.of("analyst", "admin"))),
                Optional.empty());

        OntologyVersionRef versions = V1;
        assertThat(StampCalculator.stamp(union, Set.of(versions), ROOT_ONLY))
                .isNotEqualTo(StampCalculator.stamp(pinned, Set.of(versions), ROOT_ONLY));
    }

    @Test
    void contentHashDiffersWhenMaxDepthDiffers() {
        FieldScopeRequest depthThree = fixedInputRequest();
        FieldScopeRequest depthFive = request(
                "acme",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(5), true, StampMode.CONTENT_HASH, false),
                Optional.of(new Principal("u-42", Set.of("analyst", "admin"))),
                Optional.empty());

        OntologyVersionRef versions = V1;
        assertThat(StampCalculator.stamp(depthThree, Set.of(versions), ROOT_ONLY))
                .isNotEqualTo(StampCalculator.stamp(depthFive, Set.of(versions), ROOT_ONLY));
    }

    @Test
    void contentHashDiffersWhenPrincipalAbsentVsPresent() {
        FieldScopeRequest withPrincipal = fixedInputRequest();
        FieldScopeRequest withoutPrincipal = request(
                "acme",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.CONTENT_HASH, false),
                Optional.empty(),
                Optional.empty());

        OntologyVersionRef versions = V1;
        assertThat(StampCalculator.stamp(withPrincipal, Set.of(versions), ROOT_ONLY))
                .isNotEqualTo(StampCalculator.stamp(withoutPrincipal, Set.of(versions), ROOT_ONLY));
    }

    @Test
    void contentHashIgnoresLocale() {
        FieldScopeRequest english = request(
                "acme",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.CONTENT_HASH, false),
                Optional.of(new Principal("u-42", Set.of("analyst", "admin"))),
                Optional.of(Locale.ENGLISH));
        FieldScopeRequest french = request(
                "acme",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.CONTENT_HASH, false),
                Optional.of(new Principal("u-42", Set.of("analyst", "admin"))),
                Optional.of(Locale.FRENCH));

        OntologyVersionRef versions = V1;
        assertThat(StampCalculator.stamp(english, Set.of(versions), ROOT_ONLY))
                .isEqualTo(StampCalculator.stamp(french, Set.of(versions), ROOT_ONLY));
    }

    @Test
    void contentHashIsStableAcrossRepeatedCalls() {
        FieldScopeRequest request = fixedInputRequest();
        Set<OntologyVersionRef> versions = Set.of(V1, V2);

        String first = StampCalculator.stamp(request, versions, ROOT_ONLY);
        String second = StampCalculator.stamp(request, versions, ROOT_ONLY);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void contentHashIsInjectiveAcrossFieldBoundaries() {
        // Without a length prefix on every field, tenant "a" followed by role "bc" would render the
        // same characters as tenant "ab" followed by role "c" once concatenated naively. The
        // netstring framework must keep these apart.
        FieldScopeRequest tenantA = request(
                "a",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.CONTENT_HASH, false),
                Optional.of(new Principal("bc", Set.of())),
                Optional.empty());
        FieldScopeRequest tenantAb = request(
                "ab",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.CONTENT_HASH, false),
                Optional.of(new Principal("c", Set.of())),
                Optional.empty());

        assertThat(StampCalculator.stamp(tenantA, Set.of(), ROOT_ONLY))
                .isNotEqualTo(StampCalculator.stamp(tenantAb, Set.of(), ROOT_ONLY));
    }

    @Test
    void versionOnlyAppendsConcreteTypesSortedByIriAfterTheVersions() {
        FieldScopeRequest request = request(
                "acme",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.VERSION_ONLY, false),
                Optional.empty(),
                Optional.empty());
        Set<ClassRef> insertedRootFirst =
                new LinkedHashSet<>(List.of(classRef(ROOT_ID, "Invoice"), classRef(SUBTYPE_ID, "CreditNote")));

        String stamp = StampCalculator.stamp(request, Set.of(V2, V1), insertedRootFirst);

        assertThat(stamp).isEqualTo("1,2|concreteTypes:urn:class:CreditNote,urn:class:Invoice");
    }

    @Test
    void versionOnlyDiffersWhenConcreteTypesDiffer() {
        FieldScopeRequest request = request(
                "acme",
                ROOT_ID,
                new VersionPolicy.Union(),
                new TraversalConfig(OptionalInt.of(3), true, StampMode.VERSION_ONLY, false),
                Optional.empty(),
                Optional.empty());

        assertThat(StampCalculator.stamp(request, Set.of(V1), ROOT_ONLY))
                .isNotEqualTo(StampCalculator.stamp(request, Set.of(V1), ROOT_AND_SUBTYPE));
    }

    @Test
    void contentHashDiffersWhenConcreteTypesDiffer() {
        assertThat(StampCalculator.stamp(fixedInputRequest(), Set.of(V1), ROOT_ONLY))
                .isNotEqualTo(StampCalculator.stamp(fixedInputRequest(), Set.of(V1), ROOT_AND_SUBTYPE));
    }

    @Test
    void contentHashIsOrderIndependentForConcreteTypes() {
        Set<ClassRef> forward =
                new LinkedHashSet<>(List.of(classRef(ROOT_ID, "Invoice"), classRef(SUBTYPE_ID, "CreditNote")));
        Set<ClassRef> backward =
                new LinkedHashSet<>(List.of(classRef(SUBTYPE_ID, "CreditNote"), classRef(ROOT_ID, "Invoice")));

        assertThat(StampCalculator.stamp(fixedInputRequest(), Set.of(V1), forward))
                .isEqualTo(StampCalculator.stamp(fixedInputRequest(), Set.of(V1), backward));
    }

    @Test
    void contentHashCountsAnIriOnceWhenTwoClassRefsShareItAndDifferOnlyInLabel() {
        ClassRef english = new ClassRef(ROOT_ID, "Invoice", "urn:class:Invoice", new LocalizedText("Invoice", "en"));
        ClassRef french = new ClassRef(ROOT_ID, "Invoice", "urn:class:Invoice", new LocalizedText("Facture", "fr"));

        assertThat(StampCalculator.stamp(fixedInputRequest(), Set.of(V1), Set.of(english, french)))
                .isEqualTo(StampCalculator.stamp(fixedInputRequest(), Set.of(V1), Set.of(english)));
    }
}
