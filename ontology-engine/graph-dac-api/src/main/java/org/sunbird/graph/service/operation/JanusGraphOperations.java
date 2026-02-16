package org.sunbird.graph.service.operation;

import org.apache.commons.lang3.StringUtils;

import org.janusgraph.core.JanusGraphEdge;
import org.janusgraph.core.JanusGraphTransaction;
import org.janusgraph.core.JanusGraphVertex;
import org.sunbird.graph.dac.util.JanusGraphNodeUtil;
import org.sunbird.common.dto.Request;
import org.sunbird.common.exception.ClientException;

import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.GraphDACParams;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * JanusGraph operations for graph-level operations like creating, updating, and
 * deleting relations
 * This replaces Neo4JBoltGraphOperations
 */
public class JanusGraphOperations {

	/**
	 * Creates a relation between two nodes using MERGE behavior (idempotent).
	 * Implements Neo4j MERGE with ON CREATE SET and ON MATCH SET semantics:
	 * - If edge doesn't exist: creates with metadata + sequence index (ON CREATE)
	 * - If edge exists: updates only metadata, preserves existing sequence index
	 * (ON MATCH)
	 *
	 * @param graphId      the graph id
	 * @param startNodeId  the start node id
	 * @param endNodeId    the end node id
	 * @param relationType the relation type
	 * @param request      the request containing metadata
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

		JanusGraphTransaction tx = null;
		try {
			tx = DriverUtil.beginTransaction(graphId);

			Map<String, Object> metadata = (Map<String, Object>) request.get(GraphDACParams.metadata.name());
			if (metadata == null) {
				metadata = new HashMap<>();
			}

			// Extract sequence index
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

			// Get Start Vertex
			Iterator<JanusGraphVertex> startIter = tx.query().has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
					.has("graphId", graphId).vertices().iterator();
			if (!startIter.hasNext()) {
				throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
						"Start node not found: " + startNodeId);
			}
			JanusGraphVertex startV = startIter.next();

			// Get End Vertex
			Iterator<JanusGraphVertex> endIter = tx.query().has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
					.has("graphId", graphId).vertices().iterator();
			if (!endIter.hasNext()) {
				throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
						"End node not found: " + endNodeId);
			}
			JanusGraphVertex endV = endIter.next();

			// Auto-calculate sequence index if needed
			if ("SEQUENCE_MEMBERSHIP".equalsIgnoreCase(relationType) && sequenceIndex == null) {
				sequenceIndex = calculateNextSequenceIndex(startV, relationType);
				TelemetryManager
						.log("Auto-calculated sequence index: " + sequenceIndex + " for relation: " + relationType);
			}

			if (sequenceIndex != null) {
				metadata.put("IL_SEQUENCE_INDEX", sequenceIndex);
			}

			// Check if edge exists
			JanusGraphEdge edge = null;
			Iterator<JanusGraphEdge> edgeIter = JanusGraphNodeUtil.getEdges(startV, "OUT", relationType);
			while (edgeIter.hasNext()) {
				JanusGraphEdge e = edgeIter.next();
				if (e.inVertex().id().equals(endV.id())) {
					edge = e;
					break;
				}
			}

			if (edge != null) {
				// Update existing
				for (Map.Entry<String, Object> entry : metadata.entrySet()) {
					edge.property(entry.getKey(), entry.getValue());
				}
			} else {
				// Create new
				edge = (JanusGraphEdge) startV.addEdge(relationType, endV);
				for (Map.Entry<String, Object> entry : metadata.entrySet()) {
					edge.property(entry.getKey(), entry.getValue());
				}
			}

			tx.commit();
			TelemetryManager.log("'Create Relation' (MERGE) Operation Finished. Edge ID: " + edge.id());
		} catch (Exception e) {
			if (null != tx)
				tx.rollback();
			TelemetryManager.error("Error creating relation", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}

	/**
	 * Calculate next sequence index for SEQUENCE_MEMBERSHIP relations
	 * Finds max existing index and returns max + 1 (or 1 if no edges exist)
	 */
	private static Integer calculateNextSequenceIndex(JanusGraphVertex startV, String relationType) {
		try {
			List<Integer> existingIndices = new ArrayList<>();
			Iterator<JanusGraphEdge> edges = JanusGraphNodeUtil.getEdges(startV, "OUT", relationType);
			while (edges.hasNext()) {
				JanusGraphEdge e = edges.next();
				if (e.property("IL_SEQUENCE_INDEX").isPresent()) {
					try {
						existingIndices.add(Integer.parseInt(e.value("IL_SEQUENCE_INDEX").toString()));
					} catch (Exception ex) {
					} // Ignore parse errors
				}
			}

			if (existingIndices.isEmpty()) {
				return 1;
			}

			return Collections.max(existingIndices) + 1;
		} catch (Exception e) {
			TelemetryManager.log("Error calculating sequence index, using default: " + e.getMessage());
			return 1;
		}
	}

	/**
	 * Update a relation between two nodes.
	 *
	 * @param graphId      the graph id
	 * @param startNodeId  the start node id
	 * @param endNodeId    the end node id
	 * @param relationType the relation type
	 * @param request      the request containing updated metadata
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
			JanusGraphTransaction tx = null;
			try {
				tx = DriverUtil.beginTransaction(graphId);

				// Get Start Vertex
				Iterator<JanusGraphVertex> startIter = tx.query().has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
						.has("graphId", graphId).vertices().iterator();
				if (!startIter.hasNext()) {
					// Need to throw exception or return?
					// Old code didn't check existence explicitly before traversal?
					// But we need start vertex to find edge.
					throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
							"Start node not found");
				}
				JanusGraphVertex startV = startIter.next();

				// Find edge
				JanusGraphEdge edge = null;
				Iterator<JanusGraphEdge> edgeIter = JanusGraphNodeUtil.getEdges(startV, "OUT", relationType);

				// We need end vertex ID check.
				// Since we don't have end vertex object, we can avoid fetching it if we check
				// ID on edge end vertex.

				while (edgeIter.hasNext()) {
					JanusGraphEdge e = edgeIter.next();
					JanusGraphVertex endV = e.inVertex();
					if (endV.property(SystemProperties.IL_UNIQUE_ID.name()).isPresent() &&
							endV.value(SystemProperties.IL_UNIQUE_ID.name()).equals(endNodeId)) {
						edge = e;
						break;
					}
				}

				if (edge != null) {
					for (Map.Entry<String, Object> entry : metadata.entrySet()) {
						edge.property(entry.getKey(), entry.getValue());
					}
					TelemetryManager.log("'Update Relation' Operation Finished. Edge ID: " + edge.id());
				} else {
					throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(), "Relation not found");
				}

				tx.commit();
			} catch (Exception e) {
				if (tx != null)
					tx.rollback();
				TelemetryManager.error("Error updating relation", e);
				throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
						DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
			}
		}
	}

	/**
	 * Delete a relation between two nodes.
	 *
	 * @param graphId      the graph id
	 * @param startNodeId  the start node id
	 * @param endNodeId    the end node id
	 * @param relationType the relation type
	 * @param request      the request
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

		JanusGraphTransaction tx = null;
		try {
			tx = DriverUtil.beginTransaction(graphId);

			// Get Start Vertex
			Iterator<JanusGraphVertex> startIter = tx.query().has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
					.has("graphId", graphId).vertices().iterator();

			if (startIter.hasNext()) {
				JanusGraphVertex startV = startIter.next();
				Iterator<JanusGraphEdge> edgeIter = JanusGraphNodeUtil.getEdges(startV, "OUT", relationType);

				while (edgeIter.hasNext()) {
					JanusGraphEdge e = edgeIter.next();
					JanusGraphVertex endV = e.inVertex();
					if (endV.property(SystemProperties.IL_UNIQUE_ID.name()).isPresent() &&
							endV.value(SystemProperties.IL_UNIQUE_ID.name()).equals(endNodeId)) {
						e.remove();
						// Assuming only one edge of this type between these two nodes?
						// Usually yes for this domain.
					}
				}
			}

			tx.commit();
			TelemetryManager.log("'Delete Relation' Operation Finished.");
		} catch (Exception e) {
			if (null != tx)
				tx.rollback();
			TelemetryManager.error("Error deleting relation", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}


}
