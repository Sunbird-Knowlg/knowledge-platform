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
import java.util.Map;

/**
 * JanusGraph operations for graph-level operations like creating, updating, and deleting relations
 * This replaces Neo4JBoltGraphOperations
 */
public class JanusGraphOperations {

	/**
	 * Creates a relation between two nodes.
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
			
			Edge edge = GremlinQueryBuilder.createEdge(g, graphId, startNodeId, endNodeId, relationType, metadata).next();
			TelemetryManager.log("'Create Relation' Operation Finished. Edge ID: " + edge.id());
		} catch (Exception e) {
			TelemetryManager.error("Error creating relation", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
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
}
