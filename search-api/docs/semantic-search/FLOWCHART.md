# Semantic Search — Flowcharts

Mermaid diagrams. Render in any markdown viewer that supports mermaid.

## End-to-end request flow

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant CTL as SearchController
    participant MGR as SearchManager
    participant ACT as SearchActor
    participant DTO as getSearchDTO
    participant SP  as SearchProcessor
    participant QSF as QueryStrategyFactory
    participant ES  as OpenSearch

    Client->>CTL: POST /v3/search { search_mode, query, ... }
    CTL->>CTL: validate, header context, visibility
    CTL->>MGR: search(internalReq, actor)
    MGR->>ACT: ask(INDEX_SEARCH)
    ACT->>DTO: parse request → SearchDTO (incl. searchMode, semantic)
    DTO-->>ACT: SearchDTO
    ACT->>SP: processSearch(dto, true)
    SP->>QSF: get(dto.searchMode)
    QSF-->>SP: QueryStrategy (text|semantic|hybrid)
    SP->>ES: search(index, querystrategy.build(...))
    ES-->>SP: SearchResponse
    SP-->>ACT: { count, results, facets, params }
    ACT-->>MGR: response
    MGR-->>CTL: response
    CTL-->>Client: JSON
```

## Mode dispatch inside QueryStrategyFactory

```mermaid
flowchart LR
    A[SearchDTO.searchMode] --> B{mode?}
    B -- text --> C[TextQueryStrategy]
    B -- semantic --> D[SemanticQueryStrategy]
    B -- hybrid --> E[HybridQueryStrategy]
    B -- unknown --> X[ERR_INVALID_SEARCH_MODE]
```

## Semantic query path

```mermaid
flowchart TD
    A[request.query] --> B[EmbeddingCache.get]
    B -- hit --> H[byte 1536 vector]
    B -- miss --> C[EmbeddingClientFactory.get]
    C --> D{service}
    D -- openai --> E[OpenAIEmbeddingClient.embed]
    D -- e5 --> F[E5EmbeddingClient.embed]
    E --> G[float 1536 vector]
    F --> G
    G --> Q[QuantizationStrategy.quantize]
    Q --> H
    H --> I[build nested knn query]
    R[request.filters] --> I
    R2[request.semantic.schema_versions] --> I
    I --> J[ElasticSearchUtil.search]
    J --> K[hits with cosine score]
    K --> L[facets + sort + paginate]
    L --> M[Response]
```

## Hybrid + RRF

```mermaid
flowchart TD
    A[request] --> B[parallel:]
    B --> T[TextQueryStrategy.execute]
    B --> S[SemanticQueryStrategy.execute]
    T --> T2[ranked list text]
    S --> S2[ranked list semantic]
    T2 --> F[RrfFusion.fuse k=60]
    S2 --> F
    F --> P[score = Σ 1/k+rank_i]
    P --> Q[apply filters facets sort]
    Q --> R[paginate to limit]
    R --> M[Response with score_components]
```

## Embedding circuit breaker state

```mermaid
stateDiagram-v2
    [*] --> Closed
    Closed --> HalfOpen: N failures in window
    HalfOpen --> Open: probe failed
    HalfOpen --> Closed: probe succeeded
    Open --> HalfOpen: open_seconds elapsed
    Closed: serves embedding calls
    HalfOpen: allow one probe
    Open: every semantic request degrades to text
```

## Degraded fallback

```mermaid
sequenceDiagram
    autonumber
    participant SP as SearchProcessor
    participant QSF as QueryStrategyFactory
    participant EC as EmbeddingClient
    participant CB as CircuitBreaker
    participant ES as OpenSearch

    SP->>QSF: get(semantic)
    QSF-->>SP: SemanticQueryStrategy
    SP->>CB: allow?
    alt breaker open
        CB-->>SP: no
        SP->>SP: degraded=true, reason=circuit_open
        SP->>QSF: get(text)
        QSF-->>SP: TextQueryStrategy
    else breaker closed
        SP->>EC: embed(query)
        alt success
            EC-->>SP: vector
            SP->>ES: nested knn query
        else timeout/error
            EC-->>SP: error
            SP->>CB: recordFailure
            SP->>SP: degraded=true, reason=embedding_unavailable
            SP->>QSF: get(text)
            QSF-->>SP: TextQueryStrategy
            SP->>ES: text query
        end
    end
    ES-->>SP: hits
    SP-->>SP: response with params.degraded
```

## Component dependency

```mermaid
graph LR
    subgraph search-actors
        SBA[SearchBaseActor]
        SA[SearchActor]
    end
    subgraph search-core
        SP[SearchProcessor]
        QSF[QueryStrategyFactory]
        TQS[TextQueryStrategy]
        SQS[SemanticQueryStrategy]
        HQS[HybridQueryStrategy]
        EF[EmbeddingClientFactory]
        OAI[OpenAIEmbeddingClient]
        E5[E5EmbeddingClient]
        EC[EmbeddingCache]
        QF[QuantizationStrategyFactory]
        I8[Int8QuantizationStrategy]
        RRF[RrfFusion]
        CB[EmbeddingCircuitBreaker]
        ESU[ElasticSearchUtil]
    end
    subgraph search-service
        CTL[SearchController]
        MGR[SearchManager]
    end
    CTL --> MGR --> SA --> SBA --> SP
    SP --> QSF
    QSF --> TQS
    QSF --> SQS
    QSF --> HQS
    SQS --> EF --> OAI
    EF --> E5
    SQS --> EC
    SQS --> QF --> I8
    SQS --> CB
    HQS --> TQS
    HQS --> SQS
    HQS --> RRF
    TQS --> ESU
    SQS --> ESU
```
