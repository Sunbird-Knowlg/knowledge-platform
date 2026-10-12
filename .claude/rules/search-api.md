---
paths:
  - "search-api/**"
---

# search-api (composite search service)

Composite and Assets search. Submodules:

- `search-service` — Play2 application (runnable service)
- `search-actors` — search business logic (Pekko actors)
- `search-core` — core search logic (Elasticsearch/OpenSearch integration, port 9200)
- `api-tests` — API-level tests

Run: `cd search-api/search-service && mvn play2:run` (port 9000).
