package org.sunbird.janus.service.operation

import org.apache.commons.lang3.{BooleanUtils, StringUtils}
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.valueMap
import org.janusgraph.core.JanusGraph
import org.sunbird.common.exception.ClientException
import org.sunbird.common.{DateUtils, JsonUtils}
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.common.enums.{AuditProperties, GraphDACParams, SystemProperties}
import org.sunbird.graph.dac.model.{Node, Vertex}
import org.sunbird.graph.service.common.{DACErrorCodeConstants, DACErrorMessageConstants}
import org.sunbird.janus.service.util.JanusConnectionUtil
import org.sunbird.telemetry.logger.TelemetryManager

import java.util
import scala.collection.convert.ImplicitConversions.`map AsScala`
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GremlinOperations {

  val graphConnection = new JanusConnectionUtil
  def addNode(graphId: String, node: Vertex): Future[Vertex] = { Future {
    if (StringUtils.isBlank(graphId))
      throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
            DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Node Operation Failed.]")

    if (null == node)
      throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name,
            DACErrorMessageConstants.INVALID_NODE + " | [Create Node Operation Failed.]")

    val parameterMap = new util.HashMap[String, AnyRef]
    parameterMap.put(GraphDACParams.graphId.name, graphId)
    parameterMap.put(GraphDACParams.node.name, setPrimitiveData(node.getMetadata))
    prepareMap(parameterMap)

    graphConnection.initialiseGraphClient()
    val g: GraphTraversalSource = graphConnection.getGts
    val graph: JanusGraph = graphConnection.getGraph

    val vertex = g.addV(node.getGraphId)
    val finalMap = parameterMap.getOrDefault(GraphDACParams.paramValueMap.name, new util.HashMap[String, AnyRef]).asInstanceOf[util.Map[String, AnyRef]]

    finalMap.foreach { case (key, value) => vertex.property(key, value) }
    vertex.as("ee").next()

    val retrieveVertex = g.V().select("ee").by(valueMap()).next()
    println(" vertex details found !" + retrieveVertex)

    node.setGraphId("domain")
    node.setIdentifier("do_12332409i")
    node.getMetadata.put(GraphDACParams.versionKey.name, "1023535325")

    node
  }

  }

  def prepareMap(parameterMap: util.Map[String, AnyRef]) = {
    if(null != parameterMap){
      val graphId = parameterMap.getOrDefault("graphId","").asInstanceOf[String]
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Create Node' Query Generation Failed.]")

      val node: Node = parameterMap.get(GraphDACParams.node.name).asInstanceOf[Node]
      if (null == node)
        throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name,
          DACErrorMessageConstants.INVALID_NODE + " | [Create Node Query Generation Failed.]")

      val date: String = DateUtils.formatCurrentDate

      val mpMap :util.Map[String, AnyRef] = getMetadataCypherQueryMap(node)
      val spMap :util.Map[String, AnyRef] = getSystemPropertyMap(node, date)
      val apMap :util.Map[String, AnyRef] = getAuditPropertyMap(node, date, false)
      val vpMap :util.Map[String, AnyRef] = getVersionPropertyMap(node, date)

      parameterMap.put(GraphDACParams.paramValueMap.name, mpMap)
      parameterMap.put(GraphDACParams.paramValueMap.name, spMap)
      parameterMap.put(GraphDACParams.paramValueMap.name, apMap)
      parameterMap.put(GraphDACParams.paramValueMap.name, vpMap)

      println("parameterMap  ->"+parameterMap)
    }
  }

  def getMetadataCypherQueryMap(node: Node): util.Map[String, AnyRef] = {
    val metadataPropertyMap = new util.HashMap[String, AnyRef]
    if (null != node && null != node.getMetadata && !node.getMetadata.isEmpty) {
      node.getMetadata.foreach { case (key, value) => metadataPropertyMap.put(key, value) }
    }
    metadataPropertyMap
  }
  def getSystemPropertyMap(node: Node, date: String): util.Map[String, AnyRef] = {
    val systemPropertyMap = new util.HashMap[String, AnyRef]
    if (null != node && StringUtils.isNotBlank(date)) {
      if (StringUtils.isBlank(node.getIdentifier))
        node.setIdentifier(Identifier.getIdentifier(node.getGraphId, Identifier.getUniqueIdFromTimestamp))
      systemPropertyMap.put(SystemProperties.IL_UNIQUE_ID.name, node.getIdentifier)
      systemPropertyMap.put(SystemProperties.IL_SYS_NODE_TYPE.name, node.getNodeType)
      systemPropertyMap.put(SystemProperties.IL_FUNC_OBJECT_TYPE.name, node.getObjectType)
    }
    systemPropertyMap
  }

  def getAuditPropertyMap(node: Node, date: String, isUpdateOnly: Boolean):util.Map[String, AnyRef] = {
    val auditPropertyMap = new util.HashMap[String, AnyRef]
    if(null != node && StringUtils.isNotBlank(date)) {
      if (BooleanUtils.isFalse(isUpdateOnly)) {
        auditPropertyMap.put(AuditProperties.createdOn.name,
          if (node.getMetadata.containsKey(AuditProperties.createdOn.name))
            node.getMetadata.get(AuditProperties.createdOn.name)
          else date)
      }
      if (null != node.getMetadata && null == node.getMetadata.get(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name))
        auditPropertyMap.put(AuditProperties.lastUpdatedOn.name, date)
    }
    auditPropertyMap
  }

  def getVersionPropertyMap(node: Node, date: String): util.Map[String, AnyRef] = {
    val versionPropertyMap = new util.HashMap[String, AnyRef]
    if (null != node && StringUtils.isNotBlank(date))
      versionPropertyMap.put(GraphDACParams.versionKey.name, DateUtils.parse(date).getTime.toString)
    versionPropertyMap
  }

  def setPrimitiveData(metadata: util.Map[String, AnyRef]): util.Map[String, AnyRef] = {
    metadata.forEach((key, value) => {
      try {
        value match {
          case v: util.Map[String, AnyRef] => metadata.put(key, JsonUtils.serialize(v))
          case v: util.List[util.Map[String, AnyRef]] if (!v.isEmpty && v.isInstanceOf[util.Map[String, AnyRef]]) => metadata.put(key, JsonUtils.serialize(v))
          case _ =>
        }
      } catch {
        case e: Exception => TelemetryManager.error(s"Exception Occurred While Processing Primitive Data Types | Exception is : ${e.getMessage}", e)
      }
    })
    metadata
  }


}
