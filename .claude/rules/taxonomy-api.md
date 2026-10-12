---
paths:
  - "taxonomy-api/**"
---

# taxonomy-api (categories / frameworks service)

Object categories and frameworks APIs. Submodules:

- `taxonomy-service` — Play2 application (runnable service)
- `taxonomy-controllers` — Play2 controllers
- `taxonomy-actors` — taxonomy business logic (Pekko actors)
- `api-tests` — API-level tests

Run: `cd taxonomy-api/taxonomy-service && mvn play2:run` (port 9000).

Note: `taxonomy-service-sbt/` at the repo root is a separate sbt-based variant — the Maven `taxonomy-api` is the primary build.
