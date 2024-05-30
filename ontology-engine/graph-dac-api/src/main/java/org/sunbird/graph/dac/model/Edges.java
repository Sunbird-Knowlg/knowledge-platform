package org.sunbird.graph.dac.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.enums.GraphDACErrorCodes;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Edges implements Serializable {

    private static final long serialVersionUID = -7207054262120122453L;
    private Object id;
    private String graphId;
    private String edgeType;
    private String startVertexId;
    private String endVertexId;
    private String startVertexName;
    private String endVertexName;
    private String startVertexType;
    private String endVertexType;
    private String startVertexObjectType;
    private String endVertexObjectType;
    private Map<String, Object> metadata;
    private Map<String, Object> startVertexMetadata;
    private Map<String, Object> endVertexMetadata;

    public Edges() {

    }

    public Edges(String startVertexId, String edgeType, String endVertexId) {
        this.startVertexId = startVertexId;
        this.endVertexId = endVertexId;
        this.edgeType = edgeType;
    }

    public Edges(String graphId, Edge edge) {
        if (null == edge)
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_REL.name(),
                    "Failed to create relation object. Relation from database is null.");
        this.graphId = graphId;

        Vertex startVertex = edge.inVertex();
        Vertex endVertex = edge.outVertex();
        this.startVertexId = (String) startVertex.property(SystemProperties.IL_UNIQUE_ID.name()).value();
        this.endVertexId = (String) endVertex.property(SystemProperties.IL_UNIQUE_ID.name()).value();
        this.startVertexName = getName(startVertex);
        this.endVertexName = getName(endVertex);
        this.startVertexType = getVertexType(startVertex);
        this.endVertexType = getVertexType(endVertex);
        this.startVertexObjectType = getObjectType(startVertex);
        this.endVertexObjectType = getObjectType(endVertex);
        this.edgeType = edge.label();
        this.metadata = new HashMap<String, Object>();
        this.startVertexMetadata = getNodeMetadata(edge.outVertex());
        this.endVertexMetadata = getNodeMetadata(edge.inVertex());
        edge.keys().forEach(key -> this.metadata.put(key, edge.value(key)));
    }

    public Edges(String graphId, Edge edge, Map<Object, Object> startNodeMap, Map<Object, Object> endNodeMap) {
        if (null == edge)
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_REL.name(),
                    "Failed to create relation object. Relation from database is null.");
        this.id = edge.id();
        this.graphId = graphId;

        Vertex startNode = (Vertex) startNodeMap.get(edge.outVertex().id());
        Vertex endNode = (Vertex) endNodeMap.get(edge.inVertex().id());

        this.startVertexId = startNode.property(SystemProperties.IL_UNIQUE_ID.name()).value().toString();
        this.endVertexId = endNode.property(SystemProperties.IL_UNIQUE_ID.name()).value().toString();
        this.startVertexName = getName(startNode);
        this.endVertexName = getName(endNode);
        this.startVertexType = getVertexType(startNode);
        this.endVertexType = getVertexType(endNode);
        this.startVertexObjectType = getObjectType(startNode);
        this.endVertexObjectType = getObjectType(endNode);
        this.edgeType = edge.label();
        this.metadata = new HashMap<String, Object>();
        this.startVertexMetadata = getNodeMetadata(startNode);
        this.endVertexMetadata = getNodeMetadata(endNode);
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


    private String getName(Vertex vertex) {
        String name = vertex.property("name").isPresent() ? vertex.property("name").value().toString() : null;
        if (StringUtils.isBlank(name)) {
            name = vertex.property("title").isPresent() ? vertex.property("title").value().toString() : null;
            if (StringUtils.isBlank(name)) {
                name = vertex.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).isPresent() ? vertex.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).value().toString() : null;
                if (StringUtils.isBlank(name))
                    name = vertex.property(SystemProperties.IL_SYS_NODE_TYPE.name()).isPresent() ? vertex.property(SystemProperties.IL_SYS_NODE_TYPE.name()).value().toString() : null;
            }
        }
        return name;
    }


    private String getVertexType(Vertex vertex) {
        return vertex.property(SystemProperties.IL_SYS_NODE_TYPE.name()).isPresent() ? vertex.property(SystemProperties.IL_SYS_NODE_TYPE.name()).value().toString() : null;
    }

    private String getObjectType(Vertex vertex) {
        return vertex.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).isPresent() ? vertex.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).value().toString() : null;
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

    public String getEdgeType() {
        return edgeType;
    }

    public void setEdgeType(String edgeType) {
        this.edgeType = edgeType;
    }

    public String getStartVertexId() {
        return startVertexId;
    }

    public void setStartVertexId(String startVertexId) {
        this.startVertexId = startVertexId;
    }

    public String getEndVertexId() {
        return endVertexId;
    }

    public void setEndVertexId(String endVertexId) {
        this.endVertexId = endVertexId;
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

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public String getStartVertexName() {
        return startVertexName;
    }

    public void setStartVertexName(String startVertexName) {
        this.startVertexName = startVertexName;
    }

    public String getEndVertexName() {
        return endVertexName;
    }

    public void setEndVertexName(String endVertexName) {
        this.endVertexName = endVertexName;
    }

    public String getStartVertexType() {
        return startVertexType;
    }

    public void setStartVertexType(String startVertexType) {
        this.startVertexType = startVertexType;
    }

    public String getEndVertexType() {
        return endVertexType;
    }

    public void setEndVertexType(String endVertexType) {
        this.endVertexType = endVertexType;
    }

    public String getStartVertexObjectType() {
        return startVertexObjectType;
    }

    public void setStartVertexObjectType(String startVertexObjectType) {
        this.startVertexObjectType = startVertexObjectType;
    }

    public String getEndVertexObjectType() {
        return endVertexObjectType;
    }

    public void setEndVertexObjectType(String endVertexObjectType) {
        this.endVertexObjectType = endVertexObjectType;
    }

    @JsonIgnore
    public Map<String, Object> getStartVertexMetadata() {
        return startVertexMetadata;
    }

    @JsonIgnore
    public void setStartVertexMetadata(Map<String, Object> startVertexMetadata) {
        this.startVertexMetadata = startVertexMetadata;
    }

    @JsonIgnore
    public Map<String, Object> getEndVertexMetadata() {
        return endVertexMetadata;
    }

    @JsonIgnore
    public void setEndVertexMetadata(Map<String, Object> endVertexMetadata) {
        this.endVertexMetadata = endVertexMetadata;
    }

}
