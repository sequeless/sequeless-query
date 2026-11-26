package org.sequeless.query.field.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.Cardinality;
import org.sequeless.query.field.api.ConceptSchemeRef;
import org.sequeless.query.field.api.ConflictingDefinition;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldConflict;
import org.sequeless.query.field.api.FieldErrorCode;
import org.sequeless.query.field.api.FieldResolutionException;
import org.sequeless.query.field.api.FieldType;
import org.sequeless.query.field.api.InferenceSupport;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.OperatorRestriction;
import org.sequeless.query.field.api.VersionPolicy;

/**
 * Reconciles one {@link Field} definition per ontology version into the single list a {@code FieldScopeRequest}
 * resolves to: the version policy decides which definitions apply, and a per-field disagreement between versions is
 * resolved once a policy decides two definitions belong to the same facet.
 *
 * <p><b>Grouping key.</b> Definitions are grouped by {@link Field#path()}{@code
 * .asIdPath()} — the ordered list of property {@link UUID}s — never by {@link
 * Field#path()}{@code .asSlugPath()}. This is precisely what stops a slug rename from splitting
 * one facet into two: renaming {@code custName} to {@code customerName} between v1 and v2 keeps
 * the same property UUID, so the id path is unchanged even though the slug path differs. Grouping
 * by slug would wrongly treat those as two unrelated facets instead of one renamed facet. {@code
 * List<UUID>} is a valid {@link Map} key because {@link List} equality and hashing are structural,
 * not identity-based.
 *
 * <p><b>Version order.</b> {@code versionOrder} must run oldest to newest. "Newest version wins"
 * is otherwise undefined: the caller supplies the merge order explicitly as a list, and this class
 * does not infer chronology from the values.
 *
 * <p>This class never mutates or re-derives a definition's own {@link Field#path()} identity —
 * only which whole {@code path} object, label, description, and so on end up on the merged output.
 */
public final class FieldMerger {

    private FieldMerger() {}

    /**
     * Merges one {@link Field} definition per ontology version into the list a {@code
     * FieldScopeRequest} resolves to, per {@code policy}.
     *
     * <p>Every element of {@code perVersionDefinitions} must carry exactly one entry in {@link
     * Field#definedInVersions()} — the caller is expected to hand this method raw, one-version-at-a-
     * time definitions (e.g. straight from a {@code FieldSource} per version), not already-merged
     * output. Violating that, or any of the other preconditions below, is a caller-wiring bug and is
     * reported as {@link IllegalArgumentException}, not a domain failure:
     *
     * <ul>
     *   <li>every definition's single version must be a member of {@code versionOrder};
     *   <li>no two definitions that group to the same property (same {@link Field#path()}{@code
     *       .asIdPath()}) may name the same version — that would mean the same property was
     *       supplied twice for one version, which this method cannot disambiguate.
     * </ul>
     *
     * <p>{@link VersionPolicy.Pinned}'s unknown-version case is deliberately not one of the above:
     * that version comes from the request the caller is resolving, not from how the caller wired
     * this method's inputs, so it is reported as {@link FieldResolutionException} with {@link
     * FieldErrorCode#VERSION_NOT_FOUND} instead — the one domain failure this method can raise. See
     * the {@link VersionPolicy.Pinned} case below.
     *
     * @param perVersionDefinitions one {@link Field} per ontology version that defines it; never
     *     {@code null}
     * @param policy how to reconcile the per-version definitions; never {@code null}
     * @param versionOrder every ontology version, oldest first; never {@code null}
     * @return the merged facets, sorted ascending by {@link Field#path()}{@code .asSlugPath()} (see
     *     {@link #merge(List, VersionPolicy, List)}'s ordering note below)
     * @throws IllegalArgumentException if a precondition above is violated
     * @throws FieldResolutionException with {@link FieldErrorCode#VERSION_NOT_FOUND} if {@code
     *     policy} is a {@link VersionPolicy.Pinned} naming a version absent from {@code
     *     versionOrder}, or with {@link FieldErrorCode#EMPTY_INTERSECTION} if {@code policy} is
     *     {@link VersionPolicy.Intersection} and no facet is defined in every version present in
     *     {@code perVersionDefinitions}
     */
    public static List<Field> merge(
            List<Field> perVersionDefinitions, VersionPolicy policy, List<OntologyVersionRef> versionOrder) {
        Objects.requireNonNull(perVersionDefinitions, "perVersionDefinitions must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(versionOrder, "versionOrder must not be null");

        Map<List<UUID>, List<Field>> groups = groupByIdPath(perVersionDefinitions, versionOrder);

        List<Field> merged = switch (policy) {
            case VersionPolicy.Union ignored -> mergeUnion(groups, versionOrder);
            case VersionPolicy.Pinned pinned -> mergePinned(groups, pinned, versionOrder);
            case VersionPolicy.Intersection ignored -> mergeIntersection(groups, perVersionDefinitions, versionOrder);
        };

        // A FieldSource can return this list as-is and get a stable, human-readable order for free, which is the point
        // of putting this logic in the contract rather than duplicating it in each FieldSource implementation. Merged
        // facets are presented in this order.
        return merged.stream()
                .sorted(Comparator.comparing(facet -> facet.path().asSlugPath()))
                .toList();
    }

    /**
     * Groups the raw per-version definitions by property identity ({@link Field#path()}{@code
     * .asIdPath()}) while validating the preconditions documented on {@link #merge(List,
     * VersionPolicy, List)}. Validation and grouping happen in the same pass because both need to
     * walk every definition exactly once, and grouping cannot proceed for a definition that fails
     * validation.
     */
    private static Map<List<UUID>, List<Field>> groupByIdPath(
            List<Field> facets, List<OntologyVersionRef> versionOrder) {
        Map<List<UUID>, List<Field>> groups = new LinkedHashMap<>();
        Map<List<UUID>, Set<OntologyVersionRef>> versionsSeenPerGroup = new LinkedHashMap<>();
        for (Field facet : facets) {
            if (facet.definedInVersions().size() != 1) {
                throw new IllegalArgumentException(
                        ("input facet at path '%s' must define exactly one version in definedInVersions, "
                                        + "but defines %d")
                                .formatted(
                                        facet.path().asSlugPath(),
                                        facet.definedInVersions().size()));
            }
            OntologyVersionRef version = soleVersion(facet);
            if (!versionOrder.contains(version)) {
                throw new IllegalArgumentException(
                        "input facet at path '%s' defines version '%s', which is not present in versionOrder"
                                .formatted(facet.path().asSlugPath(), version.value()));
            }
            List<UUID> key = facet.path().asIdPath();
            Set<OntologyVersionRef> versionsSeen =
                    versionsSeenPerGroup.computeIfAbsent(key, ignored -> new HashSet<>());
            if (!versionsSeen.add(version)) {
                throw new IllegalArgumentException("multiple input facets for property path '%s' define version '%s'"
                        .formatted(facet.path().asSlugPath(), version.value()));
            }
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(facet);
        }
        return groups;
    }

    /**
     * {@link VersionPolicy.Union}: every group becomes one merged facet, per {@link
     * #mergeGroup(List, List)}.
     */
    private static List<Field> mergeUnion(Map<List<UUID>, List<Field>> groups, List<OntologyVersionRef> versionOrder) {
        List<Field> result = new ArrayList<>();
        for (List<Field> members : groups.values()) {
            result.add(mergeGroup(members, versionOrder));
        }
        return result;
    }

    /**
     * {@link VersionPolicy.Pinned}: select, never merge. For each group with a member defined in
     * the pinned version, that member's {@link Field} is emitted unchanged — no field is recomputed
     * and no {@link FieldConflict} is ever attached, since a single version cannot disagree with
     * itself. Groups with no member in the pinned version are dropped entirely, exactly as if that
     * property did not exist in this version's schema.
     */
    private static List<Field> mergePinned(
            Map<List<UUID>, List<Field>> groups, VersionPolicy.Pinned pinned, List<OntologyVersionRef> versionOrder) {
        OntologyVersionRef pinnedVersion = pinned.version();
        if (!versionOrder.contains(pinnedVersion)) {
            // Unlike the precondition checks in groupByIdPath, this version comes from the request
            // being resolved, not from how the caller wired this method's inputs — so it is a
            // domain failure, reported the way every other resolution failure is.
            throw new FieldResolutionException(FieldErrorCode.VERSION_NOT_FOUND);
        }
        List<Field> result = new ArrayList<>();
        for (List<Field> members : groups.values()) {
            for (Field member : members) {
                if (member.definedInVersions().contains(pinnedVersion)) {
                    result.add(member);
                    break; // groupByIdPath already guarantees at most one member per version.
                }
            }
        }
        return result;
    }

    /**
     * {@link VersionPolicy.Intersection}: keep only groups whose version set equals every version
     * present in {@code allInputs}, then merge each surviving group exactly as {@link
     * #mergeUnion(Map, List)} would.
     *
     * <p>The "in scope" version set is computed from {@code allInputs}, not from {@code
     * versionOrder}. {@code versionOrder} may be the ontology's whole global version history, while
     * this particular request may only be in scope for a subset of it; using the global history here
     * would raise a spurious {@link FieldErrorCode#EMPTY_INTERSECTION} whenever some
     * globally-known-but-request-irrelevant version happens not to define a facet. Design.md section
     * 4.4 says "every version <em>in scope</em>", and scope is exactly what {@code allInputs}
     * already represents — it is whatever versions the caller actually gathered per-version
     * definitions for.
     */
    private static List<Field> mergeIntersection(
            Map<List<UUID>, List<Field>> groups, List<Field> allInputs, List<OntologyVersionRef> versionOrder) {
        Set<OntologyVersionRef> inScope =
                allInputs.stream().map(FieldMerger::soleVersion).collect(Collectors.toSet());

        List<Field> result = new ArrayList<>();
        for (List<Field> members : groups.values()) {
            Set<OntologyVersionRef> groupVersions =
                    members.stream().map(FieldMerger::soleVersion).collect(Collectors.toSet());
            if (groupVersions.equals(inScope)) {
                result.add(mergeGroup(members, versionOrder));
            }
        }
        if (result.isEmpty()) {
            throw new FieldResolutionException(FieldErrorCode.EMPTY_INTERSECTION);
        }
        return result;
    }

    /**
     * Merges every member of one property-identity group into a single {@link Field}. Used
     * directly by {@link #mergeUnion(Map, List)} and, over a pre-filtered subset of groups, by
     * {@link #mergeIntersection(Map, List, List)} — the two policies merge identically once they
     * have each decided which groups survive, so the merge logic exists exactly once here.
     *
     * <p>Members are first sorted oldest to newest by their index in {@code versionOrder}, both
     * because several fields below are defined in terms of "the newest contributing member" and
     * because every list-valued output field (denied operators, allowed operators,
     * {@link FieldConflict#definitions()}) must be built in that same deterministic order rather
     * than in whatever order the caller happened to supply members.
     */
    private static Field mergeGroup(List<Field> members, List<OntologyVersionRef> versionOrder) {
        List<Field> ordered = members.stream()
                .sorted(Comparator.comparingInt(facet -> versionOrder.indexOf(soleVersion(facet))))
                .toList();
        Field newest = ordered.get(ordered.size() - 1);

        boolean typesAgree = allEqual(ordered, Field::type);
        boolean cardinalitiesAgree = allEqual(ordered, Field::cardinality);
        boolean conflicted = !typesAgree || !cardinalitiesAgree;

        Set<OntologyVersionRef> definedInVersions =
                ordered.stream().map(FieldMerger::soleVersion).collect(Collectors.toCollection(LinkedHashSet::new));

        return new Field(
                // A rename can happen at any hop of the path, not only the terminal slug, and
                // `path` already carries whatever naming is current at every segment — so the
                // newest member's whole path object is taken, not just its terminal slug.
                newest.path(),
                resolveType(ordered, typesAgree),
                resolveNativeDatatypeIri(ordered),
                cardinalitiesAgree ? ordered.get(0).cardinality() : Cardinality.MANY,
                resolveCapabilities(ordered),
                newest.label(),
                newest.description(),
                resolveConceptScheme(newest),
                resolveRestriction(ordered),
                resolveInference(ordered),
                definedInVersions,
                conflicted ? Optional.of(buildConflict(ordered, typesAgree, cardinalitiesAgree)) : Optional.empty());
    }

    /**
     * Section 4.5's worked example resolves {@code STRING} vs {@code DATETIME} to {@code STRING} —
     * the widest common form, since every other type's values can still be compared as text.
     * {@code UNKNOWN} must beat that fallback whenever any member is {@code UNKNOWN}: {@code
     * UNKNOWN}'s whole purpose is to derive a minimal, safe operator set (just {@code exists} /
     * {@code does not exist}), and folding an {@code UNKNOWN} disagreement into {@code STRING}
     * would silently offer more operators than are actually safe for the version that could not be
     * typed at all.
     */
    private static FieldType resolveType(List<Field> ordered, boolean typesAgree) {
        if (typesAgree) {
            return ordered.get(0).type();
        }
        boolean anyUnknown = ordered.stream().anyMatch(facet -> facet.type() == FieldType.UNKNOWN);
        return anyUnknown ? FieldType.UNKNOWN : FieldType.STRING;
    }

    /**
     * Each of the four capability booleans is ANDed across every member — a conservative
     * over-approximation. Claiming {@code sortable} when only one version's definition actually
     * supports it would be an unsafe promise to every record written under a version that does not.
     */
    private static Capabilities resolveCapabilities(List<Field> ordered) {
        boolean filterable = true;
        boolean facetable = true;
        boolean searchable = true;
        boolean sortable = true;
        for (Field facet : ordered) {
            filterable &= facet.capabilities().filterable();
            facetable &= facet.capabilities().facetable();
            searchable &= facet.capabilities().searchable();
            sortable &= facet.capabilities().sortable();
        }
        return new Capabilities(filterable, facetable, searchable, sortable);
    }

    /**
     * {@code allowed} is intersected and {@code denied} is unioned across every member, both in
     * first-occurrence order walking members oldest to newest (never {@code HashSet} iteration
     * order, which is not deterministic across runs).
     *
     * <p>A restriction may only narrow the operator set, never widen it, so a version-specific {@code denied} entry
     * must survive into the merged facet even if only one version asserted it — dropping it would let the merged facet
     * offer an operator the ontology explicitly forbade under one of its contributing versions.
     *
     * <p>The subtle case is {@code allowed}: {@link OperatorRestriction}'s own contract defines both
     * lists empty as "no opinion", not "zero operators permitted". A naive set intersection would
     * treat one version's empty (no-opinion) {@code allowed} list as literally excluding everything,
     * collapsing a real restriction asserted by another version down to zero operators. Empty lists
     * are therefore skipped entirely when intersecting: if every member has no opinion, the result
     * is empty (still no opinion); if exactly one member expressed an opinion, that member's list
     * survives unmodified, because there is nothing else to intersect it against.
     */
    private static OperatorRestriction resolveRestriction(List<Field> ordered) {
        List<List<String>> opinionsOnAllowed = ordered.stream()
                .map(facet -> facet.restriction().allowed())
                .filter(allowed -> !allowed.isEmpty())
                .toList();
        List<String> allowed;
        if (opinionsOnAllowed.isEmpty()) {
            allowed = List.of();
        } else {
            LinkedHashSet<String> intersection = new LinkedHashSet<>(opinionsOnAllowed.get(0));
            for (int i = 1; i < opinionsOnAllowed.size(); i++) {
                intersection.retainAll(new LinkedHashSet<>(opinionsOnAllowed.get(i)));
            }
            allowed = List.copyOf(intersection);
        }

        LinkedHashSet<String> denied = new LinkedHashSet<>();
        for (Field facet : ordered) {
            denied.addAll(facet.restriction().denied());
        }

        return new OperatorRestriction(allowed, List.copyOf(denied));
    }

    /**
     * {@code nativeDatatypeIri} is purely advisory — no consumer is required to understand it — so on disagreement it
     * is dropped ({@code null}) rather than arbitrarily preferring one version's value over another's. All-{@code null}
     * counts as agreement, matching {@link Field#nativeDatatypeIri()}'s own "may be {@code null}" contract.
     */
    private static String resolveNativeDatatypeIri(List<Field> ordered) {
        String first = ordered.get(0).nativeDatatypeIri();
        boolean allAgree = ordered.stream().allMatch(facet -> Objects.equals(facet.nativeDatatypeIri(), first));
        return allAgree ? first : null;
    }

    /**
     * Unlike {@code nativeDatatypeIri}, a facet's {@code conceptScheme} is behaviourally load-bearing — it gates
     * subtree operators — so on disagreement this prefers the newest contributing member's understanding rather than
     * silently dropping it. When every member agrees, that shared value and the newest member's value are the same
     * thing, so always reading it off {@code newest} is correct in both cases.
     */
    private static Optional<ConceptSchemeRef> resolveConceptScheme(Field newest) {
        return newest.conceptScheme();
    }

    /**
     * Differing inference support resolves to {@link InferenceSupport#MAY_BE_INFERRED} — a
     * conservative over-approximation. Reporting {@code ASSERTED_ONLY} when even one contributing
     * version's data may in fact come from the inference graph would be an unsafe promise to a
     * caller who explicitly asked to avoid inferred data.
     */
    private static InferenceSupport resolveInference(List<Field> ordered) {
        InferenceSupport first = ordered.get(0).inference();
        boolean allAgree = ordered.stream().allMatch(facet -> facet.inference() == first);
        return allAgree ? first : InferenceSupport.MAY_BE_INFERRED;
    }

    /**
     * Builds the {@link FieldConflict} attached to a conflicted merge. {@link FieldConflict#definitions()} lists
     * <b>every</b> member of the group, not only the ones that differ, ordered oldest to newest — a consumer narrowing
     * to conflict-safe operators needs every definition to intersect against, and the reader needs the full picture of
     * what each version actually said, not just the odd one out.
     */
    private static FieldConflict buildConflict(List<Field> ordered, boolean typesAgree, boolean cardinalitiesAgree) {
        List<ConflictingDefinition> definitions = ordered.stream()
                .map(facet -> new ConflictingDefinition(soleVersion(facet), facet.type(), facet.cardinality()))
                .toList();
        String reason;
        if (!typesAgree && !cardinalitiesAgree) {
            reason = "datatype and cardinality changed between versions";
        } else if (!typesAgree) {
            reason = "datatype changed between versions";
        } else {
            reason = "cardinality changed between versions";
        }
        return new FieldConflict(definitions, reason);
    }

    private static <T> boolean allEqual(List<Field> facets, Function<Field, T> extractor) {
        T first = extractor.apply(facets.get(0));
        return facets.stream().allMatch(facet -> Objects.equals(extractor.apply(facet), first));
    }

    /**
     * Reads the single version off an input definition. Safe to call only after {@link
     * #groupByIdPath(List, List)} has validated {@code definedInVersions} has exactly one entry.
     */
    private static OntologyVersionRef soleVersion(Field facet) {
        return facet.definedInVersions().iterator().next();
    }
}
