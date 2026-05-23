package org.sunbird.search.strategy;

import org.opensearch.index.query.QueryBuilder;
import org.sunbird.search.dto.SearchDTO;
import org.sunbird.search.processor.SearchProcessor;
import org.sunbird.search.util.SearchConstants;

/**
 * Marker strategy for hybrid mode. The actual execution path lives in
 * {@link org.sunbird.search.processor.HybridSearchExecutor} because hybrid
 * needs to issue two searches and fuse the ranked lists — something the
 * single-{@code QueryBuilder} contract of {@link QueryStrategy} cannot express.
 *
 * SearchProcessor.processSearch checks the mode first and routes through
 * HybridSearchExecutor when it is {@code hybrid}, so {@link #build} is never
 * called in practice. It exists only so QueryStrategyFactory can validate the
 * mode string and surface a clean error for misconfigured deployments.
 */
public class HybridQueryStrategy implements QueryStrategy {

    @Override
    public String getMode() {
        return SearchConstants.SEARCH_MODE_HYBRID;
    }

    @Override
    public QueryBuilder build(SearchDTO dto, SearchProcessor processor) {
        throw new UnsupportedOperationException(
                "HybridQueryStrategy is dispatched via HybridSearchExecutor, not QueryStrategy.build()");
    }
}
