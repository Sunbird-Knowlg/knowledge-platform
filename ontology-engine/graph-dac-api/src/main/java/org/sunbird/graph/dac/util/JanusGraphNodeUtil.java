package org.sunbird.graph.dac.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.enums.GraphDACErrorCodes;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.Relation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Utility class to convert between JanusGraph Vertex objects and Node model objects
 */
public class JanusGraphNodeUtil {

    /**
     * Convert a JanusGraph Vertex to a Node object
     * 
     * @param graphId the graph identifier
     * @param vertex the JanusGraph vertex
     * @return Node object representing the vertex
     */
    public static Node getNode(String graphId, Vertex vertex) {
        if (null == vertex)
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_NODE.name(),
                    "Failed to create node object. Vertex from database is null.");
        
        Node node = new Node();
        node.setGraphId(graphId);
        
        // Convert vertex ID to long
        Object vertexId = vertex.id();
        if (vertexId instanceof Long) {
            node.setId((Long) vertexId);
        } else if (vertexId instanceof Number) {
            node.setId(((Number) vertexId).longValue());
        } else {
            node.setId(vertexId.hashCode());
        }

        Map<String, Object> metadata = new HashMap<>();
        Iterator<VertexProperty<Object>> properties = vertex.properties();
        
        while (properties.hasNext()) {
            VertexProperty<Object> property = properties.next();
            String key = property.key();
            Object value = property.value();
            
            if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_UNIQUE_ID.name())) {
                node.setIdentifier(value.toString());
            } else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_SYS_NODE_TYPE.name())) {
                node.setNodeType(value.toString());
            } else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_FUNC_OBJECT_TYPE.name())) {
                node.setObjectType(value.toString());
            } else if (!StringUtils.equalsIgnoreCase(key, "graphId")) {
                // Skip graphId as it's internal
                metadata.put(key, value);
            }
        }
        node.setMetadata(metadata);

        // Get outgoing relations
        Iterator<Edge> outEdges = vertex.edges(Direction.OUT);
        if (outEdges.hasNext()) {
            List<Relation> outRelations = new ArrayList<>();
            while (outEdges.hasNext()) {
                Edge edge = outEdges.next();
                outRelations.add(createRelation(graphId, edge, vertex));
            }
            node.setOutRelations(outRelations);
        }

        // Get incoming relations
        Iterator<Edge> inEdges = vertex.edges(Direction.IN);
        if (inEdges.hasNext()) {
            List<Relation> inRelations = new ArrayList<>();
            while (inEdges.hasNext()) {
                Edge edge = inEdges.next();
                inRelations.add(createRelation(graphId, edge, vertex));
            }
            node.setInRelations(inRelations);
        }

        return node;
    }

    /**
     * Convert a JanusGraph Vertex to a Node object with minimal data (no relations)
     * 
     * @param graphId the graph identifier
     * @param vertex the JanusGraph vertex
     * @return Node object representing the vertex without relations
     */
    public static Node getNodeWithoutRelations(String graphId, Vertex vertex) {
        if (null == vertex)
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_NODE.name(),
                    "Failed to create node object. Vertex from database is null.");
        
        Node node = new Node();
        node.setGraphId(graphId);
        
        // Convert vertex ID to long
        Object vertexId = vertex.id();
        if (vertexId instanceof Long) {
            node.setId((Long) vertexId);
        } else if (vertexId instanceof Number) {
            node.setId(((Number) vertexId).longValue());
        } else {
            node.setId(vertexId.hashCode());
        }

        Map<String, Object> metadata = new HashMap<>();
        Iterator<VertexProperty<Object>> properties = vertex.properties();
        
        while (properties.hasNext()) {
            VertexProperty<Object> property = properties.next();
            String key = property.key();
            Object value = property.value();
            
            if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_UNIQUE_ID.name())) {
                node.setIdentifier(value.toString());
            } else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_SYS_NODE_TYPE.name())) {
                node.setNodeType(value.toString());
            } else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_FUNC_OBJECT_TYPE.name())) {
                node.setObjectType(value.toString());
            } else if (!StringUtils.equalsIgnoreCase(key, "graphId")) {
                metadata.put(key, value);
            }
        }
        node.setMetadata(metadata);

        return node;
    }

    /**
     * Create a Relation object from an Edge
     * 
     * @param graphId the graph identifier
     * @param edge the JanusGraph edge
     * @param currentVertex the current vertex (to determine direction)
     * @return Relation object
     */
    private static Relation createRelation(String graphId, Edge edge, Vertex currentVertex) {
        Relation relation = new Relation();
        relation.setRelationType(edge.label());
        
        // Determine start and end nodes based on direction
        Vertex startVertex = edge.outVertex();
        Vertex endVertex = edge.inVertex();
        
        Object startId = startVertex.property(SystemProperties.IL_UNIQUE_ID.name()).value();
        Object endId = endVertex.property(SystemProperties.IL_UNIQUE_ID.name()).value();
        
        relation.setStartNodeId(startId != null ? startId.toString() : null);
        relation.setEndNodeId(endId != null ? endId.toString() : null);
        
        // Get edge properties as metadata
        Map<String, Object> metadata = new HashMap<>();
        Iterator<Property<Object>> edgeProperties = edge.properties();
        while (edgeProperties.hasNext()) {
            Property<Object> property = edgeProperties.next();
            metadata.put(property.key(), property.value());
        }
        relation.setMetadata(metadata);
        
        return relation;
    }

    /**
     * Extract properties from a Vertex as a Map
     * 
     * @param vertex the JanusGraph vertex
     * @return Map of property key-value pairs
     */
    public static Map<String, Object> getVertexProperties(Vertex vertex) {
        Map<String, Object> properties = new HashMap<>();
        Iterator<VertexProperty<Object>> propertyIterator = vertex.properties();
        
        while (propertyIterator.hasNext()) {
            VertexProperty<Object> property = propertyIterator.next();
            properties.put(property.key(), property.value());
        }
        
        return properties;
    }

    /**
     * Extract properties from an Edge as a Map
     * 
     * @param edge the JanusGraph edge
     * @return Map of property key-value pairs
     */
    public static Map<String, Object> getEdgeProperties(Edge edge) {
        Map<String, Object> properties = new HashMap<>();
        Iterator<Property<Object>> propertyIterator = edge.properties();
        
        while (propertyIterator.hasNext()) {
            Property<Object> property = propertyIterator.next();
            properties.put(property.key(), property.value());
        }
        
        return properties;
    }
}
