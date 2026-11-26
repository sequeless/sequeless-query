# sequeless-query

The Query side of Sequeless: Browse, Read and index preparation over the Field contract. It is extracted from sequeless-prototype under spec 14 (`docs/extraction/spec-14-sequeless-query.md`, issue sequeless/sequeless-prototype#42). Its decisions are in `docs/adr/`.

## Modules

- `query-field`: the Field contract (`FieldSource`, `Field`, `FieldScope`, capabilities). Depends only on kernel-identity (ADR 0003).
- `query-field-testfixtures`: the Field contract kit and an in-memory source, published for implementations to test against.
- `query-core`: Browse, Read and the index-preparation use case and ports. Under construction.
- `query-schema-adapter`, `query-postgres-jsonb-adapter`, `query-testkit`, `query-spring-boot-autoconfig`: planned in spec 14.

## Building

```bash
make verify     # full build, tests and quality gates
make fmt        # apply formatting (Spotless)
make new-adr title="Some decision"
```

The parent POM comes from GitHub Packages, so export a token with `read:packages` as `GITHUB_PACKAGES_TOKEN` before any `mvn` run. The `github` server in `~/.m2/settings.xml` reads it as `<password>${env.GITHUB_PACKAGES_TOKEN}</password>`; `sequeless-build`'s README has the full entry.

If `spotless:check` fails at `verify`, run `mvn spotless:apply`, then `mvn verify` again. The parent rejects a POM that is valid XML but not in the formatter's layout.

The parent POM (`sequeless-build-parent` 0.1.3) and the reusable workflows (`workflows-v0.1.3`) come from released `sequeless-build` versions. Renovate moves both. The agent docs are under `docs/agents/`.

SonarCloud is optional: the workflow skips with a notice when `SONAR_TOKEN` is absent.
