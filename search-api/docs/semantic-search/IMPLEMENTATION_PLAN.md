# Semantic Search — Implementation Plan

Five phases. Each phase is independently shippable behind a config flag. No phase regresses behaviour for `search_mode = text` requests.

## Phase 1 — Strategy refactor (zero behaviour change)

Goal: move the existing monolithic query building into a `QueryStrategy` interface so the next phases plug in cleanly.

Files added in `search-core/src/main/java/org/sunbird/search/strategy/`:

| File | Purpose |
|---|---|
| `QueryStrategy.java` | Interface: `SearchSourceBuilder build(SearchDTO dto, List<Map<String,Object>> groupBy, boolean sortBy)` |
| `TextQueryStrategy.java` | Wraps existing logic from `SearchProcessor.processSearchQuery`. Initial impl just delegates to package-private helpers; over time, helpers migrate inside. |
| `QueryStrategyFactory.java` | Returns a strategy for a mode string. Phase 1 returns only `TextQueryStrategy`. |

Files modified:

| File | Change |
|---|---|
| `SearchProcessor.java` | Inject `QueryStrategyFactory`. Replace the inline `processSearchQuery(...)` call with `factory.get(dto.getSearchMode()).build(...)`. |
| `SearchBaseActor.java` `getSearchDTO` | Read `request.search_mode` (default `"text"`). Stash on DTO. |
| `SearchDTO.java` | New field `searchMode` (default `"text"`). |

Acceptance:

- All existing actor tests (`SearchActorTest`) pass without modification.
- All existing api-tests (Postman collection) pass.
- No new config keys yet.

## Phase 2a — Embedding clients (no query wiring)

Goal: build the pluggable embedding stack inside `search-core`. Mirrors `content-embedding-job`'s `embedding-services` module.

Files added in `search-core/src/main/java/org/sunbird/search/embedding/`:

| File | Purpose |
|---|---|
| `EmbeddingClient.java` | Interface: `embed`, `embedBatch`, `getName`, `getVersion`, `getDimensions`, `close`. |
| `EmbeddingClientConfig.java` | Plain POJO carrying service name, dimensions, timeout, host/port, apiKey, azure endpoint/deployment/api-version. |
| `OpenAIEmbeddingClient.java` | Java port of `OpenAIEmbeddingService`. Uses `java.net.http.HttpClient`. Supports OpenAI + Azure mode switch. Reads `api_key` strictly (fails fast on empty). |
| `E5EmbeddingClient.java` | Java port of `E5EmbeddingService`. Host validated against internal allowlist (loopback / RFC1918 / `.svc.cluster.local` / `.internal`). |
| `EmbeddingClientFactory.java` | Returns a client by name. Reads `semantic_search.embedding_service`. |
| `EmbeddingCache.java` | LRU + TTL keyed on `sha256(service:model:text)`. |

Files added under `search-core/src/main/java/org/sunbird/search/quantization/`:

| File | Purpose |
|---|---|
| `QuantizationStrategy.java` | Interface: `byte[] quantize(float[] v)`, `getName`, `getVersion`. |
| `Int8QuantizationStrategy.java` | Same L2-norm detection branch the job uses. |
| `QuantizationStrategyFactory.java` | Returns strategy by name (`int8` only at v1). |

Config additions in `application.conf` (disabled by default):

```hocon
semantic_search {
  enabled = false
  embedding_service = "openai"
  openai { ... }
  e5 { ... }
  quantization_strategy = "int8"
  embedding_cache { enabled = true, size = 1024, ttl_seconds = 300 }
}
```

Acceptance:

- Unit tests for `Int8QuantizationStrategy` (normalized vs unnormalized branches).
- Unit tests for `EmbeddingCache` (eviction, TTL).
- Integration test for `OpenAIEmbeddingClient` against a stubbed HTTP server.
- E5 host validation rejects public hostnames.

## Phase 2b — SemanticQueryStrategy

Goal: wire `search_mode = semantic` end-to-end.

Files added in `search-core/src/main/java/org/sunbird/search/strategy/`:

| File | Purpose |
|---|---|
| `SemanticQueryStrategy.java` | Embed query → quantize → build nested `knn` query on `chunks.embedding`. Applies filters via existing helpers. |

Files modified:

| File | Change |
|---|---|
| `QueryStrategyFactory.java` | Now dispatches on mode: `text` / `semantic`. |
| `SearchProcessor.java` | If mode is semantic and `semantic_search.enabled = false`, return `ERR_SEMANTIC_DISABLED`. If embedding fails → set `degraded = true` on the result map, swap strategy to text, retry. |
| `SearchBaseActor.getSearchDTO` | Parse `request.semantic` object into `SearchDTO.semantic`. |
| `SearchController.java` | Validate `search_mode` enum + `semantic.k` bounds. Return `ERR_SEMANTIC_QUERY_REQUIRED` if query is missing for non-text mode. |
| Response builder | Add `params.search_mode`, `params.degraded`, `params.degraded_reason`, `params.embedding_ms`, `params.search_ms`. |

Acceptance:

- Unit test: `SemanticQueryStrategy.build` produces expected nested kNN DSL.
- Integration test against local OpenSearch with seeded chunks.
- Circuit breaker test: 10 consecutive timeouts opens the breaker; subsequent calls go straight to text.

## Phase 3 — HybridQueryStrategy + RRF

Files added in `search-core/src/main/java/org/sunbird/search/{fusion,strategy}/`:

| File | Purpose |
|---|---|
| `RrfFusion.java` | `List<FusedHit> fuse(List<List<Hit>> rankedLists, int k)`. Pure function, no IO. |
| `HybridQueryStrategy.java` | Builds and executes both text + semantic in parallel via `CompletableFuture` on `semantic-embedding-pool`. Fuses with RRF. Applies filters/facets post-fusion. |

Files modified:

| File | Change |
|---|---|
| `QueryStrategyFactory.java` | Adds `hybrid`. |
| Response builder | Adds `params.fusion_ms`, per-result `score_components`. |

Acceptance:

- Unit test: `RrfFusion` produces known output on synthetic ranked lists.
- Integration test: hybrid mode returns union of text + semantic results, sorted by RRF score.
- Latency budget test: hybrid p95 < `embedding_ms + max(text_ms, semantic_ms) + 50ms`.

## Phase 4 — Observability + ops

| Change | Where |
|---|---|
| Dropwizard metrics registration | New `SemanticSearchMetrics` class. |
| Circuit breaker | `EmbeddingCircuitBreaker` — failure-window driven. |
| Startup log: embedding service + dimensions | `SearchService` boot. |
| Mode-aware INFO logs | `SearchProcessor`. |

Acceptance:

- `/health` endpoint surfaces breaker state.
- Metrics scraped by Prometheus exporter.

## Phase 5 — Helm chart + ConfigMap

`sunbird-spark-installer` change in `helmcharts/knowledgebb/charts/search-service/values.yaml` (or wherever the search-api Helm chart lives):

```yaml
semantic_search:
  enabled: true
  embedding_service: "openai"
  openai:
    api_key: ""           # ops fills in
    model: "text-embedding-3-small"
    dimensions: 1536
    azure_endpoint: "https://coss-semanticsearch.openai.azure.com/"
    azure_deployment: "text-embedding-3-small"
    azure_api_version: "2023-05-15"
```

Acceptance:

- Helm template renders.
- ConfigMap reflects values.

## Test strategy

- **Unit:** each `QueryStrategy`, each `EmbeddingClient`, `Int8QuantizationStrategy`, `RrfFusion`, `EmbeddingCache`, `EmbeddingCircuitBreaker`.
- **Integration:** local OpenSearch with seeded docs + mocked embedding HTTP. Cover text / semantic / hybrid happy paths and the degraded path.
- **Contract:** existing Postman collection runs green untouched.
- **Load:** semantic p95 < 500ms with warm cache, < 1.5s cold.

## Rollback

Set `semantic_search.enabled = false`. All non-text requests return `ERR_SEMANTIC_DISABLED`; text requests continue to work because Phase 1 was behaviour-neutral.

## Risks tracked

See `DESIGN.md → Risks & mitigations`. Open risks:

- Vector-space drift between job and api (mitigated by config + startup log + ops alarm).
- Embedding API cost spike (mitigated by LRU cache + future per-consumer rate limit).
- Hybrid result explosion (mitigated by capping union at `max(text.limit, semantic.k) * 2`).

## Ownership / sequencing

Phase 1 → Phase 2a → Phase 2b → Phase 3 → Phase 4 → Phase 5. Phase 1 is a refactor; safe to merge immediately. Phase 2a is a library addition; safe to merge with `enabled = false`. Phase 2b activates semantic on prod after a soak in lower envs.
