error id: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/util/GremlinQueryBuilder.java:_empty_/`<any>`#has#as#select#addE#
file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/util/GremlinQueryBuilder.java
empty definition using pc, found symbol in pc: _empty_/`<any>`#has#as#select#addE#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 3234
uri: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/util/GremlinQueryBuilder.java
text:
```scala
package org.sunbird.graph.service.util;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.Map;
import java.util.HashMap;

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
     * Create edge between two vertices
     */
    public static GraphTraversal<Vertex, Edge> createEdge(GraphTraversalSource g, String graphId, 
            String startNodeId, String endNodeId, String relationType, Map<String, Object> metadata) {
        
        GraphTraversal<Vertex, Edge> traversal = g.V()
                .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                .has("graphId", graphId)
                .as("start")
                .V()
                .has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
                .has("graphId", graphId)
                .as("end")
                .select("start")
                .@@addE(relationType)
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
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/`<any>`#has#as#select#addE#