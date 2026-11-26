# 0003. The Field contract depends on kernel-identity, and Principal folds into it

- Status: accepted
- Date: 2026-11-26

## Context

sequeless-ontology ADR 0002 made `facet-core` a zero-dependency contract module, so that sequeless-ontology and sequeless-filter could share one vocabulary for "what can be filtered" without depending on each other. The module carried its own `Principal` record (an id and a set of roles) so that it did not depend on the kernel.

The kernel now publishes `kernel-identity`, whose `Principal` has the same shape, and every Sequeless module uses it. Keeping a second `Principal` in the Field contract would mean two identical types that drift apart. A draft ADR in sequeless-ontology (commit `06dee75`, `docs/drafts/sequeless-ontology-adr-0003-facet-core-depends-on-kernel-identity.md`) proposed the change, and #13 decision 11 moved it to this repo.

## Decision

We will allow `query-field` one compile dependency, `org.sequeless:kernel-identity`, and remove the module's own `Principal`. `FieldScopeRequest` takes the kernel's `Principal` and `TenantId`. Every other compile and runtime dependency stays banned: the enforcer execution `ban-all-runtime-dependencies` permits `kernel-identity` only, and the ArchUnit boundary rule permits `org.sequeless.kernel.identity` and the JDK.

This amends ADR 0002 of sequeless-ontology (zero dependencies becomes zero dependencies except kernel-identity). `query-field-testfixtures` may depend on `query-field` and on test libraries, as in ontology ADR 0002.

## Consequences

- One `Principal` type across the platform. A principal passed to the Field contract is the same object the Command side uses.
- Every other compile and runtime dependency of `query-field` stays banned, and the build enforces it.
- Adding a second dependency to `query-field` needs a new ADR, because this one names exactly one allowed artifact.
- The kernel's `Principal` now sits on the Query side's public API, so a change to it is a kernel change that reaches this module.
