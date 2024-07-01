package org.sunbird.graph.service.util

import org.apache.commons.lang3.StringUtils
import org.apache.tinkerpop.gremlin.driver.{Result, ResultSet}
import org.apache.tinkerpop.gremlin.structure.{Edge, Vertex}
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.graph.common.enums.{GraphDACParams, SystemProperties}
import org.sunbird.graph.dac.model.SearchCriteria
import org.sunbird.graph.service.common.{CypherQueryConfigurationConstants, DACErrorCodeConstants, DACErrorMessageConstants, GraphOperation}

import java.util
import scala.collection.JavaConversions._

object SearchUtil {

  def getVertexByUniqueIdQuery(parameterMap: util.Map[String, AnyRef]): String = {
    val sb = new StringBuilder
    if (null != parameterMap) {
      val graphId = parameterMap.getOrDefault(GraphDACParams.graphId.name, "").asInstanceOf[String]
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Node By Id' Query Generation Failed.]")

      val nodeId = parameterMap.get(GraphDACParams.nodeId.name).asInstanceOf[String]
      if (StringUtils.isBlank(nodeId))
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
          DACErrorMessageConstants.INVALID_IDENTIFIER + " | ['Get Node By Unique Id' Query Generation Failed.]")
      sb.append("g.V().hasLabel('").append(graphId).append("').has('")
        .append(SystemProperties.IL_UNIQUE_ID).append("', '").append(nodeId).append("').as('" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT + "')")
        .append(".flatMap(")
        .append("coalesce(")
        .append("union(")
        .append("outE().dedup().as('" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT + "').inV().dedup()")
        .append(".as('" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_END_NODE_OBJECT + "')")
        .append(".select('" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT + "', '" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT + "'," +
          " '" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_END_NODE_OBJECT + "'),")
        .append("inE().dedup().as('" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT + "').outV().dedup()")
        .append(".as('" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_START_NODE_OBJECT + "')")
        .append(".select('" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT + "', '" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT + "'," +
          " '" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_START_NODE_OBJECT + "')")
        .append("),")
        .append("project('" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT + "', " +
          "'" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT + "', '" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_START_NODE_OBJECT + "', " +
          "'" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_END_NODE_OBJECT + "')")
        .append(".by(select('" + CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT + "'))")
        .append(".by(constant(null))")
        .append(".by(constant(null))")
        .append(".by(constant(null))")
        .append("))")
        .append(".dedup().toList()")
    }
    sb.toString()
  }

  def getNodePropertyQuery(parameterMap: util.Map[String, AnyRef]): String = {
    val sb = new StringBuilder
    if (null != parameterMap) {
      val graphId = parameterMap.get(GraphDACParams.graphId.name).asInstanceOf[String]
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Node Property' Query Generation Failed.]")

      val nodeId = parameterMap.get(GraphDACParams.nodeId.name).asInstanceOf[String]
      if (StringUtils.isBlank(nodeId))
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
          DACErrorMessageConstants.INVALID_IDENTIFIER + " | ['Get Node Property' Query Generation Failed.]")

      val key = parameterMap.get(GraphDACParams.key.name).asInstanceOf[String]
      if (StringUtils.isBlank(key))
        throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name,
          DACErrorMessageConstants.INVALID_PROPERTY_KEY + " | ['Get Node Property' Query Generation Failed.]")

      sb.append("g.V().hasLabel('" + graphId + "').has('" + SystemProperties.IL_UNIQUE_ID.name + "', '" + nodeId + "').elementMap('" + key + "')")
    }
    sb.toString()
  }

  def generateCheckCyclicLoopQuery(parameterMap: util.Map[String, AnyRef]): String = {
    val query = new StringBuilder
    if (null != parameterMap){
      val graphId = parameterMap.get(GraphDACParams.graphId.name).asInstanceOf[String]
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Check Cyclic Loop' Query Generation Failed.]")

      val startNodeId = parameterMap.get(GraphDACParams.startNodeId.name).asInstanceOf[String]
      if (StringUtils.isBlank(startNodeId))
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
          DACErrorMessageConstants.INVALID_START_NODE_ID + " | ['Check Cyclic Loop' Query Generation Failed.]")

      val relationType = parameterMap.get(GraphDACParams.relationType.name).asInstanceOf[String]
      if (StringUtils.isBlank(relationType))
        throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name,
          DACErrorMessageConstants.INVALID_RELATION_TYPE + " | ['Check Cyclic Loop' Query Generation Failed.]")

      val endNodeId = parameterMap.get(GraphDACParams.endNodeId.name).asInstanceOf[String]
      if (StringUtils.isBlank(endNodeId))
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
          DACErrorMessageConstants.INVALID_END_NODE_ID + " | ['Check Cyclic Loop' Query Generation Failed.]")

      query.append("g.V().hasLabel('" + graphId + "').has('" + SystemProperties.IL_UNIQUE_ID.name + "', '" + startNodeId + "')")
      query.append(".repeat(outE('" + relationType + "').inV().simplePath()).until(has('" + SystemProperties.IL_UNIQUE_ID.name + "', '" + endNodeId + "'))")
      query.append(".hasLabel('" + graphId + "')")
    }
    query.toString()
  }


  def generateGetNodeByUniqueIdsQuery(graphId: String, searchCriteria: SearchCriteria)= {
    val sb = new StringBuilder
    sb.append("g.V().hasLabel('" + graphId + "')")
      .append(searchCriteria.getJanusQuery.replace("AND", ""))
    sb.toString()
  }
}
