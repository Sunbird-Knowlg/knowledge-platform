package org.sunbird.janus.service.operation

import org.apache.commons.lang3.{BooleanUtils, StringUtils}
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.{GraphTraversal, GraphTraversalSource}
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.common.{DateUtils, JsonUtils}
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.common.enums.{AuditProperties, GraphDACParams, SystemProperties}
import org.sunbird.graph.dac.model.Vertex
import org.sunbird.graph.service.common.{DACErrorCodeConstants, DACErrorMessageConstants}
import org.sunbird.janus.service.util.JanusConnectionUtil
import org.sunbird.telemetry.logger.TelemetryManager

import java.util
import scala.collection.convert.ImplicitConversions.`map AsScala`
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VertexOperations {

  val graphConnection = new JanusConnectionUtil
  def addVertex(graphId: String, vertex: Vertex): Future[Vertex] = {
    Future {
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
              DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Node Operation Failed.]")

      if (null == vertex)
        throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name,
              DACErrorMessageConstants.INVALID_NODE + " | [Create Node Operation Failed.]")

      val parameterMap = new util.HashMap[String, AnyRef]
      parameterMap.put(GraphDACParams.graphId.name, graphId)
      parameterMap.put(GraphDACParams.vertex.name, setPrimitiveData(vertex))

      try {
          graphConnection.initialiseGraphClient()
          val g: GraphTraversalSource = graphConnection.getGraphTraversalSource

          val addedVertex = createVertexTraversal(parameterMap, g)
          val vertexElementMap = addedVertex.elementMap().next()

          vertex.setGraphId(graphId)
          vertex.setIdentifier(vertexElementMap.get(SystemProperties.IL_UNIQUE_ID.name))
          vertex.getMetadata.put(GraphDACParams.versionKey.name, vertexElementMap.get(GraphDACParams.versionKey.name))
          vertex
      }
      catch {
          case e: Throwable =>
            e.getCause match {
              case cause: org.apache.tinkerpop.gremlin.driver.exception.ResponseException =>
                throw new ClientException(
                  DACErrorCodeConstants.CONSTRAINT_VALIDATION_FAILED.name(),
                  DACErrorMessageConstants.CONSTRAINT_VALIDATION_FAILED + vertex.getIdentifier
                )
              case cause =>
                throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name, DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
            }
      }
    }
  }

  def deleteVertex(graphId: String, vertexId: String, request: Request): Future[java.lang.Boolean] = {
    Future {
      if (StringUtils.isBlank(graphId))
          throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
              DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Remove Property Values Operation Failed.]")

      if (StringUtils.isBlank(vertexId))
          throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
              DACErrorMessageConstants.INVALID_IDENTIFIER + " | [Remove Property Values Operation Failed.]")

      try {
          graphConnection.initialiseGraphClient()
          val g: GraphTraversalSource = graphConnection.getGraphTraversalSource

          val parameterMap = new util.HashMap[String, AnyRef]
          parameterMap.put(GraphDACParams.graphId.name, graphId)
          parameterMap.put(GraphDACParams.nodeId.name, vertexId)
          parameterMap.put(GraphDACParams.request.name, request)

          deleteQuery(parameterMap, g)

          true
      }
      catch {
        case e: Exception => throw e
      }

    }

  }

  def deleteQuery(parameterMap: util.Map[String, AnyRef], g: GraphTraversalSource): Unit = {

  }

  private def createVertexTraversal(parameterMap: util.Map[String, AnyRef], graphTraversalSource: GraphTraversalSource): GraphTraversal[org.apache.tinkerpop.gremlin.structure.Vertex, org.apache.tinkerpop.gremlin.structure.Vertex] = {
    if (null != parameterMap) {
      val graphId = parameterMap.getOrDefault(GraphDACParams.graphId.name,"").asInstanceOf[String]
      val vertex = parameterMap.getOrDefault(GraphDACParams.vertex.name, null).asInstanceOf[Vertex]

      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Create Node' Query Generation Failed.]")

      if (null == vertex)
        throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name,
          DACErrorMessageConstants.INVALID_NODE + " | [Create Node Query Generation Failed.]")

      val date: String = DateUtils.formatCurrentDate

      val mpMap :util.Map[String, AnyRef] = getMetadataCypherQueryMap(vertex)
      val spMap :util.Map[String, AnyRef] = getSystemPropertyMap(vertex, date)
      val apMap :util.Map[String, AnyRef] = getAuditPropertyMap(vertex, date, false)
      val vpMap :util.Map[String, AnyRef] = getVersionPropertyMap(vertex, date)

      val combinedMap: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
      combinedMap.putAll(mpMap)
      combinedMap.putAll(spMap)
      combinedMap.putAll(apMap)
      combinedMap.putAll(vpMap)

      parameterMap.put(GraphDACParams.paramValueMap.name, combinedMap)

      val newVertexTraversal = graphTraversalSource.addV(vertex.getGraphId)
      val finalMap = parameterMap.getOrDefault(GraphDACParams.paramValueMap.name, new util.HashMap[String, AnyRef]).asInstanceOf[util.Map[String, AnyRef]]

      finalMap.foreach { case (key, value) => newVertexTraversal.property(key, value) }

      newVertexTraversal
    }
    else {
      throw new ClientException(DACErrorCodeConstants.INVALID_PARAMETER.name, DACErrorMessageConstants.INVALID_PARAM_MAP )
    }
  }

  def getMetadataCypherQueryMap(node: Vertex): util.Map[String, AnyRef] = {
    val metadataPropertyMap = new util.HashMap[String, AnyRef]
    if (null != node && null != node.getMetadata && !node.getMetadata.isEmpty) {
      node.getMetadata.foreach { case (key, value) => metadataPropertyMap.put(key, value) }
    }
    metadataPropertyMap
  }

  def getSystemPropertyMap(node: Vertex, date: String): util.Map[String, AnyRef] = {
    val systemPropertyMap = new util.HashMap[String, AnyRef]
    if (null != node && StringUtils.isNotBlank(date)) {
      if (StringUtils.isBlank(node.getIdentifier))
        node.setIdentifier(Identifier.getIdentifier(node.getGraphId, Identifier.getUniqueIdFromTimestamp))
      systemPropertyMap.put(SystemProperties.IL_UNIQUE_ID.name, node.getIdentifier)
      systemPropertyMap.put(SystemProperties.IL_SYS_NODE_TYPE.name, node.getVertexType)
      systemPropertyMap.put(SystemProperties.IL_FUNC_OBJECT_TYPE.name, node.getObjectType)
    }
    systemPropertyMap
  }

  def getAuditPropertyMap(node: Vertex, date: String, isUpdateOnly: Boolean):util.Map[String, AnyRef] = {
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

  def getVersionPropertyMap(node: Vertex, date: String): util.Map[String, AnyRef] = {
    val versionPropertyMap = new util.HashMap[String, AnyRef]
    if (null != node && StringUtils.isNotBlank(date))
      versionPropertyMap.put(GraphDACParams.versionKey.name, DateUtils.parse(date).getTime.toString)
    versionPropertyMap
  }

  def setPrimitiveData(vertex: Vertex): Vertex = {
    val metadata: util.Map[String, AnyRef] = vertex.getMetadata
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
    vertex
  }


}
