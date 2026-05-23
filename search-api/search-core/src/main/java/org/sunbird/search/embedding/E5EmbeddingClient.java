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
 * Java port of content-embedding-job's E5EmbeddingService. Calls a
 * HuggingFace TEI server running intfloat/multilingual-e5-large.
 *
 * Query-time prefix MUST be "query: " (different from index-time "passage: ").
 * E5 instruction-tuned models require this asymmetry; mismatching prefixes
 * silently degrades recall.
 */
public class E5EmbeddingClient implements EmbeddingClient {

    private static final Logger logger = LoggerFactory.getLogger(E5EmbeddingClient.class);

    private final EmbeddingClientConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String endpointUrl;

    public E5EmbeddingClient(EmbeddingClientConfig config) {
        String host = config.getHost() != null ? config.getHost() : "localhost";
        if (!isInternalHost(host)) {
            throw new IllegalArgumentException(
                    "E5 host '" + host + "' is not internal/private; refusing to send content to public targets");
        }
        int port = config.getPort() != null ? config.getPort() : 80;
        this.config = config;
        this.endpointUrl = "http://" + host + ":" + port + "/embed";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
        logger.info("E5EmbeddingClient ready: {} ({}d)", endpointUrl, config.getDimensions());
    }

    @Override public String getName()       { return "e5"; }
    @Override public String getVersion()    { return "2.0"; }
    @Override public int    getDimensions() { return config.getDimensions(); }

    @Override
    public float[] embed(String text) {
        return embedBatch(Collections.singletonList(text)).get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<float[]> embedBatch(List<String> texts) {
        try {
            List<String> prefixed = new ArrayList<>(texts.size());
            for (String t : texts) prefixed.add("query: " + t);

            Map<String, Object> body = new HashMap<>();
            body.put("inputs", prefixed);
            String json = mapper.writeValueAsString(body);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("TEI server error " + resp.statusCode() + " (body suppressed)");
            }

            // TEI returns a raw array of arrays.
            List<List<Number>> rows = mapper.readValue(resp.body(), List.class);
            List<float[]> out = new ArrayList<>(rows.size());
            for (List<Number> row : rows) {
                float[] vec = new float[row.size()];
                for (int i = 0; i < row.size(); i++) vec[i] = row.get(i).floatValue();
                out.add(vec);
            }
            return out;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("E5 embed failed", e);
        }
    }

    @Override
    public void close() {
        logger.info("E5EmbeddingClient closed");
    }

    private static boolean isInternalHost(String host) {
        if (host == null || host.isEmpty()) return false;
        String h = host.toLowerCase();
        if (h.equals("localhost"))         return true;
        if (h.startsWith("127."))          return true;
        if (h.startsWith("10."))           return true;
        if (h.startsWith("192.168."))      return true;
        if (h.startsWith("172.")) {
            String[] parts = h.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) return true;
                } catch (NumberFormatException ignore) { }
            }
        }
        if (h.endsWith(".svc.cluster.local")) return true;
        if (h.endsWith(".cluster.local"))     return true;
        if (h.endsWith(".internal"))          return true;
        if (!h.contains("."))                 return true;   // bare k8s service name
        return false;
    }
}
