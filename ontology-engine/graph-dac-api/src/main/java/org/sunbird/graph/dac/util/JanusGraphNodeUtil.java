package org.sunbird.graph.dac.util;

import org.apache.commons.lang3.StringUtils;
import org.janusgraph.core.JanusGraphEdge;
import org.janusgraph.core.JanusGraphElement;
import org.janusgraph.core.JanusGraphVertex;
import org.janusgraph.core.JanusGraphVertexProperty;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.enums.GraphDACErrorCodes;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.Relation;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Utility class to convert between JanusGraph Vertex objects and Node model
 * objects
 */
public class JanusGraphNodeUtil {

    private static ObjectMapper mapper = new ObjectMapper();

    /**
     * Convert a JanusGraph Vertex to a Node object
     * 
     * @param graphId the graph identifier
     * @param vertex  the JanusGraph vertex
     * @return Node object representing the vertex with relations
     */
    public static Node getNode(String graphId, JanusGraphVertex vertex) {
        // Get base node without relations
        Node node = getNodeWithoutRelations(graphId, vertex);

        // Add outgoing relations
        Iterator<JanusGraphEdge> outEdges = getEdges(vertex, "OUT");
        if (outEdges.hasNext()) {
            List<Relation> outRelations = new ArrayList<>();
            while (outEdges.hasNext()) {
                JanusGraphEdge edge = outEdges.next();
                outRelations.add(createRelation(graphId, edge, vertex));
            }
            node.setOutRelations(outRelations);
        }

        // Add incoming relations
        Iterator<JanusGraphEdge> inEdges = getEdges(vertex, "IN");
        if (inEdges.hasNext()) {
            List<Relation> inRelations = new ArrayList<>();
            while (inEdges.hasNext()) {
                JanusGraphEdge edge = inEdges.next();
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
     * @param vertex  the JanusGraph vertex
     * @return Node object representing the vertex without relations
     */
    public static Node getNodeWithoutRelations(String graphId, JanusGraphVertex vertex) {
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
        Iterator<JanusGraphVertexProperty> properties = vertex.query().properties().iterator();

        while (properties.hasNext()) {
            JanusGraphVertexProperty property = properties.next();
            String key = property.key();
            Object value = property.value();

            if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_UNIQUE_ID.name())) {
                node.setIdentifier(value.toString());
            } else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_SYS_NODE_TYPE.name())) {
                node.setNodeType(value.toString());
            } else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_FUNC_OBJECT_TYPE.name())) {
                node.setObjectType(value.toString());
            } else if (!StringUtils.equalsIgnoreCase(key, "graphId")) {
                if (value instanceof List) {
                    // Keep as List to match Neo4j behavior
                    metadata.put(key, new ArrayList<>((List<?>) value));
                } else if (value instanceof String && ((String) value).startsWith("[")
                        && ((String) value).endsWith("]")) {
                    try {
                        metadata.put(key, mapper.readValue((String) value, List.class));
                    } catch (Exception e) {
                        metadata.put(key, value);
                    }
                } else {
                    metadata.put(key, value);
                }
            }
        }
        node.setMetadata(metadata);

        return node;
    }

    /**
     * Create a Relation object from an Edge
     * 
     * @param graphId       the graph identifier
     * @param edge          the JanusGraph edge
     * @param currentVertex the current vertex (to determine direction)
     * @return Relation object
     */
    private static Relation createRelation(String graphId, JanusGraphEdge edge, JanusGraphVertex currentVertex) {
        Relation relation = new Relation();
        relation.setRelationType(edge.label());

        // Determine start and end nodes based on direction
        JanusGraphVertex startVertex = edge.outVertex();
        JanusGraphVertex endVertex = edge.inVertex();

        if (null != currentVertex) {
            if (StringUtils.equals(startVertex.id().toString(), currentVertex.id().toString())) {
                startVertex = currentVertex;
            }
            if (StringUtils.equals(endVertex.id().toString(), currentVertex.id().toString())) {
                endVertex = currentVertex;
            }
        }

        Object startId = null;
        if (startVertex.property(SystemProperties.IL_UNIQUE_ID.name()).isPresent()) {
            startId = startVertex.property(SystemProperties.IL_UNIQUE_ID.name()).value();
        }
        Object endId = null;
        if (endVertex.property(SystemProperties.IL_UNIQUE_ID.name()).isPresent()) {
            endId = endVertex.property(SystemProperties.IL_UNIQUE_ID.name()).value();
        }

        relation.setStartNodeId(startId != null ? startId.toString() : null);
        relation.setEndNodeId(endId != null ? endId.toString() : null);

        // Populate objectType, name, and node metadata from vertices
        // These are required by NodeUtil.getRelationMap() to build the relation lookup key
        // (e.g., "associatedTo_out_AssessmentItem" -> "questions") and by
        // NodeUtil.populateRelationMaps() to populate name, description, status fields.
        relation.setStartNodeObjectType(getVertexStringProperty(startVertex, SystemProperties.IL_FUNC_OBJECT_TYPE.name()));
        relation.setEndNodeObjectType(getVertexStringProperty(endVertex, SystemProperties.IL_FUNC_OBJECT_TYPE.name()));
        relation.setStartNodeName(getVertexStringProperty(startVertex, "name"));
        relation.setEndNodeName(getVertexStringProperty(endVertex, "name"));

        Map<String, Object> startNodeMeta = new HashMap<>();
        startNodeMeta.put("description", getVertexStringProperty(startVertex, "description"));
        startNodeMeta.put("status", getVertexStringProperty(startVertex, "status"));
        relation.setStartNodeMetadata(startNodeMeta);

        Map<String, Object> endNodeMeta = new HashMap<>();
        endNodeMeta.put("description", getVertexStringProperty(endVertex, "description"));
        endNodeMeta.put("status", getVertexStringProperty(endVertex, "status"));
        relation.setEndNodeMetadata(endNodeMeta);

        // Get edge properties as metadata
        Map<String, Object> metadata = new HashMap<>();
        Iterator<java.lang.String> keyIterator = edge.keys().iterator();
        while (keyIterator.hasNext()) {
            String key = keyIterator.next();
            if (edge.property(key).isPresent()) {
                metadata.put(key, edge.value(key));
            }
        }
        relation.setMetadata(metadata);

        return relation;
    }

    /**
     * Safely read a string property from a JanusGraph vertex.
     * Returns null if the property is not present.
     */
    private static String getVertexStringProperty(JanusGraphVertex vertex, String key) {
        if (vertex != null && vertex.property(key).isPresent()) {
            Object val = vertex.property(key).value();
            return val != null ? val.toString() : null;
        }
        return null;
    }

    /**
     * Extract properties from an Edge as a Map
     * 
     * @param edge the JanusGraph edge
     * @return Map of property key-value pairs
     */
    public static Map<String, Object> getEdgeProperties(JanusGraphEdge edge) {
        Map<String, Object> properties = new HashMap<>();
        Iterator<java.lang.String> keyIterator = edge.keys().iterator();
        while (keyIterator.hasNext()) {
            String key = keyIterator.next();
            if (edge.property(key).isPresent()) {
                properties.put(key, edge.value(key));
            }
        }
        return properties;
    }

    public static Iterator<JanusGraphEdge> getEdges(JanusGraphVertex vertex, String direction, String... labels) {
        org.apache.tinkerpop.gremlin.structure.Direction dir;
        if (StringUtils.equalsIgnoreCase("IN", direction)) {
            dir = org.apache.tinkerpop.gremlin.structure.Direction.IN;
        } else if (StringUtils.equalsIgnoreCase("OUT", direction)) {
            dir = org.apache.tinkerpop.gremlin.structure.Direction.OUT;
        } else {
            dir = org.apache.tinkerpop.gremlin.structure.Direction.BOTH;
        }
        return vertex.query().direction(dir).labels(labels).edges().iterator();
    }

    /**
     * Convert a JanusGraph Edge to a Relation object
     * 
     * @param edge the JanusGraph edge
     * @return Relation object
     */
    public static Relation getRelation(JanusGraphEdge edge) {
        if (edge == null) {
            return null;
        }

        Relation relation = new Relation();
        relation.setRelationType(edge.label());

        // Get start and end node IDs
        JanusGraphVertex outVertex = edge.outVertex();
        JanusGraphVertex inVertex = edge.inVertex();

        if (outVertex != null && outVertex.property(SystemProperties.IL_UNIQUE_ID.name()).isPresent()) {
            relation.setStartNodeId((String) outVertex.property(SystemProperties.IL_UNIQUE_ID.name()).value());
        }

        if (inVertex != null && inVertex.property(SystemProperties.IL_UNIQUE_ID.name()).isPresent()) {
            relation.setEndNodeId((String) inVertex.property(SystemProperties.IL_UNIQUE_ID.name()).value());
        }

        // Set relation metadata from edge properties
        Map<String, Object> metadata = getEdgeProperties(edge);
        relation.setMetadata(metadata);

        return relation;
    }

}
