package org.sunbird.search.strategy;

import org.opensearch.index.query.QueryBuilder;
import org.sunbird.search.dto.SearchDTO;
import org.sunbird.search.processor.SearchProcessor;
import org.sunbird.search.util.SearchConstants;

/**
 * Hybrid strategy. The full execution path (two searches + RRF) lives in
 * {@link org.sunbird.search.processor.HybridSearchExecutor}, which is dispatched
 * by SearchProcessor.processSearch.
 *
 * For leaf paths that only need a single QueryBuilder — processCount, the
 * public processSearchQuery overloads called from outside the actor, the
 * collection-mode fetch — falling back to the text query is correct. Hybrid
 * fusion is only meaningful when results are returned to the caller and can be
 * fused; count/exists/collection-children paths just need the filter set.
 */
public class HybridQueryStrategy implements QueryStrategy {

    @Override
    public String getMode() {
        return SearchConstants.SEARCH_MODE_HYBRID;
    }

    /**
     * Leaf-level fallback — returns the text query so that {@code processCount},
     * collection-hierarchy fetches, and any external {@code processSearchQuery} callers
     * continue to work correctly. The full hybrid path (parallel text + semantic legs
     * fused via RRF) is executed by {@link org.sunbird.search.processor.HybridSearchExecutor},
     * which intercepts before this strategy is reached in {@code processSearch}.
     */
    @Override
    public QueryBuilder build(SearchDTO dto, SearchProcessor processor) {
        // Leaf-level fallback: behave like text. Avoids breaking processCount,
        // getCollectionsResult, and any external processSearchQuery callers.
        return processor.buildTextQuery(dto);
    }
}
