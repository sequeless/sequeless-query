package org.sequeless.query.field.api;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.TenantId;

/**
 * Everything needed to resolve a {@link FieldScope}: who is asking, from which tenant, rooted at
 * which class, reconciling which ontology versions, and how far to traverse.
 *
 * <p>Per field-contract.md section 2.6, this is the sole input to {@code FieldSource.resolve} (and is
 * echoed back on the resulting {@code FieldScope} so that scope can later be re-expanded without
 * the caller having to reassemble the request).
 *
 * @param tenantId the tenant this request is scoped to; never {@code null}, and a kernel-identity
 *     {@link TenantId} rejects blank values by construction. Per field-contract.md
 *     section 4.8, ontologies are tenant-scoped and {@code tenantId} is required on every request
 *     and participates in the cache key. With a single tenant deployment today this is one
 *     parameter that is always the same value — but it costs nothing to require now, versus a
 *     breaking change across every signature in this contract the first time a customer needs a
 *     custom property.
 * @param root the class this request is rooted at; never {@code null}. Per field-contract.md section 4.1,
 *     a scope is one root class plus whatever traversal reaches from it — there is no syntax for
 *     naming an unrelated set of classes.
 * @param versionPolicy how to reconcile multiple ontology versions in scope; never {@code null}
 * @param traversal how far and how this request may traverse from {@link #root()}, and how the
 *     resulting scope is stamped; never {@code null}
 * @param principal the requesting principal, consulted only when {@link
 *     TraversalConfig#rbacFiltersFacets()} is enabled; never {@code null} as a wrapper — use {@link
 *     Optional#empty()} rather than {@code null} when there is none
 * @param locale the locale to prefer when selecting {@link LocalizedText} values; never {@code
 *     null} as a wrapper — use {@link Optional#empty()} rather than {@code null} when there is no
 *     preference
 */
public record FieldScopeRequest(
        TenantId tenantId,
        ClassRef root,
        VersionPolicy versionPolicy,
        TraversalConfig traversal,
        Optional<Principal> principal,
        Optional<Locale> locale) {

    /**
     * Validates {@code tenantId}, {@code root}, {@code versionPolicy}, and {@code traversal} are
     * present, and that the {@code Optional} wrappers themselves are non-{@code null} (their
     * contents may of course be absent). Blank tenant values are rejected by {@link TenantId}
     * itself.
     */
    public FieldScopeRequest {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(versionPolicy, "versionPolicy must not be null");
        Objects.requireNonNull(traversal, "traversal must not be null");
        Objects.requireNonNull(principal, "principal must not be null (use Optional.empty())");
        Objects.requireNonNull(locale, "locale must not be null (use Optional.empty())");
    }
}
