package org.sunbird.search.embedding;

import java.util.List;

/**
 * Query-time counterpart of content-embedding-job's EmbeddingService.
 * Returns float vectors; quantization to byte is a separate concern handled
 * by {@link org.sunbird.search.quantization.QuantizationStrategy}.
 *
 * Implementations are thread-safe and reused across requests via the factory.
 * They own their HTTP client lifecycle.
 */
public interface EmbeddingClient {

    /** Service identifier — "openai" | "e5" — must match the embedding job's. */
    String getName();

    String getVersion();

    /** Must match the dimension on the OpenSearch knn_vector mapping. */
    int getDimensions();

    /** Embed a single string. */
    float[] embed(String text);

    /** Batched embedding; some providers (OpenAI) accept up to ~2048 inputs. */
    List<float[]> embedBatch(List<String> texts);

    /** Release underlying resources (HTTP client, connection pool). */
    void close();
}
