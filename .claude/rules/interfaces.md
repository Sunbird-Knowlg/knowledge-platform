---
paths:
  - "**/*.scala"
---

# Interfaces & dependency-injection seams

The graph layer is injected through a **context object**, not free-standing interfaces.

## Standard

- **`OntologyEngineContext` is the DI seam.**
  (`ontology-engine/graph-core_2.13/src/main/scala/org/sunbird/graph/OntologyEngineContext.scala`)
  It exposes `graphService`, `dialgraphService`, `httpUtil`, `kafkaClient`. Every actor
  takes it as an `implicit oec: OntologyEngineContext` constructor param
  (e.g. `FrameworkActor.scala:24`). New graph-touching code should depend on `oec.graphService`
  rather than constructing graph clients directly — this is the seam tests mock (see
  `testing.md`).
- **`GraphService` is a concrete class**, not a trait
  (`graph-core_2.13/.../GraphService.scala`) — it delegates to `NodeAsyncOperations` /
  `SearchAsyncOperations` and returns `Future`s.
- **Genuine traits exist for pluggable pieces** — implement these when adding a variant:
  `SchemaValidator`, `MimeTypeManager` + the mimetype `*Processor` traits, `RedisConnector`.

When adding a new pluggable capability, prefer a trait + implementation over a concrete
class only if it genuinely has multiple implementations; otherwise follow the existing
concrete-class-behind-`oec` style.
