package org.sunbird.graph.service.operation;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.sunbird.common.exception.ClientException;
import org.sunbird.graph.common.enums.GraphDACParams;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.common.GraphOperation;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Graph search operations using JanusGraph/Gremlin
 * Replaces Neo4JBoltSearchOperations with Gremlin traversals
 */
public class GraphSearchOperations {

    /**
     * Check if there's a cyclic loop between two nodes.
     * Determines if startNode can reach endNode via the given relation type.
     *
     * @param graphId       the graph id
     * @param startNodeId   the start node id (IL_UNIQUE_ID)
     * @param relationType  the relation type (edge label)
     * @param endNodeId     the end node id (IL_UNIQUE_ID)
     * @return Map containing "loop" (boolean) and optional "message" (string)
     */
    public static Map<String, Object> checkCyclicLoop(String graphId, String startNodeId, String relationType,
                                                       String endNodeId) {
        TelemetryManager.log("Checking Cyclic Loop - Graph Id: " + graphId + ", Start Node: " + startNodeId + 
                           ", Relation Type: " + relationType + ", End Node: " + endNodeId);

        // Validate inputs
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

        Map<String, Object> cyclicLoopMap = new HashMap<>();
        GraphTraversalSource g = DriverUtil.getGraphTraversalSource(graphId, GraphOperation.READ);
        TelemetryManager.log("GraphTraversalSource Initialized. | [Graph Id: " + graphId + "]");

        try {
            // Check if a path exists from startNode to endNode via the specified relation type
            // Using repeat().until() to traverse the graph looking for cycles
            boolean pathExists = g.V()
                    .has("IL_UNIQUE_ID", startNodeId)
                    .has("graphId", graphId)
                    .repeat(__.out(relationType))
                    .until(__.has("IL_UNIQUE_ID", endNodeId).or().loops().is(100)) // Limit to 100 hops to prevent infinite loops
                    .has("IL_UNIQUE_ID", endNodeId)
                    .hasNext();

            if (pathExists) {
                cyclicLoopMap.put(GraphDACParams.loop.name(), Boolean.TRUE);
                cyclicLoopMap.put(GraphDACParams.message.name(),
                        startNodeId + " and " + endNodeId + " are connected by relation: " + relationType);
                TelemetryManager.log("Cyclic loop detected between " + startNodeId + " and " + endNodeId);
            } else {
                cyclicLoopMap.put(GraphDACParams.loop.name(), Boolean.FALSE);
                TelemetryManager.log("No cyclic loop found between " + startNodeId + " and " + endNodeId);
            }

        } catch (Exception e) {
            TelemetryManager.error("Error checking cyclic loop: " + e.getMessage(), e);
            // Return false if there's an error checking (safe default)
            cyclicLoopMap.put(GraphDACParams.loop.name(), Boolean.FALSE);
            cyclicLoopMap.put(GraphDACParams.message.name(), "Error checking cycle: " + e.getMessage());
        }

        TelemetryManager.log("Returning Cyclic Loop Map: ", cyclicLoopMap);
        return cyclicLoopMap;
    }
}
