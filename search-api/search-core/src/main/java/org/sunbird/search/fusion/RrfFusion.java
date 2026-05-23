package org.sunbird.search.fusion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion (RRF). Pure function, no IO.
 *
 *   score(d) = Σ over input lists of 1 / (k + rank_i(d))
 *
 * Rank is 1-based. A doc absent from a list contributes nothing. Default k = 60
 * follows the original Cormack et al. recommendation.
 *
 * Identity for documents is supplied by a caller-provided key extractor so the
 * fusion is agnostic to OpenSearch SearchHit vs. raw maps.
 */
public final class RrfFusion {

    public static final int DEFAULT_K = 60;

    private RrfFusion() { }

    /**
     * @param rankedLists  per-source ranked lists; first element of each list is rank 1
     * @param idOf         extracts the identity (e.g. "identifier") used to dedupe across lists
     * @param k            RRF constant; lower values let the top of each list dominate more
     * @return             fused docs in descending RRF score order, with per-source rank attached
     */
    public static <T> List<FusedHit<T>> fuse(
            List<List<T>> rankedLists,
            java.util.function.Function<T, String> idOf,
            int k) {

        if (rankedLists == null || rankedLists.isEmpty()) return Collections.emptyList();
        if (k <= 0) k = DEFAULT_K;

        // Preserve insertion order so ties resolve by first-seen list.
        Map<String, FusedHit<T>> acc = new LinkedHashMap<>();

        for (int listIdx = 0; listIdx < rankedLists.size(); listIdx++) {
            List<T> list = rankedLists.get(listIdx);
            if (list == null) continue;
            for (int rank0 = 0; rank0 < list.size(); rank0++) {
                T item = list.get(rank0);
                if (item == null) continue;
                String id = idOf.apply(item);
                if (id == null) continue;
                int rank = rank0 + 1;
                double contribution = 1.0 / (k + rank);
                FusedHit<T> hit = acc.get(id);
                if (hit == null) {
                    hit = new FusedHit<>(id, item, rankedLists.size());
                    acc.put(id, hit);
                }
                hit.score += contribution;
                hit.ranks[listIdx] = rank;
                // Keep the first sighting of the document so the original
                // payload (with fields like score, source) is preserved.
            }
        }

        List<FusedHit<T>> result = new ArrayList<>(acc.values());
        result.sort(Comparator.<FusedHit<T>>comparingDouble(h -> h.score).reversed());
        return result;
    }

    /**
     * Result of fusing one document across N source lists.
     * {@code ranks[i]} is the 1-based rank of this doc in list i, or 0 if absent.
     */
    public static final class FusedHit<T> {
        public final String id;
        public final T      payload;
        public double       score;
        public final int[]  ranks;

        FusedHit(String id, T payload, int sources) {
            this.id      = id;
            this.payload = payload;
            this.score   = 0.0;
            this.ranks   = new int[sources];
        }
    }
}
