---
paths:
  - "platform-core/**"
---

# platform-core (shared core utilities)

Shared foundation modules used by all services:

- `actor-core` — base actor classes for the actor-per-request pattern
- `platform-common` — common utilities
- `platform-cache` — Redis-backed caching
- `cassandra-connector` — Cassandra/Yugabyte DB integration
- `kafka-client` — Kafka message broker client
- `platform-telemetry` — telemetry/logging
- `schema-validator` — request schema validation (see the schema-validation rule)

Changes here ripple across every API service — build the full project to verify:
`mvn clean install -DskipTests`.
