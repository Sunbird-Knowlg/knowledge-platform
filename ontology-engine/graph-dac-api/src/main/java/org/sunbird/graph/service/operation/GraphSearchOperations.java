package org.sunbird.graph.service.operation;

import org.apache.commons.lang3.StringUtils;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphVertex;
import org.janusgraph.core.JanusGraphTransaction;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.sunbird.common.exception.ClientException;
import org.sunbird.graph.common.enums.GraphDACParams;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Graph search operations using JanusGraph Native API
 */
public class GraphSearchOperations {

    /**
     * Check if there's a cyclic loop between two nodes.
     * Determines if startNode can reach endNode via the given relation type.
     *
     * @param graphId      the graph id
     * @param startNodeId  the start node id (IL_UNIQUE_ID)
     * @param relationType the relation type (edge label)
     * @param endNodeId    the end node id (IL_UNIQUE_ID)
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
        JanusGraphTransaction tx = null;

        try {
            JanusGraph graph = DriverUtil.getJanusGraph(graphId);
            tx = graph.newTransaction();
            TelemetryManager.log("JanusGraph Transaction Initialized. | [Graph Id: " + graphId + "]");

            // Find start vertex
            Iterator<JanusGraphVertex> startIter = tx.query()
                    .has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
                    .has("graphId", graphId)
                    .vertices().iterator();

            if (startIter.hasNext()) {
                JanusGraphVertex startV = startIter.next();
                Set<String> visited = new HashSet<>();
                boolean pathExists = checkCycleRecursive(startV, endNodeId, relationType, visited, 0, 100);

                if (pathExists) {
                    cyclicLoopMap.put(GraphDACParams.loop.name(), Boolean.TRUE);
                    cyclicLoopMap.put(GraphDACParams.message.name(),
                            startNodeId + " and " + endNodeId + " are connected by relation: " + relationType);
                    TelemetryManager.log("Cyclic loop detected between " + startNodeId + " and " + endNodeId);
                } else {
                    cyclicLoopMap.put(GraphDACParams.loop.name(), Boolean.FALSE);
                    TelemetryManager.log("No cyclic loop found between " + startNodeId + " and " + endNodeId);
                }
            } else {
                // Start node not found, so no loop possible from it
                cyclicLoopMap.put(GraphDACParams.loop.name(), Boolean.FALSE);
                TelemetryManager.log("Start node not found based on Id: " + startNodeId);
            }
            tx.commit();

        } catch (Exception e) {
            if (tx != null)
                tx.rollback();
            TelemetryManager.error("Error checking cyclic loop: " + e.getMessage(), e);
            // Return false if there's an error checking (safe default)
            cyclicLoopMap.put(GraphDACParams.loop.name(), Boolean.FALSE);
            cyclicLoopMap.put(GraphDACParams.message.name(), "Error checking cycle: " + e.getMessage());
        }

        TelemetryManager.log("Returning Cyclic Loop Map: ", cyclicLoopMap);
        return cyclicLoopMap;
    }

    private static boolean checkCycleRecursive(Vertex currentV, String targetNodeId, String relationType,
            Set<String> visited, int depth, int maxDepth) {
        if (depth > maxDepth) {
            return false;
        }

        // Check if current vertex is the target (excluding start node in first call if
        // needed,
        // but typically loop check implies start->...->end where end is reachable.
        // If checking for loop back to start, targetNodeId would be startNodeId)

        String currentId = null;
        if (currentV.property(SystemProperties.IL_UNIQUE_ID.name()).isPresent()) {
            currentId = currentV.value(SystemProperties.IL_UNIQUE_ID.name()).toString();
        }

        if (StringUtils.equals(currentId, targetNodeId)) {
            // Found target!
            // However, if depth is 0 and we are checking if start==end?
            // The original logic checked if path exists.
            // If start==end initially, loop is trivially true or false depending on
            // definition?
            // Gremlin: repeat(out).until(hasId(end))
            // This implies at least one hop.
            if (depth > 0)
                return true;
        }

        if (currentId != null) {
            if (visited.contains(currentId)) {
                return false; // Already visited in this path or search
            }
            visited.add(currentId);
        }

        Iterator<Edge> edges = currentV.edges(Direction.OUT, relationType);
        while (edges.hasNext()) {
            Edge edge = edges.next();
            Vertex nextV = edge.inVertex();
            if (checkCycleRecursive(nextV, targetNodeId, relationType, visited, depth + 1, maxDepth)) {
                return true;
            }
        }

        // Backtrack (optional, depending on if we want simple path or any path.
        // For 'reachability' we don't need to unvisit, because if reachable via other
        // path, we'd have found it?
        // Actually for pure reachability (DFS), if we visited a node and didn't find
        // target from there,
        // we don't need to visit it again.)
        // So keeping it in visited is correct for efficiency.

        return false;
    }
}
