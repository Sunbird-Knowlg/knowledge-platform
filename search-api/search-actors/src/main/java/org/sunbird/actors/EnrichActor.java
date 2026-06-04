package org.sunbird.actors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pekko.dispatch.Mapper;
import org.sunbird.common.Platform;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseHandler;
import org.sunbird.common.exception.ClientException;
import org.sunbird.kafka.client.KafkaClient;
import org.sunbird.search.dto.SearchDTO;
import org.sunbird.search.processor.SearchProcessor;
import org.sunbird.search.util.SearchConstants;
import org.sunbird.telemetry.logger.TelemetryManager;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.util.*;

/**
 * Handles the enrich API: resolves objectType for each identifier via the
 * composite search index (OpenSearch), then emits a {@code BE_JOB_REQUEST}
 * Kafka event with {@code edata.action=enrich} for each valid identifier.
 * The downstream knowlg-publish Flink job consumes these events via
 * {@code EnrichOnlyFunction} to produce enriched metadata.
 */
public class EnrichActor extends SearchBaseActor {

    private static final KafkaClient KAFKA_CLIENT = new KafkaClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Future<Response> onReceive(Request request) throws Throwable {
        if ("triggerEnrich".equals(request.getOperation())) {
            return triggerEnrich(request);
        }
        return ERROR(request.getOperation());
    }

    @SuppressWarnings("unchecked")
    private Future<Response> triggerEnrich(Request request) {
        List<String> identifiers = parseIdentifiers(request);
        String topic = Platform.config.hasPath("kafka.publish.request.topic")
                ? Platform.config.getString("kafka.publish.request.topic")
                : "sunbirddev.publish.job.request";

        SearchDTO searchDTO = buildIdentifierSearchDTO(identifiers);
        SearchProcessor processor = new SearchProcessor();

        try {
        return processor.processSearch(searchDTO, true).map(
                new Mapper<Map<String, Object>, Response>() {
                    @Override
                    public Response apply(Map<String, Object> result) {
                        List<Map<String, Object>> docs = (List<Map<String, Object>>)
                                result.getOrDefault("results", Collections.emptyList());

                        // Index by base identifier (strip .img suffix from image nodes)
                        Map<String, Map<String, Object>> docMap = new LinkedHashMap<>();
                        for (Map<String, Object> doc : docs) {
                            String id = String.valueOf(doc.getOrDefault("identifier", ""));
                            if (id.endsWith(".img")) id = id.substring(0, id.length() - 4);
                            docMap.putIfAbsent(id, doc);
                        }

                        List<String> notFound = new ArrayList<>();
                        for (String id : identifiers) {
                            if (!docMap.containsKey(id)) notFound.add(id);
                        }
                        if (!notFound.isEmpty()) {
                            throw new ClientException("ERR_CONTENT_NOT_FOUND",
                                    "Content not found for identifier(s): " + String.join(", ", notFound));
                        }

                        List<String> succeeded = new ArrayList<>();
                        List<String> failed = new ArrayList<>();
                        for (String id : identifiers) {
                            Map<String, Object> doc = docMap.get(id);
                            String objectType = normalizeObjectType(
                                    String.valueOf(doc.getOrDefault("objectType", "Content")));
                            String mimeType = String.valueOf(doc.getOrDefault("mimeType", ""));
                            try {
                                KAFKA_CLIENT.send(buildEvent(id, objectType, mimeType), topic);
                                TelemetryManager.log("EnrichActor: emitted enrich event for " + id + " (" + objectType + ")");
                                succeeded.add(id);
                            } catch (Exception e) {
                                TelemetryManager.error("EnrichActor: kafka send failed for " + id + ": " + e.getMessage(), e);
                                failed.add(id);
                            }
                        }

                        if (succeeded.isEmpty()) {
                            throw new ClientException("ERR_KAFKA_SEND_FAILED",
                                    "Failed to emit enrich events for: " + String.join(", ", failed));
                        }

                        Response response = ResponseHandler.OK();
                        response.put("count", succeeded.size());
                        response.put("identifiers", succeeded);
                        response.put("failed", failed);
                        return response;
                    }
                }, ExecutionContext.Implicits$.MODULE$.global());
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute enrich search query", e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<String> parseIdentifiers(Request request) {
        Object raw = request.getRequest().get("identifiers");
        if (!(raw instanceof List) || ((List) raw).isEmpty()) {
            throw new ClientException("ERR_INVALID_REQUEST", "identifiers must be a non-empty list");
        }
        List<String> ids = new ArrayList<>();
        for (Object o : (List) raw) {
            String s = (o == null) ? "" : o.toString().trim();
            if (!s.isEmpty()) ids.add(s);
        }
        if (ids.isEmpty()) {
            throw new ClientException("ERR_INVALID_REQUEST", "identifiers list must not be empty");
        }
        return ids;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private SearchDTO buildIdentifierSearchDTO(List<String> identifiers) {
        SearchDTO dto = new SearchDTO();
        dto.setOperation(SearchConstants.SEARCH_OPERATION_AND);
        // buffer for .img variants
        dto.setLimit(identifiers.size() * 2 + 10);

        Map<String, Object> idProp = new LinkedHashMap<>();
        idProp.put("propertyName", "identifier");
        idProp.put("operation", SearchConstants.SEARCH_OPERATION_EQUAL);
        idProp.put("values", identifiers);

        dto.setProperties(Collections.singletonList((Map) idProp));
        dto.setFields(Arrays.asList("identifier", "objectType", "mimeType", "status"));
        return dto;
    }

    private String normalizeObjectType(String objectType) {
        // Strip "Image" suffix — ContentImage → Content, CollectionImage → Collection, etc.
        if (objectType.endsWith("Image")) {
            return objectType.substring(0, objectType.length() - 5);
        }
        return objectType;
    }

    private String buildEvent(String identifier, String objectType, String mimeType) {
        long ets = System.currentTimeMillis();
        String mid = "LP." + ets + "." + UUID.randomUUID();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("identifier", identifier);
        metadata.put("objectType", objectType);
        metadata.put("mimeType", mimeType);

        Map<String, Object> edata = new LinkedHashMap<>();
        edata.put("action", "enrich");
        edata.put("metadata", metadata);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eid", "BE_JOB_REQUEST");
        event.put("ets", ets);
        event.put("mid", mid);
        event.put("actor", Map.of("id", "content-enrich-api", "type", "System"));
        event.put("context", Map.of("pdata", Map.of("ver", "1.0", "id", "org.sunbird.platform")));
        event.put("object", Map.of("id", identifier, "ver", "1.0"));
        event.put("edata", edata);

        try {
            return MAPPER.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize enrich event for " + identifier, e);
        }
    }
}
