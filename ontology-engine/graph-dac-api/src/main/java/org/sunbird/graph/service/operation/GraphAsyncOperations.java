package org.sunbird.graph.service.operation;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Value;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseHandler;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.MiddlewareException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.Relation;
import org.sunbird.graph.dac.model.SubGraph;
import org.sunbird.graph.dac.util.Neo4jNodeUtil;

import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.common.GraphOperation;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.graph.service.util.GraphQueryGenerationUtil;
import org.sunbird.telemetry.logger.TelemetryManager;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.util.*;
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

		Driver driver = DriverUtil.getDriver(graphId, GraphOperation.WRITE);
		TelemetryManager.log("Driver Initialised. | [Graph Id: " + graphId + "]");
		String query = GraphQueryGenerationUtil.generateCreateBulkRelationsCypherQuery(graphId);

		Map<String, Object> dataMap = new HashMap<String, Object>(){{
			put("data",relationData);
		}};
		try (Session session = driver.session()) {
			CompletionStage<Response> cs = session.runAsync(query, dataMap)
					.thenCompose(fn -> fn.singleAsync()).thenApply(record->{
						return ResponseHandler.OK();
					}).exceptionally(error -> {
						error.printStackTrace();
						throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
								"Error! Something went wrong while creating node object. ", error.getCause());
					});
			return FutureConverters.toScala(cs);
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

		Driver driver = DriverUtil.getDriver(graphId, GraphOperation.WRITE);
		TelemetryManager.log("Driver Initialised. | [Graph Id: " + graphId + "]");
		String query = GraphQueryGenerationUtil.generateDeleteBulkRelationsCypherQuery(graphId);
		Map<String, Object> dataMap = new HashMap<String, Object>(){{
			put("data",relationData);
		}};
		try (Session session = driver.session()) {
			CompletionStage<Response> cs = session.runAsync(query, dataMap)
					.thenCompose(fn -> fn.singleAsync()).thenApply(record->{
						return ResponseHandler.OK();
					}).exceptionally(error -> {
						error.printStackTrace();
						throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
								"Error! Something went wrong while creating node object. ", error.getCause());
					});
			return FutureConverters.toScala(cs);
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

		Driver driver = DriverUtil.getDriver(graphId, GraphOperation.WRITE);
		TelemetryManager.log("Driver Initialised. | [Graph Id: " + graphId + "]");
		String query = GraphQueryGenerationUtil.generateSubGraphCypherQuery(graphId, nodeId, depth);
		System.out.println("Query:  "+query);
		try (Session session = driver.session()) {
			CompletionStage cs = session.runAsync(query)
					.thenCompose(fn -> fn.listAsync()).thenApply(records -> {
						Map<Long, Object> relationMap = new HashMap<Long, Object>();
						Set<Node> nodes = new HashSet<>();
						Set<Relation> relations = new HashSet<>();
						Map<Long, Object> startNodeMap = new HashMap<>();
						Map<Long, Object> endNodeMap = new HashMap<>();
						for (Record record : records) {
							org.neo4j.driver.v1.types.Node startNode = record.get("startNode").asNode();
							org.neo4j.driver.v1.types.Node endNode = record.get("endNode").asNode();
							String relationName = record.get("relationName").asString();
							Map<String, Object> relationMetadata = record.get("relationMedatadata").asMap();
							nodes.add(Neo4jNodeUtil.getNode(graphId, startNode, relationMap, startNodeMap, endNodeMap));
							nodes.add(Neo4jNodeUtil.getNode(graphId, endNode, relationMap, startNodeMap, endNodeMap));
							//Relation MetaData
							Relation relData = new Relation(startNode.get(SystemProperties.IL_UNIQUE_ID.name()).asString(), relationName, endNode.get(SystemProperties.IL_UNIQUE_ID.name()).asString());
							relData.setMetadata(relationMetadata);
							relData.setStartNodeObjectType(startNode.get(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).asString());
							relData.setStartNodeName(startNode.get("name").asString());
							relData.setStartNodeType(startNode.get(SystemProperties.IL_SYS_NODE_TYPE.name()).asString());
							relData.setEndNodeObjectType(endNode.get(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).asString());
							relData.setEndNodeName(endNode.get("name").asString());
							relData.setEndNodeType(endNode.get(SystemProperties.IL_SYS_NODE_TYPE.name()).asString());
							relations.add(relData);
						}
						Set<Node> uniqNodes =  nodes.stream().collect(Collectors.groupingBy(n -> n.getIdentifier())).values().stream().map(a -> a.get(0)).collect(Collectors.toSet());
						Map<String, Node> nodeMap = new HashMap<>();
						for (Node nodeObj: uniqNodes) {
							nodeMap.put(nodeObj.getIdentifier(), nodeObj);
						}
						List<Relation> relationsList = new ArrayList<>();
						relationsList = relations.stream().collect(Collectors.toList());
						return new SubGraph(nodeMap, relationsList);
					}).exceptionally(error -> {
						error.printStackTrace();
						throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
								"Error! Something went wrong while creating node object. ", error.getCause());
					});
			return FutureConverters.toScala(cs);
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

	private static void getRecordValues(Record record, Map<Long, Object> nodeMap, Map<Long, Object> relationMap,
										Map<Long, Object> startNodeMap, Map<Long, Object> endNodeMap) {
		if (null != startNodeMap) {
			Value startNodeValue = record.get("startNode");
			if (null != startNodeValue && StringUtils.equalsIgnoreCase("NODE", startNodeValue.type().name())) {
				org.neo4j.driver.v1.types.Node startNode = record.get("startNode").asNode();
				nodeMap.put(startNode.id(), startNode);
			}
		}
		if (null != endNodeMap) {
			Value endNodeValue = record.get("endNode");
			if (null != endNodeValue && StringUtils.equalsIgnoreCase("NODE", endNodeValue.type().name())) {
				org.neo4j.driver.v1.types.Node endNode = record.get("endNode").asNode();
				nodeMap.put(endNode.id(), endNode);
			}
		}
//		if (null != relationMap) {
//			Value relValue = record.get("relationMedata");
//			if (null != relValue && StringUtils.equalsIgnoreCase("RELATIONSHIP", relValue.type().name())) {
//				org.neo4j.driver.v1.types.Relationship relationship = record
//						.get("relationMedata").asRelationship();
//				relationMap.put(relationship.id(), relationship);
//			}
//		}
	}
}
