package org.sunbird.janus.service.util

import org.apache.commons.lang3.StringUtils
import org.apache.tinkerpop.gremlin.driver.{Result, ResultSet}
import org.apache.tinkerpop.gremlin.structure.Edge
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.graph.common.enums.{GraphDACParams, SystemProperties}
import org.sunbird.graph.service.common.{CypherQueryConfigurationConstants, DACErrorCodeConstants, DACErrorMessageConstants, GraphOperation}

import java.util
import scala.collection.JavaConversions._

object SearchUtil {

  def getVertexByUniqueIdQuery(graphId: String, nodeId: String): String = {
    val sb = new StringBuilder
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

    sb.toString()
  }

  def getNodePropertyQuery(graphId: String, nodeId: String, key: String): String = {
    val sb = new StringBuilder
    sb.append("g.V().hasLabel('" + graphId + "').has('" + SystemProperties.IL_UNIQUE_ID.name + "', '" + nodeId + "').values("+key+")")
    sb.toString()
  }

  def checkCyclicLoopQuery(graphId: String, startNodeId: String, endNodeId: String, relationType: String): String = {
    val sb = new StringBuilder
    sb.append("g.V().hasLabel('" + graphId + "').has('" + SystemProperties.IL_UNIQUE_ID.name + "', '" + startNodeId + "')")
    sb.append(".repeat(outE('" + relationType + "').inV().simplePath()).until(has('"+ SystemProperties.IL_UNIQUE_ID.name + "', '" + endNodeId + "'))")
    sb.append(".hasLabel('"+graphId+"')")

    sb.toString()
  }

}
