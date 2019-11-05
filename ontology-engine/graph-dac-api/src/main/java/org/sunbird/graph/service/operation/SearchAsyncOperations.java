package org.sunbird.graph.service.operation;

import org.apache.commons.lang3.StringUtils;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.sunbird.common.dto.Property;
import org.sunbird.common.dto.Request;
import org.sunbird.common.exception.MiddlewareException;
import org.sunbird.common.exception.ResourceNotFoundException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.GraphDACParams;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.SearchCriteria;
import org.sunbird.graph.dac.util.Neo4jNodeUtil;
import org.sunbird.graph.service.common.CypherQueryConfigurationConstants;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.common.GraphOperation;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.graph.service.util.SearchQueryGenerationUtil;
import org.sunbird.telemetry.logger.TelemetryManager;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class SearchAsyncOperations {


    /**
     * Gets the node by unique ids.
     *
     * @param graphId
     *            the graph id
     * @param searchCriteria
     *            the search criteria
     * @return the node by unique ids
     */
    public static Future<List<Node>> getNodeByUniqueIds(String graphId, SearchCriteria searchCriteria) {

        if (StringUtils.isBlank(graphId))
            throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                    DACErrorMessageConstants.INVALID_GRAPH_ID
                            + " | ['Get Nodes By Search Criteria' Operation Failed.]");

        if (null == searchCriteria)
            throw new ClientException(DACErrorCodeConstants.INVALID_CRITERIA.name(),
                    DACErrorMessageConstants.INVALID_SEARCH_CRITERIA
                            + " | ['Get Nodes By Search Criteria' Operation Failed.]");

        List<Node> nodes = new ArrayList<Node>();
        Driver driver = DriverUtil.getDriver(graphId, GraphOperation.READ);
        TelemetryManager.log("Driver Initialised. | [Graph Id: " + graphId + "]");
        try (Session session = driver.session()) {
            Map<String, Object> parameterMap = new HashMap<String, Object>();
            parameterMap.put(GraphDACParams.graphId.name(), graphId);
            parameterMap.put(GraphDACParams.searchCriteria.name(), searchCriteria);
            Map<Long, Object> nodeMap = new HashMap<Long, Object>();
            Map<Long, Object> relationMap = new HashMap<Long, Object>();
            Map<Long, Object> startNodeMap = new HashMap<Long, Object>();
            Map<Long, Object> endNodeMap = new HashMap<Long, Object>();
            String query = SearchQueryGenerationUtil.generateGetNodeByUniqueIdsCypherQuery(parameterMap);
            Map<String, Object> params = searchCriteria.getParams();
            CompletionStage<List<Node>> cs = session.runAsync(query, params)
                    .thenCompose(fn -> fn.listAsync()).thenApply(result -> {
                        if (null != result) {
                            for (Record record : result) {
                                TelemetryManager.log("'Get Nodes By Search Criteria' Operation Finished.", record.asMap());
                                if (null != record)
                                    getRecordValues(record, nodeMap, relationMap, startNodeMap, endNodeMap);
                            }
                        }
                        if (!nodeMap.isEmpty()) {
                            for (Map.Entry<Long, Object> entry : nodeMap.entrySet()) {
                                nodes.add(Neo4jNodeUtil.getNode(graphId, (org.neo4j.driver.v1.types.Node) entry.getValue(), relationMap,
                                        startNodeMap, endNodeMap));
                            }
                        }
                        return nodes;
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
        if (null != nodeMap) {
            Value nodeValue = record.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT);
            if (null != nodeValue && StringUtils.equalsIgnoreCase("NODE", nodeValue.type().name())) {
                org.neo4j.driver.v1.types.Node neo4jBoltNode = record
                        .get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT).asNode();
                nodeMap.put(neo4jBoltNode.id(), neo4jBoltNode);
            }
        }
        if (null != relationMap) {
            Value relValue = record.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT);
            if (null != relValue && StringUtils.equalsIgnoreCase("RELATIONSHIP", relValue.type().name())) {
                org.neo4j.driver.v1.types.Relationship relationship = record
                        .get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT).asRelationship();
                relationMap.put(relationship.id(), relationship);
            }
        }
        if (null != startNodeMap) {
            Value startNodeValue = record.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_START_NODE_OBJECT);
            if (null != startNodeValue && StringUtils.equalsIgnoreCase("NODE", startNodeValue.type().name())) {
                org.neo4j.driver.v1.types.Node startNode = record
                        .get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_START_NODE_OBJECT).asNode();
                startNodeMap.put(startNode.id(), startNode);
            }
        }
        if (null != endNodeMap) {
            Value endNodeValue = record.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_END_NODE_OBJECT);
            if (null != endNodeValue && StringUtils.equalsIgnoreCase("NODE", endNodeValue.type().name())) {
                org.neo4j.driver.v1.types.Node endNode = record
                        .get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_END_NODE_OBJECT).asNode();
                endNodeMap.put(endNode.id(), endNode);
            }
        }
    }


    /**
     * Gets the node by unique id.
     *
     * @param graphId
     *            the graph id
     * @param nodeId
     *            the node id
     * @param getTags
     *            the get tags
     * @param request
     *            the request
     * @return the node by unique id
     */
    public static Future<Node> getNodeByUniqueId(String graphId, String nodeId, Boolean getTags, Request request) {
        TelemetryManager.log("Graph Id: " + graphId + "\nNode Id: " + nodeId + "\nGet Tags:" + getTags);

        if (StringUtils.isBlank(graphId))
            throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                    DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Node By Unique Id' Operation Failed.]");

        if (StringUtils.isBlank(nodeId))
            throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
                    DACErrorMessageConstants.INVALID_IDENTIFIER + " | ['Get Node By Unique Id' Operation Failed.]");

            Driver driver = DriverUtil.getDriver(graphId, GraphOperation.READ);
            TelemetryManager.log("Driver Initialised. | [Graph Id: " + graphId + "]");
            try (Session session = driver.session()) {
                Map<String, Object> parameterMap = new HashMap<String, Object>();
                parameterMap.put(GraphDACParams.graphId.name(), graphId);
                parameterMap.put(GraphDACParams.nodeId.name(), nodeId);
                parameterMap.put(GraphDACParams.getTags.name(), getTags);
                parameterMap.put(GraphDACParams.request.name(), request);
                CompletionStage<Node> cs = session.runAsync(SearchQueryGenerationUtil.generateGetNodeByUniqueIdCypherQuery(parameterMap))
                        .thenCompose(fn -> fn.singleAsync()).thenApply(record -> {
                            if (null == record)
                                throw new ResourceNotFoundException(DACErrorCodeConstants.NOT_FOUND.name(),
                                        DACErrorMessageConstants.NODE_NOT_FOUND + " | [Invalid Node Id.]: " + nodeId, nodeId);

                            Map<Long, Object> nodeMap = new HashMap<Long, Object>();
                            Map<Long, Object> relationMap = new HashMap<Long, Object>();
                            Map<Long, Object> startNodeMap = new HashMap<Long, Object>();
                            Map<Long, Object> endNodeMap = new HashMap<Long, Object>();

                            getRecordValues(record, nodeMap, relationMap, startNodeMap, endNodeMap);
                            Node node = null;
                            if (!nodeMap.isEmpty()) {
                                for (Map.Entry<Long, Object> entry : nodeMap.entrySet())
                                    node = Neo4jNodeUtil.getNode(graphId, (org.neo4j.driver.v1.types.Node) entry.getValue(), relationMap,
                                            startNodeMap, endNodeMap);
                            }
                            return node;
                        });

                return FutureConverters.toScala(cs);
        }
    }


    /**
     * Gets the node property.
     *
     * @param graphId
     *            the graph id
     * @param nodeId
     *            the node id
     * @param key
     *            the key
     * @return the node property
     */
    public static Future<Property> getNodeProperty(String graphId, String nodeId, String key) {
        TelemetryManager.log("Graph Id: " + graphId + "\nNode Id: " + nodeId + "\nProperty (Key): " + key);


        if (StringUtils.isBlank(graphId))
            throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                    DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Node Property' Operation Failed.]");

        if (StringUtils.isBlank(nodeId))
            throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
                    DACErrorMessageConstants.INVALID_IDENTIFIER + " | ['Get Node Property' Operation Failed.]");

        if (StringUtils.isBlank(key))
            throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name(),
                    DACErrorMessageConstants.INVALID_PROPERTY_KEY + " | ['Get Node Property' Operation Failed.]");

        Property property = new Property();
        Driver driver = DriverUtil.getDriver(graphId, GraphOperation.READ);
        TelemetryManager.log("Driver Initialised. | [Graph Id: " + graphId + "]");
        try (Session session = driver.session()) {
            Map<String, Object> parameterMap = new HashMap<String, Object>();
            parameterMap.put(GraphDACParams.graphId.name(), graphId);
            parameterMap.put(GraphDACParams.nodeId.name(), nodeId);
            parameterMap.put(GraphDACParams.key.name(), key);

            CompletionStage<Property> cs = session.runAsync(SearchQueryGenerationUtil.generateGetNodePropertyCypherQuery(parameterMap))
                    .thenCompose(fn -> fn.singleAsync()).thenApply(record -> {
                if (null != record && null != record.get(key)) {
                    property.setPropertyName(key);
                    property.setPropertyValue(record.get(key));
                }
                return property;
            });

            return FutureConverters.toScala(cs);
        }
    }
}
