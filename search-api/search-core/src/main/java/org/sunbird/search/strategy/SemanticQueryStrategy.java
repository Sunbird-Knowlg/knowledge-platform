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

    @Override
    public QueryBuilder build(SearchDTO dto, SearchProcessor processor) {
        String query = extractQueryString(dto);
        if (query == null || query.isEmpty()) {
            throw new ClientException(SearchConstants.ERR_SEMANTIC_QUERY_REQUIRED,
                    "query is required for semantic search");
        }

        EmbeddingClient client = EmbeddingClientFactory.get();
        float[] vec = cache.get(client.getName(), client.getVersion(), query);
        if (vec == null) {
            vec = client.embed(query);
            cache.put(client.getName(), client.getVersion(), query, vec);
        }

        QuantizationStrategy quantizer = QuantizationStrategyFactory.get(
                getString(dto.getSemanticParams(), "quantization_strategy",
                        getString("semantic_search.quantization_strategy", "int8")));
        byte[] bytes = quantizer.quantize(vec);

        String vectorField = (String) dto.getSemanticParams().getOrDefault(
                "vector_field",
                getString("semantic_search.vector_field", DEFAULT_VECTOR_FIELD));
        String vectorPath = vectorField.contains(".")
                ? vectorField.substring(0, vectorField.indexOf('.'))
                : DEFAULT_VECTOR_PATH;

        int k = clampK(dto);

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
        List<String> schemaVersions = (List<String>) dto.getSemanticParams().get("schema_versions");
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
        //  - should() → dropped entirely
        //
        // Soft constraints and OR-operation property clauses live in should() and
        // are scoring boosts; they have no meaning when kNN owns the score. The
        // OR-semantics limitation for filters in semantic mode is documented in
        // docs/semantic-search/API_SPEC.md.
        BoolQueryBuilder finalBool = QueryBuilders.boolQuery().must(nested);
        QueryBuilder existing = processor.buildTextQuery(dto);
        if (existing instanceof BoolQueryBuilder) {
            BoolQueryBuilder bx = (BoolQueryBuilder) existing;
            for (QueryBuilder f : bx.filter())  finalBool.filter(f);
            for (QueryBuilder m : bx.mustNot()) finalBool.mustNot(m);
            for (QueryBuilder m : bx.must()) {
                if (!isFullTextLeg(m)) finalBool.filter(m);
            }
        }
        return finalBool;
    }

    private boolean isFullTextLeg(QueryBuilder q) {
        // OpenSearch QueryBuilder names from getName() / getWriteableName().
        // multi_match → multi_match; MatchQueryBuilder → match;
        // QueryStringQueryBuilder → query_string; MatchPhraseQueryBuilder → match_phrase;
        // SimpleQueryStringQueryBuilder → simple_query_string.
        String name = q.getName() == null ? "" : q.getName();
        return name.equals("multi_match")
                || name.equals("match")
                || name.equals("match_phrase")
                || name.equals("query_string")
                || name.equals("simple_query_string");
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

    private int clampK(SearchDTO dto) {
        int maxK = Platform.config.hasPath("semantic_search.max_k")
                ? Platform.config.getInt("semantic_search.max_k") : 1000;
        int defaultK = Platform.config.hasPath("semantic_search.default_k")
                ? Platform.config.getInt("semantic_search.default_k") : 50;
        Object kObj = dto.getSemanticParams().get("k");
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
