package org.sunbird.search.strategy;

import org.sunbird.common.Platform;
import org.sunbird.common.exception.ClientException;
import org.sunbird.search.embedding.EmbeddingCache;
import org.sunbird.search.util.SearchConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Returns the {@link QueryStrategy} for a given mode.
 *
 * Phase 1 ships only the text strategy. Phase 2 will register a semantic
 * strategy; Phase 3 a hybrid strategy. Registration happens via {@link #register}
 * from initialisation code so this factory has no compile-time dependency on
 * embedding clients.
 */
public class QueryStrategyFactory {

    private static final Map<String, QueryStrategy> STRATEGIES = new HashMap<>();

    static {
        register(new TextQueryStrategy());
        // Semantic strategy is registered only when semantic_search.enabled = true.
        // The strategy itself is independent of the embedding service config; the
        // embedding client is resolved lazily inside the strategy via
        // EmbeddingClientFactory when the first semantic request is served.
        boolean semanticEnabled = Platform.config.hasPath("semantic_search.enabled")
                && Platform.config.getBoolean("semantic_search.enabled");
        if (semanticEnabled) {
            boolean cacheEnabled = !Platform.config.hasPath("semantic_search.embedding_cache.enabled")
                    || Platform.config.getBoolean("semantic_search.embedding_cache.enabled");
            int cacheSize = Platform.config.hasPath("semantic_search.embedding_cache.size")
                    ? Platform.config.getInt("semantic_search.embedding_cache.size") : 1024;
            long cacheTtl = Platform.config.hasPath("semantic_search.embedding_cache.ttl_seconds")
                    ? Platform.config.getLong("semantic_search.embedding_cache.ttl_seconds") : 300L;
            EmbeddingCache cache = new EmbeddingCache(cacheEnabled, cacheSize, cacheTtl);
            register(new SemanticQueryStrategy(cache));
            register(new HybridQueryStrategy());
        }
    }

    private QueryStrategyFactory() { }

    public static synchronized void register(QueryStrategy strategy) {
        STRATEGIES.put(strategy.getMode(), strategy);
    }

    public static QueryStrategy get(String mode) {
        String key = (mode == null || mode.isEmpty()) ? SearchConstants.SEARCH_MODE_TEXT : mode;
        QueryStrategy strategy = STRATEGIES.get(key);
        if (strategy == null) {
            // Semantic/hybrid mode requested but disabled in config — surface
            // a clear error rather than silently degrading.
            if (SearchConstants.SEARCH_MODE_SEMANTIC.equals(key)
                    || SearchConstants.SEARCH_MODE_HYBRID.equals(key)) {
                throw new ClientException(
                        SearchConstants.ERR_SEMANTIC_DISABLED,
                        "search_mode=" + key + " is not enabled on this server");
            }
            throw new ClientException(
                    SearchConstants.ERR_INVALID_SEARCH_MODE,
                    "Unsupported search_mode: " + mode);
        }
        return strategy;
    }

    /** Test-only. Do not call from production code paths. */
    static synchronized void resetForTest() {
        STRATEGIES.clear();
        register(new TextQueryStrategy());
    }
}
