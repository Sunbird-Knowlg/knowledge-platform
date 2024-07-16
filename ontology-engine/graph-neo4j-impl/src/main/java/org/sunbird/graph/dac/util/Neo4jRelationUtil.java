package org.sunbird.graph.dac.util;

import org.apache.commons.lang3.StringUtils;
import org.neo4j.driver.v1.Value;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.enums.GraphDACErrorCodes;
import org.sunbird.graph.dac.model.Relation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Neo4jRelationUtil {


    public static Relation getRelation(String graphId, Relationship neo4jRel) {
        if (null == neo4jRel)
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_REL.name(),
                    "Failed to create relation object. Relation from database is null.");
        Relation relation = new Relation();
        relation.setGraphId(graphId);

        Node startNode = neo4jRel.getStartNode();
        Node endNode = neo4jRel.getEndNode();

        relation.setStartNodeId((String) startNode.getProperty(SystemProperties.IL_UNIQUE_ID.name()));
        relation.setEndNodeId((String) endNode.getProperty(SystemProperties.IL_UNIQUE_ID.name()));

        relation.setStartNodeName(getName(startNode));
        relation.setEndNodeName(getName(endNode));
        relation.setStartNodeType(getNodeType(startNode));
        relation.setEndNodeType(getNodeType(endNode));
        relation.setStartNodeObjectType(getObjectType(startNode));
        relation.setEndNodeObjectType(getObjectType(endNode));
        relation.setRelationType(neo4jRel.getType().name());
        relation.setStartNodeMetadata(getNodeMetadata(neo4jRel.getStartNode()));
        relation.setEndNodeMetadata(getNodeMetadata(neo4jRel.getEndNode()));

        HashMap<String, Object> metadata = new HashMap<String, Object>();
        Iterable<String> keys = neo4jRel.getPropertyKeys();
        if (null != keys && null != keys.iterator()) {
            for (String key : keys) {
                metadata.put(key, neo4jRel.getProperty(key));
            }
        }
        relation.setMetadata(metadata);
        return relation;
    }

    public static Relation getRelation(String graphId, org.neo4j.driver.v1.types.Relationship relationship, Map<Long, Object> startNodeMap,
                                       Map<Long, Object> endNodeMap) {
        if (null == relationship)
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_REL.name(),
                    "Failed to create relation object. Relation from database is null.");
        Relation relation = new Relation();
        relation.setId(relationship.id());
        relation.setGraphId(graphId);

        org.neo4j.driver.v1.types.Node startNode = (org.neo4j.driver.v1.types.Node) startNodeMap
                .get(relationship.startNodeId());
        org.neo4j.driver.v1.types.Node endNode = (org.neo4j.driver.v1.types.Node) endNodeMap
                .get(relationship.endNodeId());

        relation.setStartNodeId(startNode.get(SystemProperties.IL_UNIQUE_ID.name()).asString());
        relation.setEndNodeId(endNode.get(SystemProperties.IL_UNIQUE_ID.name()).asString());
        relation.setStartNodeName(getName(startNode));
        relation.setEndNodeName(getName(endNode));
        relation.setStartNodeType(getNodeType(startNode));
        relation.setEndNodeType(getNodeType(endNode));
        relation.setStartNodeObjectType(getObjectType(startNode));
        relation.setEndNodeObjectType(getObjectType(endNode));
        relation.setRelationType(relationship.type());
        relation.setStartNodeMetadata(getNodeMetadata(startNode));
        relation.setEndNodeMetadata(getNodeMetadata(endNode));
        HashMap<String, Object> metadata = new HashMap<String, Object>();
        Iterable<String> keys = relationship.keys();
        if (null != keys) {
            keys.iterator();
            for (String key : keys) {
                Value value = relationship.get(key);
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
        relation.setMetadata(metadata);
        return relation;
    }

    public static String getName(Node node) {
        String name = (String) node.getProperty("name", null);
        if (StringUtils.isBlank(name)) {
            name = (String) node.getProperty("title", null);
            if (StringUtils.isBlank(name)) {
                name = (String) node.getProperty(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), null);
                if (StringUtils.isBlank(name))
                    name = (String) node.getProperty(SystemProperties.IL_SYS_NODE_TYPE.name(), null);
            }
        }
        return name;
    }

    public static String getName(org.neo4j.driver.v1.types.Node node) {
        String name = node.get("name").asString();
        if (StringUtils.isBlank(name) || StringUtils.equalsIgnoreCase("null", name)) {
            name = node.get("title").asString();
            if (StringUtils.isBlank(name) || StringUtils.equalsIgnoreCase("null", name)) {
                name = node.get(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).asString();
                if (StringUtils.isBlank(name) || StringUtils.equalsIgnoreCase("null", name))
                    name = node.get(SystemProperties.IL_SYS_NODE_TYPE.name()).asString();
            }
        }
        return name;
    }

    public static String getNodeType(org.neo4j.driver.v1.types.Node node) {
        return node.get(SystemProperties.IL_SYS_NODE_TYPE.name()).asString();
    }

    public static String getObjectType(org.neo4j.driver.v1.types.Node node) {
        return node.get(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).asString();
    }

    public static String getNodeType(Node node) {
        return (String) node.getProperty(SystemProperties.IL_SYS_NODE_TYPE.name(), null);
    }

    public static String getObjectType(Node node) {
        return (String) node.getProperty(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), null);
    }

    public static Map<String, Object> getNodeMetadata(org.neo4j.driver.v1.types.Node node) {
        Map<String, Object> metadata = new HashMap<String, Object>();
        if (null != node) {
            Iterable<String> keys = node.keys();
            if (null != keys) {
                for (String key : keys) {
                    Value value = node.get(key);
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
        }
        return metadata;
    }

    public static Map<String, Object> getNodeMetadata(Node node) {
        Map<String, Object> metadata = new HashMap<String, Object>();
        if (null != node) {
            Iterable<String> keys = node.getPropertyKeys();
            if (null != keys) {
                for (String key : keys) {
                    metadata.put(key, node.getProperty(key));
                }
            }
        }
        return metadata;
    }

}

