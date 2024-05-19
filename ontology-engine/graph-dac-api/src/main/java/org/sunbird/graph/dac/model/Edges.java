package org.sunbird.graph.dac.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.neo4j.graphdb.Node;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.enums.GraphDACErrorCodes;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Edges implements Serializable {

    private static final long serialVersionUID = -7207054262120122453L;
    private String id;
    private String graphId;
    private String relationType;
    private String startNodeId;
    private String endNodeId;
    private String startNodeName;
    private String endNodeName;
    private String startNodeType;
    private String endNodeType;
    private String startNodeObjectType;
    private String endNodeObjectType;
    private Map<String, Object> metadata;
    private Map<String, Object> startNodeMetadata;
    private Map<String, Object> endNodeMetadata;

    public Edges() {

    }

    public Edges(String startNodeId, String relationType, String endNodeId) {
        this.startNodeId = startNodeId;
        this.endNodeId = endNodeId;
        this.relationType = relationType;
    }

    public Edges(String graphId, Edge edge) {
        if (null == edge)
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_REL.name(),
                    "Failed to create relation object. Relation from database is null.");
        this.graphId = graphId;

        Vertex startNode = edge.outVertex();
        Vertex endNode = edge.inVertex();
        this.startNodeId = startNode.property(SystemProperties.IL_UNIQUE_ID.name()).value().toString();
        this.endNodeId = endNode.property(SystemProperties.IL_UNIQUE_ID.name()).value().toString();
        this.startNodeName = getName(startNode);
        this.endNodeName = getName(endNode);
        this.startNodeType = getNodeType(startNode);
        this.endNodeType = getNodeType(endNode);
        this.startNodeObjectType = getObjectType(startNode);
        this.endNodeObjectType = getObjectType(endNode);
        this.relationType = edge.label();
        this.metadata = new HashMap<String, Object>();
        this.startNodeMetadata = getNodeMetadata(edge.outVertex());
        this.endNodeMetadata = getNodeMetadata(edge.inVertex());
        edge.keys().forEach(key -> this.metadata.put(key, edge.value(key)));
    }

    public Edges(String graphId, Edge edge, Map<Object, Vertex> startNodeMap, Map<Object, Vertex> endNodeMap) {
        if (null == edge)
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_REL.name(),
                    "Failed to create relation object. Relation from database is null.");
        this.id = edge.id().toString();
        this.graphId = graphId;
        Vertex startNode = startNodeMap.get(edge.outVertex().id());
        Vertex endNode = endNodeMap.get(edge.inVertex().id());
        this.startNodeId = startNode.property(SystemProperties.IL_UNIQUE_ID.name()).value().toString();
        this.endNodeId = endNode.property(SystemProperties.IL_UNIQUE_ID.name()).value().toString();
        this.startNodeName = getName(startNode);
        this.endNodeName = getName(endNode);
        this.startNodeType = getNodeType(startNode);
        this.endNodeType = getNodeType(endNode);
        this.startNodeObjectType = getObjectType(startNode);
        this.endNodeObjectType = getObjectType(endNode);
        this.relationType = edge.label();
        this.metadata = new HashMap<String, Object>();
        this.startNodeMetadata = getNodeMetadata(startNode);
        this.endNodeMetadata = getNodeMetadata(endNode);
        edge.keys().forEach(key -> {
            Object value = edge.value(key);
            if(null != value){
                if (value instanceof List) {
                    List<Object> list = (List<Object>) value;
                    if (!list.isEmpty()) {
                        Object obj = list.get(0);
                        if (obj instanceof String) {
                            this.metadata.put(key, list.toArray(new String[0]));
                        } else if (obj instanceof Number) {
                            this.metadata.put(key, list.toArray(new Number[0]));
                        } else if (obj instanceof Boolean) {
                            this.metadata.put(key, list.toArray(new Boolean[0]));
                        } else {
                            this.metadata.put(key, list.toArray(new Object[0]));
                        }
                    }
                } else
                    this.metadata.put(key, value);
            }
        });
    }


    private String getName(Node node) {
        String name = (String) node.getProperty("name", null);
        if (StringUtils.isBlank(name)) {
            name = (String) node.getProperty("title", null);
            if (StringUtils.isBlank(name)) {
                name = (String) node.getProperty(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), null);
                if (StringUtils.isBlank(name))
                    name = (String) node.getProperty(SystemProperties.IL_SYS_NODE_TYPE.name(), null);
            }
        }
        return name;
    }

    private String getName(Vertex node) {
        String name = node.property("name").isPresent() ? node.property("name").value().toString() : null;
        if (StringUtils.isBlank(name)) {
            name = node.property("title").isPresent() ? node.property("title").value().toString() : null;
            if (StringUtils.isBlank(name)) {
                name = node.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).isPresent() ? node.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).value().toString() : null;
                if (StringUtils.isBlank(name))
                    name = node.property(SystemProperties.IL_SYS_NODE_TYPE.name()).isPresent() ? node.property(SystemProperties.IL_SYS_NODE_TYPE.name()).value().toString() : null;
            }
        }
        return name;
    }


    private String getNodeType(Vertex node) {
        return node.property(SystemProperties.IL_SYS_NODE_TYPE.name()).isPresent() ? node.property(SystemProperties.IL_SYS_NODE_TYPE.name()).value().toString() : null;
    }

    private String getObjectType(Vertex node) {
        return node.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).isPresent() ? node.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).value().toString() : null;
    }

    private Map<String, Object> getNodeMetadata(Vertex vertex) {
        Map<String, Object> metadata = new HashMap<>();
        if (vertex != null) {
            vertex.keys().forEach(key -> {
                 Object value = vertex.value(key);
                    if (value instanceof List) {
                        List<?> list = (List<?>) value;
                        if (!list.isEmpty()) {
                            Object firstElement = list.get(0);
                            if (firstElement instanceof String) {
                                metadata.put(key, list.toArray(new String[0]));
                            } else if (firstElement instanceof Number) {
                                metadata.put(key, list.toArray(new Number[0]));
                            } else if (firstElement instanceof Boolean) {
                                metadata.put(key, list.toArray(new Boolean[0]));
                            } else {
                                metadata.put(key, list.toArray(new Object[0]));
                            }
                        }
                    } else {
                        metadata.put(key, value);
                    }
            });
        }
        return metadata;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public String getStartNodeId() {
        return startNodeId;
    }

    public void setStartNodeId(String startNodeId) {
        this.startNodeId = startNodeId;
    }

    public String getEndNodeId() {
        return endNodeId;
    }

    public void setEndNodeId(String endNodeId) {
        this.endNodeId = endNodeId;
    }

    public Map<String, Object> getMetadata() {
        if (!MapUtils.isEmpty(metadata))
            return metadata;
        else
            return new HashMap<String, Object>();
    }

    public Edges updateMetadata(Map<String, Object> metadata) {
        if (!MapUtils.isEmpty(metadata))
            this.metadata = metadata;
        return this;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getGraphId() {
        return graphId;
    }

    public void setGraphId(String graphId) {
        this.graphId = graphId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStartNodeName() {
        return startNodeName;
    }

    public void setStartNodeName(String startNodeName) {
        this.startNodeName = startNodeName;
    }

    public String getEndNodeName() {
        return endNodeName;
    }

    public void setEndNodeName(String endNodeName) {
        this.endNodeName = endNodeName;
    }

    public String getStartNodeType() {
        return startNodeType;
    }

    public void setStartNodeType(String startNodeType) {
        this.startNodeType = startNodeType;
    }

    public String getEndNodeType() {
        return endNodeType;
    }

    public void setEndNodeType(String endNodeType) {
        this.endNodeType = endNodeType;
    }

    public String getStartNodeObjectType() {
        return startNodeObjectType;
    }

    public void setStartNodeObjectType(String startNodeObjectType) {
        this.startNodeObjectType = startNodeObjectType;
    }

    public String getEndNodeObjectType() {
        return endNodeObjectType;
    }

    public void setEndNodeObjectType(String endNodeObjectType) {
        this.endNodeObjectType = endNodeObjectType;
    }

    @JsonIgnore
    public Map<String, Object> getStartNodeMetadata() {
        return startNodeMetadata;
    }

    @JsonIgnore
    public void setStartNodeMetadata(Map<String, Object> startNodeMetadata) {
        this.startNodeMetadata = startNodeMetadata;
    }

    @JsonIgnore
    public Map<String, Object> getEndNodeMetadata() {
        return endNodeMetadata;
    }

    @JsonIgnore
    public void setEndNodeMetadata(Map<String, Object> endNodeMetadata) {
        this.endNodeMetadata = endNodeMetadata;
    }

}
