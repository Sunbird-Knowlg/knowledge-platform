error id: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/SearchAsyncOperations.java:_empty_/JanusGraphNodeUtil#getRelation#
file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/SearchAsyncOperations.java
empty definition using pc, found symbol in pc: _empty_/JanusGraphNodeUtil#getRelation#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 15895
uri: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/SearchAsyncOperations.java
text:
```scala
package org.sunbird.graph.service.operation;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.sunbird.common.dto.Property;
import org.sunbird.common.dto.Request;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.MiddlewareException;
import org.sunbird.common.exception.ResourceNotFoundException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.GraphDACParams;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.Relation;
import org.sunbird.graph.dac.model.SearchCriteria;
import org.sunbird.graph.dac.util.JanusGraphNodeUtil;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.common.GraphOperation;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.graph.service.util.GremlinQueryBuilder;
import org.sunbird.telemetry.logger.TelemetryManager;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Search async operations using JanusGraph/Gremlin
 * Replaces Neo4j search operations with Gremlin traversals
 */
public class SearchAsyncOperations {

    /**
     * Get a node by its unique identifier.
     *
     * @param graphId  the graph id
     * @param nodeId   the node unique identifier
     * @param getTags  whether to fetch tags (relations)
     * @param request  the request
     * @return Future<Node> with the found node
     */
    public static Future<Node> getNodeByUniqueId(String graphId, String nodeId, Boolean getTags, Request request) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            if (StringUtils.isBlank(graphId))
                throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                        DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Node By Unique Id' Operation Failed.]");

            if (StringUtils.isBlank(nodeId))
                throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
                        DACErrorMessageConstants.INVALID_IDENTIFIER + " | ['Get Node By Unique Id' Operation Failed.]");

            GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.READ);
            TelemetryManager.log("GraphTraversalSource Initialized. | [Graph Id: " + graphId + ", Node Id: " + nodeId + "]");

            try {
                Iterator<Vertex> vertexIter = g.V()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), nodeId)
                        .has("graphId", graphId);

                if (!vertexIter.hasNext()) {
                    throw new ResourceNotFoundException(DACErrorCodeConstants.NOT_FOUND.name(),
                            "Node not found with id: " + nodeId + " | ['Get Node By Unique Id' Operation Failed.]");
                }

                Vertex vertex = vertexIter.next();
                Node node;
                
                if (getTags != null && getTags) {
                    // Get node with relations (tags)
                    node = JanusGraphNodeUtil.getNode(graphId, vertex);
                } else {
                    // Get node without relations
                    node = JanusGraphNodeUtil.getNodeWithoutRelations(graphId, vertex);
                }

                TelemetryManager.log("'Get Node By Unique Id' Operation Finished. | Node ID: " + nodeId);
                return node;

            } catch (MiddlewareException e) {
                throw e;
            } catch (Exception e) {
                TelemetryManager.error("Error getting node by unique id: " + e.getMessage(), e);
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                        "Error! Something went wrong while fetching node. ", e);
            }
        }));
    }

    /**
     * Get a specific property of a node.
     *
     * @param graphId    the graph id
     * @param identifier the node identifier
     * @param property   the property name to fetch
     * @return Future<Property> with the property value
     */
    public static Future<Property> getNodeProperty(String graphId, String identifier, String property) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            if (StringUtils.isBlank(graphId))
                throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                        DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Node Property' Operation Failed.]");

            if (StringUtils.isBlank(identifier))
                throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
                        DACErrorMessageConstants.INVALID_IDENTIFIER + " | ['Get Node Property' Operation Failed.]");

            if (StringUtils.isBlank(property))
                throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name(),
                        "Invalid property name. | ['Get Node Property' Operation Failed.]");

            GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.READ);
            TelemetryManager.log("GraphTraversalSource Initialized. | [Graph Id: " + graphId + ", Node Id: " + identifier + ", Property: " + property + "]");

            try {
                Iterator<Vertex> vertexIter = g.V()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), identifier)
                        .has("graphId", graphId);

                if (!vertexIter.hasNext()) {
                    throw new ResourceNotFoundException(DACErrorCodeConstants.NOT_FOUND.name(),
                            "Node not found with id: " + identifier + " | ['Get Node Property' Operation Failed.]");
                }

                Vertex vertex = vertexIter.next();
                
                // Check if property exists
                if (!vertex.property(property).isPresent()) {
                    throw new ResourceNotFoundException(DACErrorCodeConstants.NOT_FOUND.name(),
                            "Property '" + property + "' not found on node: " + identifier);
                }

                Object value = vertex.value(property);
                Property prop = new Property(property, value);

                TelemetryManager.log("'Get Node Property' Operation Finished. | Node ID: " + identifier + ", Property: " + property);
                return prop;

            } catch (MiddlewareException e) {
                throw e;
            } catch (Exception e) {
                TelemetryManager.error("Error getting node property: " + e.getMessage(), e);
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                        "Error! Something went wrong while fetching node property. ", e);
            }
        }));
    }

    /**
     * Get multiple nodes by their unique identifiers using search criteria.
     *
     * @param graphId        the graph id
     * @param searchCriteria the search criteria containing node identifiers
     * @return Future<List < Node>> list of found nodes
     */
    public static Future<List<Node>> getNodeByUniqueIds(String graphId, SearchCriteria searchCriteria) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            if (StringUtils.isBlank(graphId))
                throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                        DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Nodes By Search Criteria' Operation Failed.]");

            if (null == searchCriteria)
                throw new ClientException(DACErrorCodeConstants.INVALID_CRITERIA.name(),
                        DACErrorMessageConstants.INVALID_SEARCH_CRITERIA + " | ['Get Nodes By Search Criteria' Operation Failed.]");

            GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.READ);
            TelemetryManager.log("GraphTraversalSource Initialized. | [Graph Id: " + graphId + "]");

            try {
                List<Node> nodes = new ArrayList<>();
                
                // Extract identifiers from search criteria
                Map<String, Object> params = searchCriteria.getParams();
                if (params == null || params.isEmpty()) {
                    return nodes; // Empty result
                }

                // Check for identifiers parameter
                Object identifiersObj = params.get("identifiers");
                if (identifiersObj == null) {
                    return nodes;
                }

                List<String> identifiers;
                if (identifiersObj instanceof List) {
                    identifiers = (List<String>) identifiersObj;
                } else if (identifiersObj instanceof String) {
                    identifiers = new ArrayList<>();
                    identifiers.add((String) identifiersObj);
                } else {
                    return nodes;
                }

                if (CollectionUtils.isEmpty(identifiers)) {
                    return nodes;
                }

                // Fetch all matching nodes
                Iterator<Vertex> vertexIter = g.V()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), org.apache.tinkerpop.gremlin.process.traversal.P.within(identifiers))
                        .has("graphId", graphId);

                while (vertexIter.hasNext()) {
                    Vertex vertex = vertexIter.next();
                    // Get node with relations
                    Node node = JanusGraphNodeUtil.getNode(graphId, vertex);
                    nodes.add(node);
                }

                TelemetryManager.log("'Get Nodes By Search Criteria' Operation Finished. | Found: " + nodes.size() + " nodes");
                return nodes;

            } catch (MiddlewareException e) {
                throw e;
            } catch (Exception e) {
                TelemetryManager.error("Error getting nodes by search criteria: " + e.getMessage(), e);
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                        "Error! Something went wrong while fetching nodes. ", e);
            }
        }));
    }

    /**
     * Get nodes by a specific property value.
     *
     * @param graphId       the graph id
     * @param propertyKey   the property key to filter by
     * @param propertyValue the property value to match
     * @return Future<List < Node>> list of matching nodes
     */
    public static Future<List<Node>> getNodesByProperty(String graphId, String propertyKey, Object propertyValue) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            if (StringUtils.isBlank(graphId))
                throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                        DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Nodes By Property' Operation Failed.]");

            if (StringUtils.isBlank(propertyKey))
                throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name(),
                        "Invalid property key. | ['Get Nodes By Property' Operation Failed.]");

            if (propertyValue == null)
                throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name(),
                        "Invalid property value. | ['Get Nodes By Property' Operation Failed.]");

            GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.READ);
            TelemetryManager.log("GraphTraversalSource Initialized. | [Graph Id: " + graphId + "]");

            try {
                List<Node> nodes = new ArrayList<>();
                
                Iterator<Vertex> vertexIter = g.V()
                        .has("graphId", graphId)
                        .has(propertyKey, propertyValue);

                while (vertexIter.hasNext()) {
                    Vertex vertex = vertexIter.next();
                    Node node = JanusGraphNodeUtil.getNode(graphId, vertex);
                    nodes.add(node);
                }

                TelemetryManager.log("'Get Nodes By Property' Operation Finished. | Found: " + nodes.size() + " nodes");
                return nodes;

            } catch (Exception e) {
                TelemetryManager.error("Error getting nodes by property: " + e.getMessage(), e);
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                        "Error! Something went wrong while fetching nodes by property. ", e);
            }
        }));
    }

    /**
     * Get all nodes in a graph.
     *
     * @param graphId the graph id
     * @return Future<List < Node>> list of all nodes
     */
    public static Future<List<Node>> getAllNodes(String graphId) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            if (StringUtils.isBlank(graphId))
                throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                        DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get All Nodes' Operation Failed.]");

            GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.READ);
            TelemetryManager.log("GraphTraversalSource Initialized. | [Graph Id: " + graphId + "]");

            try {
                List<Node> nodes = new ArrayList<>();
                
                Iterator<Vertex> vertexIter = g.V().has("graphId", graphId);

                while (vertexIter.hasNext()) {
                    Vertex vertex = vertexIter.next();
                    Node node = JanusGraphNodeUtil.getNodeWithoutRelations(graphId, vertex);
                    nodes.add(node);
                }

                TelemetryManager.log("'Get All Nodes' Operation Finished. | Found: " + nodes.size() + " nodes");
                return nodes;

            } catch (Exception e) {
                TelemetryManager.error("Error getting all nodes: " + e.getMessage(), e);
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                        "Error! Something went wrong while fetching all nodes. ", e);
            }
        }));
    }

    /**
     * Get all relations (edges) in a graph.
     *
     * @param graphId the graph id
     * @return Future<List < Relation>> list of all relations
     */
    public static Future<List<Relation>> getAllRelations(String graphId) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            if (StringUtils.isBlank(graphId))
                throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                        DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get All Relations' Operation Failed.]");

            GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.READ);
            TelemetryManager.log("GraphTraversalSource Initialized. | [Graph Id: " + graphId + "]");

            try {
                List<Relation> relations = new ArrayList<>();
                
                Iterator<Edge> edgeIter = GremlinQueryBuilder.getAllEdges(g, graphId);

                while (edgeIter.hasNext()) {
                    Edge edge = edgeIter.next();
                    Relation relation = JanusGraphNodeUtil.@@getRelation(edge);
                    relations.add(relation);
                }

                TelemetryManager.log("'Get All Relations' Operation Finished. | Found: " + relations.size() + " relations");
                return relations;

            } catch (Exception e) {
                TelemetryManager.error("Error getting all relations: " + e.getMessage(), e);
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                        "Error! Something went wrong while fetching all relations. ", e);
            }
        }));
    }

    /**
     * Get a specific relation between two nodes.
     *
     * @param graphId      the graph id
     * @param startNodeId  the start node id
     * @param endNodeId    the end node id
     * @param relationType the relation type
     * @return Future<Relation> the relation if found
     */
    public static Future<Relation> getRelation(String graphId, String startNodeId, String endNodeId, String relationType) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            if (StringUtils.isBlank(graphId))
                throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                        DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Relation' Operation Failed.]");

            if (StringUtils.isBlank(startNodeId))
                throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
                        DACErrorMessageConstants.INVALID_START_NODE_ID + " | ['Get Relation' Operation Failed.]");

            if (StringUtils.isBlank(endNodeId))
                throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
                        DACErrorMessageConstants.INVALID_END_NODE_ID + " | ['Get Relation' Operation Failed.]");

            if (StringUtils.isBlank(relationType))
                throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
                        DACErrorMessageConstants.INVALID_RELATION_TYPE + " | ['Get Relation' Operation Failed.]");

            GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.READ);
            TelemetryManager.log("GraphTraversalSource Initialized. | [Graph Id: " + graphId + "]");

            try {
                Edge edge = GremlinQueryBuilder.getEdgeBetweenNodes(g, graphId, startNodeId, endNodeId, relationType);
                
                if (edge == null) {
                    throw new ResourceNotFoundException(DACErrorCodeConstants.NOT_FOUND.name(),
                            "Relation not found between nodes: " + startNodeId + " -> " + endNodeId + 
                            " | Type: " + relationType);
                }

                Relation relation = JanusGraphNodeUtil.getRelation(edge);

                TelemetryManager.log("'Get Relation' Operation Finished.");
                return relation;

            } catch (MiddlewareException e) {
                throw e;
            } catch (Exception e) {
                TelemetryManager.error("Error getting relation: " + e.getMessage(), e);
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                        "Error! Something went wrong while fetching relation. ", e);
            }
        }));
    }

    /**
     * Get a property value from a relation.
     *
     * @param graphId      the graph id
     * @param startNodeId  the start node id
     * @param endNodeId    the end node id
     * @param relationType the relation type
     * @param propertyKey  the property key to retrieve
     * @return Future<Property> the property value
     */
    public static Future<Property> getRelationProperty(String graphId, String startNodeId, String endNodeId, 
            String relationType, String propertyKey) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            if (StringUtils.isBlank(graphId))
                throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                        DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Relation Property' Operation Failed.]");

            if (StringUtils.isBlank(startNodeId))
                throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
                        DACErrorMessageConstants.INVALID_START_NODE_ID + " | ['Get Relation Property' Operation Failed.]");

            if (StringUtils.isBlank(endNodeId))
                throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
                        DACErrorMessageConstants.INVALID_END_NODE_ID + " | ['Get Relation Property' Operation Failed.]");

            if (StringUtils.isBlank(relationType))
                throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
                        DACErrorMessageConstants.INVALID_RELATION_TYPE + " | ['Get Relation Property' Operation Failed.]");

            if (StringUtils.isBlank(propertyKey))
                throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name(),
                        "Invalid property key. | ['Get Relation Property' Operation Failed.]");

            GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.READ);
            TelemetryManager.log("GraphTraversalSource Initialized. | [Graph Id: " + graphId + "]");

            try {
                Object value = GremlinQueryBuilder.getEdgeProperty(g, graphId, startNodeId, endNodeId, 
                        relationType, propertyKey);
                
                if (value == null) {
                    throw new ResourceNotFoundException(DACErrorCodeConstants.NOT_FOUND.name(),
                            "Property '" + propertyKey + "' not found on relation between: " + startNodeId + 
                            " -> " + endNodeId);
                }

                Property property = new Property(propertyKey, value);

                TelemetryManager.log("'Get Relation Property' Operation Finished.");
                return property;

            } catch (MiddlewareException e) {
                throw e;
            } catch (Exception e) {
                TelemetryManager.error("Error getting relation property: " + e.getMessage(), e);
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                        "Error! Something went wrong while fetching relation property. ", e);
            }
        }));
    }

    /**
     * Count the total number of nodes in a graph.
     *
     * @param graphId the graph id
     * @return Future<Long> count of nodes
     */
    public static Future<Long> getNodesCount(String graphId) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            if (StringUtils.isBlank(graphId))
                throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                        DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Nodes Count' Operation Failed.]");

            GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.READ);
            TelemetryManager.log("GraphTraversalSource Initialized. | [Graph Id: " + graphId + "]");

            try {
                long count = g.V().has("graphId", graphId).count().next();

                TelemetryManager.log("'Get Nodes Count' Operation Finished. | Count: " + count);
                return count;

            } catch (Exception e) {
                TelemetryManager.error("Error counting nodes: " + e.getMessage(), e);
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                        "Error! Something went wrong while counting nodes. ", e);
            }
        }));
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/JanusGraphNodeUtil#getRelation#