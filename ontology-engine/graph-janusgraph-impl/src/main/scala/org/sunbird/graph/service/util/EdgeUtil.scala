package org.sunbird.graph.service.util

import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.service.common.CypherQueryConfigurationConstants
import org.sunbird.graph.util.ScalaJsonUtil

import java.util
import scala.collection.JavaConverters.asScalaBufferConverter

object EdgeUtil {

  def generateSubGraphCypherQuery(graphId: String, nodeId: String, depth: Integer): String = {
    val sb = new StringBuilder
    sb.append("g.V().hasLabel('")
      .append(graphId).append("').has('")
      .append(SystemProperties.IL_UNIQUE_ID).append("', '").append(nodeId).append("')")
      .append(".as('n')")
      .append(".emit().repeat(outE().inV().simplePath()).times(" + depth + ").as('m')")
      .append(".outE().as('r2').inV().as('l')")
      .append(".select('n', 'm', 'r2', 'l')")
      .append(".dedup()")
      .append(".project('relationName', 'relationMetadata', 'startNode', 'endNode')")
      .append(".by(__.select('r2').label())")
      .append(".by(__.select('r2').elementMap())")
      .append(".by(__.select('m'))")
      .append(".by(__.select('l'))")
      .append(".dedup('startNode', 'endNode')")
      .append(".toList()")

    sb.toString()
  }

  def generateDeleteRelationsQuery(graphId:String, edgeData: util.List[util.Map[String, AnyRef]]): String = {
    val sb = new StringBuilder
    for (row <- edgeData.asScala) {
      val startNodeId = row.get("startNodeId").toString
      val endNodeId = row.get("endNodeId").toString

      sb.append("g.V().hasLabel('").append(graphId).append("')")
        .append(".has('").append(SystemProperties.IL_UNIQUE_ID).append("', '").append(startNodeId).append("')")
        .append(".outE().as(").append(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT)
        .append(".inV().has('").append(SystemProperties.IL_UNIQUE_ID.name).append("', '").append(endNodeId).append("')")
        .append(".select(").append(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT).append(").drop().iterate()")

    }
    sb.toString()
  }

  def generateCreateBulkRelationsQuery(graphId: String, edgeData: util.List[util.Map[String, AnyRef]]): List[String] = {
    edgeData.asScala.distinct.map { row =>
      val startNodeId = row.get("startNodeId").toString
      val endNodeId = row.get("endNodeId").toString
      val relation = row.get("relation").toString
      val relMetadata = row.get("relMetadata").asInstanceOf[util.Map[AnyRef, AnyRef]]

      val sb = new StringBuilder
      sb.append(s"g.V().hasLabel('$graphId').has('${SystemProperties.IL_UNIQUE_ID.name}', '$startNodeId').as('a')")
      sb.append(s".V().hasLabel('$graphId').has('${SystemProperties.IL_UNIQUE_ID.name}', '$endNodeId').as('b')")
      sb.append(s".addE('$relation').from('a').to('b')")

      relMetadata.forEach { (key, value) =>
        value match {
          case list: util.List[_] => sb.append(s".property('$key', ${ScalaJsonUtil.serialize(list)})")
          case _ => sb.append(s".property('$key', '$value')")
        }
      }

      sb.toString()
    }.toList
  }

}
