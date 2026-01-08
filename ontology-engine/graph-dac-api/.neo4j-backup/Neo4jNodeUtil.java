package org.sunbird.graph.dac.util;

import org.apache.commons.lang3.StringUtils;
import org.neo4j.driver.v1.Value;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Relationship;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.enums.GraphDACErrorCodes;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.Relation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Neo4jNodeUtil {

    /**
     *
     * @param graphId
     * @param neo4jNode
     * @return
     */
    public static Node getNode(String graphId, org.neo4j.graphdb.Node neo4jNode) {
        if (null == neo4jNode)
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_NODE.name(),
                    "Failed to create node object. Node from database is null.");
        Node node = new Node();
        node.setId(neo4jNode.getId());

        Map<String, Object> metadata = new HashMap<String, Object>();
        Iterable<String> keys = neo4jNode.getPropertyKeys();
        if (null != keys && null != keys.iterator()) {
            for (String key : keys) {
                if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_UNIQUE_ID.name()))
                    node.setIdentifier(neo4jNode.getProperty(key).toString());
                else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_SYS_NODE_TYPE.name()))
                    node.setNodeType(neo4jNode.getProperty(key).toString());
                else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_FUNC_OBJECT_TYPE.name()))
                    node.setObjectType(neo4jNode.getProperty(key).toString());
                else
                    metadata.put(key, neo4jNode.getProperty(key));
            }
        }
        node.setMetadata(metadata);

        Iterable<Relationship> outRels = neo4jNode.getRelationships(Direction.OUTGOING);
        if (null != outRels && null != outRels.iterator()) {
            List<Relation> outRelations = new ArrayList<Relation>();
            for (Relationship outRel : outRels)
                outRelations.add(new Relation(graphId, outRel));
            node.setOutRelations(outRelations);
        }

        Iterable<Relationship> inRels = neo4jNode.getRelationships(Direction.INCOMING);
        if (null != inRels && null != inRels.iterator()) {
            List<Relation> inRelations = new ArrayList<Relation>();
            for (Relationship inRel : inRels) {
                inRelations.add(new Relation(graphId, inRel));
            }
        }
        return node;
    }

    /**
     *
     * @param graphId
     * @param neo4jNode
     * @param relationMap
     * @param startNodeMap
     * @param endNodeMap
     * @return
     */
    public static Node getNode(String graphId, org.neo4j.driver.v1.types.Node neo4jNode, Map<Long, Object> relationMap,
                               Map<Long, Object> startNodeMap, Map<Long, Object> endNodeMap) {

        if (null == neo4jNode)
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_NODE.name(),
                    "Failed to create node object. Node from database is null.");

        Node node = new Node();
        node.setGraphId(graphId);
        node.setId(neo4jNode.id());
        Iterable<String> keys = neo4jNode.keys();
        if (null != keys && null != keys.iterator()) {
            Map<String, Object> metadata = new HashMap<String, Object>();
            for (String key : keys) {
                if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_UNIQUE_ID.name()))
                    node.setIdentifier(neo4jNode.get(key).asString());
                else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_SYS_NODE_TYPE.name()))
                    node.setNodeType(neo4jNode.get(key).asString());
                else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_FUNC_OBJECT_TYPE.name()))
                    node.setObjectType(neo4jNode.get(key).asString());
                else {
                    Value value = neo4jNode.get(key);
                    if (null != value) {
                        if (StringUtils.startsWithIgnoreCase(value.type().name(), "LIST")) {
                            List<Object> list = value.asList();
                            if (null != list && list.size() > 0) {
                                Object obj = list.get(0);
                                if (obj instanceof String) {
                                    metadata.put(key, list.toArray(new String[0]));
                                } else if (obj instanceof Number) {
                                    metadata.put(key, list.toArray(new Number[0]));
                                } else if (obj instanceof Boolean) {
                                    metadata.put(key, list.toArray(new Boolean[0]));
                                } else {
                                    metadata.put(key, list.toArray(new Object[0]));
                                }
                            }
                        } else
                            metadata.put(key, value.asObject());
                    }
                }
            }
            node.setMetadata(metadata);
        }

        if (null != relationMap && !relationMap.isEmpty() && null != startNodeMap && !startNodeMap.isEmpty()
                && null != endNodeMap && !endNodeMap.isEmpty()) {
            List<Relation> inRelations = new ArrayList<Relation>();
            List<Relation> outRelations = new ArrayList<Relation>();

            for (Map.Entry<Long, Object> entry : relationMap.entrySet()) {
                org.neo4j.driver.v1.types.Relationship relationship = (org.neo4j.driver.v1.types.Relationship) entry
                        .getValue();
                if (relationship.startNodeId() == neo4jNode.id()) {
                    outRelations.add(new Relation(graphId, relationship, startNodeMap, endNodeMap));
                }
                if (relationship.endNodeId() == neo4jNode.id()) {
                    inRelations.add(new Relation(graphId, relationship, startNodeMap, endNodeMap));
                }
            }
            node.setInRelations(inRelations);
            node.setOutRelations(outRelations);
        }

        return node;
    }
}
