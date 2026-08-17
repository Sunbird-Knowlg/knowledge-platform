---
paths:
  - "**/conf/**"
---

# Service configuration

Each runnable Play2 service keeps its config under `{service}/conf/`:

- **Service config:** `{service}/conf/application.conf` — DB (Neo4j/JanusGraph, Cassandra/Yugabyte, Redis), Kafka, host/port settings
- **Routes:** `{service}/conf/routes` — HTTP endpoint → controller mapping
- **Logging:** `{service}/conf/logback.xml` — enable debug logging here

## Debugging tips

- Health check: `curl http://localhost:9000/health`
- Database connection issues: verify `application.conf` host/port and that the Docker containers are running.
- Local DB bootstrap:
  ```bash
  mkdir -p ~/sunbird-dbs/{neo4j,cassandra,redis,es,kafka}
  export sunbird_dbs_path=~/sunbird-dbs
  sh ./local-setup.sh    # or: docker compose up
  ```
