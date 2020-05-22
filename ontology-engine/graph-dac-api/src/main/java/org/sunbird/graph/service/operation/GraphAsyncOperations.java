package org.sunbird.graph.service.operation;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.async.AsyncSession;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseHandler;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.MiddlewareException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.common.GraphOperation;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.graph.service.util.GraphQueryGenerationUtil;
import org.sunbird.telemetry.logger.TelemetryManager;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

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
		AsyncSession session = driver.asyncSession();
		CompletionStage<Response> cs = session.runAsync(query, dataMap)
				.thenCompose(fn -> fn.singleAsync()).thenApply(record->{
					return ResponseHandler.OK();
				}).exceptionally(error -> {
					throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
							"Error! Something went wrong while creating node object. ", error.getCause());
				});
		return FutureConverters.toScala(cs);
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
		AsyncSession session = driver.asyncSession();
		CompletionStage<Response> cs = session.runAsync(query, dataMap)
				.thenCompose(fn -> fn.singleAsync()).thenApply(record->{
					return ResponseHandler.OK();
				}).exceptionally(error -> {
					throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
							"Error! Something went wrong while creating node object. ", error.getCause());
				});
		return FutureConverters.toScala(cs);
	}
}
