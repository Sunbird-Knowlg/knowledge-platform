---
paths:
  - "**/*Spec.scala"
  - "**/*Test.scala"
  - "**/src/test/**"
---

# Testing conventions

Testing stack: **ScalaTest 3.0.8 + ScalaMock 4.4.0 + Pekko TestKit**.

- Tests extend a `BaseSpec` trait, typically at
  `{service}/{actors}/src/test/scala/org/sunbird/actors/BaseSpec.scala`.
- Tests **mock `OntologyEngineContext` and `GraphService`** to avoid real database connections.
- Actor tests use **TestKit with a 10-second message timeout** — if tests hang/fail, check this limit first.

## Running tests

```bash
mvn test                                                   # all tests
mvn test -pl taxonomy-api/taxonomy-actors                  # single module
mvn test -pl taxonomy-api/taxonomy-actors -Dtest=ObjectCategoryActorTest        # single class
mvn test -pl taxonomy-api/taxonomy-actors -Dtest=ObjectCategoryActorTest#*should*create*   # single method (glob)
mvn clean install scoverage:report                         # coverage
```

Coverage reports (Scoverage / JaCoCo) are generated under `target/site/scoverage/` for each module.

## Requirements

- **Cover normal, edge, and failure paths** — not just the happy case (e.g. also the
  `ClientException`/`ResourceNotFoundException` branches; see `error-handling.md`).
- **Mock the DI seams, never hit real DBs**: `implicit val oec = mock[OntologyEngineContext]`
  and `val graphDB = mock[GraphService]`, then stub `oec.graphService` to return the mock
  (see `CategoryActorTest.scala:17,25-26`). This is the seam described in `interfaces.md`.
- Coverage is measured by **Scoverage** (`mvn clean install scoverage:report`). Fence
  untestable bootstrap code with `// $COVERAGE-OFF$ … // $COVERAGE-ON$` (e.g. `*Module.scala`).
- **No PowerMock.** Scala modules use ScalaMock; the Java modules use Mockito.
- Existing tests use the deprecated `FlatSpec` + `Matchers` style (not `AnyFlatSpec`) —
  stay consistent with the surrounding files rather than mixing styles.
