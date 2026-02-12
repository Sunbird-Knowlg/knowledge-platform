package org.sunbird.graph.service.operation;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import org.janusgraph.core.JanusGraphTransaction;
import org.janusgraph.core.JanusGraphVertex;
import org.sunbird.common.DateUtils;
import org.sunbird.common.JsonUtils;

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

            JanusGraphTransaction tx = null;
            try {
                tx = DriverUtil.beginTransaction(graphId);

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

                // Create vertex using Native API
                JanusGraphVertex vertex = tx.addVertex(node.getObjectType());
                vertex.property(SystemProperties.IL_UNIQUE_ID.name(), identifier);
                vertex.property("graphId", graphId);
                vertex.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), node.getObjectType());
                vertex.property(SystemProperties.IL_SYS_NODE_TYPE.name(),
                        StringUtils.isNotBlank(node.getNodeType()) ? node.getNodeType()
                                : SystemNodeTypes.DATA_NODE.name());

                // Add all metadata properties
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    if (entry.getValue() != null) {
                        Object value = entry.getValue();
                        if (value instanceof java.util.List) {
                            java.util.List<?> list = (java.util.List<?>) value;
                            if (list.isEmpty()) {
                                // If list is empty, skip property creation
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
                        vertex.property(entry.getKey(), value);
                    }
                }

                tx.commit();

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

            JanusGraphTransaction tx = null;
            try {
                tx = DriverUtil.beginTransaction(graphId);

                String identifier = node.getIdentifier();

                // Check if node exists using Native Query
                Iterator<JanusGraphVertex> vertexIter = tx.query()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), identifier)
                        .has("graphId", graphId)
                        .vertices().iterator();

                JanusGraphVertex vertex;
                if (vertexIter.hasNext()) {
                    // Update existing node
                    vertex = vertexIter.next();
                    node.getMetadata().put(AuditProperties.lastUpdatedOn.name(), DateUtils.formatCurrentDate());
                } else {
                    // Create new node
                    if (StringUtils.isBlank(identifier)) {
                        identifier = Identifier.getIdentifier(graphId, Identifier.getUniqueIdFromTimestamp());
                        node.setIdentifier(identifier);
                    }

                    String timestamp = DateUtils.formatCurrentDate();
                    node.getMetadata().put(AuditProperties.createdOn.name(), timestamp);
                    node.getMetadata().put(AuditProperties.lastUpdatedOn.name(), timestamp);

                    vertex = tx.addVertex(node.getObjectType());
                    vertex.property(SystemProperties.IL_UNIQUE_ID.name(), identifier);
                    vertex.property("graphId", graphId);
                    vertex.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), node.getObjectType());
                    vertex.property(SystemProperties.IL_SYS_NODE_TYPE.name(),
                            StringUtils.isNotBlank(node.getNodeType()) ? node.getNodeType()
                                    : SystemNodeTypes.DATA_NODE.name());
                }

                // Generate new version key
                String versionKey = Identifier.getUniqueIdFromTimestamp();
                node.getMetadata().put(GraphDACParams.versionKey.name(), versionKey);

                // Process primitive data
                TelemetryManager.info("NodeAsyncOperations: Upserting Node with Status: "
                        + node.getMetadata().get("status") + " | ID: " + identifier);
                Map<String, Object> metadata = setPrimitiveData(node.getMetadata());

                // Update all properties
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    if (entry.getValue() != null) {
                        Object value = entry.getValue();
                        if (value instanceof java.util.List) {
                            java.util.List<?> list = (java.util.List<?>) value;
                            if (list.isEmpty()) {
                                // For empty list, removing property using Cardinality.single (replacing with
                                // null/removing)
                                // or checking if property exists and removing it.
                                // In JanusGraph native, setting property to null usually implies removal,
                                // but safe way is to remove existing property if we can't set null.
                                // However, standard way is accessing property and calling remove().
                                // Here we can try finding property and removing.
                                if (vertex.keys().contains(entry.getKey())) {
                                    vertex.property(entry.getKey()).remove();
                                }
                                continue;
                            }
                            value = list.toArray();
                        }
                        vertex.property(entry.getKey(), value);
                    }
                }

                try {
                    tx.commit();
                } catch (Exception commitEx) {
                    TelemetryManager.error("NodeAsyncOperations.upsertNode: EXCEPTION during commit for " + identifier
                            + ": " + commitEx.getMessage(), commitEx);
                    throw commitEx;
                }

                node.setGraphId(graphId);
                node.setIdentifier(identifier);
                TelemetryManager.log("'Upsert Node' Operation Finished. | Node ID: " + identifier);

                return node;

            } catch (Exception e) {
                if (null != tx)
                    tx.rollback();
                TelemetryManager.error("Error upserting node: " + e.getMessage(), e);
                if (e instanceof MiddlewareException) {
                    throw (MiddlewareException) e;
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

            JanusGraphTransaction tx = null;
            try {
                tx = DriverUtil.beginTransaction(graphId);

                String rootId = "root";

                // Check if root node exists
                Iterator<JanusGraphVertex> vertexIter = tx.query()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), rootId)
                        .has("graphId", graphId)
                        .vertices().iterator();

                JanusGraphVertex vertex;
                if (!vertexIter.hasNext()) {
                    // Create root node
                    vertex = tx.addVertex("ROOT");
                    vertex.property(SystemProperties.IL_UNIQUE_ID.name(), rootId);
                    vertex.property("graphId", graphId);
                    vertex.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), "ROOT");
                    vertex.property(SystemProperties.IL_SYS_NODE_TYPE.name(), SystemNodeTypes.DATA_NODE.name());
                    vertex.property(AuditProperties.createdOn.name(), DateUtils.formatCurrentDate());
                    vertex.property(AuditProperties.lastUpdatedOn.name(), DateUtils.formatCurrentDate());
                } else {
                    vertex = vertexIter.next();
                    vertex.property(AuditProperties.lastUpdatedOn.name(), DateUtils.formatCurrentDate());
                }

                try {
                    tx.commit();
                } catch (Exception commitEx) {
                    TelemetryManager.error("NodeAsyncOperations.upsertRootNode: EXCEPTION during commit for " + rootId
                            + ": " + commitEx.getMessage(), commitEx);
                    throw commitEx;
                }

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

            JanusGraphTransaction tx = null;
            try {
                tx = DriverUtil.beginTransaction(graphId);

                // Find and delete the node
                Iterator<JanusGraphVertex> vertexIter = tx.query()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), nodeId)
                        .has("graphId", graphId)
                        .vertices().iterator();

                if (vertexIter.hasNext()) {
                    JanusGraphVertex vertex = vertexIter.next();
                    vertex.remove();
                    tx.commit();
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

            TelemetryManager.info("NodeAsyncOperations: updateNodes called for IDs: " + identifiers + " with keys: "
                    + updatedMetadata.keySet());

            JanusGraphTransaction tx = null;
            try {
                tx = DriverUtil.beginTransaction(graphId);

                Map<String, Node> updatedNodes = new HashMap<>();

                // Process primitive data
                Map<String, Object> metadata = setPrimitiveData(updatedMetadata);

                // Update lastUpdatedOn
                metadata.put(AuditProperties.lastUpdatedOn.name(), DateUtils.formatCurrentDate());

                // Update each node
                for (String identifier : identifiers) {
                    Iterator<JanusGraphVertex> vertexIter = tx.query()
                            .has(SystemProperties.IL_UNIQUE_ID.name(), identifier)
                            .has("graphId", graphId)
                            .vertices().iterator();

                    if (vertexIter.hasNext()) {
                        JanusGraphVertex vertex = vertexIter.next();

                        // Update properties
                        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                            if (entry.getValue() != null) {
                                Object value = entry.getValue();
                                if (value instanceof java.util.List) {
                                    java.util.List<?> list = (java.util.List<?>) value;
                                    if (list.isEmpty()) {
                                        if (vertex.keys().contains(entry.getKey())) {
                                            vertex.property(entry.getKey()).remove();
                                        }
                                        continue;
                                    }
                                    value = list.toArray();
                                }
                                vertex.property(entry.getKey(), value);
                            }
                        }

                        // Convert to Node object using the UPDATED vertex reference
                        Node node = JanusGraphNodeUtil.getNode(graphId, vertex);
                        updatedNodes.put(identifier, node);
                    }
                }

                try {
                    tx.commit();
                } catch (Exception commitEx) {
                    TelemetryManager
                            .error("NodeAsyncOperations.updateNodes: EXCEPTION during commit: " + commitEx.getMessage(),
                                    commitEx);
                    throw commitEx;
                }

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
                            value = JsonUtils.serialize(value);
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
