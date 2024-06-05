package org.sunbird.janus.service.util

import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.service.common.CypherQueryConfigurationConstants
import org.yaml.snakeyaml.nodes.NodeId

import java.util

object EdgeUtil {

  def getSubGraphQuery(graphId: String, nodeId: String, depth: Integer): String = {

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

  def deleteRelationsQuery(graphId:String, startNodeId:String, endNodeId:String): String = {
    val sb = new StringBuilder

    sb.append("g.V().hasLabel('").append(graphId).append("')")
      .append(".has('").append(SystemProperties.IL_UNIQUE_ID).append("', '").append(startNodeId).append("')")
      .append(".outE().as(").append(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT)
      .append(".inV().has('").append(SystemProperties.IL_UNIQUE_ID.name).append("', '").append(endNodeId).append("')")
      .append(".select(").append(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT).append(").drop().iterate()")

    sb.toString()
  }

  def createRelationsQuery(startNode: AnyRef, endNode:AnyRef, relation: String, relMetadata: util.Map[AnyRef, AnyRef]): String = {
    val sb = new StringBuilder

    sb.append("g.V("+startNode+").as('a').V("+endNode+").as('b').addE('"+relation+"').from('a').to('b')")
    relMetadata.forEach((key, value) => {
      sb.append(".property('" + key + "',"  + value + ")")
    })

    sb.toString()
  }


}
