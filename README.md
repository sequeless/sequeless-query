# sequeless-query

sequeless-query is the Query side of Sequeless, which provides Browse, Read, and index preparation over the Field contract.

## Concepts

| Term | Meaning |
|---|---|
| Host | The application that uses this library. |
| Query side | The read side of Sequeless. It provides Browse, Read, and index preparation. |
| Command side | The write side of Sequeless. The Query side reads the `sq_object` table, which the Command side owns. |
| Field | A queryable property, reached from a root class. A Field has a type, a cardinality, and Capabilities. |
| Capabilities | 4 independent flags of a Field: `filterable`, `facetable`, `searchable`, and `sortable`. No flag implies another. |
| Field contract | The vocabulary of the `query-field` module. It states what a caller can filter, facet, search, and sort. The Query side sees the schema only through this contract. |
| FieldSource | The port that resolves a FieldScope from a FieldScopeRequest. |
| FieldScopeRequest | The input to FieldSource. It names the tenant, the root class, the VersionPolicy, and the optional Principal. |
| FieldScope | The result of resolving a FieldScopeRequest. It holds the Fields of a root class and the relationships that a caller can follow. |
| VersionPolicy | The rule that reconciles several ontology versions. It is `Union`, `Intersection`, or `Pinned`. |
| Browse | A query over stored objects. It filters, sorts, pages, counts facet buckets, and searches text. |
| Read | A lookup of a stored object by its identifier. It returns an empty result when the object does not exist, is soft-deleted, or belongs to another tenant. |
| Index preparation | The creation of the index structures that Browse and Read use. The host calls `QueryService.prepare` after each ontology version becomes active. |
| Ontology version | A version of an ontology. The `OntologyVersionRef` type holds its number, which is 1 or greater. |
| Principal | The identity of the requester. It comes from kernel-identity, and it holds an identifier and a set of roles. |
| Port | An interface that connects the core to other code. The 3 Query ports are `BrowsePort`, `ReadPort`, and `IndexPreparationPort`. |
| Adapter | A class that implements a port. |

## Modules

The root `pom.xml` is an aggregator. It is not deployed. It lists 7 modules.

| Module | Role | Version |
|---|---|---|
| `query-field` | The Field contract: the `Field`, `FieldScope`, `FieldSource`, and `ConceptSource` types. | 0.1.1 |
| `query-field-testfixtures` | Test fixtures, an in-memory `FieldSource`, and contract tests for `FieldSource` implementations. | 0.2.0 |
| `query-core` | `QueryService`, the 3 Query ports, and the use cases. | 0.2.1 |
| `query-schema-adapter` | `SchemaFieldSource`, which builds Fields from the schema contract. | 0.2.3 |
| `query-postgres-jsonb-adapter` | Postgres adapters for the 3 Query ports, and the Query migrations. | 0.2.1 |
| `query-testkit` | Contract tests and in-memory references for the 3 Query ports. | 0.2.1 |
| `query-spring-boot-autoconfig` | Spring Boot autoconfiguration that wires the other modules. | 0.4.0 |

The modules depend on each other as follows:

- `query-field` depends on kernel-identity only.
- `query-field-testfixtures` depends on `query-field`.
- `query-core` depends on `query-field`.
- `query-schema-adapter` depends on `query-field` and schema-contract.
- `query-testkit` depends on `query-core`, `query-field`, and `query-field-testfixtures`.
- `query-postgres-jsonb-adapter` depends on `query-core` and `query-field`. It uses `query-testkit` in test scope.
- `query-spring-boot-autoconfig` depends on `query-core`, `query-field`, `query-schema-adapter`, `query-postgres-jsonb-adapter`, and schema-contract.

Modules from outside this repository are kernel-identity, kernel-values, filter-core, filter-sql-adapter, filter-sql-postgres-adapter, and schema-contract.

## Use in a Spring Boot host

The module `query-spring-boot-autoconfig` wires the Query side into a Spring Boot host. Add this dependency to the host:

```xml
<dependency>
  <groupId>org.sequeless</groupId>
  <artifactId>query-spring-boot-autoconfig</artifactId>
  <version>0.4.0</version>
</dependency>
```

The other modules arrive as transitive dependencies. The host must define these 2 beans:

- A `SchemaSource` bean, from the package `org.sequeless.ontology.schema.spi`.
- A `DataSource` bean. Spring Boot `DataSourceAutoConfiguration` can create it.

The autoconfiguration provides these 5 beans:

| Bean method | Bean type | Created by |
|---|---|---|
| `fieldSource` | `FieldSource` | `SchemaFieldSource`, over the `SchemaSource` bean |
| `browsePort` | `BrowsePort` | `PostgresBrowsePort`, over the `DataSource` bean |
| `readPort` | `ReadPort` | `PostgresReadPort`, over the `DataSource` bean |
| `indexPreparationPort` | `IndexPreparationPort` | `PostgresIndexPreparation`, over the `DataSource` bean |
| `queryService` | `QueryService` | `QueryServiceFactory.create` |

The autoconfiguration skips a bean when the host defines a bean of the same type. The module has no configuration properties.

- `QueryAutoConfiguration` registers no bean unless the host defines a `SchemaSource` bean and a `DataSource` bean.
- `QueryServiceAutoConfiguration` registers `queryService` only when a `SchemaSource` bean and the 4 other Query beans exist.

### Bean order

The `SchemaSource` bean must come from a regular configuration class. An autoconfiguration may also supply it. In that case, the autoconfiguration must declare `before = QueryAutoConfiguration.class`. Otherwise, Spring does not register the Query beans and reports no error.

`QueryServiceAutoConfiguration` runs after `QueryAutoConfiguration`, so the ports are registered before its condition is evaluated.

### Transactions

The adapters use the host's `PlatformTransactionManager` bean when the host defines that bean type. Otherwise, they use a `DataSourceTransactionManager` over the same `DataSource`. A host with several transaction managers and no primary bean fails at startup.

### Host duties

The host has 2 duties. The autoconfiguration does not perform them.

**1. Run the Query migrations.** Call `PostgresMigrations.migrate(dataSource)` before the host uses the Query side. The class is in the package `org.sequeless.query.postgres`.

```java
PostgresMigrations.migrate(dataSource);
```

The Query migrations use their own history table, `flyway_schema_history_query`. The Command migrations must already be applied to the same database. The second Query migration indexes the `search_vector` column of `sq_object`.

**2. Call `QueryService.prepare` after each ontology version becomes active.** The method takes a `TenantId` and an `OntologyVersionRef`. When a newer ontology version supersedes that version, `prepare` returns `IndexPreparationResult.EMPTY` and does not call `IndexPreparationPort`.

A host that does not use Spring calls `QueryServiceFactory.create` with a `FieldSource` and the 3 Query ports.

## Build and test

The build needs JDK 25 or later. The devcontainer in `.devcontainer/` provides JDK 25. You do not need a local Maven install. The Maven wrapper runs Maven 3.10.2.

Use `./mvnw` for every Maven command. You should not run a global `mvn` command.

The build downloads the parent POM from GitHub Packages. Configure a token before the first build:

1. Create a token with the `read:packages` scope.
2. Export the token as `GITHUB_PACKAGES_TOKEN`: `export GITHUB_PACKAGES_TOKEN=<token>`
3. Add a server with the ID `github` to `~/.m2/settings.xml`.
4. Set the server password to `${env.GITHUB_PACKAGES_TOKEN}`.
5. Set the server username to your GitHub login.

Use these `make` targets:

| Command | Runs | Purpose |
|---|---|---|
| `make verify` | `./mvnw -B verify` | Run all tests, the integration tests, and the Spotless check. |
| `make test` | `./mvnw -B -DskipITs test` | Run the unit tests only. |
| `make fmt` | `./mvnw -q spotless:apply` | Apply the Spotless format. |
| `make help` | Prints the Makefile target list | List the `make` targets. |

The Enforcer rules run in the validate phase, so every build runs them. The Spotless check runs in the verify phase.

The integration tests in `query-postgres-jsonb-adapter` have names that end in `IT`. They start a PostgreSQL 18 container through Testcontainers. A container runtime, for example Docker, must run before you run `make verify`.

If the Spotless check fails, follow these steps:

1. Run `make fmt`.
2. Run `make verify` again.

## Repository layout

The root `pom.xml` aggregates 7 modules. Each module has its own `pom.xml`.

The modules `query-core` and `query-field` use 3 packages:

```text
query-core/src/main/java/org/sequeless/query/
    api/        published types, all public
    spi/        driven ports, all public
    internal/   implementation detail, not published
```

The other modules use 1 package each. For example, `query-postgres-jsonb-adapter` uses `org.sequeless.query.postgres`.

The boundary rules are:

- `api` and `spi` must not depend on `internal`. The only exception is `QueryServiceFactory`, which is in `api` and builds the service from `internal`.
- `query-spring-boot-autoconfig` must not depend on `org.sequeless.query.internal`. It uses `QueryServiceFactory` instead.

ArchUnit enforces the rules. Each of the 7 modules has a `BoundaryRulesTest` class under `src/test/java`. These rules apply:

- `query-core` does not depend on Spring or on the ontology schema contract. Its `api` and `spi` types are public.
- `query-field` depends only on the JDK, itself, and kernel-identity.

Maven Enforcer also checks the build. The parent POM, `sequeless-build-parent`, configures the common rules. They require JDK 25 or later and Maven 3.9.0 or later. They also ban Spring, Jena, and Lombok in `query-core`, `query-schema-adapter`, and `query-testkit`. The `query-field` module has its own rule. It permits kernel-identity as the only compile or runtime dependency.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the setup, the workflow, and the commit format.

## Additional information

- Each module has its own version. release-please sets the versions from Conventional Commit titles. You must not edit a `CHANGELOG.md` by hand.
- The `query-testkit` module provides contract tests: `BrowseContract`, `ReadContract`, and `IndexPreparationContract`. An adapter extends a contract and supplies its environment.
- The `query-field-testfixtures` module provides `FieldSourceContractTest` and `ConceptSourceContractTest`. A `FieldSource` implementation extends them to check its behavior.
- `FieldSource` has 3 methods: `resolve`, `expand`, and `describe`. Each method answers a different question.
- Apache License 2.0 applies to this repository. See [LICENSE](LICENSE).
