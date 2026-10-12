---
paths:
  - "schemas/**"
  - "test_schema/**"
  - "platform-core/schema-validator/**"
---

# Schema-driven validation

- Request validation is performed by `platform-core/schema-validator`.
- JSON schemas live in the `schemas/` directory at the repo root, organized per object type
  (e.g. `content`, `collection`, `assessmentitem`, `category`, `channel`, `dialcode`).
- Versioned schema definitions allow **different API versions to coexist**.
- When adding/altering a request field, update the corresponding schema — validation is enforced from these files, not from ad-hoc code.
