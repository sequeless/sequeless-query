# query-spring-boot-autoconfig

Spring Boot autoconfiguration of the Query side (spec 14, section 5). It is thin wiring: it builds the Field source, the Postgres ports and the `QueryService` from beans the host already defines, and it adds no logic of its own.

## What it registers

- `FieldSource`: a `SchemaFieldSource` over the host's `SchemaSource` bean.
- `BrowsePort`, `ReadPort` and `IndexPreparationPort`: the Postgres adapters over the host's `DataSource` bean.
- `QueryService`: built over those four by `QueryServiceAutoConfiguration`, which runs after the first class. Its `prepare` delegates to the same `IndexPreparationPort` bean, so there is one instance. It is registered only when the `SchemaSource`, the Field source and the three ports all exist.

`QueryAutoConfiguration` registers nothing without both a `SchemaSource` bean and a `DataSource` bean. `QueryService` needs only the `SchemaSource` and the four Query beans, which a host may supply itself. Every bean backs off when the host defines a bean of its type, so a host can supply its own. The adapters use the host's `PlatformTransactionManager` when it defines one, and otherwise a `DataSourceTransactionManager` over the same `DataSource`.

The `sequeless.query.*` configuration prefix is reserved for this module. It has no keys in 0.1.0, and no `@ConfigurationProperties` class reads it.

## Ordering

- An autoconfiguration that supplies `SchemaSource` must declare `before = QueryAutoConfiguration.class`, otherwise the Query beans are not wired and no error is raised.
- `QueryAutoConfiguration` runs after Spring Boot's `DataSourceAutoConfiguration`, so a `DataSource` that autoconfiguration creates is visible to it. `QueryServiceAutoConfiguration` runs after `QueryAutoConfiguration`, so the ports are registered before its condition is evaluated.

## Host responsibilities

- The autoconfiguration never connects at startup. The host runs the Query migrations itself, with `org.sequeless.query.postgres.PostgresMigrations.migrate(dataSource)`, before it uses the Query side.
- The host calls `QueryService.prepare` after each ontology activation (import, reload and startup seed), as ADR 0002 describes.
