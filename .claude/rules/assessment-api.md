---
paths:
  - "assessment-api/**"
---

# assessment-api (assessment / QuestionSet service)

QuestionSet and assessment-item APIs; integrated with the knowledge platform. Submodules:

- `assessment-service` — Play2 application (runnable service)
- `assessment-controllers` — Play2 controllers
- `assessment-actors` — assessment business logic (Pekko actors)
- `qs-hierarchy-manager` — QuestionSet hierarchy management

Run: `cd assessment-api/assessment-service && mvn play2:run` (port 9000).
