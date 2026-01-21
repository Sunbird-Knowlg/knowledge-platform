package org.sunbird.graph.service.operation;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphTransaction;
import org.sunbird.common.DateUtils;
import org.sunbird.common.JsonUtils;
import org.sunbird.common.Platform;
import org.sunbird.common.dto.Request;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.MiddlewareException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.Identifier;
import org.sunbird.graph.common.enums.AuditProperties;
import org.sunbird.graph.common.enums.GraphDACParams;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.enums.SystemNodeTypes;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.util.JanusGraphNodeUtil;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.common.GraphOperation;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.telemetry.logger.TelemetryManager;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Node async operations using JanusGraph/Gremlin
 * Replaces Neo4j operations with Gremlin traversals
 */
public class NodeAsyncOperations {

    private static final boolean TXN_LOG_ENABLED = Platform.config.hasPath("graph.txn.enable_log")
            ? Platform.config.getBoolean("graph.txn.enable_log")
            : false;
    private static final String TXN_LOG_IDENTIFIER = "learning_graph_events";

    /**
     * Add a new node to the graph.
     *
     * @param graphId the graph id
     * @param node    the node to add
     * @return Future<Node> with the created node including generated ID
     */
    public static Future<Node> addNode(String graphId, Node node) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            if (StringUtils.isBlank(graphId))
                throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                        DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Node Operation Failed.]");

            if (null == node)
                throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name(),
                        DACErrorMessageConstants.INVALID_NODE + " | [Create Node Operation Failed.]");

            GraphTraversalSource g = null;
            JanusGraphTransaction tx = null;
            try {
                if (TXN_LOG_ENABLED) {
                    JanusGraph graph = DriverUtil.getJanusGraph(graphId);
                    tx = graph.buildTransaction().logIdentifier(TXN_LOG_IDENTIFIER).start();
                    g = tx.traversal();
                    TelemetryManager
                            .log("Initialized JanusGraph Transaction with Log Identifier: " + TXN_LOG_IDENTIFIER);
                } else {
                    g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
                    TelemetryManager.log("GraphTraversalSource Initialized. | [Graph Id: " + graphId + "]");
                }

                // Generate unique identifier if not present
                String identifier = node.getIdentifier();
                if (StringUtils.isBlank(identifier)) {
                    identifier = Identifier.getIdentifier(graphId, Identifier.getUniqueIdFromTimestamp());
                    node.setIdentifier(identifier);
                }

                // Set audit properties
                String timestamp = DateUtils.formatCurrentDate();
                node.getMetadata().put(AuditProperties.createdOn.name(), timestamp);
                node.getMetadata().put(AuditProperties.lastUpdatedOn.name(), timestamp);

                // Generate version key
                String versionKey = Identifier.getUniqueIdFromTimestamp();
                node.getMetadata().put(GraphDACParams.versionKey.name(), versionKey);

                // Process primitive data types (serialize complex objects)
                Map<String, Object> metadata = setPrimitiveData(node.getMetadata());

                // Create vertex traversal
                GraphTraversal<Vertex, Vertex> traversal = g.addV(node.getObjectType())
                        .property(SystemProperties.IL_UNIQUE_ID.name(), identifier)
                        .property("graphId", graphId)
                        .property(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), node.getObjectType())
                        .property(SystemProperties.IL_SYS_NODE_TYPE.name(),
                                StringUtils.isNotBlank(node.getNodeType()) ? node.getNodeType()
                                        : SystemNodeTypes.DATA_NODE.name());

                // Add all metadata properties
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    if (entry.getValue() != null) {
                        Object value = entry.getValue();
                        if (value instanceof java.util.List) {
                            java.util.List<?> list = (java.util.List<?>) value;
                            if (list.isEmpty()) {
                                // If list is empty, we remove the property.
                                // We use sideEffect to drop property if it exists.
                                traversal.sideEffect(__.properties(entry.getKey()).drop());
                                // Do not call property(key, value) as value is empty array which might cause
                                // error.
                                continue;
                            }
                            // Convert to typed array for JanusGraph
                            if (list.get(0) instanceof String) {
                                value = list.toArray(new String[0]);
                            } else if (list.get(0) instanceof Integer) {
                                value = list.toArray(new Integer[0]);
                            } else if (list.get(0) instanceof Double) {
                                value = list.toArray(new Double[0]);
                            } else {
                                value = list.toArray();
                            }
                        }
                        traversal.property(entry.getKey(), value);
                    }
                }

                // Execute traversal
                traversal.next();

                if (null != tx)
                    tx.commit();
                else
                    g.tx().commit();

                node.setGraphId(graphId);
                node.setIdentifier(identifier);
                TelemetryManager.log("'Add Node' Operation Finished. | Node ID: " + identifier);

                return node;

            } catch (Exception e) {
                if (null != tx)
                    tx.rollback();

                TelemetryManager.error("Error adding node: " + e.getMessage(), e);
                if (e instanceof MiddlewareException) {
                    throw e;
                } else if (e instanceof org.janusgraph.core.SchemaViolationException) {
                    throw new ClientException(DACErrorCodeConstants.CONSTRAINT_VALIDATION_FAILED.name(),
                            "Error! Node with this identifier or unique property already exists.", e);
                } else if (e.getMessage() != null && e.getMessage().contains("Unique property constraint")) {
                    throw new ClientException(DACErrorCodeConstants.CONSTRAINT_VALIDATION_FAILED.name(),
                            DACErrorMessageConstants.CONSTRAINT_VALIDATION_FAILED + node.getIdentifier());
                } else {
                    throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                            "Error! Something went wrong while creating node object. ", e);
                }
            }
        }));
    }

    /**
     * Update or insert (upsert) a node.
     *
     * @param graphId the graph id
     * @param node    the node to upsert
     * @param request the request containing context
     * @return Future<Node> with the upserted node
     */
    public static Future<Node> upsertNode(String graphId, Node node, Request request) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            TelemetryManager.log("Applying the Consumer Authorization Check for Node Id: " + node.getIdentifier());
            setRequestContextToNode(node, request);
            validateAuthorization(graphId, node, request);
            TelemetryManager.log("Consumer is Authorized for Node Id: " + node.getIdentifier());

            TelemetryManager.log("Validating the Update Operation for Node Id: " + node.getIdentifier());
            node.getMetadata().remove(GraphDACParams.versionKey.name());
            TelemetryManager.log("Node Update Operation has been Validated for Node Id: " + node.getIdentifier());

            GraphTraversalSource g = null;
            JanusGraphTransaction tx = null;

            try {
                if (TXN_LOG_ENABLED) {
                    JanusGraph graph = DriverUtil.getJanusGraph(graphId);
                    tx = graph.buildTransaction().logIdentifier(TXN_LOG_IDENTIFIER).start();
                    g = tx.traversal();
                    TelemetryManager
                            .log("Initialized JanusGraph Transaction with Log Identifier: " + TXN_LOG_IDENTIFIER);
                } else {
                    g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
                    TelemetryManager.log("GraphTraversalSource Initialized. | [Graph Id: " + graphId + "]");
                }

                String identifier = node.getIdentifier();

                // Check if node exists
                Iterator<Vertex> vertexIter = g.V()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), identifier)
                        .has("graphId", graphId);

                Vertex vertex;
                boolean isNew = !vertexIter.hasNext();

                GraphTraversal<Vertex, Vertex> traversal;

                if (isNew) {
                    // Create new node
                    if (StringUtils.isBlank(identifier)) {
                        identifier = Identifier.getIdentifier(graphId, Identifier.getUniqueIdFromTimestamp());
                        node.setIdentifier(identifier);
                    }

                    String timestamp = DateUtils.formatCurrentDate();
                    node.getMetadata().put(AuditProperties.createdOn.name(), timestamp);
                    node.getMetadata().put(AuditProperties.lastUpdatedOn.name(), timestamp);

                    traversal = g.addV(node.getObjectType())
                            .property(SystemProperties.IL_UNIQUE_ID.name(), identifier)
                            .property("graphId", graphId)
                            .property(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), node.getObjectType())
                            .property(SystemProperties.IL_SYS_NODE_TYPE.name(),
                                    StringUtils.isNotBlank(node.getNodeType()) ? node.getNodeType()
                                            : SystemNodeTypes.DATA_NODE.name());
                } else {
                    // Update existing node
                    vertex = vertexIter.next();
                    node.getMetadata().put(AuditProperties.lastUpdatedOn.name(), DateUtils.formatCurrentDate());
                    traversal = g.V(vertex.id());
                }

                // Generate new version key
                String versionKey = Identifier.getUniqueIdFromTimestamp();
                node.getMetadata().put(GraphDACParams.versionKey.name(), versionKey);

                // Process primitive data
                Map<String, Object> metadata = setPrimitiveData(node.getMetadata());

                // Update all properties
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    if (entry.getValue() != null) {
                        Object value = entry.getValue();
                        if (value instanceof java.util.List) {
                            java.util.List<?> list = (java.util.List<?>) value;
                            if (list.isEmpty()) {
                                traversal.sideEffect(__.properties(entry.getKey()).drop());
                                continue;
                            }
                            value = list.toArray();
                        }
                        traversal.property(entry.getKey(), value);
                    }
                }

                // Execute traversal
                traversal.next();

                if (null != tx)
                    tx.commit();
                else
                    g.tx().commit();

                node.setGraphId(graphId);
                node.setIdentifier(identifier);
                TelemetryManager.log("'Upsert Node' Operation Finished. | Node ID: " + identifier);

                return node;

            } catch (Exception e) {
                if (null != tx)
                    tx.rollback();
                TelemetryManager.error("Error upserting node: " + e.getMessage(), e);
                if (e instanceof MiddlewareException) {
                    throw e;
                } else {
                    throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                            "Error! Something went wrong while upserting node object. ", e);
                }
            }
        }));
    }

    /**
     * Upsert root node (special case for root nodes).
     *
     * @param graphId the graph id
     * @param request the request containing node data
     * @return Future<Node> with the upserted root node
     */
    public static Future<Node> upsertRootNode(String graphId, Request request) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            if (StringUtils.isBlank(graphId))
                throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                        DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Upsert Root Node Operation Failed.]");

            if (null == request)
                throw new ClientException(DACErrorCodeConstants.INVALID_REQUEST.name(),
                        DACErrorMessageConstants.INVALID_REQUEST + " | [Upsert Root Node Operation Failed.]");

            GraphTraversalSource g = null;
            JanusGraphTransaction tx = null;
            try {
                if (TXN_LOG_ENABLED) {
                    JanusGraph graph = DriverUtil.getJanusGraph(graphId);
                    tx = graph.buildTransaction().logIdentifier(TXN_LOG_IDENTIFIER).start();
                    g = tx.traversal();
                } else {
                    g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
                }

                String rootId = "root";

                // Check if root node exists
                Iterator<Vertex> vertexIter = g.V()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), rootId)
                        .has("graphId", graphId);

                Vertex vertex;
                GraphTraversal<Vertex, Vertex> traversal;
                if (!vertexIter.hasNext()) {
                    // Create root node
                    traversal = g.addV("ROOT")
                            .property(SystemProperties.IL_UNIQUE_ID.name(), rootId)
                            .property("graphId", graphId)
                            .property(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), "ROOT")
                            .property(SystemProperties.IL_SYS_NODE_TYPE.name(), SystemNodeTypes.DATA_NODE.name())
                            .property(AuditProperties.createdOn.name(), DateUtils.formatCurrentDate())
                            .property(AuditProperties.lastUpdatedOn.name(), DateUtils.formatCurrentDate());
                } else {
                    vertex = vertexIter.next();
                    traversal = g.V(vertex.id())
                            .property(AuditProperties.lastUpdatedOn.name(), DateUtils.formatCurrentDate());
                }

                vertex = traversal.next();

                if (null != tx)
                    tx.commit();
                else
                    g.tx().commit();

                Node rootNode = JanusGraphNodeUtil.getNode(graphId, vertex);
                TelemetryManager.log("'Upsert Root Node' Operation Finished. | Node ID: " + rootId);

                return rootNode;

            } catch (Exception e) {
                if (null != tx)
                    tx.rollback();
                TelemetryManager.error("Error upserting root node: " + e.getMessage(), e);
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                        "Error! Something went wrong while upserting root node. ", e);
            }
        }));
    }

    /**
     * Delete a node from the graph.
     *
     * @param graphId the graph id
     * @param nodeId  the node identifier to delete
     * @param request the request
     * @return Future<Boolean> true if deleted successfully
     */
    public static Future<Boolean> deleteNode(String graphId, String nodeId, Request request) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            if (StringUtils.isBlank(graphId))
                throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                        DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Delete Node Operation Failed.]");

            if (StringUtils.isBlank(nodeId))
                throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
                        DACErrorMessageConstants.INVALID_IDENTIFIER + " | [Delete Node Operation Failed.]");

            GraphTraversalSource g = null;
            JanusGraphTransaction tx = null;
            try {
                if (TXN_LOG_ENABLED) {
                    JanusGraph graph = DriverUtil.getJanusGraph(graphId);
                    tx = graph.buildTransaction().logIdentifier(TXN_LOG_IDENTIFIER).start();
                    g = tx.traversal();
                } else {
                    g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
                    TelemetryManager.log("GraphTraversalSource Initialized. | [Graph Id: " + graphId + "]");
                }

                // Find and delete the node (this also removes all edges)
                long deletedCount = g.V()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), nodeId)
                        .has("graphId", graphId)
                        .sideEffect(__.drop())
                        .count()
                        .next();

                if (null != tx)
                    tx.commit();
                else
                    g.tx().commit();

                if (deletedCount > 0) {
                    TelemetryManager.log("'Delete Node' Operation Finished. | Node ID: " + nodeId);
                    return true;
                } else {
                    TelemetryManager.log("Node not found for deletion. | Node ID: " + nodeId);
                    return false;
                }

            } catch (Exception e) {
                if (null != tx)
                    tx.rollback();
                TelemetryManager.error("Error deleting node: " + e.getMessage(), e);
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                        "Error! Something went wrong while deleting node. ", e);
            }
        }));
    }

    /**
     * Update multiple nodes with the same metadata.
     *
     * @param graphId         the graph id
     * @param identifiers     list of node identifiers to update
     * @param updatedMetadata metadata to update
     * @return Future<Map < String, Node>> map of updated nodes
     */
    public static Future<Map<String, Node>> updateNodes(String graphId, List<String> identifiers,
            Map<String, Object> updatedMetadata) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            if (StringUtils.isBlank(graphId))
                throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                        DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Update Nodes Operation Failed.]");

            if (CollectionUtils.isEmpty(identifiers))
                throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
                        "Empty identifiers list. | [Update Nodes Operation Failed.]");

            if (MapUtils.isEmpty(updatedMetadata))
                return new HashMap<>(); // Nothing to update

            GraphTraversalSource g = null;
            JanusGraphTransaction tx = null;
            try {
                if (TXN_LOG_ENABLED) {
                    JanusGraph graph = DriverUtil.getJanusGraph(graphId);
                    tx = graph.buildTransaction().logIdentifier(TXN_LOG_IDENTIFIER).start();
                    g = tx.traversal();
                } else {
                    g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
                    TelemetryManager.log("GraphTraversalSource Initialized. | [Graph Id: " + graphId + "]");
                }

                Map<String, Node> updatedNodes = new HashMap<>();

                // Process primitive data
                Map<String, Object> metadata = setPrimitiveData(updatedMetadata);

                // Update lastUpdatedOn
                metadata.put(AuditProperties.lastUpdatedOn.name(), DateUtils.formatCurrentDate());

                // Update each node
                for (String identifier : identifiers) {
                    Iterator<Vertex> vertexIter = g.V()
                            .has(SystemProperties.IL_UNIQUE_ID.name(), identifier)
                            .has("graphId", graphId);

                    if (vertexIter.hasNext()) {
                        Vertex vertex = vertexIter.next();
                        GraphTraversal<Vertex, Vertex> traversal = g.V(vertex.id());

                        // Update properties
                        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                            if (entry.getValue() != null) {
                                Object value = entry.getValue();
                                if (value instanceof java.util.List) {
                                    java.util.List<?> list = (java.util.List<?>) value;
                                    if (list.isEmpty()) {
                                        traversal.sideEffect(__.properties(entry.getKey()).drop());
                                        continue;
                                    }
                                    value = list.toArray();
                                }
                                traversal.property(entry.getKey(), value);
                            }
                        }
                        traversal.next();

                        // Convert to Node object
                        Node node = JanusGraphNodeUtil.getNode(graphId, vertex);
                        updatedNodes.put(identifier, node);
                    }
                }

                if (null != tx)
                    tx.commit();
                else
                    g.tx().commit();

                TelemetryManager.log("'Update Nodes' Operation Finished. | Updated count: " + updatedNodes.size());
                return updatedNodes;

            } catch (Exception e) {
                if (null != tx)
                    tx.rollback();
                TelemetryManager.error("Error updating nodes: " + e.getMessage(), e);
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                        "Error! Something went wrong while updating nodes. ", e);
            }
        }));
    }

    // Helper methods

    private static Node setPrimitiveData(Node node) {
        node.setMetadata(setPrimitiveData(node.getMetadata()));
        return node;
    }

    private static Map<String, Object> setPrimitiveData(Map<String, Object> metadata) {
        if (metadata == null) {
            return new HashMap<>();
        }

        return metadata.entrySet().stream()
                .peek(entry -> {
                    Object value = entry.getValue();
                    try {
                        if (value instanceof Map) {
                            value = JsonUtils.serialize(value);
                        } else if (value instanceof List) {
                            List<?> listValue = (List<?>) value;
                            if (CollectionUtils.isNotEmpty(listValue) && listValue.get(0) instanceof Map) {
                                value = JsonUtils.serialize(value);
                            }
                        }
                        entry.setValue(value);
                    } catch (Exception e) {
                        TelemetryManager
                                .error("Exception Occurred While Processing Primitive Data Types | Exception is : "
                                        + e.getMessage(), e);
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue() != null ? entry.getValue() : "",
                        (v1, v2) -> v2, HashMap::new));
    }

    private static void setRequestContextToNode(Node node, Request request) {
        if (null != request && null != request.getContext()) {
            String channel = (String) request.getContext().get(GraphDACParams.CHANNEL_ID.name());
            TelemetryManager.log("Channel from request: " + channel + " for content: " + node.getIdentifier());
            if (StringUtils.isNotBlank(channel))
                node.getMetadata().put(GraphDACParams.channel.name(), channel);

            String consumerId = (String) request.getContext().get(GraphDACParams.CONSUMER_ID.name());
            TelemetryManager.log("ConsumerId from request: " + consumerId + " for content: " + node.getIdentifier());
            if (StringUtils.isNotBlank(consumerId))
                node.getMetadata().put(GraphDACParams.consumerId.name(), consumerId);

            String appId = (String) request.getContext().get(GraphDACParams.APP_ID.name());
            TelemetryManager.log("App Id from request: " + appId + " for content: " + node.getIdentifier());
            if (StringUtils.isNotBlank(appId))
                node.getMetadata().put(GraphDACParams.appId.name(), appId);
        }
    }

    private static void validateAuthorization(String graphId, Node node, Request request) {
        if (StringUtils.isBlank(graphId))
            throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                    DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Invalid or 'null' Graph Id.]");
        if (null == node)
            throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name(),
                    DACErrorMessageConstants.INVALID_NODE + " | [Invalid or 'null' Node.]");
        if (null == request)
            throw new ClientException(DACErrorCodeConstants.INVALID_REQUEST.name(),
                    DACErrorMessageConstants.INVALID_REQUEST + " | [Invalid or 'null' Request Object.]");
    }
}
