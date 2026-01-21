package org.sunbird.graph.dac.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.collections4.MapUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Relation model representing edges/relationships between graph nodes
 * Simplified version compatible with JanusGraph/Gremlin
 */
public class Relation implements Serializable {

	private static final long serialVersionUID = -7207054262120122453L;
	private long id;
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

	public Relation() {

	}

	public Relation(String startNodeId, String relationType, String endNodeId) {
		this.startNodeId = startNodeId;
		this.endNodeId = endNodeId;
		this.relationType = relationType;
	}

	// TODO: Add JanusGraph Edge-based constructor when needed
	// public Relation(String graphId, Edge edge, Vertex startVertex, Vertex endVertex) {
	//     // Implementation using JanusGraph/Gremlin API
	// }

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

	public Relation updateMetadata(Map<String, Object> metadata) {
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

	public long getId() {
		return id;
	}

	public void setId(long id) {
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
