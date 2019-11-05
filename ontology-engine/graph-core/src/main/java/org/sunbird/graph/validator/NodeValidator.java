package org.sunbird.graph.validator;

import org.sunbird.common.exception.ResourceNotFoundException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.model.Filter;
import org.sunbird.graph.dac.model.MetadataCriterion;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.SearchConditions;
import org.sunbird.graph.dac.model.SearchCriteria;
import org.sunbird.graph.exception.GraphEngineErrorCodes;
import org.sunbird.graph.service.operation.SearchAsyncOperations;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * This class provides utility methods for node validation
 * @author Kumar Gauraw
 */
public class NodeValidator {

    /**
     * This method validates whether given identifiers exist in the given graph or not.
     * @param graphId
     * @param identifiers
     * @return List<Node>
     */
    public static Future<Map<String, Node>> validate(String graphId, List<String> identifiers) {
        List<Map<String, Object>> result;
        Future<List<Node>> nodes = getDataNodes(graphId, identifiers);
        CompletionStage<Map<String, Node>> cs = FutureConverters.toJava(nodes).thenApply(dataNodes -> {
            Map<String, Node> relationNodes = new HashMap<>();
            if (dataNodes.size() != identifiers.size()) {
                List<String> invalidIds = identifiers.stream().filter(id -> dataNodes.stream().noneMatch(node -> node.getIdentifier().equals(id)))
                        .collect(Collectors.toList());
                throw new ResourceNotFoundException(GraphEngineErrorCodes.ERR_INVALID_NODE.name(), "Node Not Found With Identifier " + invalidIds);
            } else {
                relationNodes = dataNodes.stream().collect(Collectors.toMap(node -> node.getIdentifier(), node -> node));
                return relationNodes;
            }
        });

        return FutureConverters.toScala(cs);
    }

    /**
     * This method fetch and return list of Node object for given graph & identifiers
     * @param graphId
     * @param identifiers
     * @return List<Node>
     */
    private static Future<List<Node>> getDataNodes(String graphId, List<String> identifiers) {
        SearchCriteria searchCriteria = new SearchCriteria();
        MetadataCriterion mc = null;
        if (identifiers.size() == 1) {
            mc = MetadataCriterion
                    .create(Arrays.asList(new Filter(SystemProperties.IL_UNIQUE_ID.name(), SearchConditions.OP_EQUAL, identifiers.get(0))));
        } else {
            mc = MetadataCriterion.create(Arrays.asList(new Filter(SystemProperties.IL_UNIQUE_ID.name(), SearchConditions.OP_IN, identifiers)));
        }
        searchCriteria.addMetadata(mc);
        searchCriteria.setCountQuery(false);
        try {
            Future<List<Node>> nodes = SearchAsyncOperations.getNodeByUniqueIds(graphId, searchCriteria);
            return nodes;
        } catch (Exception e) {
            throw new ServerException(GraphEngineErrorCodes.ERR_GRAPH_PROCESSING_ERROR.name(), "Unable To Fetch Nodes From Graph. Exception is: " + e.getMessage());

        }
    }
}
