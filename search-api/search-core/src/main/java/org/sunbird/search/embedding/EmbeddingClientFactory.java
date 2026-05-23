package org.sunbird.search.embedding;

import org.sunbird.common.Platform;
import org.sunbird.common.exception.ClientException;

/**
 * Lazily builds the configured {@link EmbeddingClient} from application config.
 * Singleton-per-service to amortise HTTP client construction.
 *
 * Config keys (under {@code semantic_search}):
 *  - embedding_service: "openai" | "e5"
 *  - openai.{api_key, model, dimensions, timeout, azure_endpoint, azure_deployment, azure_api_version}
 *  - e5.{host, port, dimensions, timeout}
 */
public final class EmbeddingClientFactory {

    private static volatile EmbeddingClient INSTANCE;
    private static final Object LOCK = new Object();

    private EmbeddingClientFactory() { }

    public static EmbeddingClient get() {
        EmbeddingClient ref = INSTANCE;
        if (ref != null) return ref;
        synchronized (LOCK) {
            if (INSTANCE == null) INSTANCE = build();
            return INSTANCE;
        }
    }

    /** Test hook. */
    public static void overrideForTest(EmbeddingClient client) {
        synchronized (LOCK) { INSTANCE = client; }
    }

    /** Test hook. */
    public static void resetForTest() {
        synchronized (LOCK) { INSTANCE = null; }
    }

    private static EmbeddingClient build() {
        String service = Platform.config.hasPath("semantic_search.embedding_service")
                ? Platform.config.getString("semantic_search.embedding_service")
                : "openai";

        if ("openai".equalsIgnoreCase(service)) {
            EmbeddingClientConfig cfg = EmbeddingClientConfig.builder()
                    .serviceName("openai")
                    .dimensions(getInt("semantic_search.openai.dimensions", 1536))
                    .timeoutSeconds(getInt("semantic_search.openai.timeout", 5))
                    .apiKey(getString("semantic_search.openai.api_key", ""))
                    .model(getString("semantic_search.openai.model", "text-embedding-3-small"))
                    .azureEndpoint(getString("semantic_search.openai.azure_endpoint", ""))
                    .azureDeployment(getString("semantic_search.openai.azure_deployment", ""))
                    .azureApiVersion(getString("semantic_search.openai.azure_api_version", "2024-12-01-preview"))
                    .build();
            return new OpenAIEmbeddingClient(cfg);
        }
        if ("e5".equalsIgnoreCase(service)) {
            EmbeddingClientConfig cfg = EmbeddingClientConfig.builder()
                    .serviceName("e5")
                    .dimensions(getInt("semantic_search.e5.dimensions", 768))
                    .timeoutSeconds(getInt("semantic_search.e5.timeout", 5))
                    .host(getString("semantic_search.e5.host", "localhost"))
                    .port(getInt("semantic_search.e5.port", 80))
                    .build();
            return new E5EmbeddingClient(cfg);
        }
        throw new ClientException("ERR_UNSUPPORTED_EMBEDDING_SERVICE",
                "Unsupported embedding service: " + service);
    }

    private static String getString(String key, String def) {
        return Platform.config.hasPath(key) ? Platform.config.getString(key) : def;
    }

    private static int getInt(String key, int def) {
        return Platform.config.hasPath(key) ? Platform.config.getInt(key) : def;
    }
}
