# Semantic Search — API Spec

Endpoint: `POST /v3/search` (existing). This document only lists **additions** to the contract. All existing fields work unchanged.

## Request

### New top-level fields under `request`

| Field | Type | Default | Description |
|---|---|---|---|
| `search_mode` | enum string | `"text"` | One of `text`, `semantic`, `hybrid`. |
| `semantic` | object | `{}` | Semantic knobs. Ignored when `search_mode = text`. |

### `request.semantic` object

| Field | Type | Default (server) | Description |
|---|---|---|---|
| `k` | int | `50` | Top-k chunks fetched by kNN per shard. Max `1000`. |
| `min_score` | float | `0.0` | Drop chunks with cosine score below this. Range `[0,1]`. |
| `schema_versions` | string[] | server config | Limit kNN to chunks with these `schema_version` keyword values. Use during reindex windows. |
| `rrf_k` | int | `60` | Reciprocal rank fusion constant. Lower = top results dominate. Hybrid only. |
| `vector_field` | string | `"chunks.embedding"` | Override for A/B testing alternate vector fields. |

### Example — semantic

```json
POST /v3/search
{
  "id": "api.v1.search",
  "ver": "1.0",
  "ts": "2026-05-24T10:30:00Z",
  "params": { "msgid": "...", "cid": "consumer-1" },
  "request": {
    "query": "fractions for class 5",
    "filters": {
      "status": ["Live"],
      "objectType": ["Content"],
      "board": ["CBSE"]
    },
    "fields": ["identifier", "name", "description"],
    "limit": 20,
    "offset": 0,
    "search_mode": "semantic",
    "semantic": {
      "k": 50,
      "min_score": 0.4,
      "schema_versions": ["1.0"]
    }
  }
}
```

### Example — hybrid

```json
{
  "request": {
    "query": "fractions for class 5",
    "filters": { "status": ["Live"] },
    "limit": 20,
    "search_mode": "hybrid",
    "semantic": { "k": 100, "rrf_k": 60 }
  }
}
```

### Example — text (no change from today)

```json
{
  "request": {
    "query": "fractions for class 5",
    "filters": { "status": ["Live"] },
    "limit": 20
  }
}
```

## Response

### Existing fields unchanged

`id`, `ver`, `ts`, `params.status`, `params.errmsg`, `params.err`, `result.count`, `result.results`, `result.facets` are all unchanged.

### New fields under `params`

| Field | Type | Always present? | Description |
|---|---|---|---|
| `search_mode` | enum string | yes | Echo of mode actually applied. May differ from request when degraded. |
| `degraded` | boolean | yes | `true` if request asked for `semantic`/`hybrid` but fell back to `text`. |
| `degraded_reason` | string | only when `degraded=true` | `"embedding_unavailable"` / `"semantic_disabled"` / `"circuit_open"`. |
| `embedding_ms` | long | semantic + hybrid | Embedding service latency (0 if cache hit). |
| `search_ms` | long | yes | OpenSearch query latency. |
| `fusion_ms` | long | hybrid only | RRF compute latency. |

### New fields per result (only in hybrid mode)

| Field | Type | Description |
|---|---|---|
| `score_components.text_rank` | int (1-based) | Rank in text result set. `null` if not matched by text. |
| `score_components.semantic_rank` | int (1-based) | Rank in semantic result set. `null` if not matched by semantic. |
| `score_components.text_score` | float | Raw text score from OpenSearch. |
| `score_components.semantic_score` | float | Cosine similarity from kNN. |

`score` at result level:
- `text` mode: existing OpenSearch text score (when `fuzzy=true` was already enabled today).
- `semantic` mode: cosine similarity, range `[0, 1]`.
- `hybrid` mode: fused RRF score, sum of `1/(rrf_k + rank_i)` across each ranked list.

### Example response — semantic

```json
{
  "id": "api.v1.search",
  "ver": "1.0",
  "ts": "2026-05-24T10:30:01Z",
  "params": {
    "msgid": "...",
    "resmsgid": "uuid",
    "status": "successful",
    "search_mode": "semantic",
    "degraded": false,
    "embedding_ms": 142,
    "search_ms": 87
  },
  "result": {
    "count": 38,
    "results": [
      {
        "identifier": "do_213",
        "name": "Intro to Fractions",
        "description": "...",
        "score": 0.913
      }
    ],
    "facets": []
  }
}
```

### Example response — hybrid

```json
{
  "params": {
    "search_mode": "hybrid",
    "degraded": false,
    "embedding_ms": 0,
    "search_ms": 124,
    "fusion_ms": 3
  },
  "result": {
    "count": 57,
    "results": [
      {
        "identifier": "do_213",
        "name": "Intro to Fractions",
        "score": 0.0312,
        "score_components": {
          "text_rank": 4,
          "semantic_rank": 1,
          "text_score": 12.5,
          "semantic_score": 0.913
        }
      }
    ]
  }
}
```

### Example response — degraded

Client asked for `semantic`, embedding API failed:

```json
{
  "params": {
    "search_mode": "text",
    "degraded": true,
    "degraded_reason": "embedding_unavailable",
    "embedding_ms": 5001,
    "search_ms": 41
  },
  "result": { "count": 1200, "results": [ /* text results */ ] }
}
```

## Errors

| Code | HTTP | When |
|---|---|---|
| `ERR_SEMANTIC_QUERY_REQUIRED` | 400 | `search_mode != "text"` and `query` is empty/missing |
| `ERR_SEMANTIC_DISABLED` | 503 | `search_mode != "text"` and server `semantic_search.enabled = false` |
| `ERR_INVALID_SEARCH_MODE` | 400 | unknown enum value |
| `ERR_SEMANTIC_K_TOO_HIGH` | 400 | `semantic.k > semantic_search.max_k` |
| `ERR_SEMANTIC_K_INVALID` | 400 | `semantic.k <= 0` |
| `ERR_SEMANTIC_MIN_SCORE_INVALID` | 400 | `min_score` not in `[0,1]` |

## Behavioral contract

- `query` is required for `search_mode != text`. For `text`, it remains optional (filters-only search is allowed, same as today).
- Filters from `request.filters` apply to all modes. In `semantic` mode they are added as a bool `filter` clause beside the nested kNN.
- Facets are computed over the final result set (after filters; after fusion in hybrid).
- Sort is honored in `text` mode as today. In `semantic` and `hybrid`, the default sort is by `score` desc. Explicit `sort_by` overrides.
- Pagination (`limit`, `offset`) is honored in all modes, applied after fusion in hybrid.
- A `semantic` or `hybrid` request that matches zero chunks returns `count: 0` and `results: []` — not an error.

## Underlying OpenSearch DSL (informational)

`semantic` mode renders to:

```json
{
  "query": {
    "bool": {
      "must": [
        {
          "nested": {
            "path": "chunks",
            "score_mode": "max",
            "query": {
              "bool": {
                "must": [
                  {
                    "knn": {
                      "chunks.embedding": {
                        "vector": [/* byte[1536] */],
                        "k": 50
                      }
                    }
                  }
                ],
                "filter": [
                  { "terms": { "chunks.schema_version": ["1.0"] } }
                ]
              }
            }
          }
        }
      ],
      "filter": [/* status, objectType, etc. */]
    }
  }
}
```

`hybrid` issues two separate queries and fuses application-side. This avoids depending on `rank_features` / hybrid query APIs that vary across OpenSearch versions.

## Deprecation policy

No existing field is deprecated. Adding new fields under `params` and per-result `score_components` is additive; clients that ignore unknown JSON fields are unaffected.
