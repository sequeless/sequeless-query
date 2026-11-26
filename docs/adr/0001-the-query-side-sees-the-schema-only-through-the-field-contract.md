# 0001. The Query side sees the Schema only through the Field contract

- Status: accepted
- Date: 2026-11-26

## Context

The Query side answers Browse and Read requests. It needs to know what can be filtered, faceted, searched and sorted, and which types a root covers. The Schema (`schema-contract`, in sequeless-ontology) describes types and properties, not what a query may do with them. Before this decision the prototype read `PropertyDefinition.facet()` and other Schema hints directly, so the Query side depended on the Schema's shape.

#14 settled the names: `Field` is a property reached from a type by a path, together with what a query may do with it. `Capabilities` (`filterable`, `facetable`, `searchable`, `sortable`) are derived from the Schema's hints. The Field contract was then ported from `facet-core` (sequeless-ontology) into this repo (spec 14, section 2.1).

## Decision

We will make the Query side see the Schema only through the Field contract. `query-field` defines `FieldSource` and the Field types. `query-schema-adapter` is the single module that depends on `schema-contract` and translates the Schema into Fields. No other module in this repo imports `org.sequeless.ontology`.

Deviations from a pure rename, recorded because the Field contract has no released API before 0.1.0:

- `FieldScopeRequest.tenantId` is kernel-identity's `TenantId`, not a `String`. The blank check comes from `TenantId`.
- Every ontology version identifier is `OntologyVersionRef`, a record over an `int` of at least 1, not a string IRI. The Schema carries only the `int`. The five places are `FieldScope.ontologyVersions`, `Field.definedInVersions`, `Relationship.definedInVersions`, `ConflictingDefinition.version` (renamed from `versionIri`) and `VersionPolicy.Pinned.version` (renamed from `versionIri`).
- The stamp sorts versions numerically, not as strings, so `10` sorts after `9`. The stamp format therefore differs from the ontology's.
- `PathSegment` and `Relationship` carry the property IRI (`iri`), because the JSONB store keys properties by IRI and a UUID would need a second lookup that could go stale (Navigator decision Q13).
- `FieldScope.concreteTypes` (Navigator decision Q15) holds the root and its concrete subtypes, filled by query-schema-adapter, and is part of the stamp, so a subtype change invalidates a cached scope.
- `FieldType.OBJECT_REF` (Navigator decision Q18) is added before 0.1.0: a relationship Field's value is an object id, not an IRI, so it is not `IRI_REF`. The postgres adapter casts it to uuid by this type.

## Consequences

- The Query side can be tested with a hand-built `FieldScope` and no ontology.
- Changing the Schema's shape reaches the Query side only through `query-schema-adapter`.
- The deviations are breaking changes once 0.1.0 is released. A later change to them is a major release and needs owner confirmation.
- The Schema hints map to capabilities (`sq:facet` to `facetable`, `sq:searchable` to `searchable`, `sq:filterable` to `filterable`, `sq:sortable` to `sortable`). `sq:indexed` stays storage-only and never reaches the Query side (#14).
- This accepts sequeless-ontology ADR 0005 (`facet-core` moves here). ADR 0003 of this repo amends ontology ADR 0002.
