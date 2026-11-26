# Contributing to sequeless-query

## Setup

Requires JDK 25. The Maven wrapper (`./mvnw`) is committed, so no local Maven install is needed.
The devcontainer (`.devcontainer/`) provides a ready JDK 25 toolchain.

Library modules build with `--release 25`. Each module takes its parent from `sequeless-build-parent`.
Maven reads the `github` package registry, so a read token is needed:

```bash
export GITHUB_PACKAGES_TOKEN=<PAT with read:packages>   # locally; CI reads the org secret
make verify    # confirm a green baseline
```

Your `~/.m2/settings.xml` needs a `github` server entry (`server-id: github`). Set its password to `${env.GITHUB_PACKAGES_TOKEN}`.

CI runs the reusable workflows from `sequeless-build`, pinned to `workflows-v0.1.3`.

There are no local git hooks. Conventional Commits are checked in CI, not on commit.

## Workflow

1. **Branch** off `main`.
2. **Make the change** with tests. The test is the specification. Format with `make fmt`.
3. **Verify** with `make verify`.
4. **Commit** using Conventional Commits.
5. **Open a PR** following the template. The `pr-title.yml` check lints the PR title with commitlint.
6. **Merge by squash.** The PR title becomes the commit on `main`. release-please reads it.

## Conventional Commits

`feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
Use `feat!:` or a `BREAKING CHANGE:` footer for majors. release-please derives versions and
changelogs from these titles. Never hand-edit a `CHANGELOG.md`.

## Recording a decision (ADR)

`make new-adr title="Short title"` scaffolds the next-numbered record under `docs/adr/`. Fill in
Context, Decision and Consequences. Do not change an accepted ADR's decision. To change a decision, write a new ADR and set the old ADR's Status to `superseded by` the new one.

## Quality gates

Spotless (format), Enforcer (dependency hygiene, including the parent's Spring, Jena and Lombok
ban on core modules), and ArchUnit (boundaries) all run in `make verify`. CodeQL and Sonar run in CI.

## Coverage (JaCoCo) convention

JaCoCo instrumentation runs on `verify`, but the line-coverage `check` (`jacoco-check`
execution) is **off by default**. This repo has **not yet opted in**. A module opts in once it carries settled production logic. Set these properties in that module's `pom.xml`:

```xml
<properties>
  <jacoco.check.skip>false</jacoco.check.skip>
  <jacoco.line.min>0.32</jacoco.line.min> <!-- measured line coverage minus ~5pp -->
</properties>
```

Set `jacoco.line.min` from the module's measured coverage (see `target/site/jacoco/` in that module), and ratchet it up over time rather than guessing a threshold now.
