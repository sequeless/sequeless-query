# 0002. Index preparation is a host-called prepare over the resolved Fields

- Status: accepted
- Date: 2026-11-26

## Context

The Query side needs indexes that match the Fields it answers with. Today `ensureIndexes` runs synchronously inside the ontology activation (`DefaultOntologyAdministration.importTurtle`). #24 found that this is the wrong place: the ontology activation should not know about the Query side, and reload and startup did not call it at all. #28 decided which indexes follow from which Fields. Two alternatives were on the table: the host calls `prepare` after each activation, or an "Ontology activated" event triggers it. No component emits Ontology events today; the outbox holds Store events only.

## Decision

We will expose index preparation as a host-called `prepare(TenantId, OntologyVersionRef)` use case in `query-core`, backed by a driven `IndexPreparationPort`. The host calls it after every activation: import, reload and startup seed.

- **Version guard.** The use case resolves the tenant's active Schema through `FieldSource`. If the resolved scopes do not list the requested version, a newer activation has superseded the call, and it does nothing.
- **Port input.** `IndexPreparationPort.prepare(TenantId, OntologyVersionRef, Map<ClassRef, List<Field>>)` receives the resolved Fields of every root class, one map entry per class. The adapter is a pure translator from Fields to DDL. The Schema never reaches the adapter.
- **Port output.** It returns `IndexPreparationResult`, which lists the searchable Fields newly registered for the tenant, in deterministic order. The list is empty when nothing new was registered. The host runs the `search_vector` backfill when the list is not empty. A boolean would keep the trigger but not say which Fields changed.
- **Index policy (#28).** One expression index for each Field with `facetable` or `sortable`, relationships included. One search registration for each `searchable` Field. `filterable` alone creates no index. Expression indexes use one expression function for WHERE, ORDER BY and DDL.
- **Promise.** `prepare` returns once its work is done or durably accepted. `IndexPreparationContract` calls `prepare`, then the seeding hook's `awaitPrepared()`, then asserts only the port-visible result (`newlyRegistered`, idempotence and order; spec 14 Q24). It does not observe derived state. The postgres adapter's own tests assert index DDL and `search_vector` state and pin synchronous completion, which keeps the guarantee that text search covers existing rows when activation returns. (Amended for spec 14 Q24: the contract never asserted derived state; this bullet said it did.)
- **Idempotent.** A repeat call with the same version creates nothing and returns an empty list.
- **Not in this ADR.** Dropping and rebuilding indexes for changed or removed Fields belongs to the Phase 7 evolution map (#26, #8, #9). This ADR creates indexes only.

## Consequences

- Activation no longer touches the Query side. The host owns the three call sites.
- The Query side's ports need no change when a projected read model arrives: a projection is a second implementation of the same ports (#24 point 3).
- The returned list is a new contract: a later change to it is breaking after 0.1.0.
- A status query is not part of the port. It can be added later if a caller needs one.
- Testing: `IndexPreparationContract` is synchronous in the postgres adapter and in-memory references, and asynchronous adapters must implement `awaitPrepared()`.
