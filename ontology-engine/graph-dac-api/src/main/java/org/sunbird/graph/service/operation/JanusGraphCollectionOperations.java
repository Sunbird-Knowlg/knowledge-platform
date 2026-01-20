package org.sunbird.graph.service.operation;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
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
import org.sunbird.graph.service.common.GraphOperation;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.HashMap;
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
	 * @param graphId the graph id
	 * @param collectionId the collection node identifier
	 * @param collection the collection node with metadata
	 * @param members list of member node identifiers
	 * @param relationType the relation type to link members
	 * @param indexProperty the property name for sequence index (e.g., "IL_SEQUENCE_INDEX")
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

		GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
		TelemetryManager.log("GraphTraversalSource Initialised. | [Graph Id: " + graphId + "]");

		try {
			String timestamp = DateUtils.formatCurrentDate();
			
			// Set collection identifier if not already set
			if (StringUtils.isBlank(collection.getIdentifier())) {
				collection.setIdentifier(collectionId);
			}
			
			// Check if collection node already exists
			Vertex collectionVertex = g.V()
					.has(SystemProperties.IL_UNIQUE_ID.name(), collectionId)
					.has("graphId", graphId)
					.tryNext()
					.orElse(null);

			if (collectionVertex == null) {
				// ON CREATE - Create new collection node
				TelemetryManager.log("Creating new collection node: " + collectionId);
				
				// Generate version key
				String versionKey = Identifier.getIdentifier(graphId, Identifier.getUniqueIdFromTimestamp());
				
				collectionVertex = g.addV(collection.getObjectType())
						.property(SystemProperties.IL_UNIQUE_ID.name(), collectionId)
						.property("graphId", graphId)
						.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), collection.getObjectType())
						.property(SystemProperties.IL_SYS_NODE_TYPE.name(),
								StringUtils.isNotBlank(collection.getNodeType()) ? 
										collection.getNodeType() : SystemNodeTypes.DATA_NODE.name())
						.property(GraphDACParams.versionKey.name(), versionKey)
						.property(AuditProperties.createdOn.name(), timestamp)
						.property(AuditProperties.lastUpdatedOn.name(), timestamp)
						.next();
				
				// Add metadata properties
				if (collection.getMetadata() != null) {
					for (Map.Entry<String, Object> entry : collection.getMetadata().entrySet()) {
						if (entry.getValue() != null && !entry.getKey().equals(GraphDACParams.versionKey.name())) {
							collectionVertex.property(entry.getKey(), entry.getValue());
						}
					}
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
				
				// Create edge from collection to member
				Vertex memberVertex = g.V()
						.has(SystemProperties.IL_UNIQUE_ID.name(), memberId)
						.has("graphId", graphId)
						.tryNext()
						.orElse(null);
				
				if (memberVertex != null) {
					// Check if edge already exists
					Edge existingEdge = g.V(collectionVertex.id())
							.outE(relationType)
							.where(org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inV().hasId(memberVertex.id()))
							.tryNext()
							.orElse(null);
					
					if (existingEdge != null) {
						// Update existing edge
						existingEdge.property(indexProperty, sequenceIndex);
					} else {
						// Create new edge
						Edge edge = collectionVertex.addEdge(relationType, memberVertex);
						edge.property(indexProperty, sequenceIndex);
					}
				} else {
					TelemetryManager.log("Warning: Member node not found: " + memberId);
				}
				
				sequenceIndex++;
			}

			g.tx().commit();

			collection.setGraphId(graphId);
			collection.setIdentifier(collectionId);
			
			TelemetryManager.log("'Create Collection' Operation Finished. | Collection ID: " + collectionId + 
					", Members: " + members.size());
			
			return collection;

		} catch (Exception e) {
			TelemetryManager.error("Error creating collection", e);
			try {
				g.tx().rollback();
			} catch (Exception rollbackEx) {
				TelemetryManager.error("Error rolling back transaction", rollbackEx);
			}
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}

	/**
	 * Delete a collection node with all its edges (DETACH DELETE)
	 * Removes the collection node and all incoming/outgoing relationships
	 * 
	 * @param graphId the graph id
	 * @param collectionId the collection node identifier
	 */
	public static void deleteCollection(String graphId, String collectionId) {

		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Delete Collection' Operation Failed.]");

		if (StringUtils.isBlank(collectionId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_COLLECTION_NODE_ID + " | ['Delete Collection' Operation Failed.]");

		GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
		TelemetryManager.log("GraphTraversalSource Initialised. | [Graph Id: " + graphId + "]");

		try {
			// Find the collection node
			Vertex collectionVertex = g.V()
					.has(SystemProperties.IL_UNIQUE_ID.name(), collectionId)
					.has("graphId", graphId)
					.tryNext()
					.orElse(null);

			if (collectionVertex == null) {
				TelemetryManager.log("Collection node not found: " + collectionId + " (already deleted or doesn't exist)");
				return;
			}

			// Delete all edges (both incoming and outgoing) - DETACH behavior
			g.V(collectionVertex.id()).bothE().drop().iterate();
			
			// Delete the vertex itself
			g.V(collectionVertex.id()).drop().iterate();

			g.tx().commit();

			TelemetryManager.log("'Delete Collection' Operation Finished. | Collection ID: " + collectionId);

		} catch (Exception e) {
			TelemetryManager.error("Error deleting collection", e);
			try {
				g.tx().rollback();
			} catch (Exception rollbackEx) {
				TelemetryManager.error("Error rolling back transaction", rollbackEx);
			}
			throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
					DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
		}
	}
}
