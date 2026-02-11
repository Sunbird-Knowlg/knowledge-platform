package org.sunbird.graph.service.operation;

import org.apache.commons.lang3.StringUtils;

import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphEdge;
import org.janusgraph.core.JanusGraphTransaction;
import org.janusgraph.core.JanusGraphVertex;
import org.sunbird.common.DateUtils;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.Identifier;
import org.sunbird.graph.common.enums.AuditProperties;
import org.sunbird.graph.common.enums.GraphDACParams;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.enums.SystemNodeTypes;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.dac.util.JanusGraphNodeUtil;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * JanusGraph operations for collection management
 * Handles creating and deleting collection nodes with member relationships
 */
public class JanusGraphCollectionOperations {

	/**
	 * Create a collection node and link all members with sequence indices
	 * Implements Neo4j MERGE behavior for collection node + batch member linking
	 * 
	 * @param graphId       the graph id
	 * @param collectionId  the collection node identifier
	 * @param collection    the collection node with metadata
	 * @param members       list of member node identifiers
	 * @param relationType  the relation type to link members
	 * @param indexProperty the property name for sequence index (e.g.,
	 *                      "IL_SEQUENCE_INDEX")
	 * @return the created/updated collection Node
	 */
	public static Node createCollection(String graphId, String collectionId, Node collection,
			List<String> members, String relationType, String indexProperty) {

		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Create Collection' Operation Failed.]");

		if (StringUtils.isBlank(collectionId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_COLLECTION_NODE_ID + " | ['Create Collection' Operation Failed.]");

		if (null == collection)
			throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name(),
					DACErrorMessageConstants.INVALID_COLLECTION_NODE + " | ['Create Collection' Operation Failed.]");

		if (StringUtils.isBlank(relationType))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_RELATION_TYPE + " | ['Create Collection' Operation Failed.]");

		if (null == members || members.isEmpty())
			throw new ClientException(DACErrorCodeConstants.INVALID_MEMBERS.name(),
					DACErrorMessageConstants.INVALID_COLLECTION_MEMBERS + " | ['Create Collection' Operation Failed.]");

		if (StringUtils.isBlank(indexProperty))
			throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name(),
					DACErrorMessageConstants.INVALID_INDEX_PROPERTY + " | ['Create Collection' Operation Failed.]");

		JanusGraphTransaction tx = null;
		try {
			JanusGraph graph = DriverUtil.getJanusGraph(graphId);
			tx = graph.newTransaction();
			TelemetryManager.log("JanusGraph Transaction Initialised. | [Graph Id: " + graphId + "]");

			String timestamp = DateUtils.formatCurrentDate();

			// Set collection identifier if not already set
			if (StringUtils.isBlank(collection.getIdentifier())) {
				collection.setIdentifier(collectionId);
			}

			// Check if collection node already exists
			JanusGraphVertex collectionVertex = null;
			Iterator<JanusGraphVertex> colIter = tx.query().has(SystemProperties.IL_UNIQUE_ID.name(), collectionId)
					.has("graphId", graphId).vertices().iterator();
			if (colIter.hasNext()) {
				collectionVertex = colIter.next();
			}

			if (collectionVertex == null) {
				// ON CREATE - Create new collection node
				TelemetryManager.log("Creating new collection node: " + collectionId);

				// Generate version key
				String versionKey = Identifier.getIdentifier(graphId, Identifier.getUniqueIdFromTimestamp());

				collectionVertex = tx.addVertex();
				collectionVertex.property(SystemProperties.IL_UNIQUE_ID.name(), collectionId);
				collectionVertex.property("graphId", graphId);
				collectionVertex.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), collection.getObjectType());
				collectionVertex.property(SystemProperties.IL_SYS_NODE_TYPE.name(),
						StringUtils.isNotBlank(collection.getNodeType()) ? collection.getNodeType()
								: SystemNodeTypes.DATA_NODE.name());
				collectionVertex.property(GraphDACParams.versionKey.name(), versionKey);
				collectionVertex.property(AuditProperties.createdOn.name(), timestamp);
				collectionVertex.property(AuditProperties.lastUpdatedOn.name(), timestamp);

				// Add metadata properties
				if (collection.getMetadata() != null) {
					for (Map.Entry<String, Object> entry : collection.getMetadata().entrySet()) {
						if (entry.getValue() != null && !entry.getKey().equals(GraphDACParams.versionKey.name())) {
							collectionVertex.property(entry.getKey(), entry.getValue());
						}
					}
				}

				if (collection.getMetadata() == null) {
					collection.setMetadata(new HashMap<>());
				}
				collection.getMetadata().put(GraphDACParams.versionKey.name(), versionKey);

			} else {
				// ON MATCH - Update existing collection node
				TelemetryManager.log("Updating existing collection node: " + collectionId);
				collectionVertex.property(AuditProperties.lastUpdatedOn.name(), timestamp);

				// Update metadata properties
				if (collection.getMetadata() != null) {
					for (Map.Entry<String, Object> entry : collection.getMetadata().entrySet()) {
						if (entry.getValue() != null && !entry.getKey().equals(GraphDACParams.versionKey.name())) {
							collectionVertex.property(entry.getKey(), entry.getValue());
						}
					}
				}
			}

			// Link all members with sequence indices
			TelemetryManager.log("Linking " + members.size() + " members to collection: " + collectionId);

			int sequenceIndex = 1;
			for (String memberId : members) {
				// Create metadata with sequence index
				Map<String, Object> metadata = new HashMap<>();
				metadata.put(indexProperty, sequenceIndex);

				// Get Member Vertex
				JanusGraphVertex memberVertex = null;
				Iterator<JanusGraphVertex> memIter = tx.query().has(SystemProperties.IL_UNIQUE_ID.name(), memberId)
						.has("graphId", graphId).vertices().iterator();
				if (memIter.hasNext()) {
					memberVertex = memIter.next();
				}

				if (memberVertex != null) {
					// Check if edge already exists
					JanusGraphEdge existingEdge = null;
					Iterator<JanusGraphEdge> edgeIter = JanusGraphNodeUtil.getEdges(collectionVertex, "OUT",
							relationType);
					while (edgeIter.hasNext()) {
						JanusGraphEdge e = edgeIter.next();
						if (e.inVertex().id().equals(memberVertex.id())) {
							existingEdge = e;
							break;
						}
					}

					if (existingEdge != null) {
						// Update existing edge
						existingEdge.property(indexProperty, sequenceIndex);
					} else {
						// Create new edge
						JanusGraphEdge edge = (JanusGraphEdge) collectionVertex.addEdge(relationType, memberVertex);
						edge.property(indexProperty, sequenceIndex);
					}
				} else {
					TelemetryManager.log("Warning: Member node not found: " + memberId);
				}

				sequenceIndex++;
			}

			tx.commit();

			collection.setGraphId(graphId);
			collection.setIdentifier(collectionId);

			TelemetryManager.log("'Create Collection' Operation Finished. | Collection ID: " + collectionId +
					", Members: " + members.size());

			return collection;

		} catch (Exception e) {
			if (null != tx)
				tx.rollback();
			TelemetryManager.error("Error creating collection", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}

	/**
	 * Delete a collection node with all its edges (DETACH DELETE)
	 * Removes the collection node and all incoming/outgoing relationships
	 * 
	 * @param graphId      the graph id
	 * @param collectionId the collection node identifier
	 */
	public static void deleteCollection(String graphId, String collectionId) {

		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Delete Collection' Operation Failed.]");

		if (StringUtils.isBlank(collectionId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_COLLECTION_NODE_ID + " | ['Delete Collection' Operation Failed.]");

		JanusGraphTransaction tx = null;
		try {
			JanusGraph graph = DriverUtil.getJanusGraph(graphId);
			tx = graph.newTransaction();
			TelemetryManager.log("JanusGraph Transaction Initialised. | [Graph Id: " + graphId + "]");

			// Find the collection node
			JanusGraphVertex collectionVertex = null;
			Iterator<JanusGraphVertex> iter = tx.query().has(SystemProperties.IL_UNIQUE_ID.name(), collectionId)
					.has("graphId", graphId).vertices().iterator();
			if (iter.hasNext()) {
				collectionVertex = iter.next();
			}

			if (collectionVertex == null) {
				TelemetryManager
						.log("Collection node not found: " + collectionId + " (already deleted or doesn't exist)");
				return;
			}

			// Delete all edges (both incoming and outgoing)
			Iterator<JanusGraphEdge> edges = JanusGraphNodeUtil.getEdges(collectionVertex, "BOTH");
			while (edges.hasNext()) {
				edges.next().remove();
			}

			// Delete the vertex itself
			collectionVertex.remove();

			tx.commit();

			TelemetryManager.log("'Delete Collection' Operation Finished. | Collection ID: " + collectionId);

		} catch (Exception e) {
			if (null != tx)
				tx.rollback();
			TelemetryManager.error("Error deleting collection", e);
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}
}
