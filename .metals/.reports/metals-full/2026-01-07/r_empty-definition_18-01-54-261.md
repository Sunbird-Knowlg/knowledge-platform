error id: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/JanusGraphOperations.java:_empty_/GremlinQueryBuilder#removeEdgeProperty#
file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/JanusGraphOperations.java
empty definition using pc, found symbol in pc: _empty_/GremlinQueryBuilder#removeEdgeProperty#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 20330
uri: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/JanusGraphOperations.java
text:
```scala
package org.sunbird.graph.service.operation;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.sunbird.common.dto.Request;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.GraphDACParams;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.common.GraphOperation;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.graph.service.util.GremlinQueryBuilder;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JanusGraph operations for graph-level operations like creating, updating, and deleting relations
 * This replaces Neo4JBoltGraphOperations
 */
public class JanusGraphOperations {

	/**
	 * Creates a relation between two nodes using MERGE behavior (idempotent).
	 * Implements Neo4j MERGE with ON CREATE SET and ON MATCH SET semantics:
	 * - If edge doesn't exist: creates with metadata + sequence index (ON CREATE)
	 * - If edge exists: updates only metadata, preserves existing sequence index (ON MATCH)
	 *
	 * @param graphId the graph id
	 * @param startNodeId the start node id
	 * @param endNodeId the end node id
	 * @param relationType the relation type
	 * @param request the request containing metadata
	 */
	@SuppressWarnings("unchecked")
	public static void createRelation(String graphId, String startNodeId, String endNodeId, String relationType,
			Request request) {
		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Create Relation' Operation Failed.]");

		if (StringUtils.isBlank(startNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_START_NODE_ID + " | ['Create Relation' Operation Failed.]");

		if (StringUtils.isBlank(endNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_END_NODE_ID + " | ['Create Relation' Operation Failed.]");

		if (StringUtils.isBlank(relationType))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_RELATION_TYPE + " | ['Create Relation' Operation Failed.]");

		GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
		TelemetryManager.log("GraphTraversalSource Initialised. | [Graph Id: " + graphId + "]");
		
		try {
			Map<String, Object> metadata = (Map<String, Object>) request.get(GraphDACParams.metadata.name());
			if (metadata == null) {
				metadata = new HashMap<>();
			}
			
			// Extract sequence index if present (for SEQUENCE_MEMBERSHIP relations)
			Integer sequenceIndex = null;
			if (metadata.containsKey("IL_SEQUENCE_INDEX")) {
				try {
					Object indexObj = metadata.get("IL_SEQUENCE_INDEX");
					if (indexObj != null) {
						sequenceIndex = Integer.parseInt(indexObj.toString());
					}
				} catch (Exception e) {
					TelemetryManager.log("Could not parse sequence index: " + e.getMessage());
				}
			}
			
			// For SEQUENCE_MEMBERSHIP, auto-calculate next index if not provided
			if ("SEQUENCE_MEMBERSHIP".equalsIgnoreCase(relationType) && sequenceIndex == null) {
				sequenceIndex = calculateNextSequenceIndex(g, graphId, startNodeId, relationType);
				TelemetryManager.log("Auto-calculated sequence index: " + sequenceIndex + " for relation: " + relationType);
			}
			
			// Prepare ON CREATE metadata (includes sequence index + metadata)
			Map<String, Object> createMetadata = new HashMap<>(metadata);
			if (sequenceIndex != null) {
				createMetadata.put("IL_SEQUENCE_INDEX", sequenceIndex);
			}
			
			// ON MATCH metadata (only metadata, no sequence index override)
			Map<String, Object> matchMetadata = new HashMap<>(metadata);
			
			// Use MERGE behavior with separate ON CREATE and ON MATCH metadata
			Edge edge = GremlinQueryBuilder.mergeEdgeWithMetadata(g, graphId, startNodeId, endNodeId, 
					relationType, createMetadata, matchMetadata).next();
			
			TelemetryManager.log("'Create Relation' (MERGE) Operation Finished. Edge ID: " + edge.id());
		} catch (Exception e) {
			TelemetryManager.error("Error creating relation", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}
	
	/**
	 * Calculate next sequence index for SEQUENCE_MEMBERSHIP relations
	 * Finds max existing index and returns max + 1 (or 1 if no edges exist)
	 */
	private static Integer calculateNextSequenceIndex(GraphTraversalSource g, String graphId, 
			String startNodeId, String relationType) {
		try {
			// Get all outgoing SEQUENCE_MEMBERSHIP edges from start node
			List<Integer> existingIndices = g.V()
					.has("IL_UNIQUE_ID", startNodeId)
					.has("graphId", graphId)
					.outE(relationType)
					.has("IL_SEQUENCE_INDEX")
					.values("IL_SEQUENCE_INDEX")
					.toList()
					.stream()
					.map(obj -> {
						try {
							return Integer.parseInt(obj.toString());
						} catch (Exception e) {
							return 0;
						}
					})
					.collect(java.util.stream.Collectors.toList());
			
			if (existingIndices.isEmpty()) {
				return 1;
			}
			
			return java.util.Collections.max(existingIndices) + 1;
		} catch (Exception e) {
			TelemetryManager.log("Error calculating sequence index, using default: " + e.getMessage());
			return 1;
		}
	}

	/**
	 * Update a relation between two nodes.
	 *
	 * @param graphId the graph id
	 * @param startNodeId the start node id
	 * @param endNodeId the end node id
	 * @param relationType the relation type
	 * @param request the request containing updated metadata
	 */
	@SuppressWarnings("unchecked")
	public static void updateRelation(String graphId, String startNodeId, String endNodeId, String relationType,
			Request request) {

		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Update Relation' Operation Failed.]");

		if (StringUtils.isBlank(startNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_START_NODE_ID + " | ['Update Relation' Operation Failed.]");

		if (StringUtils.isBlank(endNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_END_NODE_ID + " | ['Update Relation' Operation Failed.]");

		if (StringUtils.isBlank(relationType))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_RELATION_TYPE + " | ['Update Relation' Operation Failed.]");

		Map<String, Object> metadata = (Map<String, Object>) request.get(GraphDACParams.metadata.name());
		if (null != metadata && !metadata.isEmpty()) {
			GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
			TelemetryManager.log("GraphTraversalSource Initialised. | [Graph Id: " + graphId + "]");
			
			try {
				Edge edge = GremlinQueryBuilder.updateEdge(g, graphId, startNodeId, endNodeId, relationType, metadata).next();
				TelemetryManager.log("'Update Relation' Operation Finished. Edge ID: " + edge.id());
			} catch (Exception e) {
				TelemetryManager.error("Error updating relation", e);
				throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
						DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
			}
		}
	}

	/**
	 * Delete a relation between two nodes.
	 *
	 * @param graphId the graph id
	 * @param startNodeId the start node id
	 * @param endNodeId the end node id
	 * @param relationType the relation type
	 * @param request the request
	 */
	public static void deleteRelation(String graphId, String startNodeId, String endNodeId, String relationType,
			Request request) {

		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Delete Relation' Operation Failed.]");

		if (StringUtils.isBlank(startNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_START_NODE_ID + " | ['Delete Relation' Operation Failed.]");

		if (StringUtils.isBlank(endNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_END_NODE_ID + " | ['Delete Relation' Operation Failed.]");

		if (StringUtils.isBlank(relationType))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_RELATION_TYPE + " | ['Delete Relation' Operation Failed.]");

		GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
		TelemetryManager.log("GraphTraversalSource Initialised. | [Graph Id: " + graphId + "]");
		
		try {
			GremlinQueryBuilder.deleteEdge(g, graphId, startNodeId, endNodeId, relationType).iterate();
			TelemetryManager.log("'Delete Relation' Operation Finished.");
		} catch (Exception e) {
			TelemetryManager.error("Error deleting relation", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}

	/**
	 * Create multiple incoming relations (many-to-one)
	 * Creates edges from multiple start nodes to a single end node
	 *
	 * @param graphId the graph id
	 * @param startNodeIds list of start node ids
	 * @param endNodeId the end node id
	 * @param relationType the relation type
	 * @param request the request containing metadata
	 */
	@SuppressWarnings("unchecked")
	public static void createIncomingRelations(String graphId, List<String> startNodeIds, String endNodeId,
			String relationType, Request request) {

		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Create Incoming Relations' Operation Failed.]");

		if (null == startNodeIds || startNodeIds.isEmpty())
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_START_NODE_ID_LIST + " | ['Create Incoming Relations' Operation Failed.]");

		if (StringUtils.isBlank(endNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_END_NODE_ID + " | ['Create Incoming Relations' Operation Failed.]");

		if (StringUtils.isBlank(relationType))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_RELATION_TYPE + " | ['Create Incoming Relations' Operation Failed.]");

		GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
		TelemetryManager.log("GraphTraversalSource Initialised. | [Graph Id: " + graphId + "]");

		try {
			Map<String, Object> metadata = new HashMap<>();
			if (null != request) {
				metadata = (Map<String, Object>) request.get(GraphDACParams.metadata.name());
				if (metadata == null) {
					metadata = new HashMap<>();
				}
			}

			List<Edge> createdEdges = GremlinQueryBuilder.createIncomingEdges(g, graphId, startNodeIds, 
					endNodeId, relationType, metadata);
			
			TelemetryManager.log("'Create Incoming Relations' Operation Finished. Created " + 
					createdEdges.size() + " edges.");
		} catch (Exception e) {
			TelemetryManager.error("Error creating incoming relations", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}

	/**
	 * Create multiple outgoing relations (one-to-many)
	 * Creates edges from a single start node to multiple end nodes
	 *
	 * @param graphId the graph id
	 * @param startNodeId the start node id
	 * @param endNodeIds list of end node ids
	 * @param relationType the relation type
	 * @param request the request containing metadata
	 */
	@SuppressWarnings("unchecked")
	public static void createOutgoingRelations(String graphId, String startNodeId, List<String> endNodeIds,
			String relationType, Request request) {

		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Create Outgoing Relations' Operation Failed.]");

		if (StringUtils.isBlank(startNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_START_NODE_ID + " | ['Create Outgoing Relations' Operation Failed.]");

		if (null == endNodeIds || endNodeIds.isEmpty())
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_END_NODE_ID_LIST + " | ['Create Outgoing Relations' Operation Failed.]");

		if (StringUtils.isBlank(relationType))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_RELATION_TYPE + " | ['Create Outgoing Relations' Operation Failed.]");

		GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
		TelemetryManager.log("GraphTraversalSource Initialised. | [Graph Id: " + graphId + "]");

		try {
			Map<String, Object> metadata = new HashMap<>();
			if (null != request) {
				metadata = (Map<String, Object>) request.get(GraphDACParams.metadata.name());
				if (metadata == null) {
					metadata = new HashMap<>();
				}
			}

			List<Edge> createdEdges = GremlinQueryBuilder.createOutgoingEdges(g, graphId, startNodeId, 
					endNodeIds, relationType, metadata);
			
			TelemetryManager.log("'Create Outgoing Relations' Operation Finished. Created " + 
					createdEdges.size() + " edges.");
		} catch (Exception e) {
			TelemetryManager.error("Error creating outgoing relations", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}

	/**
	 * Delete multiple incoming relations (many-to-one)
	 * Deletes edges from multiple start nodes to a single end node
	 *
	 * @param graphId the graph id
	 * @param startNodeIds list of start node ids
	 * @param endNodeId the end node id
	 * @param relationType the relation type
	 * @param request the request
	 */
	public static void deleteIncomingRelations(String graphId, List<String> startNodeIds, String endNodeId,
			String relationType, Request request) {

		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Delete Incoming Relations' Operation Failed.]");

		if (null == startNodeIds || startNodeIds.isEmpty())
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_START_NODE_ID_LIST + " | ['Delete Incoming Relations' Operation Failed.]");

		if (StringUtils.isBlank(endNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_END_NODE_ID + " | ['Delete Incoming Relations' Operation Failed.]");

		if (StringUtils.isBlank(relationType))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_RELATION_TYPE + " | ['Delete Incoming Relations' Operation Failed.]");

		GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
		TelemetryManager.log("GraphTraversalSource Initialised. | [Graph Id: " + graphId + "]");

		try {
			int deletedCount = GremlinQueryBuilder.deleteIncomingEdges(g, graphId, startNodeIds, 
					endNodeId, relationType);
			
			TelemetryManager.log("'Delete Incoming Relations' Operation Finished. Deleted " + 
					deletedCount + " edges.");
		} catch (Exception e) {
			TelemetryManager.error("Error deleting incoming relations", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}

	/**
	 * Delete multiple outgoing relations (one-to-many)
	 * Deletes edges from a single start node to multiple end nodes
	 *
	 * @param graphId the graph id
	 * @param startNodeId the start node id
	 * @param endNodeIds list of end node ids
	 * @param relationType the relation type
	 * @param request the request
	 */
	public static void deleteOutgoingRelations(String graphId, String startNodeId, List<String> endNodeIds,
			String relationType, Request request) {

		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Delete Outgoing Relations' Operation Failed.]");

		if (StringUtils.isBlank(startNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_START_NODE_ID + " | ['Delete Outgoing Relations' Operation Failed.]");

		if (null == endNodeIds || endNodeIds.isEmpty())
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_END_NODE_ID_LIST + " | ['Delete Outgoing Relations' Operation Failed.]");

		if (StringUtils.isBlank(relationType))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_RELATION_TYPE + " | ['Delete Outgoing Relations' Operation Failed.]");

		GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
		TelemetryManager.log("GraphTraversalSource Initialised. | [Graph Id: " + graphId + "]");

		try {
			int deletedCount = GremlinQueryBuilder.deleteOutgoingEdges(g, graphId, startNodeId, 
					endNodeIds, relationType);
			
			TelemetryManager.log("'Delete Outgoing Relations' Operation Finished. Deleted " + 
					deletedCount + " edges.");
		} catch (Exception e) {
			TelemetryManager.error("Error deleting outgoing relations", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}

	/**
	 * Remove a specific metadata property from a relation
	 *
	 * @param graphId the graph id
	 * @param startNodeId the start node id
	 * @param endNodeId the end node id
	 * @param relationType the relation type
	 * @param key the metadata key to remove
	 */
	public static void removeRelationMetadata(String graphId, String startNodeId, String endNodeId,
			String relationType, String key) {

		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Remove Relation Metadata' Operation Failed.]");

		if (StringUtils.isBlank(startNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_START_NODE_ID + " | ['Remove Relation Metadata' Operation Failed.]");

		if (StringUtils.isBlank(endNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_END_NODE_ID + " | ['Remove Relation Metadata' Operation Failed.]");

		if (StringUtils.isBlank(relationType))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_RELATION_TYPE + " | ['Remove Relation Metadata' Operation Failed.]");

		if (StringUtils.isBlank(key))
			throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name(),
					DACErrorMessageConstants.INVALID_PROPERTY_KEY + " | ['Remove Relation Metadata' Operation Failed.]");

		GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
		TelemetryManager.log("GraphTraversalSource Initialised. | [Graph Id: " + graphId + "]");

		try {
			GremlinQueryBuilder.@@removeEdgeProperty(g, graphId, startNodeId, endNodeId, relationType, key);
			TelemetryManager.log("'Remove Relation Metadata' Operation Finished.");
		} catch (Exception e) {
			TelemetryManager.error("Error removing relation metadata", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/GremlinQueryBuilder#removeEdgeProperty#