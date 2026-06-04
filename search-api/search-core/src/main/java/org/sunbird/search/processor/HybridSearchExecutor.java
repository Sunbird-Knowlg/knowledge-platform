package org.sunbird.search.processor;

import org.apache.pekko.dispatch.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunbird.search.dto.SearchDTO;
import org.sunbird.search.fusion.RrfFusion;
import org.sunbird.search.util.SearchConstants;
import scala.Tuple2;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs text and semantic searches in parallel and fuses their ranked lists
 * with Reciprocal Rank Fusion. Owns its own execution because the strategy
 * contract returns a single QueryBuilder, which cannot express two-pass logic.
 *
 * V1 behaviour:
 *  - dispatches both text and semantic searches via SearchProcessor.processSearch
 *    using cloned DTOs (the only thing different is the searchMode field), so
 *    every existing filter, facet, sort and exists rule applies to both legs
 *  - fuses on the "identifier" key extracted from each result document
 *  - returns the union as the `results` array, sorted by RRF score
 *  - count = size of fused union (capped at limit + offset window)
 *  - facets: borrows the text leg's facets (broader population)
 *
 * Deferred: response-level {@code score_components} surfacing, parallel-only
 * latency budget enforcement, fuzziness flag propagation.
 */
public final class HybridSearchExecutor {

    private static final Logger logger = LoggerFactory.getLogger(HybridSearchExecutor.class);

    private HybridSearchExecutor() { }

    public static Future<Map<String, Object>> execute(SearchDTO dto, SearchProcessor processor) throws Exception {
        if (dto.getSortBy() != null && !dto.getSortBy().isEmpty()) {
            logger.warn("hybrid mode ignores explicit sort_by; result order is RRF score. " +
                    "Drop sort_by from the request to silence this warning.");
        }
        SearchDTO textDto = cloneWithMode(dto, SearchConstants.SEARCH_MODE_TEXT);
        SearchDTO semDto  = cloneWithMode(dto, SearchConstants.SEARCH_MODE_SEMANTIC);

        // SearchProcessor.relevanceSort is mutable per-request state. Running
        // both legs in parallel on the same processor would race on that flag,
        // flipping the sort order non-deterministically. Use fresh instances.
        SearchProcessor textProcessor = new SearchProcessor();
        SearchProcessor semProcessor  = new SearchProcessor();
        Future<Map<String, Object>> textF = textProcessor.processSearch(textDto, true);
        // Degrade gracefully if the semantic leg fails (embedding timeout, service down, etc.).
        // A failed semF must not propagate as a 500 on hybrid requests; substitute an empty
        // result map and mark the response degraded so callers can detect the fallback.
        final AtomicBoolean degraded = new AtomicBoolean(false);
        Future<Map<String, Object>> semFSafe = semProcessor.processSearch(semDto, true)
                .recover(new scala.runtime.AbstractPartialFunction<Throwable, Map<String, Object>>() {
                    @Override public boolean isDefinedAt(Throwable t) { return true; }
                    @Override public Map<String, Object> apply(Throwable t) {
                        logger.warn("hybrid: semantic leg failed, degrading to text-only. reason={}", t.getMessage());
                        degraded.set(true);
                        return Collections.emptyMap();
                    }
                }, ExecutionContext.Implicits$.MODULE$.global());

        int rrfK = readRrfK(dto);
        int limit  = dto.getLimit() <= 0 ? 50 : dto.getLimit();
        int offset = Math.max(0, dto.getOffset());

        return textF.zip(semFSafe).map(
                new Mapper<Tuple2<Map<String, Object>, Map<String, Object>>, Map<String, Object>>() {
                    @Override
                    public Map<String, Object> apply(Tuple2<Map<String, Object>, Map<String, Object>> pair) {
                        Map<String, Object> result = fuse(pair._1(), pair._2(), rrfK, limit, offset);
                        if (degraded.get()) {
                            result.put("degraded", true);
                            result.put("degraded_reason", "semantic_leg_failed");
                        }
                        return result;
                    }
                },
                ExecutionContext.Implicits$.MODULE$.global()
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fuse(Map<String, Object> textResp,
                                            Map<String, Object> semResp,
                                            int rrfK,
                                            int limit,
                                            int offset) {
        List<Map<String, Object>> textHits = (List<Map<String, Object>>) textResp.getOrDefault("results", Collections.emptyList());
        List<Map<String, Object>> semHits  = (List<Map<String, Object>>) semResp.getOrDefault("results", Collections.emptyList());

        List<List<Map<String, Object>>> sources = new ArrayList<>(2);
        sources.add(textHits);
        sources.add(semHits);

        List<RrfFusion.FusedHit<Map<String, Object>>> fused = RrfFusion.fuse(
                sources,
                m -> {
                    Object id = m == null ? null : m.get("identifier");
                    return id == null ? null : id.toString();
                },
                rrfK);

        // Pagination on the fused list.
        int total = fused.size();
        int end = Math.min(offset + limit, total);
        if (offset >= total) {
            return emptyShape(total, textResp);
        }

        // Build response payload and surface score_components.
        List<Map<String, Object>> shaped = new ArrayList<>(end - offset);
        for (int i = offset; i < end; i++) {
            RrfFusion.FusedHit<Map<String, Object>> fh = fused.get(i);
            Map<String, Object> doc = new LinkedHashMap<>(fh.payload);
            doc.put("score", fh.score);
            Map<String, Object> sc = new HashMap<>();
            sc.put("text_rank",     fh.ranks[0] == 0 ? null : fh.ranks[0]);
            sc.put("semantic_rank", fh.ranks[1] == 0 ? null : fh.ranks[1]);
            // Per-source scores are not surfaced today; ElasticSearchUtil
            // does not return per-hit text scores in non-fuzzy mode. Fill
            // when that path is hardened.
            doc.put("score_components", sc);
            shaped.add(doc);
        }

        Map<String, Object> out = new HashMap<>();
        out.put("results", shaped);
        out.put("count", total);
        if (textResp.containsKey("facets")) {
            // Facets are computed on the text leg which represents the broader
            // population. Fusing facets across the union is deferred.
            out.put("facets", textResp.get("facets"));
        }
        logger.info("hybrid fused: text_hits={}, sem_hits={}, fused={}, returned={}",
                textHits.size(), semHits.size(), total, shaped.size());
        return out;
    }

    private static Map<String, Object> emptyShape(int total, Map<String, Object> textResp) {
        Map<String, Object> out = new HashMap<>();
        out.put("results", Collections.emptyList());
        out.put("count", total);
        if (textResp.containsKey("facets")) {
            out.put("facets", textResp.get("facets"));
        }
        return out;
    }

    private static int readRrfK(SearchDTO dto) {
        Object k = dto.getSemanticParams() == null ? null : dto.getSemanticParams().get("rrf_k");
        if (k instanceof Number) return ((Number) k).intValue();
        return RrfFusion.DEFAULT_K;
    }

    /**
     * Shallow clone — flips searchMode for the sub-request. Other fields are
     * shared; SearchProcessor is read-only against them within processSearch,
     * so this is safe.
     */
    private static SearchDTO cloneWithMode(SearchDTO src, String mode) {
        SearchDTO copy = new SearchDTO();
        copy.setProperties(src.getProperties());
        copy.setImplicitFilterProperties(src.getImplicitFilterProperties());
        copy.setFacets(src.getFacets());
        copy.setFields(src.getFields());
        copy.setSortBy(src.getSortBy());
        copy.setOperation(src.getOperation());
        copy.setLimit(src.getLimit());
        copy.setOffset(src.getOffset());
        copy.setFuzzySearch(src.isFuzzySearch());
        copy.setSoftConstraints(src.getSoftConstraints());
        copy.setAdditionalProperties(src.getAdditionalProperties());
        if (src.getAggregations() != null && !src.getAggregations().isEmpty()) {
            copy.setAggregations(new ArrayList<>(src.getAggregations()));
        }
        copy.setSearchMode(mode);
        copy.setSemanticParams(src.getSemanticParams());
        return copy;
    }
}
