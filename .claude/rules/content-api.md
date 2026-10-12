---
paths:
  - "content-api/**"
---

# content-api (content management service)

Content V3+V4 APIs, Collections, and Assets. Submodules:

- `content-service` — Play2 application (runnable service)
- `content-controllers` — Play2 controllers
- `content-actors` — content business logic (Pekko actors)
- `collection-csv-actors` — CSV import/export for collections
- `hierarchy-manager` — collection/content hierarchy management
- `api-tests` — API-level tests

Run: `cd content-api/content-service && mvn play2:run` (port 9000).
