# Semantic Search — Design

## Goals

1. Add semantic search to `POST /v3/search` without breaking existing text-search clients.
2. Reuse the **pluggable embedding + int8 quantization** architecture already shipped by `content-embedding-job` so vectors are produced and consumed by the same algorithms.
3. Support three modes: `text` (current behaviour), `semantic`, `hybrid`.

## Non-goals

- No query-time chunking. Chunking is index-time only and lives in `content-embedding-job`.
- No new index. Reads from existing `compositesearch` alias.
- No re-ranking model. Hybrid uses Reciprocal Rank Fusion (RRF) only.

## Core architecture invariants

The embedding job stamps every chunk with:

| Field | Type | Notes |
|---|---|---|
| `chunks.text` | text | source text of chunk |
| `chunks.embedding` | `knn_vector` (byte, dim 1536) | **int8-quantized**, cosine, HNSW (Lucene engine), ef_construction 128, m 16 |
| `chunks.word_count` | integer | |
| `chunks.chunk_index` | integer | |
| `chunks.schema_version` | keyword | source `_schema_version` of event |

Search-api must produce a **byte vector that lives in the same space** as the stored vectors. That means:

- Same embedding model (`text-embedding-3-small`, 1536d, normalized) — or whichever the job is configured for.
- Same quantization (`byte = round(v × 127)` for L2-normalized vectors; otherwise per-vector min-max).

## High-level component map

```
search-api/
  search-core/
    src/main/java/org/sunbird/search/
      strategy/
        QueryStrategy.java              (new, interface)
        TextQueryStrategy.java          (new, current logic moved here)
        SemanticQueryStrategy.java      (new)
        HybridQueryStrategy.java        (new)
        QueryStrategyFactory.java       (new)
      embedding/
        EmbeddingClient.java            (new, interface — mirrors job's EmbeddingService)
        EmbeddingClientFactory.java     (new)
        OpenAIEmbeddingClient.java      (new — OpenAI + Azure)
        E5EmbeddingClient.java          (new)
        EmbeddingClientConfig.java      (new)
      quantization/
        QuantizationStrategy.java       (new, interface — mirrors job's QuantizationStrategy)
        QuantizationStrategyFactory.java(new)
        Int8QuantizationStrategy.java   (new)
      fusion/
        RrfFusion.java                  (new)
      processor/
        SearchProcessor.java            (modified: delegates to QueryStrategy)
```

### Why mirror, not depend on job's modules

`content-embedding-job` is a Flink job tree with Scala + Flink runtime deps. Pulling its modules into search-api would drag in Flink, Kafka, Cassandra clients we do not need at query time. The interfaces are tiny (~3 methods each) — re-implementing in Java inside `search-core` is cheaper than the dependency surface area.

The names are kept identical (`EmbeddingService` → `EmbeddingClient` to disambiguate; `QuantizationStrategy` stays) so cross-repo reasoning stays easy.

## Data flow

### `search_mode = text`
Identical to today. The refactor only moves existing logic behind `TextQueryStrategy`. Zero behavioural delta.

### `search_mode = semantic`

```
Request → SearchController → SearchActor → getSearchDTO()
   │                                          │
   │                                          ▼
   │                              search_mode = "semantic"
   │                              query = "fractions for class 5"
   ▼
SearchProcessor.processSearch()
   │
   ▼
QueryStrategyFactory.get("semantic") → SemanticQueryStrategy
   │
   ├── EmbeddingClient.embed(query)         ← pluggable: OpenAI/E5
   │      → float[1536]
   │
   ├── QuantizationStrategy.quantize(vec)   ← pluggable: int8
   │      → byte[1536]
   │
   └── builds nested kNN OpenSearch DSL on chunks.embedding
   │
   ▼
ElasticSearchUtil.search(index, query) → SearchResponse
   │
   ▼
Filters / facets / sort applied via existing pipeline
   │
   ▼
Response (with score per doc, score_components if hybrid)
```

### `search_mode = hybrid`

```
HybridQueryStrategy
   │
   ├── parallel:
   │     ├── TextQueryStrategy.execute()      → ranked list A
   │     └── SemanticQueryStrategy.execute()  → ranked list B
   │
   ├── RrfFusion.fuse(A, B, k=60)             → union ranked by Σ 1/(k+rank)
   │
   └── slice to request.limit, apply facets on fused set
```

## Pluggability — embedding client

```java
public interface EmbeddingClient {
  String getName();         // "openai" | "e5"
  String getVersion();
  int    getDimensions();   // must match index mapping
  float[] embed(String text);              // single
  List<float[]> embedBatch(List<String> texts);
  void close();
}
```

Factory looks at `semantic_search.embedding_service` config key. Same enum as embedding job.

## Pluggability — quantization

```java
public interface QuantizationStrategy {
  String getName();         // "int8"
  String getVersion();
  byte[] quantize(float[] vector);
}
```

`Int8QuantizationStrategy` implements the same L2-norm detection branch as the job: if `‖v‖ ≈ 1`, use global scale `byte = round(v × 127)`; otherwise per-vector min-max.

## Vector-space contract

Both embedding job and search-api MUST agree on:

| Parameter | Source of truth | Today's value |
|---|---|---|
| Embedding model | embedding job config | `text-embedding-3-small` (OpenAI) |
| Dimensions | OpenSearch mapping | 1536 |
| Normalization | model output | normalized (OpenAI 3-series is L2-normalized) |
| Quantization | embedding job | int8 byte |
| Distance | index mapping | cosine |

A mismatch is an operational incident — query vectors will sit in a different space than indexed vectors and recall collapses silently. Mitigation: search-api logs the configured `embedding_service` + `dimensions` at startup; ops compares with the embedding job's config.

## Request-time embedding cache

In-process LRU. Key = `sha256(embedding_service + ":" + model + ":" + query_text)`. TTL 5 minutes. Avoids re-embedding the top hot queries and trims OpenAI cost on repeated traffic.

Cache size: default 1024 entries, configurable via `semantic_search.embedding_cache.size`.

## Fallback / circuit breaker

- Embedding HTTP failure or timeout (default 5s) → fall back to `text` mode.
- Response carries `params.degraded = true` and `params.degraded_reason = "embedding_unavailable"`.
- Failure counter increments `semantic_disabled_fallback_count`.
- After N consecutive failures (default 10 in 30s) → circuit opens for 60s, every semantic/hybrid request falls through to text without trying the embedding API.

## Configuration

`application.conf` additions:

```hocon
semantic_search {
  enabled = true

  embedding_service = "openai"   # "openai" | "e5"

  openai {
    api_key = ${?OPENAI_API_KEY}
    model = "text-embedding-3-small"
    dimensions = 1536
    timeout = 5
    azure_endpoint = ${?AZURE_OPENAI_ENDPOINT}
    azure_deployment = ${?AZURE_OPENAI_DEPLOYMENT}
    azure_api_version = "2023-05-15"
  }

  e5 {
    host = "e5-embedding.semantic.svc.cluster.local"
    port = 80
    dimensions = 768
    timeout = 5
  }

  quantization_strategy = "int8"

  vector_field = "chunks.embedding"
  vector_path  = "chunks"

  default_k = 50
  max_k     = 1000
  min_score = 0.0

  schema_versions = ["1.0"]    # passed as filter on chunks.schema_version

  rrf_k = 60                    # hybrid fusion constant

  embedding_cache {
    enabled = true
    size = 1024
    ttl_seconds = 300
  }

  circuit_breaker {
    failure_threshold = 10
    window_seconds = 30
    open_seconds = 60
  }
}
```

## Observability

Metrics (Dropwizard, reuse existing search-api metric infra):

| Metric | Tag | Meaning |
|---|---|---|
| `semantic_search.embedding_ms` | service | latency per embedding call |
| `semantic_search.search_ms` | mode | OpenSearch round-trip per mode |
| `semantic_search.fusion_ms` | — | RRF compute time |
| `semantic_search.cache_hit_total` | — | embedding cache hits |
| `semantic_search.cache_miss_total` | — | cache misses |
| `semantic_search.degraded_total` | reason | fallback count |
| `semantic_search.circuit_open_total` | — | times breaker opened |

Logs at INFO: mode, query text length, embedding ms, search ms, total hits. Debug: filters dump.

## Threading

- Embedding HTTP must not run on the Akka actor dispatcher. Dedicated `ForkJoinPool` (parallelism = `Math.max(8, cpu*2)`) named `semantic-embedding-pool`.
- Hybrid runs text and semantic on separate threads via `CompletableFuture.supplyAsync(..., pool)`.

## Security

- Query text is sent to the embedding service (OpenAI/Azure). Same data-egress consideration as the embedding job. Already approved at infra level.
- API key sourced from env var, never inlined in config maps in code path. Same pattern as embedding job's `OPENAI_API_KEY`.
- E5 host is validated as internal (mirror the embedding job's check) to prevent SSRF when env var is overridden.

## Backward compatibility

- Default `search_mode` is `text`. All existing requests behave identically.
- `request.semantic` block is ignored when `search_mode = text`.
- Existing response fields unchanged. New fields are additive under `params` and per-result `score_components` (only in `hybrid`).

## Open questions deferred to later phases

- Should we cache OpenSearch responses on the query path? Not in v1 — premature.
- Should we expose per-consumer rate limits on semantic mode? Defer.
- Should we add MMR diversification on hybrid output? Defer.
