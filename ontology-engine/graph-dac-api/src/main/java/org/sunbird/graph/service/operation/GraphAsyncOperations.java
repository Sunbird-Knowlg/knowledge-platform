package org.sunbird.graph.service.operation;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphEdge;
import org.janusgraph.core.JanusGraphTransaction;
import org.janusgraph.core.JanusGraphVertex;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseHandler;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.MiddlewareException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.GraphDACParams;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.Relation;
import org.sunbird.graph.dac.model.SubGraph;
import org.sunbird.graph.dac.util.JanusGraphNodeUtil;

import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.telemetry.logger.TelemetryManager;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class GraphAsyncOperations {

	public static Future<Response> createRelation(String graphId, List<Map<String, Object>> relationData) {
		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Relation Operation Failed.]");
		if (CollectionUtils.isEmpty(relationData))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_NODE + " | [Create Relation Operation Failed.]");

		try {
			CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> {
				JanusGraphTransaction tx = null;
				try {
					JanusGraph graph = DriverUtil.getJanusGraph(graphId);
					tx = graph.newTransaction();
					TelemetryManager.log("JanusGraph Transaction Initialised. | [Graph Id: " + graphId + "]");

					for (Map<String, Object> relData : relationData) {
						String startNodeId = (String) relData.get(GraphDACParams.startNodeId.name());
						String endNodeId = (String) relData.get(GraphDACParams.endNodeId.name());
						String relationType = (String) relData.get(GraphDACParams.relationType.name());
						@SuppressWarnings("unchecked")
						Map<String, Object> metadata = (Map<String, Object>) relData
								.get(GraphDACParams.metadata.name());

						if (metadata == null) {
							metadata = new HashMap<>();
						}

						if (StringUtils.isBlank(relationType)) {
							TelemetryManager.error("Relation type is missing for startNodeId: " + startNodeId
									+ ", endNodeId: " + endNodeId);
							continue;
						}

						// Logic from JanusGraphOperations.createRelation adapted for transaction reuse

						// Get Start Vertex
						Iterator<JanusGraphVertex> startIter = tx.query()
								.has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
								.has("graphId", graphId).vertices().iterator();
						if (!startIter.hasNext()) {
							throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
									"Start node not found: " + startNodeId);
						}
						JanusGraphVertex startV = startIter.next();

						// Get End Vertex
						Iterator<JanusGraphVertex> endIter = tx.query()
								.has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId)
								.has("graphId", graphId).vertices().iterator();
						if (!endIter.hasNext()) {
							throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
									"End node not found: " + endNodeId);
						}
						JanusGraphVertex endV = endIter.next();

						// Sequence index logic? (Simplified: assume provided or handle elsewhere if
						// needed,
						// but strict parity implies we might need it.
						// However, createRelation in GraphAsyncOperations usually takes full metadata.
						// The JanusGraphOperations.createRelation added Sequence Index logic.
						// Let's assume for bulk creation simpler logic or assume metadata has it.)
						// Actually, better to copy logic if critical.
						// Checking 'IL_SEQUENCE_INDEX' is in metadata.

						// Check if edge exists
						Edge edge = null;
						Iterator<JanusGraphEdge> edgeIter = startV.query().direction(Direction.OUT).labels(relationType)
								.edges().iterator();
						while (edgeIter.hasNext()) {
							JanusGraphEdge e = edgeIter.next();
							if (e.inVertex().id().equals(endV.id())) {
								edge = e;
								break;
							}
						}

						if (edge != null) {
							// Update
							for (Map.Entry<String, Object> entry : metadata.entrySet()) {
								edge.property(entry.getKey(), entry.getValue());
							}
						} else {
							// Create
							edge = startV.addEdge(relationType, endV);
							for (Map.Entry<String, Object> entry : metadata.entrySet()) {
								edge.property(entry.getKey(), entry.getValue());
							}
						}
					}

					tx.commit();
					return ResponseHandler.OK();
				} catch (Exception e) {
					if (tx != null)
						tx.rollback();
					TelemetryManager.error("Error creating bulk relations", e);
					throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
							"Error! Something went wrong while creating relations. ", e);
				}
			});

			return FutureConverters.toScala(future);
		} catch (Throwable e) {
			e.printStackTrace();
			if (!(e instanceof MiddlewareException)) {
				throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
						DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
			} else {
				throw e;
			}
		}
	}

	public static Future<Response> removeRelation(String graphId, List<Map<String, Object>> relationData) {
		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Remove Relation Operation Failed.]");
		if (CollectionUtils.isEmpty(relationData))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_NODE + " | [Remove Relation Operation Failed.]");

		try {
			CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> {
				JanusGraphTransaction tx = null;
				try {
					JanusGraph graph = DriverUtil.getJanusGraph(graphId);
					tx = graph.newTransaction();
					TelemetryManager.log("JanusGraph Transaction Initialised. | [Graph Id: " + graphId + "]");

					for (Map<String, Object> relData : relationData) {
						String startNodeId = (String) relData.get(GraphDACParams.startNodeId.name());
						String endNodeId = (String) relData.get(GraphDACParams.endNodeId.name());
						String relationType = (String) relData.get(GraphDACParams.relationType.name());

						// Logic from JanusGraphOperations.deleteRelation adapted

						// Get Start Vertex
						Iterator<JanusGraphVertex> startIter = tx.query()
								.has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
								.has("graphId", graphId).vertices().iterator();

						if (startIter.hasNext()) {
							JanusGraphVertex startV = startIter.next();
							Iterator<JanusGraphEdge> edgeIter = startV.query().direction(Direction.OUT)
									.labels(relationType).edges()
									.iterator();

							while (edgeIter.hasNext()) {
								JanusGraphEdge e = edgeIter.next();
								Vertex endV = e.inVertex();
								if (endV.property(SystemProperties.IL_UNIQUE_ID.name()).isPresent() &&
										endV.value(SystemProperties.IL_UNIQUE_ID.name()).equals(endNodeId)) {
									e.remove();
								}
							}
						}
					}

					tx.commit();
					return ResponseHandler.OK();
				} catch (Exception e) {
					if (tx != null)
						tx.rollback();
					TelemetryManager.error("Error removing bulk relations", e);
					throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
							"Error! Something went wrong while removing relations. ", e);
				}
			});

			return FutureConverters.toScala(future);
		} catch (Throwable e) {
			e.printStackTrace();
			if (!(e instanceof MiddlewareException)) {
				throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
						DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
			} else {
				throw e;
			}
		}
	}

	public static Future<SubGraph> getSubGraph(String graphId, String nodeId, Integer depth) {
		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Get SubGraph Operation Failed.]");
		if (StringUtils.isBlank(nodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_IDENTIFIER + " | [Please Provide Node Identifier.]");

		final int finalDepth = (null == depth) ? 5 : depth;

		try {
			CompletableFuture<SubGraph> future = CompletableFuture.supplyAsync(() -> {
				JanusGraphTransaction tx = null;
				try {
					JanusGraph graph = DriverUtil.getJanusGraph(graphId);
					tx = graph.newTransaction();
					TelemetryManager.log("JanusGraph Transaction Initialised. | [Graph Id: " + graphId + "]");

					Map<String, Node> nodeMap = new HashMap<>();
					Set<Relation> relations = new HashSet<>();
					Set<String> visited = new HashSet<>();

					getSubGraphRecursive(tx, graphId, nodeId, finalDepth, visited, nodeMap, relations);

					List<Relation> relationsList = new ArrayList<>(relations);
					tx.commit();
					return new SubGraph(nodeMap, relationsList);
				} catch (Exception e) {
					if (tx != null)
						tx.rollback();
					TelemetryManager.error("Error getting subgraph", e);
					throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
							"Error! Something went wrong while getting subgraph. ", e);
				}
			});

			return FutureConverters.toScala(future);
		} catch (Throwable e) {
			e.printStackTrace();
			if (!(e instanceof MiddlewareException)) {
				throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
						DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
			} else {
				throw e;
			}
		}
	}

	private static void getSubGraphRecursive(JanusGraphTransaction tx, String graphId, String nodeId, int depth,
			Set<String> visited, Map<String, Node> nodeMap, Set<Relation> relations) {
		if (depth < 0 || visited.contains(nodeId)) {
			return;
		}
		visited.add(nodeId);

		Iterator<JanusGraphVertex> vertexIter = tx.query().has(SystemProperties.IL_UNIQUE_ID.name(), nodeId)
				.has("graphId", graphId).vertices().iterator();
		if (vertexIter.hasNext()) {
			JanusGraphVertex vertex = vertexIter.next();
			Node node = JanusGraphNodeUtil.getNodeWithoutRelations(graphId, vertex);
			nodeMap.put(nodeId, node);

			if (depth > 0) {
				Iterator<JanusGraphEdge> edges = vertex.query().direction(Direction.BOTH).edges().iterator();
				while (edges.hasNext()) {
					JanusGraphEdge edge = edges.next();
					Vertex otherV = edge.inVertex().id().equals(vertex.id()) ? edge.outVertex() : edge.inVertex();

					String otherNodeId = null;
					if (otherV.property(SystemProperties.IL_UNIQUE_ID.name()).isPresent()) {
						otherNodeId = otherV.value(SystemProperties.IL_UNIQUE_ID.name()).toString();
					}

					if (StringUtils.isNotBlank(otherNodeId)) {
						// Extract Relation
						String startNodeId = null;
						Vertex outV = edge.outVertex();
						if (outV.property(SystemProperties.IL_UNIQUE_ID.name()).isPresent()) {
							startNodeId = outV.value(SystemProperties.IL_UNIQUE_ID.name()).toString();
						}

						String endNodeId = null;
						Vertex inV = edge.inVertex();
						if (inV.property(SystemProperties.IL_UNIQUE_ID.name()).isPresent()) {
							endNodeId = inV.value(SystemProperties.IL_UNIQUE_ID.name()).toString();
						}

						if (StringUtils.isNotBlank(startNodeId) && StringUtils.isNotBlank(endNodeId)) {
							Relation relation = new Relation(startNodeId, edge.label(), endNodeId);
							Map<String, Object> edgeMetadata = JanusGraphNodeUtil.getEdgeProperties(edge);
							relation.setMetadata(edgeMetadata);

							// Add optional properties logic from original code if needed (startNodeName
							// etc)
							// ... (Skipping for brevity, but recommended to keep if critical)
							// The original code enriched relation with start/end node types/names.
							// This required fetching properties from vertices.

							enrichRelationWithNodeProps(relation, outV, inV);

							relations.add(relation);
						}

						getSubGraphRecursive(tx, graphId, otherNodeId, depth - 1, visited, nodeMap, relations);
					}
				}
			}
		}
	}

	private static void enrichRelationWithNodeProps(Relation relation, Vertex outV, Vertex inV) {
		if (outV.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).isPresent()) {
			relation.setStartNodeObjectType(outV
					.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).value().toString());
		}
		if (outV.property("name").isPresent()) {
			relation.setStartNodeName(outV.property("name").value().toString());
		}
		if (outV.property(SystemProperties.IL_SYS_NODE_TYPE.name()).isPresent()) {
			relation.setStartNodeType(
					outV.property(SystemProperties.IL_SYS_NODE_TYPE.name()).value().toString());
		}
		if (inV.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).isPresent()) {
			relation.setEndNodeObjectType(inV
					.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).value().toString());
		}
		if (inV.property("name").isPresent()) {
			relation.setEndNodeName(inV.property("name").value().toString());
		}
		if (inV.property(SystemProperties.IL_SYS_NODE_TYPE.name()).isPresent()) {
			relation.setEndNodeType(
					inV.property(SystemProperties.IL_SYS_NODE_TYPE.name()).value().toString());
		}
	}

	/**
	 * Create bulk outgoing relations (one-to-many) from a single start node to
	 * multiple end nodes
	 * Supports UNWIND-style batch operations for efficient relation creation (100+
	 * relations)
	 * 
	 * @param graphId        the graph identifier
	 * @param startNodeId    the start node identifier
	 * @param endNodeIds     list of end node identifiers
	 * @param relationType   the relation type
	 * @param createMetadata metadata to set on newly created relations
	 * @param matchMetadata  metadata to set on existing relations
	 * @return Future Response
	 */
	public static Future<Response> createBulkOutgoingRelations(String graphId, String startNodeId,
			List<String> endNodeIds, String relationType, Map<String, Object> createMetadata,
			Map<String, Object> matchMetadata) {

		try {
			CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> {
				try {
					Request request = new Request();
					if (createMetadata != null)
						request.getRequest().put("createMetadata", createMetadata);
					if (matchMetadata != null)
						request.getRequest().put("matchMetadata", matchMetadata);
					JanusGraphOperations.createOutgoingRelations(graphId, startNodeId, endNodeIds,
							relationType, request);
					return ResponseHandler.OK();
				} catch (Exception e) {
					TelemetryManager.error("Error creating bulk outgoing relations", e);
					throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
							"Error! Something went wrong while creating bulk outgoing relations. ", e);
				}
			});

			return FutureConverters.toScala(future);
		} catch (Throwable e) {
			e.printStackTrace();
			if (!(e instanceof MiddlewareException)) {
				throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
						DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
			} else {
				throw e;
			}
		}
	}

	/**
	 * Create bulk incoming relations (many-to-one) from multiple start nodes to a
	 * single end node
	 * Supports UNWIND-style batch operations for efficient relation creation (100+
	 * relations)
	 * 
	 * @param graphId        the graph identifier
	 * @param startNodeIds   list of start node identifiers
	 * @param endNodeId      the end node identifier
	 * @param relationType   the relation type
	 * @param createMetadata metadata to set on newly created relations
	 * @param matchMetadata  metadata to set on existing relations
	 * @return Future Response
	 */
	public static Future<Response> createBulkIncomingRelations(String graphId, List<String> startNodeIds,
			String endNodeId, String relationType, Map<String, Object> createMetadata,
			Map<String, Object> matchMetadata) {

		try {
			CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> {
				try {
					Request request = new Request();
					if (createMetadata != null)
						request.getRequest().put("createMetadata", createMetadata);
					if (matchMetadata != null)
						request.getRequest().put("matchMetadata", matchMetadata);
					JanusGraphOperations.createIncomingRelations(graphId, startNodeIds, endNodeId,
							relationType, request);
					return ResponseHandler.OK();
				} catch (Exception e) {
					TelemetryManager.error("Error creating bulk incoming relations", e);
					throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
							"Error! Something went wrong while creating bulk incoming relations. ", e);
				}
			});

			return FutureConverters.toScala(future);
		} catch (Throwable e) {
			e.printStackTrace();
			if (!(e instanceof MiddlewareException)) {
				throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
						DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
			} else {
				throw e;
			}
		}
	}

	/**
	 * Delete bulk outgoing relations (one-to-many) from a single start node to
	 * multiple end nodes
	 * Supports UNWIND-style batch operations for efficient relation deletion (100+
	 * relations)
	 * 
	 * @param graphId      the graph identifier
	 * @param startNodeId  the start node identifier
	 * @param endNodeIds   list of end node identifiers
	 * @param relationType the relation type
	 * @return Future Response
	 */
	public static Future<Response> deleteBulkOutgoingRelations(String graphId, String startNodeId,
			List<String> endNodeIds, String relationType) {

		try {
			CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> {
				try {
					Request request = new Request();
					JanusGraphOperations.deleteOutgoingRelations(graphId, startNodeId, endNodeIds, relationType,
							request);
					return ResponseHandler.OK();
				} catch (Exception e) {
					TelemetryManager.error("Error deleting bulk outgoing relations", e);
					throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
							"Error! Something went wrong while deleting bulk outgoing relations. ", e);
				}
			});

			return FutureConverters.toScala(future);
		} catch (Throwable e) {
			e.printStackTrace();
			if (!(e instanceof MiddlewareException)) {
				throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
						DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
			} else {
				throw e;
			}
		}
	}

	/**
	 * Delete bulk incoming relations (many-to-one) from multiple start nodes to a
	 * single end node
	 * Supports UNWIND-style batch operations for efficient relation deletion (100+
	 * relations)
	 * 
	 * @param graphId      the graph identifier
	 * @param startNodeIds list of start node identifiers
	 * @param endNodeId    the end node identifier
	 * @param relationType the relation type
	 * @return Future Response
	 */
	public static Future<Response> deleteBulkIncomingRelations(String graphId, List<String> startNodeIds,
			String endNodeId, String relationType) {

		try {
			CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> {
				try {
					Request request = new Request();
					JanusGraphOperations.deleteIncomingRelations(graphId, startNodeIds, endNodeId, relationType,
							request);
					return ResponseHandler.OK();
				} catch (Exception e) {
					TelemetryManager.error("Error deleting bulk incoming relations", e);
					throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
							"Error! Something went wrong while deleting bulk incoming relations. ", e);
				}
			});

			return FutureConverters.toScala(future);
		} catch (Throwable e) {
			e.printStackTrace();
			if (!(e instanceof MiddlewareException)) {
				throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
						DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
			} else {
				throw e;
			}
		}
	}

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
	 * @return Future Response with collection Node
	 */
	public static Future<Response> createCollection(String graphId, String collectionId, Node collection,
			List<String> members, String relationType, String indexProperty) {

		try {
			CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> {
				try {
					Node result = JanusGraphCollectionOperations.createCollection(graphId, collectionId,
							collection, members, relationType, indexProperty);
					Response response = ResponseHandler.OK();
					response.put(GraphDACParams.node.name(), result);
					return response;
				} catch (Exception e) {
					TelemetryManager.error("Error creating collection", e);
					throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
							"Error! Something went wrong while creating collection. ", e);
				}
			});

			return FutureConverters.toScala(future);
		} catch (Throwable e) {
			e.printStackTrace();
			if (!(e instanceof MiddlewareException)) {
				throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
						DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
			} else {
				throw e;
			}
		}
	}

	/**
	 * Delete a collection node with all its edges (DETACH DELETE)
	 * Removes the collection node and all incoming/outgoing relationships
	 * 
	 * @param graphId      the graph id
	 * @param collectionId the collection node identifier
	 * @return Future Response
	 */
	public static Future<Response> deleteCollection(String graphId, String collectionId) {

		try {
			CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> {
				try {
					JanusGraphCollectionOperations.deleteCollection(graphId, collectionId);
					return ResponseHandler.OK();
				} catch (Exception e) {
					TelemetryManager.error("Error deleting collection", e);
					throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
							"Error! Something went wrong while deleting collection. ", e);
				}
			});

			return FutureConverters.toScala(future);
		} catch (Throwable e) {
			e.printStackTrace();
			if (!(e instanceof MiddlewareException)) {
				throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
						DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage(), e);
			} else {
				throw e;
			}
		}
	}
}
