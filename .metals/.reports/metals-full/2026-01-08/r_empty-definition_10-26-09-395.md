error id: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/GraphAsyncOperations.java:org/sunbird/common/dto/Request#
file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/GraphAsyncOperations.java
empty definition using pc, found symbol in pc: org/sunbird/common/dto/Request#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 369
uri: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/GraphAsyncOperations.java
text:
```scala
package org.sunbird.graph.service.operation;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.sunbird.common.dto.@@Request;
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
import org.sunbird.graph.service.common.GraphOperation;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.graph.service.util.GremlinQueryBuilder;
import org.sunbird.telemetry.logger.TelemetryManager;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.*;

public class GraphAsyncOperations {

	public static Future<Response> createRelation(String graphId, List<Map<String, Object>> relationData) {
		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Relation Operation Failed.]");
		if (CollectionUtils.isEmpty(relationData))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_NODE + " | [Create Relation Operation Failed.]");

		GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
		TelemetryManager.log("GraphTraversalSource Initialised. | [Graph Id: " + graphId + "]");

		try {
			CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> {
				try {
					for (Map<String, Object> relData : relationData) {
						String startNodeId = (String) relData.get(GraphDACParams.startNodeId.name());
						String endNodeId = (String) relData.get(GraphDACParams.endNodeId.name());
						String relationType = (String) relData.get(GraphDACParams.relationType.name());
						@SuppressWarnings("unchecked")
						Map<String, Object> metadata = (Map<String, Object>) relData.get(GraphDACParams.metadata.name());
						
						if (metadata == null) {
							metadata = new HashMap<>();
						}
						
						GremlinQueryBuilder.createEdge(g, graphId, startNodeId, endNodeId, relationType, metadata).next();
					}
					return ResponseHandler.OK();
				} catch (Exception e) {
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

		GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.WRITE);
		TelemetryManager.log("GraphTraversalSource Initialised. | [Graph Id: " + graphId + "]");

		try {
			CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> {
				try {
					for (Map<String, Object> relData : relationData) {
						String startNodeId = (String) relData.get(GraphDACParams.startNodeId.name());
						String endNodeId = (String) relData.get(GraphDACParams.endNodeId.name());
						String relationType = (String) relData.get(GraphDACParams.relationType.name());
						
						GremlinQueryBuilder.deleteEdge(g, graphId, startNodeId, endNodeId, relationType).iterate();
					}
					return ResponseHandler.OK();
				} catch (Exception e) {
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
		if (null == depth) depth = 5;

		GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.READ);
		TelemetryManager.log("GraphTraversalSource Initialised. | [Graph Id: " + graphId + "]");
		
		final Integer finalDepth = depth;
		try {
			CompletableFuture<SubGraph> future = CompletableFuture.supplyAsync(() -> {
				try {
					Map<String, Node> nodeMap = new HashMap<>();
					Set<Relation> relations = new HashSet<>();
					
					// Get starting vertex
					Vertex startVertex = GremlinQueryBuilder.getVertexByIdentifier(g, graphId, nodeId).next();
					
					// Traverse the graph up to the specified depth
					List<org.apache.tinkerpop.gremlin.process.traversal.Path> traversalResults = g.V(startVertex.id())
							.repeat(bothE().otherV().simplePath())
							.times(finalDepth)
							.path()
							.toList();
					
					// Process results
					for (org.apache.tinkerpop.gremlin.process.traversal.Path path : traversalResults) {
						
						for (Object obj : path.objects()) {
							if (obj instanceof Vertex) {
								Vertex vertex = (Vertex) obj;
								Node node = JanusGraphNodeUtil.getNodeWithoutRelations(graphId, vertex);
								nodeMap.putIfAbsent(node.getIdentifier(), node);
							} else if (obj instanceof Edge) {
								Edge edge = (Edge) obj;
								Vertex outV = edge.outVertex();
								Vertex inV = edge.inVertex();
								
								String startNodeId = outV.property(SystemProperties.IL_UNIQUE_ID.name()).value().toString();
								String endNodeId = inV.property(SystemProperties.IL_UNIQUE_ID.name()).value().toString();
								String relationType = edge.label();
								
								Relation relation = new Relation(startNodeId, relationType, endNodeId);
								Map<String, Object> edgeMetadata = JanusGraphNodeUtil.getEdgeProperties(edge);
								relation.setMetadata(edgeMetadata);
								
								// Set additional node information
								if (outV.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).isPresent()) {
									relation.setStartNodeObjectType(outV.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).value().toString());
								}
								if (outV.property("name").isPresent()) {
									relation.setStartNodeName(outV.property("name").value().toString());
								}
								if (outV.property(SystemProperties.IL_SYS_NODE_TYPE.name()).isPresent()) {
									relation.setStartNodeType(outV.property(SystemProperties.IL_SYS_NODE_TYPE.name()).value().toString());
								}
								if (inV.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).isPresent()) {
									relation.setEndNodeObjectType(inV.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).value().toString());
								}
								if (inV.property("name").isPresent()) {
									relation.setEndNodeName(inV.property("name").value().toString());
								}
								if (inV.property(SystemProperties.IL_SYS_NODE_TYPE.name()).isPresent()) {
									relation.setEndNodeType(inV.property(SystemProperties.IL_SYS_NODE_TYPE.name()).value().toString());
								}
								
								relations.add(relation);
							}
						}
					}
					
					List<Relation> relationsList = new ArrayList<>(relations);
					return new SubGraph(nodeMap, relationsList);
				} catch (Exception e) {
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

	/**
	 * Create bulk outgoing relations (one-to-many) from a single start node to multiple end nodes
	 * Supports UNWIND-style batch operations for efficient relation creation (100+ relations)
	 * 
	 * @param graphId the graph identifier
	 * @param startNodeId the start node identifier  
	 * @param endNodeIds list of end node identifiers
	 * @param relationType the relation type
	 * @param createMetadata metadata to set on newly created relations
	 * @param matchMetadata metadata to set on existing relations
	 * @return Future Response
	 */
	public static Future<Response> createBulkOutgoingRelations(String graphId, String startNodeId,
			List<String> endNodeIds, String relationType, Map<String, Object> createMetadata,
			Map<String, Object> matchMetadata) {
		
		try {
			CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> {
				try {
					Request request = new Request();
					if (createMetadata != null) request.getRequest().put("createMetadata", createMetadata);
					if (matchMetadata != null) request.getRequest().put("matchMetadata", matchMetadata);
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
	 * Create bulk incoming relations (many-to-one) from multiple start nodes to a single end node
	 * Supports UNWIND-style batch operations for efficient relation creation (100+ relations)
	 * 
	 * @param graphId the graph identifier
	 * @param startNodeIds list of start node identifiers
	 * @param endNodeId the end node identifier
	 * @param relationType the relation type
	 * @param createMetadata metadata to set on newly created relations
	 * @param matchMetadata metadata to set on existing relations
	 * @return Future Response
	 */
	public static Future<Response> createBulkIncomingRelations(String graphId, List<String> startNodeIds,
			String endNodeId, String relationType, Map<String, Object> createMetadata,
			Map<String, Object> matchMetadata) {
		
		try {
			CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> {
				try {
					Request request = new Request();
					if (createMetadata != null) request.getRequest().put("createMetadata", createMetadata);
					if (matchMetadata != null) request.getRequest().put("matchMetadata", matchMetadata);
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
	 * Delete bulk outgoing relations (one-to-many) from a single start node to multiple end nodes
	 * Supports UNWIND-style batch operations for efficient relation deletion (100+ relations)
	 * 
	 * @param graphId the graph identifier
	 * @param startNodeId the start node identifier
	 * @param endNodeIds list of end node identifiers
	 * @param relationType the relation type
	 * @return Future Response
	 */
	public static Future<Response> deleteBulkOutgoingRelations(String graphId, String startNodeId,
			List<String> endNodeIds, String relationType) {
		
		try {
			CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> {
				try {
					Request request = new Request();
					JanusGraphOperations.deleteOutgoingRelations(graphId, startNodeId, endNodeIds, relationType, request);
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
	 * Delete bulk incoming relations (many-to-one) from multiple start nodes to a single end node
	 * Supports UNWIND-style batch operations for efficient relation deletion (100+ relations)
	 * 
	 * @param graphId the graph identifier
	 * @param startNodeIds list of start node identifiers
	 * @param endNodeId the end node identifier
	 * @param relationType the relation type
	 * @return Future Response
	 */
	public static Future<Response> deleteBulkIncomingRelations(String graphId, List<String> startNodeIds,
			String endNodeId, String relationType) {
		
		try {
			CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> {
				try {
					Request request = new Request();
					JanusGraphOperations.deleteIncomingRelations(graphId, startNodeIds, endNodeId, relationType, request);
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
	 * @param graphId the graph id
	 * @param collectionId the collection node identifier
	 * @param collection the collection node with metadata
	 * @param members list of member node identifiers
	 * @param relationType the relation type to link members
	 * @param indexProperty the property name for sequence index (e.g., "IL_SEQUENCE_INDEX")
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
	 * @param graphId the graph id
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

```


#### Short summary: 

empty definition using pc, found symbol in pc: org/sunbird/common/dto/Request#