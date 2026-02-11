package org.sunbird.graph.service.operation;

import org.apache.commons.lang3.StringUtils;
import org.janusgraph.core.JanusGraph;
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
			JanusGraph graph = DriverUtil.getJanusGraph(graphId);
			tx = graph.newTransaction();
			TelemetryManager.log("JanusGraph Transaction Initialised. | [Graph Id: " + graphId + "]");

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
				JanusGraph graph = DriverUtil.getJanusGraph(graphId);
				tx = graph.newTransaction();
				TelemetryManager.log("JanusGraph Transaction Initialised. | [Graph Id: " + graphId + "]");

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
			JanusGraph graph = DriverUtil.getJanusGraph(graphId);
			tx = graph.newTransaction();
			TelemetryManager.log("JanusGraph Transaction Initialised. | [Graph Id: " + graphId + "]");

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

	/**
	 * Create multiple incoming relations (many-to-one)
	 * Creates edges from multiple start nodes to a single end node
	 *
	 * @param graphId      the graph id
	 * @param startNodeIds list of start node ids
	 * @param endNodeId    the end node id
	 * @param relationType the relation type
	 * @param request      the request containing metadata
	 */
	@SuppressWarnings("unchecked")
	public static void createIncomingRelations(String graphId, List<String> startNodeIds, String endNodeId,
			String relationType, Request request) {

		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Create Incoming Relations' Operation Failed.]");

		if (null == startNodeIds || startNodeIds.isEmpty())
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_START_NODE_ID_LIST
							+ " | ['Create Incoming Relations' Operation Failed.]");

		if (StringUtils.isBlank(endNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_END_NODE_ID
							+ " | ['Create Incoming Relations' Operation Failed.]");

		if (StringUtils.isBlank(relationType))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_RELATION_TYPE
							+ " | ['Create Incoming Relations' Operation Failed.]");

		JanusGraphTransaction tx = null;
		try {
			JanusGraph graph = DriverUtil.getJanusGraph(graphId);
			tx = graph.newTransaction();
			TelemetryManager.log("JanusGraph Transaction Initialised. | [Graph Id: " + graphId + "]");

			Map<String, Object> reqMetadata = (Map<String, Object>) request.get(GraphDACParams.metadata.name());
			final Map<String, Object> metadata = (reqMetadata != null) ? reqMetadata : new HashMap<>();

			// Get End Vertex
			Iterator<JanusGraphVertex> endIter = tx.query().has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
					.has("graphId", graphId).vertices().iterator();
			if (!endIter.hasNext()) {
				throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
						"End node not found: " + endNodeId);
			}
			JanusGraphVertex endV = endIter.next();

			int createdCount = 0;
			for (String startNodeId : startNodeIds) {
				Iterator<JanusGraphVertex> startIter = tx.query().has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
						.has("graphId", graphId).vertices().iterator();
				if (startIter.hasNext()) {
					JanusGraphVertex startV = startIter.next();

					// Check if edge exists
					boolean exists = false;
					JanusGraphEdge existingEdge = null;
					Iterator<JanusGraphEdge> edgeIter = JanusGraphNodeUtil.getEdges(startV, "OUT", relationType);
					while (edgeIter.hasNext()) {
						JanusGraphEdge e = edgeIter.next();
						if (e.inVertex().id().equals(endV.id())) {
							exists = true;
							existingEdge = e; // Use existing edge to update metadata if needed?
							// Original logic implies CREATING edges.
							// GremlinQueryBuilder.createIncomingEdges usually does MERGE/Upsert behavior?
							// The instructions say "create". Let's assume merge/update if exists.
							break;
						}
					}

					if (!exists) {
						JanusGraphEdge edge = (JanusGraphEdge) startV.addEdge(relationType, endV);
						for (Map.Entry<String, Object> entry : metadata.entrySet()) {
							edge.property(entry.getKey(), entry.getValue());
						}
						createdCount++;
					} else {
						// Update metadata if exists (consistent with createRelation)
						for (Map.Entry<String, Object> entry : metadata.entrySet()) {
							existingEdge.property(entry.getKey(), entry.getValue());
						}
					}
				}
			}

			tx.commit();
			TelemetryManager.log("'Create Incoming Relations' Operation Finished. Created/Updated " +
					createdCount + " edges.");
		} catch (Exception e) {
			if (null != tx)
				tx.rollback();
			TelemetryManager.error("Error creating incoming relations", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}

	/**
	 * Create multiple outgoing relations (one-to-many)
	 * Creates edges from a single start node to multiple end nodes
	 *
	 * @param graphId      the graph id
	 * @param startNodeId  the start node id
	 * @param endNodeIds   list of end node ids
	 * @param relationType the relation type
	 * @param request      the request containing metadata
	 */
	@SuppressWarnings("unchecked")
	public static void createOutgoingRelations(String graphId, String startNodeId, List<String> endNodeIds,
			String relationType, Request request) {

		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Create Outgoing Relations' Operation Failed.]");

		if (StringUtils.isBlank(startNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_START_NODE_ID
							+ " | ['Create Outgoing Relations' Operation Failed.]");

		if (null == endNodeIds || endNodeIds.isEmpty())
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_END_NODE_ID_LIST
							+ " | ['Create Outgoing Relations' Operation Failed.]");

		if (StringUtils.isBlank(relationType))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_RELATION_TYPE
							+ " | ['Create Outgoing Relations' Operation Failed.]");

		JanusGraphTransaction tx = null;
		try {
			JanusGraph graph = DriverUtil.getJanusGraph(graphId);
			tx = graph.newTransaction();
			TelemetryManager.log("JanusGraph Transaction Initialised. | [Graph Id: " + graphId + "]");

			Map<String, Object> reqMetadata = (Map<String, Object>) request.get(GraphDACParams.metadata.name());
			final Map<String, Object> metadata = (reqMetadata != null) ? reqMetadata : new HashMap<>();

			// Get Start Vertex
			Iterator<JanusGraphVertex> startIter = tx.query().has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
					.has("graphId", graphId).vertices().iterator();
			if (!startIter.hasNext()) {
				throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
						"Start node not found: " + startNodeId);
			}
			JanusGraphVertex startV = startIter.next();

			int createdCount = 0;
			for (String endNodeId : endNodeIds) {
				Iterator<JanusGraphVertex> endIter = tx.query().has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
						.has("graphId", graphId).vertices().iterator();
				if (endIter.hasNext()) {
					JanusGraphVertex endV = endIter.next();

					// Check if edge exists
					boolean exists = false;
					JanusGraphEdge existingEdge = null;
					Iterator<JanusGraphEdge> edgeIter = JanusGraphNodeUtil.getEdges(startV, "OUT", relationType);
					while (edgeIter.hasNext()) {
						JanusGraphEdge e = edgeIter.next();
						if (e.inVertex().id().equals(endV.id())) {
							exists = true;
							existingEdge = e;
							break;
						}
					}

					if (!exists) {
						JanusGraphEdge edge = (JanusGraphEdge) startV.addEdge(relationType, endV);
						for (Map.Entry<String, Object> entry : metadata.entrySet()) {
							edge.property(entry.getKey(), entry.getValue());
						}
						createdCount++;
					} else {
						for (Map.Entry<String, Object> entry : metadata.entrySet()) {
							existingEdge.property(entry.getKey(), entry.getValue());
						}
					}
				}
			}

			tx.commit();
			TelemetryManager.log("'Create Outgoing Relations' Operation Finished. Created/Updated " +
					createdCount + " edges.");
		} catch (Exception e) {
			if (null != tx)
				tx.rollback();
			TelemetryManager.error("Error creating outgoing relations", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}

	/**
	 * Delete multiple incoming relations (many-to-one)
	 * Deletes edges from multiple start nodes to a single end node
	 *
	 * @param graphId      the graph id
	 * @param startNodeIds list of start node ids
	 * @param endNodeId    the end node id
	 * @param relationType the relation type
	 * @param request      the request
	 */
	public static void deleteIncomingRelations(String graphId, List<String> startNodeIds, String endNodeId,
			String relationType, Request request) {

		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Delete Incoming Relations' Operation Failed.]");

		if (null == startNodeIds || startNodeIds.isEmpty())
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_START_NODE_ID_LIST
							+ " | ['Delete Incoming Relations' Operation Failed.]");

		if (StringUtils.isBlank(endNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_END_NODE_ID
							+ " | ['Delete Incoming Relations' Operation Failed.]");

		if (StringUtils.isBlank(relationType))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_RELATION_TYPE
							+ " | ['Delete Incoming Relations' Operation Failed.]");

		JanusGraphTransaction tx = null;
		try {
			JanusGraph graph = DriverUtil.getJanusGraph(graphId);
			tx = graph.newTransaction();
			TelemetryManager.log("JanusGraph Transaction Initialised. | [Graph Id: " + graphId + "]");

			// Get End Vertex
			Iterator<JanusGraphVertex> endIter = tx.query().has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
					.has("graphId", graphId).vertices().iterator();

			if (endIter.hasNext()) {
				JanusGraphVertex endV = endIter.next();

				// Since we want to delete INCOMING relations to endV from startNodeIds
				// We can iterate IN edges on endV and check start nodes,
				// OR iterate start nodes and check OUT edges to endV.
				// Iterating IN edges on endV might be faster if startNodeIds is large?
				// But iterating startNodes is safer if we have indexes.

				for (String startNodeId : startNodeIds) {
					Iterator<JanusGraphVertex> startIter = tx.query()
							.has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
							.has("graphId", graphId).vertices().iterator();
					if (startIter.hasNext()) {
						JanusGraphVertex startV = startIter.next();
						Iterator<JanusGraphEdge> edges = JanusGraphNodeUtil.getEdges(startV, "OUT", relationType);
						while (edges.hasNext()) {
							JanusGraphEdge e = edges.next();
							if (e.inVertex().id().equals(endV.id())) {
								e.remove();
							}
						}
					}
				}
			}

			tx.commit();
			TelemetryManager.log("'Delete Incoming Relations' Operation Finished.");
		} catch (Exception e) {
			if (null != tx)
				tx.rollback();
			TelemetryManager.error("Error deleting incoming relations", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}

	/**
	 * Delete multiple outgoing relations (one-to-many)
	 * Deletes edges from a single start node to multiple end nodes
	 *
	 * @param graphId      the graph id
	 * @param startNodeId  the start node id
	 * @param endNodeIds   list of end node ids
	 * @param relationType the relation type
	 * @param request      the request
	 */
	public static void deleteOutgoingRelations(String graphId, String startNodeId, List<String> endNodeIds,
			String relationType, Request request) {

		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Delete Outgoing Relations' Operation Failed.]");

		if (StringUtils.isBlank(startNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_START_NODE_ID
							+ " | ['Delete Outgoing Relations' Operation Failed.]");

		if (null == endNodeIds || endNodeIds.isEmpty())
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_END_NODE_ID_LIST
							+ " | ['Delete Outgoing Relations' Operation Failed.]");

		if (StringUtils.isBlank(relationType))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_RELATION_TYPE
							+ " | ['Delete Outgoing Relations' Operation Failed.]");

		JanusGraphTransaction tx = null;
		try {
			JanusGraph graph = DriverUtil.getJanusGraph(graphId);
			tx = graph.newTransaction();
			TelemetryManager.log("JanusGraph Transaction Initialised. | [Graph Id: " + graphId + "]");

			// Get Start Vertex
			Iterator<JanusGraphVertex> startIter = tx.query().has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
					.has("graphId", graphId).vertices().iterator();

			if (startIter.hasNext()) {
				JanusGraphVertex startV = startIter.next();

				for (String endNodeId : endNodeIds) {
					Iterator<JanusGraphEdge> edgeIter = JanusGraphNodeUtil.getEdges(startV, "OUT", relationType);
					while (edgeIter.hasNext()) {
						JanusGraphEdge e = edgeIter.next();
						JanusGraphVertex endV = e.inVertex();
						if (endV.property(SystemProperties.IL_UNIQUE_ID.name()).isPresent() &&
								endV.value(SystemProperties.IL_UNIQUE_ID.name()).equals(endNodeId)) {
							e.remove();
						}
					}
				}
			}

			tx.commit();
			TelemetryManager.log("'Delete Outgoing Relations' Operation Finished.");
		} catch (Exception e) {
			if (null != tx)
				tx.rollback();
			TelemetryManager.error("Error deleting outgoing relations", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}

	/**
	 * Remove a specific metadata property from a relation
	 *
	 * @param graphId      the graph id
	 * @param startNodeId  the start node id
	 * @param endNodeId    the end node id
	 * @param relationType the relation type
	 * @param key          the metadata key to remove
	 */
	public static void removeRelationMetadata(String graphId, String startNodeId, String endNodeId,
			String relationType, String key) {

		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Remove Relation Metadata' Operation Failed.]");

		if (StringUtils.isBlank(startNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_START_NODE_ID
							+ " | ['Remove Relation Metadata' Operation Failed.]");

		if (StringUtils.isBlank(endNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_END_NODE_ID + " | ['Remove Relation Metadata' Operation Failed.]");

		if (StringUtils.isBlank(relationType))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_RELATION_TYPE
							+ " | ['Remove Relation Metadata' Operation Failed.]");

		if (StringUtils.isBlank(key))
			throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name(),
					DACErrorMessageConstants.INVALID_PROPERTY_KEY
							+ " | ['Remove Relation Metadata' Operation Failed.]");

		JanusGraphTransaction tx = null;
		try {
			JanusGraph graph = DriverUtil.getJanusGraph(graphId);
			tx = graph.newTransaction();
			TelemetryManager.log("JanusGraph Transaction Initialised. | [Graph Id: " + graphId + "]");

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

						if (e.property(key).isPresent()) {
							e.property(key).remove();
						}
						break;
					}
				}
			}

			tx.commit();
			TelemetryManager.log("'Remove Relation Metadata' Operation Finished.");
		} catch (Exception e) {
			if (null != tx)
				tx.rollback();
			TelemetryManager.error("Error removing relation metadata", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}
}
