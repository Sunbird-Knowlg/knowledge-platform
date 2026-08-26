# CLAUDE.md

Guidance for Claude Code when working in the **knowledge-platform** repo.

## Project Overview

**Knowledge Platform** is a graph-based content management and knowledge system providing APIs for content, assessments, search, taxonomy, and collections.

**Stack:** Scala 2.13 (Java 11 compat) · Play Framework 3.0.5 (Netty) · Apache Pekko 1.0.3 actors · Maven 3.9+ · ScalaTest/ScalaMock/Pekko TestKit · JanusGraph/Neo4j + Cassandra/Yugabyte + Redis · Kafka · Scoverage/JaCoCo.

## Module Map (multi-module Maven project)

```
platform-core/     # shared core: actor-core, platform-common, platform-cache,
                   #   cassandra-connector, kafka-client, platform-telemetry, schema-validator
ontology-engine/   # knowledge graph: graph-dac-api, graph-core, graph-engine, graph-common, parseq
content-api/       # content mgmt: content-service, content-controllers, content-actors,
                   #   collection-csv-actors, hierarchy-manager
search-api/        # composite search: search-service, search-actors, search-core
assessment-api/    # QuestionSets: assessment-service, assessment-controllers, assessment-actors, qs-hierarchy-manager
taxonomy-api/      # categories/frameworks: taxonomy-service, taxonomy-controllers, taxonomy-actors
platform-modules/  # import-manager, mimetype-manager, url-manager
knowlg-service/    # general knowledge service
schemas/           # request JSON schemas (repo root); enforced by schema-validator
```

Each API service runs independently as a Netty/Play2 server on **port 9000**.

## Build & Test

```bash
mvn clean install -DskipTests                 # full build, skip tests
mvn clean install -DskipTests -pl content-api # single module
mvn test                                      # all tests
mvn test -pl <module> -Dtest=<TestClass>      # single test class
mvn clean install scoverage:report            # coverage (target/site/scoverage/)
```

Run a service: `cd <api>/<api>-service && mvn play2:run`; health check `curl http://localhost:9000/health`.

## Development Workflow

1. Feature branch from `develop` (primary branch is `master`; `develop` is integration).
2. Implement (usually in actors or managers) → write ScalaTest + ScalaMock tests.
3. `mvn test -pl {module}` → then `mvn clean install -DskipTests` to verify deps.
4. PR to `develop`.

## Commands

Slash-commands live in `.claude/commands/` (type `/<name>`):

| Command | Use |
|---|---|
| `/commit [hint]` | Conventional Commits commit (confirms before staging/committing; no AI trailer) |
| `/build [module]` | `mvn clean install -DskipTests` (optionally `-pl <module> -am`) |
| `/test [module] [TestClass]` | Run tests, scoped; summarize failures |
| `/coverage` | `mvn ... scoverage:report` + per-module summary |
| `/pr [base-branch]` | Open a PR (base defaults to `develop`) from `.github/pull_request_template.md` |

## Rules

Component rules live in `.claude/rules/` (auto-discovered). Path-scoped rules load only when you open a matching file.

| Rule file | Loads |
|---|---|
| `scala-conventions.md` | on `**/*.scala` — actor-per-request flow, controller/actor layout, Netty pinning |
| `base-class-pattern.md` | on actor/controller files — extend Java `BaseActor` + operation-match dispatch; Guice-singleton actors via ask pattern; per-service `BaseController` |
| `interfaces.md` | on `**/*.scala` — the `OntologyEngineContext` DI seam; `GraphService`; pluggable traits |
| `error-handling.md` | on `**/*.scala`, `**/*.java` — `MiddlewareException` hierarchy, `ResponseCode`, `ResponseHandler`, central `BaseActor` recovery |
| `logging-observability.md` | on `**/*.scala`, `**/*.java` — use `TelemetryManager`; `println`/`printStackTrace` anti-patterns |
| `configuration-discipline.md` | on `**/*.scala`, `**/*.java` — read config only via the `Platform` accessor; no direct `ConfigFactory` |
| `code-documentation.md` | on `**/*.scala`, `**/*.java` — Javadoc in Java core, light/why-focused comments in Scala |
| `testing.md` | on test files (`*Spec.scala`, `*Test.scala`, `src/test/**`) — ScalaTest/ScalaMock/TestKit conventions + requirements |
| `ontology-engine.md` | on `ontology-engine/**` — graph engine + JanusGraph/Neo4j migration note |
| `platform-core.md` | on `platform-core/**` — shared core modules |
| `schema-validation.md` | on `schemas/**`, `test_schema/**`, `schema-validator/**` — schema-driven validation |
| `content-api.md` | on `content-api/**` — content service |
| `search-api.md` | on `search-api/**` — search service |
| `assessment-api.md` | on `assessment-api/**` — assessment service |
| `taxonomy-api.md` | on `taxonomy-api/**` — taxonomy service |
| `service-config.md` | on `**/conf/**` — application.conf/routes/logback + local DB & debugging |
