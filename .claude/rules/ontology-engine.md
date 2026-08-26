---
paths:
  - "ontology-engine/**"
---

# Ontology engine (knowledge graph)

The knowledge-graph engine. Submodules:

- `graph-dac-api` — data access layer (lowest level graph access)
- `graph-core_2.13` — core graph operations
- `graph-engine_2.13` — high-level graph API (services call this)
- `graph-common` — shared utilities
- `parseq` — async composition utility

Graph queries go through `graph-engine_2.13` → `graph-dac-api`.

## Graph database migration (important architectural note)

- The backend recently migrated **from pure Neo4j to JanusGraph**.
- JanusGraph provides better scalability and multi-backend support (CQL-backed on Yugabyte, port 8182).
- Both backends may still be referenced; changes to graph access must account for the JanusGraph path.
