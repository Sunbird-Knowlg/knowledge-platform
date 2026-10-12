---
paths:
  - "**/*.scala"
  - "**/*.java"
---

# Code documentation

Match the existing, deliberately uneven convention — do not mandate blanket Scaladoc.

- **Java `platform-core` carries full Javadoc** (class + method level, with `@author`
  headers) — e.g. `MiddlewareException.java`, `Platform.java`, `TelemetryManager.java`.
  Keep that standard when editing Java core: document public classes/methods, `@param`/
  `@return`/`@throws`.
- **Scala business logic (actors/controllers/managers) is intentionally light.** Document
  the **non-obvious — the *why***, not the mechanics. Good existing examples: offset-reset
  semantics and security/feature-flag notes explained in a short comment. Don't add
  Scaladoc to every method or restate what the code plainly says.
- Keep the `// section header` style used to group config/metric blocks.
