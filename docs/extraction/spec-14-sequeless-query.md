# Spec 14 — sequeless-query: the Query side

Issue: [#42](https://github.com/sequeless/sequeless-prototype/issues/42). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Decisions: [#16](https://github.com/sequeless/sequeless-prototype/issues/16), [#14](https://github.com/sequeless/sequeless-prototype/issues/14), [#15](https://github.com/sequeless/sequeless-prototype/issues/15), [#28](https://github.com/sequeless/sequeless-prototype/issues/28), [#24](https://github.com/sequeless/sequeless-prototype/issues/24). Earlier specs: [spec 11](https://github.com/sequeless/sequeless-ontology/blob/main/docs/extraction/spec-11-sequeless-ontology.md) (its section 11 holds the owner decisions this spec inherits).

This spec is committed in the first code PR of this repo, as the run rules require. The prototype is unchanged until spec 17 (prototype-stage4-cutover) deletes the moved code.

Labels such as "(Q8)" or "(Q11)" name the Navigator decisions taken for this spec on 2026-11-26, in the order they were taken: Q1 ADR numbering; Q2 Browse request and result names; Q3 and Q10 the version wrapper; Q4 the testfixtures module; Q5 the Flyway migrations; Q6 query-owned Object view component types; Q7 the traversal key; Q8 the tenant type; Q9 the index port's input; Q11 version identifiers in the Field contract; Q12 the index port's return value; Q13 the property IRI on `PathSegment` and `Relationship`. Each decision is restated where it applies.

## 1. Identity, stage and dependencies

- **Repo:** `sequeless/sequeless-query`, created from `sequeless/sequeless-template` on 2026-11-26. Groups `org.sequeless`. The root POM is an aggregator and is never deployed.
- **Stage 4** (#23 table). Waits for spec 5 ([#33](https://github.com/sequeless/sequeless-prototype/issues/33), closed), spec 6 ([#34](https://github.com/sequeless/sequeless-prototype/issues/34), closed), spec 11 ([#39](https://github.com/sequeless/sequeless-prototype/issues/39), closed) and spec 13 ([#41](https://github.com/sequeless/sequeless-prototype/issues/41), closed). Spec 14 is waited on by spec 16 (ontology-loses-facet-core) and spec 17 (prototype-stage4-cutover).
- **Released artifacts this repo consumes** (from the BOM at `sequeless-build` `sequeless-bom` 0.1.5, and from each repo's latest release on 2026-11-26):
  - `kernel-identity` 0.1.1 and `kernel-values` 0.1.1 (spec 5). Minimum required by spec 14: 0.1.1.
  - `filter-core` 0.1.4 (`FilterNode`, `FieldRegistry`, `FilterParser`, `FilterValidator`, `FieldContributor`). The spec minimum was 0.1.1 for the `FieldResolver` port; the port itself lives in `filter-sql-adapter` (see below). The current release satisfies the minimum.
  - `filter-sql-adapter` 0.1.5 (`FieldResolver`, `Field`, `SqlExpression`, `ColumnMapping`). Required by `query-postgres-jsonb-adapter` only.
  - `filter-sql-postgres-adapter` 0.1.1 (`PostgresQueryBuilder`). Required by `query-postgres-jsonb-adapter` only.
  - `schema-contract` 0.1.0 (`Schema`, `OntologyVersion`, `SchemaSource`). Required by `query-schema-adapter` only.
- **Drift against the spec:** the spec's version notes (filter 0.1.1, sequeless-ontology 0.1.x) are minimums. The BOM lists later releases, and this spec builds against the BOM.

## 2. Moves

Every moved file is copied into this repo with its new package and name (section 4). Each source is recorded in `PROVENANCE.md`. The source SHAs are the `origin/main` heads read on 2026-11-26.

### 2.1 From sequeless-ontology, `b38938d`

| Source path (ontology) | Target (this repo) | Move |
|---|---|---|
| `facet-core/src/main/java/org/sequeless/ontology/facet/core/api/*.java` | `query-field/src/main/java/org/sequeless/query/field/api/*.java` | Package and type renames (section 4). `Principal.java` is dropped (folded into kernel-identity). `OntologyVersionRef` is added (Q10, Q11) |
| `facet-core/src/main/java/org/sequeless/ontology/facet/core/spi/*.java` | `query-field/src/main/java/org/sequeless/query/field/spi/*.java` | `FacetSource` becomes `FieldSource` and gains `rootClasses(TenantId)` (section 6.3). `ConceptSource` keeps its name |
| `facet-core/src/main/java/org/sequeless/ontology/facet/core/internal/*.java` | `query-field/src/main/java/org/sequeless/query/field/internal/*.java` | `FacetMerger`, `FacetPathCodec` and `StampCalculator` are renamed to `FieldMerger`, `FieldPathCodec` and `StampCalculator`. `StampCalculator` sorts versions numerically (Q11) |
| `facet-core/src/test/java/org/sequeless/ontology/facet/core/**` | `query-field/src/test/java/org/sequeless/query/field/**` | Follows its class's package (spec 11, OQ-6) |
| `facet-core-testfixtures/src/main/java/org/sequeless/ontology/facet/core/testfixtures/*.java` | `query-field-testfixtures/src/main/java/org/sequeless/query/field/testfixtures/*.java` | Renames as above. Kit and in-memory source stay in `src/main` (ADR 0002 pattern) |
| `facet-core-testfixtures/src/test/java/**` | `query-field-testfixtures/src/test/java/**` | Renames as above |
| `docs/adr/0002-facet-core-is-a-zero-dependency-contract-module.md` | Superseded by query ADR 0003 (section 9) | Not copied; cited in query ADR 0003 |
| `docs/adr/0005-facet-core-moves-to-sequeless-query.md` | Accepted by this move | Not copied; cited in query ADR 0001 |
| `docs/specs/facet-contract/design.md` | `docs/specs/field-contract.md` | Copied; its `Facet` vocabulary is renamed to `Field` |

The module is not released from ontology. `ontology-facet-core` and `ontology-facet-core-testfixtures` are frozen and deleted in spec 16.

### 2.2 From sequeless-kernel, `11a92be`

`Principal` (`kernel-identity/src/main/java/org/sequeless/kernel/identity/Principal.java`) is used as-is. Nothing is copied.

### 2.3 From sequeless-prototype, `48a0cc6`

Behaviour is ported, not moved byte for byte. The prototype's Browse and Read code stays in the prototype until spec 17.

| Source path (prototype) | Target (this repo) | Move |
|---|---|---|
| `sequeless-spi/src/main/java/org/sequeless/spi/query/BrowsePort.java` | `query-core/src/main/java/org/sequeless/query/spi/BrowsePort.java` | Signature changes to `browse(FieldScope, BrowseQuery)` (section 6.1) |
| `sequeless-spi/src/main/java/org/sequeless/spi/query/ReadPort.java` | `query-core/src/main/java/org/sequeless/query/spi/ReadPort.java` | Returns `Optional<ObjectView>`, as before |
| `sequeless-spi/src/main/java/org/sequeless/spi/query/IndexPreparationPort.java` | `query-core/src/main/java/org/sequeless/query/spi/IndexPreparationPort.java` | Signature changes (section 6.2) |
| `sequeless-spi/src/main/java/org/sequeless/spi/query/ObjectView.java` | `query-core/src/main/java/org/sequeless/query/api/ObjectView.java` | Uses query-owned `TypeRef`, `PropertyRef` and `Audit` (section 4) |
| `sequeless-spi/src/main/java/org/sequeless/spi/query/ReadRequest.java` | `query-core/src/main/java/org/sequeless/query/api/ReadRequest.java` | Unchanged apart from package |
| `sequeless-spi/src/main/java/org/sequeless/spi/query/FacetBucket.java` | `query-core/src/main/java/org/sequeless/query/api/FacetBucket.java` | Keeps its name (#23). Unchanged apart from package |
| `sequeless-spi/src/main/java/org/sequeless/spi/query/Sort.java`, `Direction.java` | `query-core/src/main/java/org/sequeless/query/api/Sort.java`, `Direction.java` | `Sort` takes a `FieldPath` (section 6.1) |
| `sequeless-spi/src/main/java/org/sequeless/spi/query/QueryResult.java` | `query-core/src/main/java/org/sequeless/query/api/BrowseResult.java` | Renamed (Q2). Facets are keyed by `FieldPath` |
| `sequeless-spi/src/main/java/org/sequeless/spi/query/Query.java` | `query-core/src/main/java/org/sequeless/query/api/BrowseQuery.java` | Renamed (Q2). `Criterion` becomes `FilterNode`; `includeDeleted` is removed (#16 point 10) |
| `sequeless-spi/src/main/java/org/sequeless/spi/object/Page.java` | `query-core/src/main/java/org/sequeless/query/api/Page.java` | Query-owned offset page (#16 point 11) |
| `sequeless-spi/src/main/java/org/sequeless/spi/object/Audit.java` | `query-core/src/main/java/org/sequeless/query/api/Audit.java` | Query-owned copy, same four fields (Q6) |
| `sequeless-spi/src/main/java/org/sequeless/spi/object/PropertyRef.java`, `TypeRef.java` | `query-core/src/main/java/org/sequeless/query/api/PropertyRef.java`, `TypeRef.java` | Query-owned copies wrapping an IRI (Q6) |
| `sequeless-spi/src/main/java/org/sequeless/spi/query/Criterion.java`, `Operator.java` | Not moved | Replaced by `FilterNode` and filter-core's operators (#16 point 5) |
| `sequeless-spi/src/main/java/org/sequeless/spi/query/AggregateRequest.java`, `AggregateResult.java`, `QueryPort.java` | Not moved | Aggregates stay on the Command side (#16 point 2). `QueryPort` keeps `aggregate` |
| `sequeless-spi-testkit/src/main/java/org/sequeless/testkit/query/BrowseContract.java` | `query-testkit/src/main/java/org/sequeless/query/testkit/BrowseContract.java` | Ported to the new request and result (#16 point 12) |
| `sequeless-spi-testkit/src/main/java/org/sequeless/testkit/query/ReadContract.java` | `query-testkit/src/main/java/org/sequeless/query/testkit/ReadContract.java` | Ported as above |
| `sequeless-spi-testkit/src/main/java/org/sequeless/testkit/query/ObjectViews.java`, `QueryFixtures.java` | `query-testkit/src/main/java/org/sequeless/query/testkit/` | Ported. Fixtures that need a `FieldScope` build it from `query-field-testfixtures` |
| `sequeless-spi-testkit/src/test/java/org/sequeless/testkit/query/InMemoryBrowsePort.java`, `InMemoryReadPort.java` | `query-testkit/src/main/java/org/sequeless/query/testkit/InMemoryBrowsePort.java`, `InMemoryReadPort.java` | Promoted to `src/main` so the BOM can run the contracts (section 7.3) |
| `sequeless-adapter-persistence-postgres/src/main/java/org/sequeless/adapter/persistence/postgres/PostgresQueryStore.java` (`query`, `ensureIndexes`, `castExpression`, `castExpressionLiteral`) | `query-postgres-jsonb-adapter/src/main/java/org/sequeless/query/postgres/*.java` | Rewritten on `FieldResolver` (section 6.4). `aggregate` stays in the prototype |
| `sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres-query/V1__index_registry.sql` | `query-postgres-jsonb-adapter/src/main/resources/db/migration/sequeless/postgres-query/V1__index_registry.sql` | Copied unchanged (Q5) |
| `sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres-query/V2__search_gin_index.sql` | `query-postgres-jsonb-adapter/src/main/resources/db/migration/sequeless/postgres-query/V2__search_gin_index.sql` | Copied unchanged (Q5) |
| `sequeless-adapter-persistence-postgres/src/test/java/.../PostgresQueryStoreContractIT.java`, `PostgresQueryStoreIndexUsageIT.java`, `PostgresReadContractIT.java` | `query-postgres-jsonb-adapter/src/test/java/org/sequeless/query/postgres/` | Ported to the contracts and the new DDL |

### 2.4 Provenance record

`PROVENANCE.md` carries the table above, the three source SHAs (`b38938d` for ontology, `11a92be` for kernel, `48a0cc6` for the prototype, plus the filter head `67e9155` for the filter sources), and the date (2026-11-26). The import commit is the first commit of the code PR. Prototype deletion is spec 17's job and names this repo's import commit SHA.

## 3. Prerequisite refactors

**None.** Per the owner's note on #42 (2026-11-05), no pre-factor PRs are opened. The behaviour changes that the decisions imply are made in this repo, not in the prototype first. The consequence is recorded as a hazard in section 10. The prototype keeps its current behaviour until spec 17 switches it to this repo's artifacts.

Prerequisites already on `main` and used as they are:

- Spec 13 P6a and P6b: `ReadPort`, `BrowsePort`, the index-preparation port and the Object view in the prototype's `spi.query`.
- Spec 13 P7: index separation (`PostgresQueryStore` owns the Query tables and their migrations).
- Spec 11: the Schema and Behaviour split in sequeless-ontology, and the `FilterNode` criteria on rollup rules (its Q3 and Q5).
- Spec 5 and spec 6: kernel identity and values, and filter-core's `FieldResolver` port.

## 4. Package and type renames

### 4.1 Packages

Every module follows ADR kernel 0001, the package name follows the repo segment: `org.sequeless.query.<module>`.

| Module | Packages |
|---|---|
| `query-field` | `org.sequeless.query.field.api`, `.spi`, `.internal` |
| `query-field-testfixtures` | `org.sequeless.query.field.testfixtures` |
| `query-core` | `org.sequeless.query.api`, `.spi`, `.internal` (the template's layout, not a `field` segment) |
| `query-schema-adapter` | `org.sequeless.query.schema` |
| `query-postgres-jsonb-adapter` | `org.sequeless.query.postgres` |
| `query-testkit` | `org.sequeless.query.testkit` |
| `query-spring-boot-autoconfig` | `org.sequeless.query.autoconfig` |

### 4.2 Types in query-field

| Ontology name | Query name | Note |
|---|---|---|
| `Facet` (record) | `Field` | `definedInVersions` becomes `Set<OntologyVersionRef>` (Q11) |
| `FacetPath` | `FieldPath` | |
| `FacetType` | `FieldType` | Gains `OBJECT_REF` (a reference to another object by id; relationship Fields map to it) (Q18) |
| `FacetScope` | `FieldScope` | `ontologyVersions` becomes `Set<OntologyVersionRef>` (Q11) |
| `FieldScope` | gains `concreteTypes` (Set of ClassRef, the root and its concrete subtypes; empty for an abstract root with no concrete subtypes) | Q15 |
| `FacetScopeRequest` | `FieldScopeRequest` | `tenantId` becomes kernel-identity `TenantId` (Q8). `Optional<Principal>` uses kernel-identity's `Principal` |
| `FacetConflict` | `FieldConflict` | |
| `FacetErrorCode` | `FieldErrorCode` | |
| `FacetResolutionException` | `FieldResolutionException` | |
| `FacetSource` | `FieldSource` | Gains `rootClasses(TenantId)` (section 6.3) |
| `Relationship` | `Relationship` | `definedInVersions` becomes `Set<OntologyVersionRef>` (Q11) |
| `ConflictingDefinition.versionIri` | `ConflictingDefinition.version` | Type `OntologyVersionRef` (Q11) |
| `VersionPolicy.Pinned.versionIri` | `VersionPolicy.Pinned.version` | Type `OntologyVersionRef` (Q11) |
| `Principal` (facet-core) | Removed | Use kernel-identity `Principal` (ADR 0003) |
| `Capabilities` (`filterable`, `facetable`, `searchable`, `sortable`) | Keeps its name | `facetable` is kept (#14) |
| `Concept`, `ConceptQuery`, `ConceptSource`, `ConceptSchemeRef` | Keep their names | #14 |
| `ClassRef`, `Cardinality`, `LocalizedText`, `OperatorRestriction`, `InferenceSupport`, `StampMode`, `TraversalConfig` | Keep their names | |
| `PathSegment` | Keeps its name and gains `iri` (String, non-blank, no `\|`) | Q13: the traversal key of 6.5 is built from segment IRIs |

New in query-field: `OntologyVersionRef`, a record of one `int` value of at least 1. It lives in `org.sequeless.query.field.api`, because the Field contract needs it and query-core and query-schema-adapter import it from there (Q10, Q11).

### 4.3 Types in query-core

| Name | Kind | Note |
|---|---|---|
| `BrowseQuery` | Record | `Optional<FilterNode> filter`, `Optional<String> text`, `List<Sort> sorts`, `Page page`, `List<FieldPath> facets` (Q2) |
| `BrowseResult` | Record | `List<ObjectView> items`, `long total`, `Map<FieldPath, List<FacetBucket>> facets` |
| `ObjectView` | Record | `ObjectId id`, `TypeRef type`, `TenantId tenant`, `long version`, `Optional<String> state`, `Map<PropertyRef, Value> properties`, `Audit audit`, `boolean deleted` |
| `TypeRef`, `PropertyRef` | Records | Each wraps one IRI `String` (Q6). A property key is one IRI. A traversal key is the `\|`-joined encoding of section 6.5, and is never a `PropertyRef` |
| `Audit` | Record | `Instant createdAt`, `String createdBy`, `Instant updatedAt`, `String updatedBy` (Q6) |
| `Page` | Record | `int number`, `int size` (offset paging, #16 point 11) |
| `Sort` | Record | `FieldPath path`, `Direction direction` |
| `Direction` | Enum | As in the prototype |
| `ReadRequest` | Record | `ObjectId id` |
| `FacetBucket` | Record | `String value`, `long count` (name kept, #23) |
| `IndexPreparationResult` | Record | `List<FieldPath> newlyRegistered`, deterministic order, immutable (Q12) |

Records that can gain components (`BrowseQuery`, `BrowseResult`, `ObjectView`, `ReadRequest`, `IndexPreparationResult`) follow #24 point 2. Each new component ships with a delegating secondary constructor that defaults the new component, so existing callers and adapters compile unchanged.

## 5. Modules and their dependencies

| Module | Depends on (compile) | Test-scoped only | Notes |
|---|---|---|---|
| `query-field` | `kernel-identity` | JUnit, AssertJ, ArchUnit | ADR 0003: kernel-identity is the only compile dependency. The enforcer rule `ban-all-runtime-dependencies` allows that one artifact |
| `query-field-testfixtures` | `query-field`, JUnit, AssertJ | | Published test support. Kit and in-memory source in `src/main`, as `facet-core-testfixtures` did (ADR 0002) |
| `query-core` | `query-field`, `filter-core`, `kernel-identity`, `kernel-values` | JUnit, AssertJ, ArchUnit | Use cases, ports, the Field-to-`FieldRegistry` bridge, Object view types. No `schema-contract`, no JDBC |
| `query-schema-adapter` | `query-field`, `schema-contract`, `kernel-identity` | | The only module that depends on `schema-contract` (#14, #16 point 6) |
| `query-postgres-jsonb-adapter` | `query-core`, `query-field`, `filter-sql-adapter`, `filter-sql-postgres-adapter`, `kernel-values`, Spring JDBC | `query-testkit`, Testcontainers | Implements `FieldResolver` (filter-sql-adapter) and the browse, read and index ports. Owns index DDL and V1/V2 (Q5) |
| `query-testkit` | `query-core`, `query-field-testfixtures`, JUnit, AssertJ | | Contracts: `BrowseContract`, `ReadContract`, `IndexPreparationContract`. Also the in-memory `BrowsePort`, `ReadPort` and index references |
| `query-spring-boot-autoconfig` | `query-core`, `query-schema-adapter`, `query-postgres-jsonb-adapter`, Spring Boot autoconfigure | `ApplicationContextRunner` | Thin wiring. Exposes `prepare` (#24, spec 14 list) |

ArchUnit: every module has a `BoundaryRulesTest` (ADR kernel 0001 and spec 11 section 5.4). The template's four rules stay. The "depends only on the JDK and itself" rule is widened to each module's declared dependencies. `query-core` also forbids `org.springframework..` and `org.sequeless.ontology..`.

## 6. Behaviour of the ports and use cases

### 6.1 Browse

- `BrowsePort.browse(FieldScope scope, BrowseQuery query)` returns `BrowseResult`. The tenant is `scope.request().tenantId()`. The authorization `Scope` is not an input (#24 point 2).
- `FilterNode` is validated by filter-core's `FilterValidator` against the `FieldRegistry` bridged from the `FieldScope` (section 6.5). The adapter translates the validated node; it does not parse text.
- `Sort.path` must be a sortable Field of the scope. Results are ordered as the prototype's `BrowsePort` javadoc states: with sorts, each sort key in list order and then `id`; otherwise with free text, relevance first, then `createdAt` and then `id`; otherwise `createdAt` and then `id`. The javadoc of `query-core`'s `BrowsePort` carries the full rule.
- Soft-deleted objects are never visible. The port has no `includeDeleted` (#16 point 10).
- Paging is offset-based (`Page`, #16 point 11).
- **Candidates (Q15).** A Browse matches only objects whose type is in `scope.concreteTypes()`, the root and its concrete subtypes (#16 point 7). The port does no hierarchy reasoning. An abstract root with no concrete subtypes gives an empty result with no error.
- **Multi-select facet counts (Q14).** For a facet on path P, the counts are computed with the filter minus its top-level AND conjuncts that are a `FieldFilter` on P (compared by the Q7 key). Every other conjunct is kept whole, including any `OrFilter` or `AnyFilter` that mentions P. If the filter is not an `AndFilter`, it counts as one conjunct; if it is a `FieldFilter` on P, it is removed and the result is match-all. The free-text condition narrows every facet, as in the prototype.
- Facets are returned only for Fields with `facetable` (#14). A sort key on a relationship Field orders by target id, as the prototype's javadoc states.

### 6.2 Index preparation

- Driven port: `IndexPreparationPort.prepare(TenantId tenant, OntologyVersionRef version, Map<ClassRef, List<Field>> rootFields)` returns `IndexPreparationResult`.
- `rootFields` holds the resolved Fields of every root class, one map entry per class (Q9).
- Host-facing use case: `prepare(TenantId tenant, OntologyVersionRef version)` in `query-core`. It lists the tenant's root classes through `FieldSource.rootClasses(tenant)`, resolves each root's Fields, then calls the port. It is the version guard of #24 point 8: if the resolved scopes do not list `version` in `ontologyVersions`, a newer activation has superseded the call, and the use case does nothing.
- Index policy by capability (#28): an expression index for each Field with `facetable` or `sortable` (relationships included); a search registration for each `searchable` Field; no index for `filterable` alone.
- Idempotent: a repeat call with the same version returns an empty `newlyRegistered` and creates nothing.
- Done-or-durably-accepted (#24 point 10). `IndexPreparationContract` calls `prepare`, then the seeding hook's `awaitPrepared()`, then asserts the derived state. The postgres adapter's own tests pin synchronous completion.
- The host calls `prepare` after import, reload and the startup seed (#24 point 7). The backfill of `search_vector` runs in the host when `newlyRegistered` is not empty (Q12; the prototype's `IndexPreparation.java` line 73).

### 6.3 Field source

- `FieldSource.resolve(FieldScopeRequest)`, `expand(FieldScope, FieldPath)` and `describe(FieldScopeRequest, FieldPath)` keep their behaviour.
- New: `FieldSource.rootClasses(TenantId tenant)` returns `List<ClassRef>` (#24 point 8).
- `query-schema-adapter` implements `FieldSource` over `SchemaSource.schema(TenantId)`. It maps `Schema.ontologyVersion().value()` one-to-one to an `OntologyVersionRef` (Q10).
- The stamp: `StampCalculator` sorts `OntologyVersionRef` values numerically before joining them. This changes the stamp format from the ontology's string sort, and the change is recorded in ADR 0001 (Q11).
- No consistent active Ontology gives UNKNOWN_TENANT (Q19).
- **Unknown or inconsistent tenant (Q19).** `SchemaSource` throws `NoConsistentOntologyException` (schema-contract 0.2.0); `OntologyException` (ontology-core) extends it. query-schema-adapter translates it to `UNKNOWN_TENANT` in every `FieldSource` method. Option C of the Navigator's choice: the supertype relationship, not a wrapper and not a throw-site change. One-way door: the public supertype relationship.

### 6.4 Postgres JSONB adapter

- Implements filter-core's `FieldResolver` (in `filter-sql-adapter`). Each Field resolves to an `Expression` (SQL plus parameters).
- The property IRI is embedded as an escaped literal, not bound, so one expression function feeds WHERE, ORDER BY and the DDL, and expression indexes always match (#21 amendment, #15).
- A per-datatype `ValueConverter` (`LocalDate`, `OffsetDateTime`, `UUID`, `BigDecimal`) coerces each JSON value (#21 amendment).
- Operators and rendering come from `PostgresQueryBuilder` (`IS DISTINCT FROM`, escaped `ILIKE`) in filter-sql-postgres-adapter.
- Migrations V1 and V2 are copied unchanged (Q5).

### 6.5 FieldRegistry key for traversal paths

- A Field's key in the `FieldRegistry` is its segment property IRIs joined by `|` (U+007C), with no spaces (Q7).
- A single-segment key is the bare property IRI, so spec 11 decision 5 holds for direct Fields.
- One encode and decode function, defined once in `query-core`, produces and reads the key. The postgres adapter splits on `|` and escapes each IRI as a literal. Injection tests cover keys that contain `|` or quotes.
- `FieldPathCodec` (renamed from `FacetPathCodec`) keeps its UUID and slug forms, which are internal. The IRI key does not use them.

## 7. Release config, BOM and contracts

### 7.1 First version and tags

- Every module has never been published, so every module's first release is pinned to **0.1.0**: `query-field`, `query-field-testfixtures`, `query-core`, `query-schema-adapter`, `query-postgres-jsonb-adapter`, `query-testkit`, `query-spring-boot-autoconfig`.
- Tags are component-prefixed, as in filter and kernel: `query-field-v0.1.0`, and so on.
- `release-please-config.json` lists each module with `"initial-version": "0.1.0"` and `"skip-snapshot": true`, using the `maven` release type and `maven-workspace` plugin, as kernel does.
- `.release-please-manifest.json` is empty until the release PR.
- The release PR is created and merged after the checks are green (pre-approved, owner note on the run rules). A first release at 0.1.0 is not major, so no owner confirmation is needed.

### 7.2 BOM change

- `sequeless-build`'s `sequeless-bom/pom.xml` gains an entry for each of the seven modules at 0.1.0, after the release. The BOM change is a PR in `sequeless/sequeless-build`.
- The BOM is re-released after that PR (a minor bump of `sequeless-bom`, pre-approved). Spec 15 (automation) edits the same POM, so it waits for this merge.

### 7.3 BOM contract test

- `sequeless-build`'s `bom-contracts` gains `QueryBomContractTest`. It extends `BrowseContract`, `ReadContract` and `IndexPreparationContract` and runs them against the in-memory references in `query-testkit` at the BOM's versions.
- A failure there means a released query artifact no longer satisfies its own contracts together with the rest of the BOM (#23 definition of done).

## 8. CI and workflows

- `ci.yml`, `codeql.yml`, `release.yml` and `pr-title.yml` come from the template. Each `uses:` line pins `sequeless/sequeless-build` by SHA with a `# workflows-v0.1.3` comment (the latest workflows tag at 2026-11-26).
- Java release 25, as ontology's root POM sets it (`maven.compiler.release` 25; owner answer Q11 of spec 11). The parent's default of 21 is overridden in the root POM.
- Verification gate for each PR: `make verify` (the root `./mvnw -B verify`). Locally, Maven runs one at a time (`flock -w 1800 $HOME/.mvn-build.lock`), with `MAVEN_OPTS=-Xmx1g` and `-T 1`. Testcontainers ITs run one class at a time.
- Dependency analysis runs in `verify`. Every module declares each artifact it imports, including the test-scoped ones.
- Spotless and the sonar job come from the template. CodeQL runs on Java.

## 9. ADRs

Numbered as Navigator decision Q1. Every ADR is in `docs/adr/`.

- **Query ADR 0001, "The Query side sees the Schema only through the Field contract".** Settles #14 and #16 point 13. Records the deviations from a pure rename: `TenantId` in `FieldScopeRequest` (Q8), `OntologyVersionRef` for every version identifier (Q11), and the numeric stamp sort (Q11). Cites ontology ADR 0005 (accepted by this move).
- **Query ADR 0002, "Index preparation is a host-called prepare over the resolved Fields".** Settles #24 points 6 to 10 and the index policy of #28. Records the version guard, the port's input `Map<ClassRef, List<Field>>`, the `IndexPreparationResult` return value (Q12), and "done or durably accepted". Cites #24 and #28.
- **Query ADR 0003, "Field contract depends on kernel-identity, and Principal folds into it".** The ontology draft ADR 0003 (commit `06dee75`, `docs/drafts/sequeless-ontology-adr-0003-facet-core-depends-on-kernel-identity.md`, on the prototype's `wayfinder/kernel-adr-0002-amendment` branch). It amends ontology ADR 0002 and allows one compile dependency, kernel-identity. Its enforcer `<includes>` exception and ArchUnit change belong to this module.

## 10. Definition of done and hazards

Definition of done (#42, the owner's list):

- The new repo's CI is green. (Each PR; then the release.)
- The prototype's CI is green against released artifacts. The prototype does not reference a SNAPSHOT and builds with the released BOM. Verified at the end of this spec, not by a prototype PR.
- The BOM's CI, which runs every testkit contract, is green (section 7.3).
- The release and the BOM re-release are done (section 7).
- Cut-over clauses do not apply to this spec. Spec 17 does the prototype's cut-over.

Hazards recorded for the next specs:

- **No pre-factor PRs.** The behaviour changes (FilterNode replaces Criterion, index policy by capability, `prepare` with the version guard, Object views) exist first in this repo. The prototype keeps the old behaviour until spec 17. Spec 17's contracts must run green against this repo's 0.1.0 before the prototype's cut-over merges.
- **The Field contract changes its version and tenant types before 0.1.0 (Q8, Q11).** After 0.1.0 these are breaking changes. Any later change to them is a major release, which needs owner confirmation.
- **The stamp format changes (numeric version sort).** A stamp computed in the ontology is not comparable to a stamp from this repo.
- **`Field` collides with filter-sql-adapter's `Field`.** Code in `query-postgres-jsonb-adapter` imports filter's `Field` by its full name, never with a bare import.
- **`ontology-facet-core` is frozen, not deleted, until spec 16.** Nothing in this repo depends on it.

Open items for the next run:

- The release PR for 0.1.0, the sequeless-build BOM PR, and the BOM re-release (sections 7.1 and 7.2).
- The prototype's own proof of its CI against released artifacts (section 10, first bullet) is checked when spec 17 starts.
