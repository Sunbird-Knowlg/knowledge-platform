---
paths:
  - "**/*.scala"
---

# Scala / Actor-per-request conventions

All business logic runs in **Apache Pekko** actors (Pekko 1.0.3, formerly Akka). Scala 2.13 with Java 11 compatibility, Play Framework 3.0.5 (Netty-based).

## Request flow

1. **Play2 route** (`conf/routes`) maps HTTP endpoints.
2. **Play2 controller** validates the request, builds a `Request` object.
3. **Actor** (a Guice-bound singleton `ActorRef`, invoked via the Pekko ask pattern — *not* created per request) executes business logic — enables async, non-blocking handling. See `base-class-pattern.md`.
4. **Graph Service** interacts with JanusGraph/Neo4j via `graph-engine`.
5. **Cassandra/Redis** persists/retrieves data.

Each API service runs independently as a Netty server on Play2.

## Code layout patterns

- **Actors:** `{service}/{actors}/src/main/scala/org/sunbird/{service}/actors/*Actor.scala`
- **Controllers:** `{service}/{controllers}/src/main/scala/org/sunbird/{service}/controllers/*Controller.scala`

## Dependency management gotcha

Netty versions are pinned in the play2 `pom.xml` to match the Play Framework version. Transitive dependency conflicts (especially Netty) are a common cause of build failures.
