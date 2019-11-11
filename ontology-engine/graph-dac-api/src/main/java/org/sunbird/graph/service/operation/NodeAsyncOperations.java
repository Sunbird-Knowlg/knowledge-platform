package org.sunbird.graph.service.operation;

import org.apache.commons.lang3.StringUtils;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Session;
import org.sunbird.common.dto.Request;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.MiddlewareException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.GraphDACParams;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.service.common.CypherQueryConfigurationConstants;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.common.GraphOperation;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.graph.service.util.NodeQueryGenerationUtil;
import org.sunbird.telemetry.logger.TelemetryManager;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class NodeAsyncOperations {

    private final static String DEFAULT_CYPHER_NODE_OBJECT = "ee";


    public static Future<Node> addNode(String graphId, Node node) {

        if (StringUtils.isBlank(graphId))
            throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                    DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Node Operation Failed.]");

        if (null == node)
            throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name(),
                    DACErrorMessageConstants.INVALID_NODE + " | [Create Node Operation Failed.]");

        Driver driver = DriverUtil.getDriver(graphId, GraphOperation.WRITE);
        TelemetryManager.log("Driver Initialised. | [Graph Id: " + graphId + "]");

        Map<String, Object> parameterMap = new HashMap<String, Object>();
        parameterMap.put(GraphDACParams.graphId.name(), graphId);
        parameterMap.put(GraphDACParams.node.name(), node);
        NodeQueryGenerationUtil.generateCreateNodeCypherQuery(parameterMap);
        Map<String, Object> queryMap = (Map<String, Object>) parameterMap.get(GraphDACParams.queryStatementMap.name());
        Map<String, Object> entry = (Map<String, Object>) queryMap.entrySet().stream().findFirst().get().getValue();

        try (Session session = driver.session()) {
            String statementTemplate = StringUtils.removeEnd((String) entry.get(GraphDACParams.query.name()), CypherQueryConfigurationConstants.COMMA);
            Map<String, Object> statementParameters = (Map<String, Object>) entry.get(GraphDACParams.paramValueMap.name());
            CompletionStage<Node> cs = session.runAsync(statementTemplate, statementParameters)
            .thenCompose(fn -> fn.singleAsync())
            .thenApply(record -> {
                org.neo4j.driver.v1.types.Node neo4JNode = record.get(DEFAULT_CYPHER_NODE_OBJECT).asNode();
                String versionKey = (String) neo4JNode.get(GraphDACParams.versionKey.name()).asString();
                String identifier = (String) neo4JNode.get(SystemProperties.IL_UNIQUE_ID.name()).asString();
                node.setGraphId(graphId);
                node.setIdentifier(identifier);
                if (StringUtils.isNotBlank(versionKey))
                    node.getMetadata().put(GraphDACParams.versionKey.name(), versionKey);
                return node;
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

    @SuppressWarnings("unchecked")
    public static Future<Node> upsertNode(String graphId, Node node, Request request) {
        TelemetryManager.log("Applying the Consumer Authorization Check for Node Id: " + node.getIdentifier());
        setRequestContextToNode(node, request);
        validateAuthorization(graphId, node, request);
        TelemetryManager.log("Consumer is Authorized for Node Id: " + node.getIdentifier());

        TelemetryManager.log("Validating the Update Operation for Node Id: " + node.getIdentifier());
        node.getMetadata().remove(GraphDACParams.versionKey.name());
        TelemetryManager.log("Node Update Operation has been Validated for Node Id: " + node.getIdentifier());

        Driver driver = DriverUtil.getDriver(graphId, GraphOperation.WRITE);
        TelemetryManager.log("Driver Initialised. | [Graph Id: " + graphId + "]");

        Map<String, Object> parameterMap = new HashMap<String, Object>();
        parameterMap.put(GraphDACParams.graphId.name(), graphId);
        parameterMap.put(GraphDACParams.node.name(), node);
        parameterMap.put(GraphDACParams.request.name(), request);
        NodeQueryGenerationUtil.generateUpsertNodeCypherQuery(parameterMap);
        Map<String, Object> queryMap = (Map<String, Object>) parameterMap.get(GraphDACParams.queryStatementMap.name());
        Map<String, Object> entry = (Map<String, Object>) queryMap.entrySet().stream().findFirst().get().getValue();


        try(Session session = driver.session()) {
            CompletionStage<Node> cs = session.writeTransactionAsync(tx -> {
                String statement = StringUtils.removeEnd((String) entry.get(GraphDACParams.query.name()), CypherQueryConfigurationConstants.COMMA);
                Map<String, Object> statementParams = (Map<String, Object>) entry.get(GraphDACParams.paramValueMap.name());
                return tx.runAsync(statement, statementParams);
            })
            .thenCompose(fn -> fn.singleAsync())
            .thenApply(record -> {
                org.neo4j.driver.v1.types.Node neo4JNode = record.get(DEFAULT_CYPHER_NODE_OBJECT).asNode();
                String versionKey = (String) neo4JNode.get(GraphDACParams.versionKey.name()).asString();
                String identifier = (String) neo4JNode.get(SystemProperties.IL_UNIQUE_ID.name()).asString();
                node.setGraphId(graphId);
                node.setIdentifier(identifier);
                if (StringUtils.isNotBlank(versionKey))
                    node.getMetadata().put(GraphDACParams.versionKey.name(), versionKey);
                return node;
            });
            return FutureConverters.toScala(cs);
        } catch (Exception e) {
            if (!(e instanceof MiddlewareException)) {
                throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
                        DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage());
            } else {
                throw e;
            }
        }
    }


    private static void setRequestContextToNode(Node node, Request request) {
        if (null != request && null != request.getContext()) {
            String channel = (String) request.getContext().get(GraphDACParams.CHANNEL_ID.name());
            TelemetryManager.log("Channel from request: " + channel + " for content: " + node.getIdentifier());
            if (StringUtils.isNotBlank(channel))
                node.getMetadata().put(GraphDACParams.channel.name(), channel);

            String consumerId = (String) request.getContext().get(GraphDACParams.CONSUMER_ID.name());
            TelemetryManager.log("ConsumerId from request: " + consumerId + " for content: " + node.getIdentifier());
            if (StringUtils.isNotBlank(consumerId))
                node.getMetadata().put(GraphDACParams.consumerId.name(), consumerId);

            String appId = (String) request.getContext().get(GraphDACParams.APP_ID.name());
            TelemetryManager.log("App Id from request: " + appId + " for content: " + node.getIdentifier());
            if (StringUtils.isNotBlank(appId))
                node.getMetadata().put(GraphDACParams.appId.name(), appId);
        }
    }

    private static void validateAuthorization(String graphId, Node node, Request request) {
        if (StringUtils.isBlank(graphId))
            throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                    DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Invalid or 'null' Graph Id.]");
        if (null == node)
            throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name(),
                    DACErrorMessageConstants.INVALID_NODE + " | [Invalid or 'null' Node.]");
        if (null == request)
            throw new ClientException(DACErrorCodeConstants.INVALID_REQUEST.name(),
                    DACErrorMessageConstants.INVALID_REQUEST + " | [Invalid or 'null' Request Object.]");
    }
}
