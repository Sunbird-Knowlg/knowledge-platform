package org.sunbird.janusgraph.cdc;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;
import org.janusgraph.core.JanusGraphTransaction;
import org.janusgraph.core.log.Change;
import org.janusgraph.core.log.ChangeProcessor;
import org.janusgraph.core.log.ChangeState;
import org.janusgraph.core.log.TransactionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class GraphLogProcessor implements ChangeProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphLogProcessor.class);
    private static final String LOG_IDENTIFIER = "learning_graph_events";
    private final KafkaEventProducer producer;

    public GraphLogProcessor(Map<String, String> config) {
        this.producer = new KafkaEventProducer(config);
    }

    public static void start(JanusGraph graph, Map<String, String> config) {
        String logEnabled = config.getOrDefault("graph.txn.log_processor.enable", "false");
        if ("true".equalsIgnoreCase(logEnabled)) {
            LOGGER.info("Starting GraphLogProcessor...");
            GraphLogProcessor processor = new GraphLogProcessor(config);
            JanusGraphFactory.openTransactionLog(graph)
                    .logIdentifier(LOG_IDENTIFIER)
                    .startTime(java.time.Instant.now())
                    .build()
                    .registerLogProcessor(processor);
            LOGGER.info("GraphLogProcessor started successfully.");
        } else {
            LOGGER.info("GraphLogProcessor is disabled by configuration.");
        }
    }

    @Override
    public void process(JanusGraphTransaction tx, TransactionId txId, ChangeState changeState) {
        try {
            // We are interested in Vertex changes
            for (Vertex vertex : changeState.getVertices(Change.ADDED)) {
                processVertexChange(vertex, Change.ADDED, txId, changeState);
            }
            for (Vertex vertex : changeState.getVertices(Change.REMOVED)) {
                processVertexChange(vertex, Change.REMOVED, txId, changeState);
            }
            
            // For property updates, JanusGraph doesn't give "UPDATED" directly.
            // It gives REMOVED (old state) and ADDED (new state) for the same vertex.
            // We need to iterate all vertices that have ANY change
            Set<Vertex> changedVertices = changeState.getVertices(Change.ANY);
            for (Vertex vertex : changedVertices) {
                // If the vertex itself wasn't added/removed, check properties
                if (!changeState.getVertices(Change.ADDED).contains(vertex) && 
                    !changeState.getVertices(Change.REMOVED).contains(vertex)) {
                    processPropertyChanges(vertex, txId, changeState);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error processing transaction {}", txId, e);
        }
    }

    private void processVertexChange(Vertex vertex, Change change, TransactionId txId, ChangeState changeState) {
        if (isSystemLabel(vertex.label())) return;

        Map<String, Object> event = new HashMap<>();
        event.put("operationType", change == Change.ADDED ? "CREATE" : "DELETE");
        event.put("nodeGraphId", "domain"); // Assuming domain graph
        event.put("nodeUniqueId", vertex.property("IL_UNIQUE_ID").isPresent() ? vertex.value("IL_UNIQUE_ID") : vertex.id().toString());
        event.put("objectType", vertex.label());
        event.put("timestamp", System.currentTimeMillis());
        event.put("txId", txId.toString());

        if (change == Change.ADDED) {
            Map<String, Object> properties = new HashMap<>();
            vertex.properties().forEachRemaining(vp -> properties.put(vp.key(), vp.value()));
            event.put("properties", properties);
        }

        producer.publish(event);
        LOGGER.info("CDC: Published {} event for {}", change, vertex.id());
    }

    private void processPropertyChanges(Vertex vertex, TransactionId txId, ChangeState changeState) {
        if (isSystemLabel(vertex.label())) return;

        Map<String, Map<String, Object>> propertyDiffs = new HashMap<>();

        // Capture Removed Properties (Old Values)
        changeState.getProperties(vertex, Change.REMOVED, Direction.OUT).forEach(p -> {
            String key = p.key();
            propertyDiffs.putIfAbsent(key, new HashMap<>());
            propertyDiffs.get(key).put("ov", p.value()); // ov = Old Value
        });

        // Capture Added Properties (New Values)
        changeState.getProperties(vertex, Change.ADDED, Direction.OUT).forEach(p -> {
            String key = p.key();
            propertyDiffs.putIfAbsent(key, new HashMap<>());
            propertyDiffs.get(key).put("nv", p.value()); // nv = New Value
        });

        if (!propertyDiffs.isEmpty()) {
            Map<String, Object> event = new HashMap<>();
            event.put("operationType", "UPDATE");
            event.put("nodeGraphId", "domain");
            event.put("nodeUniqueId", getUniqueId(vertex));
            event.put("objectType", vertex.label());
            event.put("timestamp", System.currentTimeMillis());
            event.put("txId", txId.toString());
            
            Map<String, Object> transactionData = new HashMap<>();
            transactionData.put("properties", propertyDiffs);
            event.put("transactionData", transactionData);

            producer.publish(event);
            LOGGER.info("CDC: Published UPDATE event for {}", vertex.id());
        }
    }

    private String getUniqueId(Vertex vertex) {
        // Try to get IL_UNIQUE_ID from current state, if not, try to recover from properties
        try {
             if (vertex.property("IL_UNIQUE_ID").isPresent()) {
                 return (String) vertex.value("IL_UNIQUE_ID");
             }
        } catch (Exception e) {
            // use ID if property not available
        }
        return vertex.id().toString();
    }

    private boolean isSystemLabel(String label) {
        return label.startsWith("system_") || label.equals("titan_id");
    }
}
