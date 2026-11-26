# Provenance

- Created from: sequeless/sequeless-template@125d7fe (the template script `scripts/init-repo.sh query --github` ran once).
- Date: 2026-11-26
- Spec: sequeless-prototype #42. The spec file was removed from this repository.

## Moved from sequeless-ontology at `b38938d` (main, 2026-11-26)

- Source paths, moved to `query-field` (package `org.sequeless.query.field`):
  - `facet-core/src/main/java/org/sequeless/ontology/facet/core/{api,spi,internal}/*.java`, renamed: `Facet*` to `Field*`, `FacetSource` to `FieldSource`, `FacetPathCodec` to `FieldPathCodec`, `FacetMerger` to `FieldMerger`. `Principal.java` is dropped and folded into kernel-identity.
  - `facet-core/src/test/java/org/sequeless/ontology/facet/core/**`
  - `facet-core/pom.xml`, `facet-core/src/test/.../BoundaryRulesTest.java`
  - The facet-contract design doc, copied as the Field contract spec with renamed prose. Both files were removed from this repository.
- Source paths, moved to `query-field-testfixtures` (package `org.sequeless.query.field.testfixtures`):
  - `facet-core-testfixtures/src/main/java/org/sequeless/ontology/facet/core/testfixtures/*.java`
  - `facet-core-testfixtures/src/test/java/org/sequeless/ontology/facet/core/testfixtures/*.java`
- Source ADRs in sequeless-ontology, read and not copied: the facet-core zero-dependency ADR (amended here) and the facet-core move ADR (accepted here).
- Changes made in the move: `OntologyVersionRef` replaces the version IRI strings; `FieldScopeRequest.tenantId` is kernel-identity `TenantId`; `StampCalculator` sorts versions numerically; `FieldSource.rootClasses(TenantId)` is added.

## Consumed, not copied

- sequeless-kernel at `11a92be`: `org.sequeless.kernel.identity.Principal` (kernel-identity 0.1.1) replaces facet-core's `Principal`.
- sequeless-filter at `67e9155`: `filter-core`, `filter-sql-adapter` and `filter-sql-postgres-adapter` (BOM versions) provide `FilterNode`, `FieldRegistry`, `FieldResolver` and `PostgresQueryBuilder`.

## Moved from sequeless-prototype at `48a0cc6` (main), in later changes of this spec

- Ported, not byte-moved, to `query-core`, `query-testkit` and `query-postgres-jsonb-adapter`:
  - `sequeless-spi/src/main/java/org/sequeless/spi/query/{BrowsePort,ReadPort,IndexPreparationPort,Query,QueryResult,FacetBucket,ObjectView,ReadRequest,Sort,Direction}.java`
  - `sequeless-spi/src/main/java/org/sequeless/spi/object/{Page,Audit,PropertyRef,TypeRef}.java`
  - `sequeless-spi-testkit/src/main/java/org/sequeless/testkit/query/{BrowseContract,ReadContract,ObjectViews,QueryFixtures}.java`
  - `sequeless-spi-testkit/src/test/java/org/sequeless/testkit/query/{InMemoryBrowsePort,InMemoryReadPort}.java`
  - `sequeless-adapter-persistence-postgres/src/main/java/org/sequeless/adapter/persistence/postgres/PostgresQueryStore.java` (`query`, `ensureIndexes`, `castExpression`)
  - `sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres-query/V1__index_registry.sql` and `V2__search_gin_index.sql`, copied unchanged.
- Not moved: `Criterion`, `Operator`, `AggregateRequest`, `AggregateResult`, `QueryPort`. Aggregates stay on the Command side.

## Reverse pointers

The prototype deletes the moved code in spec 17 (prototype-stage4-cutover), in a commit that names this repo and this import commit's SHA.
