package org.sunbird.graph.service.operation;

import org.apache.commons.lang3.StringUtils;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.sunbird.graph.common.enums.GraphDACParams;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.common.GraphOperation;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.graph.service.util.SearchQueryGenerationUtil;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.HashMap;
import java.util.Map;


public class Neo4JBoltSearchOperations {

	/**
	 * Check cyclic loop.
	 *
	 * @param graphId
	 *            the graph id
	 * @param startNodeId
	 *            the start node id
	 * @param relationType
	 *            the relation type
	 * @param endNodeId
	 *            the end node id
	 * @return the map
	 */
	public static Map<String, Object> checkCyclicLoop(String graphId, String startNodeId, String relationType,
			String endNodeId) {
		TelemetryManager.log("Graph Id: " + graphId + "\nStart Node Id: " + startNodeId + "\nRelation Type: "
				+ relationType + "\nEnd Node Id: " + endNodeId);


		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Check Cyclic Loop' Operation Failed.]");

		if (StringUtils.isBlank(startNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_START_NODE_ID + " | ['Check Cyclic Loop' Operation Failed.]");

		if (StringUtils.isBlank(relationType))
			throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name(),
					DACErrorMessageConstants.INVALID_RELATION_TYPE + " | ['Check Cyclic Loop' Operation Failed.]");

		if (StringUtils.isBlank(endNodeId))
			throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
					DACErrorMessageConstants.INVALID_END_NODE_ID + " | ['Check Cyclic Loop' Operation Failed.]");

		Map<String, Object> cyclicLoopMap = new HashMap<String, Object>();
		Driver driver = DriverUtil.getDriver(graphId, GraphOperation.WRITE);
		TelemetryManager.log("Driver Initialised. | [Graph Id: " + graphId + "]");
		try (Session session = driver.session()) {
			Map<String, Object> parameterMap = new HashMap<String, Object>();
			parameterMap.put(GraphDACParams.graphId.name(), graphId);
			parameterMap.put(GraphDACParams.startNodeId.name(), startNodeId);
			parameterMap.put(GraphDACParams.relationType.name(), relationType);
			parameterMap.put(GraphDACParams.endNodeId.name(), endNodeId);

			StatementResult result = session
					.run(SearchQueryGenerationUtil.generateCheckCyclicLoopCypherQuery(parameterMap));
			if (null != result && result.hasNext()) {
				cyclicLoopMap.put(GraphDACParams.loop.name(), new Boolean(true));
				cyclicLoopMap.put(GraphDACParams.message.name(),
						startNodeId + " and " + endNodeId + " are connected by relation: " + relationType);
			} else {
				cyclicLoopMap.put(GraphDACParams.loop.name(), new Boolean(false));
			}
		}

		TelemetryManager.log("Returning Cyclic Loop Map: ", cyclicLoopMap);
		return cyclicLoopMap;
	}

}
