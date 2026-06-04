package org.sunbird.search.strategy;

import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.WrapperQueryBuilder;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ClientException;
import org.sunbird.search.dto.SearchDTO;
import org.sunbird.search.embedding.EmbeddingCache;
import org.sunbird.search.embedding.EmbeddingClient;
import org.sunbird.search.embedding.EmbeddingClientFactory;
import org.sunbird.search.processor.SearchProcessor;
import org.sunbird.search.quantization.QuantizationStrategy;
import org.sunbird.search.quantization.QuantizationStrategyFactory;
import org.sunbird.search.util.SearchConstants;

import java.util.List;
import java.util.Map;

/**
 * Builds a nested kNN query against the int8-quantized {@code chunks.embedding}
 * field. Filter, soft-constraint, exists/not-exists clauses from the existing
 * text path are added as a bool {@code filter} alongside the kNN, so all the
 * existing facet/sort/pagination behaviour continues to apply.
 *
 * Note on Pluggability:
 *  - Embedding client (OpenAI / E5) is resolved at runtime through
 *    {@link EmbeddingClientFactory}.
 *  - Vector quantization (int8 today) is resolved through
 *    {@link QuantizationStrategyFactory}.
 *  - Both factories read the same config keys the embedding job uses so the
 *    query-time and index-time vectors share a byte-space contract.
 */
public class SemanticQueryStrategy implements QueryStrategy {

    private static final String DEFAULT_VECTOR_FIELD = "chunks.embedding";
    private static final String DEFAULT_VECTOR_PATH  = "chunks";

    private final EmbeddingCache cache;

    public SemanticQueryStrategy(EmbeddingCache cache) {
        this.cache = cache;
    }

    @Override
    public String getMode() {
        return SearchConstants.SEARCH_MODE_SEMANTIC;
    }

    /**
     * Builds a nested kNN query for semantic search.
     *
     * <ol>
     *   <li>Extracts the query string from the {@code propertyName="*"} property entry.</li>
     *   <li>Embeds the query text via {@link org.sunbird.search.embedding.EmbeddingClientFactory},
     *       backed by an in-process LRU+TTL cache.</li>
     *   <li>Quantizes the float vector to int8 bytes via {@link org.sunbird.search.quantization.QuantizationStrategyFactory}.</li>
     *   <li>Builds an OpenSearch kNN JSON query against the configured {@code vector_field}.</li>
     *   <li>Inherits all existing filters (property filters, must-not, implicit-filter should clauses)
     *       from the text query path, demoting text-scoring clauses to filters so kNN owns ranking.</li>
     *   <li>Applies optional {@code schema_versions} filter on the nested path.</li>
     * </ol>
     *
     * @throws org.sunbird.common.exception.ClientException if the query string is missing
     *         ({@code ERR_SEMANTIC_QUERY_REQUIRED}), or if {@code k} is out of range.
     */
    @Override
    public QueryBuilder build(SearchDTO dto, SearchProcessor processor) {
        String query = extractQueryString(dto);
        if (query == null || query.isEmpty()) {
            throw new ClientException(SearchConstants.ERR_SEMANTIC_QUERY_REQUIRED,
                    "query is required for semantic search");
        }

        // Defensive: initialize semanticParams if null
        Map<String, Object> semanticParams = dto.getSemanticParams();
        if (semanticParams == null) {
            semanticParams = new java.util.HashMap<>();
        }

        EmbeddingClient client = EmbeddingClientFactory.get();
        float[] vec = cache.get(client.getName(), client.getModelId(), query);
        if (vec == null) {
            vec = client.embed(query);
            cache.put(client.getName(), client.getModelId(), query, vec);
        }

        QuantizationStrategy quantizer = QuantizationStrategyFactory.get(
                getString(semanticParams, "quantization_strategy",
                        getString("semantic_search.quantization_strategy", "int8")));
        byte[] bytes = quantizer.quantize(vec);

        String vectorField = (String) semanticParams.getOrDefault(
                "vector_field",
                getString("semantic_search.vector_field", DEFAULT_VECTOR_FIELD));
        String vectorPath = vectorField.contains(".")
                ? vectorField.substring(0, vectorField.indexOf('.'))
                : DEFAULT_VECTOR_PATH;

        int k = clampK(semanticParams);

        // The OpenSearch kNN plugin's QueryBuilder is not on the
        // rest-high-level-client classpath; build the kNN JSON directly and
        // wrap it. The body shape ("knn": {field: {vector, k}}) is stable
        // across OpenSearch 2.x.
        StringBuilder knn = new StringBuilder();
        knn.append("{\"knn\":{\"").append(vectorField).append("\":{\"vector\":[");
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) knn.append(',');
            knn.append((int) bytes[i]);          // signed int, fits the byte knn_vector
        }
        knn.append("],\"k\":").append(k).append("}}}");

        BoolQueryBuilder innerBool = QueryBuilders.boolQuery()
                .must(new WrapperQueryBuilder(knn.toString()));

        @SuppressWarnings("unchecked")
        List<String> schemaVersions = (List<String>) semanticParams.get("schema_versions");
        if (schemaVersions == null || schemaVersions.isEmpty()) {
            schemaVersions = getStringList("semantic_search.schema_versions");
        }
        if (schemaVersions != null && !schemaVersions.isEmpty()) {
            innerBool.filter(QueryBuilders.termsQuery(vectorPath + ".schema_version", schemaVersions));
        }

        QueryBuilder nested = QueryBuilders.nestedQuery(vectorPath, innerBool,
                org.apache.lucene.search.join.ScoreMode.Max);

        // Compose filters from the existing text-mode bool. Strategy:
        //  - filter()  → kept as filter (no scoring impact)
        //  - mustNot() → kept as mustNot
        //  - must() excluding full-text legs → demoted to filter (kNN owns scoring)
        //  - should() from implicit-filter wrapping → promoted to filter (they constrain visibility)
        //  - should() from softConstraints → dropped (scoring boosts, not constraints;
        //    meaningless when kNN owns scoring)
        //
        // softConstraints are cleared before building the inherited bool so that
        // prepareSearchQuery never adds a soft-constraint should() clause. Any
        // remaining should() clauses in the result are implicit-filter wraps from
        // getSearchQuery (origFilterQry / implFilterQuery) and are safe to promote.
        //
        // Force-disable fuzzy on the inherited build: when fuzzySearch=true,
        // prepareFilteredSearchQuery returns a FunctionScoreQueryBuilder, not a
        // BoolQueryBuilder, which would cause us to silently drop all filters.
        // Fuzzy is a text-only relevance boost and is meaningless when kNN owns
        // scoring, so override is safe.
        // Build the inherited filter set by RE-running the text query builder
        // against a DTO whose properties have had the full-text leg removed.
        // The previous approach — building the full text query and trying to
        // strip the text leg by inspecting QueryBuilder names — failed because
        // getAllFieldsPropertyQuery wraps multi_match inside a BoolQueryBuilder,
        // which getName() reports as "bool", not "multi_match". The wrapped leg
        // then slipped through and was demoted to a filter clause, where the
        // should-only bool's implicit minimum_should_match=1 turned every query
        // term into a hard filter requirement and excluded all docs.
        BoolQueryBuilder finalBool = QueryBuilders.boolQuery().must(nested);
        boolean savedFuzzy = dto.isFuzzySearch();
        @SuppressWarnings("rawtypes")
        List<Map> savedProps = dto.getProperties();
        Map<String, Object> savedSoftConstraints = dto.getSoftConstraints();
        try {
            if (savedFuzzy) dto.setFuzzySearch(false);
            dto.setSoftConstraints(null);
            @SuppressWarnings("rawtypes")
            List<Map> filterProps = new java.util.ArrayList<>();
            if (savedProps != null) {
                for (Map p : savedProps) {
                    Object pn = p.get("propertyName");
                    if (!"*".equals(pn)) filterProps.add(p);
                }
            }
            dto.setProperties(filterProps);
            QueryBuilder existing = processor.buildTextQuery(dto);
            if (existing instanceof BoolQueryBuilder) {
                BoolQueryBuilder bx = (BoolQueryBuilder) existing;
                for (QueryBuilder f : bx.filter())  finalBool.filter(f);
                for (QueryBuilder m : bx.mustNot()) finalBool.mustNot(m);
                // Every remaining must is a property filter (full-text leg
                // already excluded). Demote to filter so kNN owns scoring.
                for (QueryBuilder m : bx.must())   finalBool.filter(m);
                // Implicit-filter shoulds (visibility/status wraps) — promote to
                // filter so they constrain. Soft-constraint shoulds are excluded
                // above by clearing softConstraints before the build.
                for (QueryBuilder s : bx.should()) finalBool.filter(s);
            }
        } finally {
            dto.setProperties(savedProps);
            if (savedFuzzy) dto.setFuzzySearch(true);
            dto.setSoftConstraints(savedSoftConstraints);
        }
        return finalBool;
    }

    private String extractQueryString(SearchDTO dto) {
        List<Map> properties = dto.getProperties();
        if (properties == null) return null;
        for (Map p : properties) {
            Object propName = p.get("propertyName");
            Object values   = p.get("values");
            if ("*".equals(propName) && values instanceof List && !((List) values).isEmpty()) {
                Object v = ((List) values).get(0);
                if (v != null) return v.toString();
            }
        }
        return null;
    }

    private int clampK(Map<String, Object> semanticParams) {
        int maxK = Platform.config.hasPath("semantic_search.max_k")
                ? Platform.config.getInt("semantic_search.max_k") : 1000;
        int defaultK = Platform.config.hasPath("semantic_search.default_k")
                ? Platform.config.getInt("semantic_search.default_k") : 50;
        Object kObj = semanticParams.get("k");
        int k = defaultK;
        if (kObj instanceof Number) k = ((Number) kObj).intValue();
        if (k <= 0) {
            throw new ClientException(SearchConstants.ERR_SEMANTIC_K_INVALID, "semantic.k must be > 0");
        }
        if (k > maxK) {
            throw new ClientException(SearchConstants.ERR_SEMANTIC_K_TOO_HIGH,
                    "semantic.k must be <= " + maxK);
        }
        return k;
    }

    private static String getString(Map<String, Object> map, String key, String def) {
        Object v = map == null ? null : map.get(key);
        return v == null ? def : v.toString();
    }

    private static String getString(String key, String def) {
        return Platform.config.hasPath(key) ? Platform.config.getString(key) : def;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStringList(String key) {
        if (!Platform.config.hasPath(key)) return null;
        return (List<String>) (List<?>) Platform.config.getStringList(key);
    }
}
