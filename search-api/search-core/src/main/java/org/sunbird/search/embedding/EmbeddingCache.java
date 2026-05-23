package org.sunbird.search.embedding;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiny LRU + TTL cache for query-text embeddings. The cache key is
 * {@code sha256(service:model:text)} so different models or providers cannot
 * collide. Thread-safe via coarse synchronization; expected QPS is low
 * relative to the OpenSearch path, and the cache hit branch is allocation-free
 * past the digest.
 *
 * Disable with {@code semantic_search.embedding_cache.enabled = false}.
 */
public class EmbeddingCache {

    private final boolean enabled;
    private final long    ttlMillis;
    private final LinkedHashMap<String, Entry> store;

    public EmbeddingCache(boolean enabled, int maxEntries, long ttlSeconds) {
        this.enabled = enabled;
        this.ttlMillis = ttlSeconds * 1000L;
        this.store = new LinkedHashMap<String, Entry>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > maxEntries;
            }
        };
    }

    public float[] get(String service, String model, String text) {
        if (!enabled) return null;
        String key = key(service, model, text);
        synchronized (store) {
            Entry e = store.get(key);
            if (e == null) return null;
            if (System.currentTimeMillis() - e.timestampMs > ttlMillis) {
                store.remove(key);
                return null;
            }
            return e.vector;
        }
    }

    public void put(String service, String model, String text, float[] vector) {
        if (!enabled || vector == null) return;
        String key = key(service, model, text);
        synchronized (store) {
            store.put(key, new Entry(vector, System.currentTimeMillis()));
        }
    }

    public void clear() {
        synchronized (store) { store.clear(); }
    }

    private static String key(String service, String model, String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = service + ":" + model + ":" + text;
            byte[] digest = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Should not happen on any standard JRE.
            return service + ":" + model + ":" + text;
        }
    }

    private static final class Entry {
        final float[] vector;
        final long    timestampMs;
        Entry(float[] vector, long timestampMs) {
            this.vector = vector;
            this.timestampMs = timestampMs;
        }
    }
}
