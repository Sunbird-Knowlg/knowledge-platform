package org.sunbird.search.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Java port of content-embedding-job's OpenAIEmbeddingService.
 *
 * Mode selection:
 *  - azureEndpoint non-empty → Azure mode (api-key header, deployment URL)
 *  - azureEndpoint empty     → standard OpenAI (Authorization: Bearer)
 *
 * Constants — timeout, headers, response shape — are kept identical to the
 * embedding job so query-time and index-time vectors share a contract.
 */
public class OpenAIEmbeddingClient implements EmbeddingClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIEmbeddingClient.class);

    private final EmbeddingClientConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean isAzure;
    private final String apiUrl;
    private final String modelName;

    public OpenAIEmbeddingClient(EmbeddingClientConfig config) {
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new IllegalArgumentException(
                    "semantic_search.openai.api_key must be set when embedding_service=openai");
        }
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
        this.isAzure   = config.getAzureEndpoint() != null && !config.getAzureEndpoint().isEmpty();
        this.modelName = config.getModel() != null ? config.getModel() : "text-embedding-3-small";

        if (isAzure) {
            String endpoint = config.getAzureEndpoint();
            while (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
            String deployment = config.getAzureDeployment() != null ? config.getAzureDeployment() : modelName;
            String apiVersion = config.getAzureApiVersion() != null ? config.getAzureApiVersion() : "2024-12-01-preview";
            this.apiUrl = endpoint + "/openai/deployments/" + deployment + "/embeddings?api-version=" + apiVersion;
        } else {
            this.apiUrl = "https://api.openai.com/v1/embeddings";
        }

        String logHost;
        try { logHost = URI.create(apiUrl).getHost(); } catch (Throwable t) { logHost = "unknown"; }
        logger.info("OpenAIEmbeddingClient ready: azure={}, host={}, dims={}",
                isAzure, logHost, config.getDimensions());
    }

    @Override public String getName()       { return "openai"; }
    @Override public String getVersion()    { return "1.0"; }
    @Override public int    getDimensions() { return config.getDimensions(); }

    @Override
    public float[] embed(String text) {
        return embedBatch(Collections.singletonList(text)).get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<float[]> embedBatch(List<String> texts) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("input", texts);
            String json = mapper.writeValueAsString(body);

            String headerName  = isAzure ? "api-key" : "Authorization";
            String headerValue = isAzure ? config.getApiKey() : "Bearer " + config.getApiKey();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header(headerName, headerValue)
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                // Do not echo body — may contain caller text or auth hints.
                throw new RuntimeException("OpenAI API error " + resp.statusCode() + " (body suppressed)");
            }

            Map<String, Object> response = mapper.readValue(resp.body(), Map.class);
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            // Order is guaranteed; sort by index defensively.
            data.sort((a, b) -> ((Integer) a.get("index")) - ((Integer) b.get("index")));

            List<float[]> out = new ArrayList<>(data.size());
            for (Map<String, Object> row : data) {
                List<Number> emb = (List<Number>) row.get("embedding");
                float[] vec = new float[emb.size()];
                for (int i = 0; i < emb.size(); i++) vec[i] = emb.get(i).floatValue();
                out.add(vec);
            }
            return out;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("OpenAI embed failed", e);
        }
    }

    @Override
    public void close() {
        // java.net.http.HttpClient has no explicit close; rely on GC.
        logger.info("OpenAIEmbeddingClient closed");
    }
}
