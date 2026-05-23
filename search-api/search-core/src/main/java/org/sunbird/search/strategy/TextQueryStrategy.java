package org.sunbird.search.strategy;

import org.opensearch.index.query.QueryBuilder;
import org.sunbird.search.dto.SearchDTO;
import org.sunbird.search.processor.SearchProcessor;
import org.sunbird.search.util.SearchConstants;

/**
 * Existing text-search behaviour. Delegates to the SearchProcessor's existing
 * query builder unchanged. Phase 1: zero behavioural delta vs pre-refactor.
 */
public class TextQueryStrategy implements QueryStrategy {

    @Override
    public String getMode() {
        return SearchConstants.SEARCH_MODE_TEXT;
    }

    @Override
    public QueryBuilder build(SearchDTO dto, SearchProcessor processor) {
        return processor.buildTextQuery(dto);
    }
}
