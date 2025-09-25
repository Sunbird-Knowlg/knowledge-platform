package org.sunbird.graph.dac.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.graph.common.enums.SystemProperties;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Vertex implements Serializable {

    private static final long serialVersionUID = 252337826576516976L;

    private Object id;
    private String graphId;
    private String identifier;
    private String vertexType;
    private String objectType;
    private Map<String, Object> metadata;
    private List<Edges> outEdges;
    private List<Edges> inEdges;
    private List<Edges> addedEdges;
    private List<Edges> deletedEdges;
    private Map<String, Vertex> edgeVertices;
    private Map<String, Object> externalData;

    public Vertex() {
        addedEdges = new ArrayList<>();
        deletedEdges = new ArrayList<>();
    }

    public Vertex(String identifier, String vertexType, String objectType) {
        this.identifier = identifier;
        this.vertexType = vertexType;
        this.objectType = objectType;
        addedEdges = new ArrayList<>();
        deletedEdges = new ArrayList<>();
    }

    public Vertex(String graphId, Map<String, Object> metadata) {
        this.graphId = graphId;
        this.metadata = metadata;
        if (null != metadata && !metadata.isEmpty()) {
            if (null != metadata.get(SystemProperties.IL_UNIQUE_ID.name()))
                this.identifier = metadata.get(SystemProperties.IL_UNIQUE_ID.name()).toString();
            if (null != metadata.get(SystemProperties.IL_SYS_NODE_TYPE.name()))
                this.vertexType = metadata.get(SystemProperties.IL_SYS_NODE_TYPE.name()).toString();
            if (null != metadata.get(SystemProperties.IL_FUNC_OBJECT_TYPE.name()))
                this.objectType = metadata.get(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).toString();
        }
        addedEdges = new ArrayList<>();
        deletedEdges = new ArrayList<>();
    }


    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    @JsonIgnore
    public String getGraphId() {
        return graphId;
    }

    public void setGraphId(String graphId) {
        this.graphId = graphId;
    }

    public String getIdentifier() {
        if (StringUtils.isBlank(identifier) && null != metadata)
            this.identifier = (String) metadata.get(SystemProperties.IL_UNIQUE_ID.name());
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getVertexType() {
        if (StringUtils.isBlank(vertexType) && null != metadata)
            this.vertexType = (String) metadata.get(SystemProperties.IL_SYS_NODE_TYPE.name());
        return vertexType;
    }

    public void setVertexType(String vertexType) {
        this.vertexType = vertexType;
    }

    public String getObjectType() {
        if (StringUtils.isBlank(objectType) && null != metadata)
            this.objectType = (String) metadata.get(SystemProperties.IL_FUNC_OBJECT_TYPE.name());
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public List<Edges> getOutEdges() {
        if (!CollectionUtils.isEmpty(outEdges))
            return outEdges;
        else return new ArrayList<>();
    }

    public void setOutEdges(List<Edges> outEdges) {
        this.outEdges = outEdges;
    }

    public List<Edges> getInEdges() {
        if (!CollectionUtils.isEmpty(inEdges))
            return inEdges;
        else return new ArrayList<>();
    }

    public void setInEdges(List<Edges> inEdges) {
        this.inEdges = inEdges;
    }

    public List<Edges> getAddedEdges() {
        return addedEdges;
    }

    public void setAddedEdges(List<Edges> addedEdges) {
        if(CollectionUtils.isEmpty(this.addedEdges))
            this.addedEdges = new ArrayList<>();
        this.addedEdges.addAll(addedEdges);
    }

    public List<Edges> getDeletedEdges() {
        return deletedEdges;
    }

    public void setDeletedEdges(List<Edges> deletedEdges) {
        this.deletedEdges = deletedEdges;
    }

    public Map<String, Object> getExternalData() {
        return externalData;
    }

    public Map<String, Vertex> getEdgeVertices() {
        return edgeVertices;
    }

    public void setEdgeVertices(Map<String, Vertex> edgeVertices) {
        this.edgeVertices = edgeVertices;
    }

    public void setExternalData(Map<String, Object> externalData) {
        this.externalData = externalData;
    }

    public Vertex getVertex() {
        return (Vertex) this;
    }

    public Vertex getRelationNode(String identifier) {
        return edgeVertices.get(identifier);
    }

    public String getArtifactUrl() {
        return (String) this.metadata.getOrDefault("artifactUrl", "");
    }

}
