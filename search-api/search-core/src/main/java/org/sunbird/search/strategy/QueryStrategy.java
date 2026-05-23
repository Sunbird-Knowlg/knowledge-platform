package org.sunbird.search.strategy;

import org.opensearch.index.query.QueryBuilder;
import org.sunbird.search.dto.SearchDTO;
import org.sunbird.search.processor.SearchProcessor;

/**
 * Strategy for building the OpenSearch query for a single search request.
 *
 * Implementations are stateless and thread-safe. The factory returns shared
 * singletons.
 *
 * Phase 1 ships only TextQueryStrategy (delegates to the existing logic on
 * SearchProcessor). Phase 2 adds SemanticQueryStrategy; Phase 3 adds
 * HybridQueryStrategy.
 */
public interface QueryStrategy {

    /** Mode key — matches request.search_mode. */
    String getMode();

    /**
     * Build the OpenSearch QueryBuilder for the given DTO.
     * The processor is supplied so the strategy can call back into shared
     * helpers (filter parsing, soft constraints, implicit filters, etc.)
     * without those helpers needing to be hoisted out of SearchProcessor in
     * Phase 1.
     */
    QueryBuilder build(SearchDTO dto, SearchProcessor processor) throws Exception;
}
