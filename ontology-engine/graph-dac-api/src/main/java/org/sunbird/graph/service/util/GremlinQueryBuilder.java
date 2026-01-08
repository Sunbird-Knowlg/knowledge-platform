package org.sunbird.graph.service.util;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Utility class to build Gremlin traversals for graph operations
 * This replaces the Cypher query generation for Neo4j
 */
public class GremlinQueryBuilder {

    /**
     * Find vertex by unique identifier
     */
    public static GraphTraversal<Vertex, Vertex> getVertexByIdentifier(GraphTraversalSource g, String graphId, String identifier) {
        return g.V()
                .has(SystemProperties.IL_UNIQUE_ID.name(), identifier)
                .has("graphId", graphId);
    }

    /**
     * Create vertex with properties
     */
    public static GraphTraversal<Vertex, Vertex> createVertex(GraphTraversalSource g, String graphId, Map<String, Object> properties) {
        GraphTraversal<Vertex, Vertex> traversal = g.addV(graphId);
        
        // Add graphId label
        properties.put("graphId", graphId);
        
        // Add all properties
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getValue() != null) {
                traversal = traversal.property(entry.getKey(), entry.getValue());
            }
        }
        
        return traversal;
    }

    /**
     * Update vertex properties
     */
    public static GraphTraversal<Vertex, Vertex> updateVertex(GraphTraversalSource g, String graphId, String identifier, Map<String, Object> properties) {
        GraphTraversal<Vertex, Vertex> traversal = getVertexByIdentifier(g, graphId, identifier);
        
        // Update properties
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getValue() != null) {
                traversal = traversal.property(entry.getKey(), entry.getValue());
            }
        }
        
        return traversal;
    }

    /**
     * Delete vertex
     */
    public static GraphTraversal<Vertex, Vertex> deleteVertex(GraphTraversalSource g, String graphId, String identifier) {
        return getVertexByIdentifier(g, graphId, identifier).drop();
    }

    /**
     * Create edge between two vertices with MERGE behavior (idempotent)
     * This implements Neo4j MERGE functionality - creates edge only if it doesn't exist
     * If edge exists, updates metadata on MATCH, otherwise sets metadata on CREATE
     */
    public static GraphTraversal<Vertex, Edge> createEdge(GraphTraversalSource g, String graphId, 
            String startNodeId, String endNodeId, String relationType, Map<String, Object> metadata) {
        
        // First check if edge already exists
        try {
            Edge existingEdge = g.V()
                    .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                    .has("graphId", graphId)
                    .outE(relationType)
                    .where(__.inV()
                            .has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
                            .has("graphId", graphId))
                    .tryNext()
                    .orElse(null);
            
            if (existingEdge != null) {
                // Edge exists - update metadata (ON MATCH behavior)
                TelemetryManager.log("Edge already exists, updating metadata | Start: " + startNodeId + 
                        ", End: " + endNodeId + ", Type: " + relationType);
                
                GraphTraversal<Vertex, Edge> updateTraversal = g.V()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                        .outE(relationType)
                        .where(__.inV().has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId));
                
                if (metadata != null && !metadata.isEmpty()) {
                    for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                        if (entry.getValue() != null) {
                            updateTraversal = updateTraversal.property(entry.getKey(), entry.getValue());
                        }
                    }
                }
                return updateTraversal;
            }
            
            // Edge doesn't exist - create new one (ON CREATE behavior)
            TelemetryManager.log("Creating new edge | Start: " + startNodeId + 
                    ", End: " + endNodeId + ", Type: " + relationType);
            
            GraphTraversal<Vertex, Edge> traversal = g.V()
                    .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                    .has("graphId", graphId)
                    .as("start")
                    .V()
                    .has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
                    .has("graphId", graphId)
                    .as("end")
                    .select("start")
                    .addE(relationType)
                    .to("end");
            
            // Add metadata properties to edge
            if (metadata != null && !metadata.isEmpty()) {
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    if (entry.getValue() != null) {
                        traversal = traversal.property(entry.getKey(), entry.getValue());
                    }
                }
            }
            
            return traversal;
        } catch (Exception e) {
            TelemetryManager.error("Error in MERGE edge operation", e);
            throw e;
        }
    }

    /**
     * Update edge properties
     */
    public static GraphTraversal<Vertex, Edge> updateEdge(GraphTraversalSource g, String graphId,
            String startNodeId, String endNodeId, String relationType, Map<String, Object> metadata) {
        
        GraphTraversal<Vertex, Edge> traversal = g.V()
                .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                .has("graphId", graphId)
                .outE(relationType)
                .where(__.inV().has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId));
        
        // Update metadata properties
        if (metadata != null && !metadata.isEmpty()) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (entry.getValue() != null) {
                    traversal = traversal.property(entry.getKey(), entry.getValue());
                }
            }
        }
        
        return traversal;
    }

    /**
     * Delete edge
     */
    public static GraphTraversal<Vertex, ?> deleteEdge(GraphTraversalSource g, String graphId,
            String startNodeId, String endNodeId, String relationType) {
        
        return g.V()
                .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                .has("graphId", graphId)
                .outE(relationType)
                .where(__.inV().has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId))
                .drop();
    }

    /**
     * Get all vertices in a graph
     */
    public static GraphTraversal<Vertex, Vertex> getAllVertices(GraphTraversalSource g, String graphId) {
        return g.V().has("graphId", graphId);
    }

    /**
     * Get outgoing edges from a vertex
     */
    public static GraphTraversal<Vertex, Edge> getOutgoingEdges(GraphTraversalSource g, String graphId, String identifier, String relationType) {
        GraphTraversal<Vertex, Vertex> vertexTraversal = getVertexByIdentifier(g, graphId, identifier);
        
        if (StringUtils.isNotBlank(relationType)) {
            return vertexTraversal.outE(relationType);
        } else {
            return vertexTraversal.outE();
        }
    }

    /**
     * Get incoming edges to a vertex
     */
    public static GraphTraversal<Vertex, Edge> getIncomingEdges(GraphTraversalSource g, String graphId, String identifier, String relationType) {
        GraphTraversal<Vertex, Vertex> traversal = getVertexByIdentifier(g, graphId, identifier);
        
        if (StringUtils.isNotBlank(relationType)) {
            return traversal.inE(relationType);
        } else {
            return traversal.inE();
        }
    }

    /**
     * Count vertices in a graph
     */
    public static GraphTraversal<Vertex, Long> countVertices(GraphTraversalSource g, String graphId) {
        return g.V().has("graphId", graphId).count();
    }

    /**
     * Search vertices by property value
     */
    public static GraphTraversal<Vertex, Vertex> searchVerticesByProperty(GraphTraversalSource g, String graphId, String propertyKey, Object propertyValue) {
        return g.V()
                .has("graphId", graphId)
                .has(propertyKey, propertyValue);
    }

    /**
     * Execute traversal and get single vertex result
     */
    public static Vertex executeSingleVertex(GraphTraversal<?, Vertex> traversal) {
        try {
            if (traversal.hasNext()) {
                return traversal.next();
            }
            return null;
        } catch (Exception e) {
            TelemetryManager.error("Error executing vertex traversal", e);
            throw e;
        }
    }

    /**
     * Execute traversal and get single edge result
     */
    public static Edge executeSingleEdge(GraphTraversal<?, Edge> traversal) {
        try {
            if (traversal.hasNext()) {
                return traversal.next();
            }
            return null;
        } catch (Exception e) {
            TelemetryManager.error("Error executing edge traversal", e);
            throw e;
        }
    }

    /**
     * Merge (upsert) edge - creates if doesn't exist, updates if exists
     * This implements Neo4j MERGE behavior with ON CREATE SET and ON MATCH SET clauses
     * 
     * @param g GraphTraversalSource
     * @param graphId Graph identifier
     * @param startNodeId Start node unique ID
     * @param endNodeId End node unique ID  
     * @param relationType Relationship/edge type
     * @param createMetadata Metadata to set only when creating new edge (ON CREATE)
     * @param matchMetadata Metadata to set when edge already exists (ON MATCH)
     * @return Edge traversal
     */
    public static GraphTraversal<Vertex, Edge> mergeEdgeWithMetadata(GraphTraversalSource g, String graphId,
            String startNodeId, String endNodeId, String relationType,
            Map<String, Object> createMetadata, Map<String, Object> matchMetadata) {
        
        try {
            // Check if edge already exists
            Edge existingEdge = g.V()
                    .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                    .has("graphId", graphId)
                    .outE(relationType)
                    .where(__.inV()
                            .has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
                            .has("graphId", graphId))
                    .tryNext()
                    .orElse(null);
            
            if (existingEdge != null) {
                // ON MATCH - edge exists, update with matchMetadata
                TelemetryManager.log("MERGE: Edge exists (ON MATCH) | Start: " + startNodeId + 
                        ", End: " + endNodeId + ", Type: " + relationType);
                
                GraphTraversal<Vertex, Edge> updateTraversal = g.V()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                        .outE(relationType)
                        .where(__.inV().has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId));
                
                if (matchMetadata != null && !matchMetadata.isEmpty()) {
                    for (Map.Entry<String, Object> entry : matchMetadata.entrySet()) {
                        if (entry.getValue() != null) {
                            updateTraversal = updateTraversal.property(entry.getKey(), entry.getValue());
                        }
                    }
                }
                return updateTraversal;
            } else {
                // ON CREATE - edge doesn't exist, create with createMetadata
                TelemetryManager.log("MERGE: Creating new edge (ON CREATE) | Start: " + startNodeId + 
                        ", End: " + endNodeId + ", Type: " + relationType);
                
                GraphTraversal<Vertex, Edge> traversal = g.V()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                        .has("graphId", graphId)
                        .as("start")
                        .V()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
                        .has("graphId", graphId)
                        .as("end")
                        .select("start")
                        .addE(relationType)
                        .to("end");
                
                // Add createMetadata properties
                if (createMetadata != null && !createMetadata.isEmpty()) {
                    for (Map.Entry<String, Object> entry : createMetadata.entrySet()) {
                        if (entry.getValue() != null) {
                            traversal = traversal.property(entry.getKey(), entry.getValue());
                        }
                    }
                }
                
                return traversal;
            }
        } catch (Exception e) {
            TelemetryManager.error("Error in MERGE edge with metadata operation", e);
            throw e;
        }
    }

    /**
     * Check if edge exists between two nodes
     * 
     * @param g GraphTraversalSource
     * @param graphId Graph identifier
     * @param startNodeId Start node unique ID
     * @param endNodeId End node unique ID
     * @param relationType Relationship/edge type
     * @return true if edge exists, false otherwise
     */
    public static boolean edgeExists(GraphTraversalSource g, String graphId,
            String startNodeId, String endNodeId, String relationType) {
        try {
            return g.V()
                    .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                    .has("graphId", graphId)
                    .outE(relationType)
                    .where(__.inV()
                            .has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
                            .has("graphId", graphId))
                    .hasNext();
        } catch (Exception e) {
            TelemetryManager.error("Error checking edge existence", e);
            return false;
        }
    }

    /**
     * Create multiple incoming edges (many-to-one relationship)
     * Creates edges from multiple start nodes to a single end node
     * 
     * @param g GraphTraversalSource
     * @param graphId Graph identifier
     * @param startNodeIds List of start node IDs
     * @param endNodeId Single end node ID
     * @param relationType Relationship/edge type
     * @param metadata Metadata to set on all created edges
     * @return List of created edges
     */
    public static List<Edge> createIncomingEdges(GraphTraversalSource g, String graphId,
            List<String> startNodeIds, String endNodeId, String relationType,
            Map<String, Object> metadata) {
        
        List<Edge> createdEdges = new ArrayList<>();
        
        try {
            TelemetryManager.log("Creating incoming edges | StartNodes: " + startNodeIds.size() + 
                    ", EndNode: " + endNodeId + ", Type: " + relationType);
            
            for (String startNodeId : startNodeIds) {
                GraphTraversal<Vertex, Edge> traversal = g.V()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                        .has("graphId", graphId)
                        .as("start")
                        .V()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
                        .has("graphId", graphId)
                        .as("end")
                        .select("start")
                        .addE(relationType)
                        .to("end");
                
                // Add metadata properties
                if (metadata != null && !metadata.isEmpty()) {
                    for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                        if (entry.getValue() != null) {
                            traversal = traversal.property(entry.getKey(), entry.getValue());
                        }
                    }
                }
                
                Edge edge = traversal.next();
                createdEdges.add(edge);
            }
            
            TelemetryManager.log("Created " + createdEdges.size() + " incoming edges");
            return createdEdges;
            
        } catch (Exception e) {
            TelemetryManager.error("Error creating incoming edges", e);
            throw e;
        }
    }

    /**
     * Create multiple outgoing edges (one-to-many relationship)
     * Creates edges from a single start node to multiple end nodes
     * 
     * @param g GraphTraversalSource
     * @param graphId Graph identifier
     * @param startNodeId Single start node ID
     * @param endNodeIds List of end node IDs
     * @param relationType Relationship/edge type
     * @param metadata Metadata to set on all created edges
     * @return List of created edges
     */
    public static List<Edge> createOutgoingEdges(GraphTraversalSource g, String graphId,
            String startNodeId, List<String> endNodeIds, String relationType,
            Map<String, Object> metadata) {
        
        List<Edge> createdEdges = new ArrayList<>();
        
        try {
            TelemetryManager.log("Creating outgoing edges | StartNode: " + startNodeId + 
                    ", EndNodes: " + endNodeIds.size() + ", Type: " + relationType);
            
            for (String endNodeId : endNodeIds) {
                GraphTraversal<Vertex, Edge> traversal = g.V()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                        .has("graphId", graphId)
                        .as("start")
                        .V()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
                        .has("graphId", graphId)
                        .as("end")
                        .select("start")
                        .addE(relationType)
                        .to("end");
                
                // Add metadata properties
                if (metadata != null && !metadata.isEmpty()) {
                    for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                        if (entry.getValue() != null) {
                            traversal = traversal.property(entry.getKey(), entry.getValue());
                        }
                    }
                }
                
                Edge edge = traversal.next();
                createdEdges.add(edge);
            }
            
            TelemetryManager.log("Created " + createdEdges.size() + " outgoing edges");
            return createdEdges;
            
        } catch (Exception e) {
            TelemetryManager.error("Error creating outgoing edges", e);
            throw e;
        }
    }

    /**
     * Delete multiple incoming edges (many-to-one relationship)
     * Deletes edges from multiple start nodes to a single end node
     * 
     * @param g GraphTraversalSource
     * @param graphId Graph identifier
     * @param startNodeIds List of start node IDs
     * @param endNodeId Single end node ID
     * @param relationType Relationship/edge type
     * @return Count of deleted edges
     */
    public static int deleteIncomingEdges(GraphTraversalSource g, String graphId,
            List<String> startNodeIds, String endNodeId, String relationType) {
        
        try {
            TelemetryManager.log("Deleting incoming edges | StartNodes: " + startNodeIds.size() + 
                    ", EndNode: " + endNodeId + ", Type: " + relationType);
            
            // Use P.within for efficient batch deletion
            long deletedCount = g.V()
                    .has(SystemProperties.IL_UNIQUE_ID.name(), P.within(startNodeIds))
                    .has("graphId", graphId)
                    .outE(relationType)
                    .where(__.inV()
                            .has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
                            .has("graphId", graphId))
                    .drop()
                    .count()
                    .next();
            
            TelemetryManager.log("Deleted " + deletedCount + " incoming edges");
            return (int) deletedCount;
            
        } catch (Exception e) {
            TelemetryManager.error("Error deleting incoming edges", e);
            throw e;
        }
    }

    /**
     * Delete multiple outgoing edges (one-to-many relationship)
     * Deletes edges from a single start node to multiple end nodes
     * 
     * @param g GraphTraversalSource
     * @param graphId Graph identifier
     * @param startNodeId Single start node ID
     * @param endNodeIds List of end node IDs
     * @param relationType Relationship/edge type
     * @return Count of deleted edges
     */
    public static int deleteOutgoingEdges(GraphTraversalSource g, String graphId,
            String startNodeId, List<String> endNodeIds, String relationType) {
        
        try {
            TelemetryManager.log("Deleting outgoing edges | StartNode: " + startNodeId + 
                    ", EndNodes: " + endNodeIds.size() + ", Type: " + relationType);
            
            // Use P.within for efficient batch deletion
            long deletedCount = g.V()
                    .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                    .has("graphId", graphId)
                    .outE(relationType)
                    .where(__.inV()
                            .has(SystemProperties.IL_UNIQUE_ID.name(), P.within(endNodeIds))
                            .has("graphId", graphId))
                    .drop()
                    .count()
                    .next();
            
            TelemetryManager.log("Deleted " + deletedCount + " outgoing edges");
            return (int) deletedCount;
            
        } catch (Exception e) {
            TelemetryManager.error("Error deleting outgoing edges", e);
            throw e;
        }
    }

    /**
     * Remove a specific property from an edge
     * 
     * @param g GraphTraversalSource
     * @param graphId Graph identifier
     * @param startNodeId Start node ID
     * @param endNodeId End node ID
     * @param relationType Relationship/edge type
     * @param propertyKey Property key to remove
     */
    public static void removeEdgeProperty(GraphTraversalSource g, String graphId,
            String startNodeId, String endNodeId, String relationType, String propertyKey) {
        
        try {
            TelemetryManager.log("Removing edge property | Start: " + startNodeId + 
                    ", End: " + endNodeId + ", Type: " + relationType + ", Property: " + propertyKey);
            
            g.V()
                    .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                    .has("graphId", graphId)
                    .outE(relationType)
                    .where(__.inV()
                            .has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
                            .has("graphId", graphId))
                    .properties(propertyKey)
                    .drop()
                    .iterate();
            
            TelemetryManager.log("Removed property '" + propertyKey + "' from edge");
            
        } catch (Exception e) {
            TelemetryManager.error("Error removing edge property", e);
            throw e;
        }
    }

    /**
     * Get all edges in a graph
     * 
     * @param g GraphTraversalSource
     * @param graphId Graph identifier
     * @return GraphTraversal of all edges
     */
    public static GraphTraversal<Edge, Edge> getAllEdges(GraphTraversalSource g, String graphId) {
        return g.E().where(__.bothV().has("graphId", graphId));
    }

    /**
     * Get nodes by property with additional filters
     * 
     * @param g GraphTraversalSource
     * @param graphId Graph identifier
     * @param propertyFilters Map of property name to value filters
     * @return GraphTraversal of matching vertices
     */
    public static GraphTraversal<Vertex, Vertex> getNodesByPropertyFilters(GraphTraversalSource g, 
            String graphId, Map<String, Object> propertyFilters) {
        
        GraphTraversal<Vertex, Vertex> traversal = g.V().has("graphId", graphId);
        
        if (propertyFilters != null && !propertyFilters.isEmpty()) {
            for (Map.Entry<String, Object> filter : propertyFilters.entrySet()) {
                if (filter.getValue() != null) {
                    traversal = traversal.has(filter.getKey(), filter.getValue());
                }
            }
        }
        
        return traversal;
    }

    /**
     * Get specific edge between two nodes
     * 
     * @param g GraphTraversalSource
     * @param graphId Graph identifier
     * @param startNodeId Start node ID
     * @param endNodeId End node ID
     * @param relationType Relationship/edge type
     * @return Edge if found, null otherwise
     */
    public static Edge getEdgeBetweenNodes(GraphTraversalSource g, String graphId,
            String startNodeId, String endNodeId, String relationType) {
        
        try {
            return g.V()
                    .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                    .has("graphId", graphId)
                    .outE(relationType)
                    .where(__.inV()
                            .has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
                            .has("graphId", graphId))
                    .tryNext()
                    .orElse(null);
        } catch (Exception e) {
            TelemetryManager.error("Error getting edge between nodes", e);
            return null;
        }
    }

    /**
     * Get edge property value
     * 
     * @param g GraphTraversalSource
     * @param graphId Graph identifier
     * @param startNodeId Start node ID
     * @param endNodeId End node ID
     * @param relationType Relationship/edge type
     * @param propertyKey Property key to retrieve
     * @return Property value if found, null otherwise
     */
    public static Object getEdgeProperty(GraphTraversalSource g, String graphId,
            String startNodeId, String endNodeId, String relationType, String propertyKey) {
        
        try {
            Edge edge = getEdgeBetweenNodes(g, graphId, startNodeId, endNodeId, relationType);
            if (edge != null && edge.property(propertyKey).isPresent()) {
                return edge.property(propertyKey).value();
            }
            return null;
        } catch (Exception e) {
            TelemetryManager.error("Error getting edge property", e);
            return null;
        }
    }

    /**
     * Count edges of a specific type in a graph
     * 
     * @param g GraphTraversalSource
     * @param graphId Graph identifier
     * @param relationType Optional relationship type filter
     * @return Count of edges
     */
    public static long countEdges(GraphTraversalSource g, String graphId, String relationType) {
        try {
            if (StringUtils.isNotBlank(relationType)) {
                return g.E()
                        .hasLabel(relationType)
                        .where(__.bothV().has("graphId", graphId))
                        .count()
                        .next();
            } else {
                return g.E()
                        .where(__.bothV().has("graphId", graphId))
                        .count()
                        .next();
            }
        } catch (Exception e) {
            TelemetryManager.error("Error counting edges", e);
            return 0;
        }
    }

    /**
     * Remove vertex property
     * 
     * @param g GraphTraversalSource
     * @param graphId Graph identifier
     * @param identifier Vertex identifier
     * @param propertyKey Property key to remove
     */
    public static void removeVertexProperty(GraphTraversalSource g, String graphId,
            String identifier, String propertyKey) {
        
        try {
            g.V()
                    .has(SystemProperties.IL_UNIQUE_ID.name(), identifier)
                    .has("graphId", graphId)
                    .properties(propertyKey)
                    .drop()
                    .iterate();
            
            TelemetryManager.log("Removed property '" + propertyKey + "' from vertex: " + identifier);
            
        } catch (Exception e) {
            TelemetryManager.error("Error removing vertex property", e);
            throw e;
        }
    }
}
