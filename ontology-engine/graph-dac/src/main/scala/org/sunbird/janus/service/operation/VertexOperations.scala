package org.sunbird.janus.service.operation

import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.{BooleanUtils, StringUtils}
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.{GraphTraversal, GraphTraversalSource}
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ServerException}
import org.sunbird.common.{DateUtils, JsonUtils}
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.common.enums.{AuditProperties, GraphDACParams, SystemProperties}
import org.sunbird.graph.dac.enums.SystemNodeTypes
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.service.common.{DACErrorCodeConstants, DACErrorMessageConstants, GraphOperation}
import org.sunbird.janus.dac.transaction.TransactionLog
import org.sunbird.janus.service.util.{DriverUtil, VertexUtil, JanusConnectionUtil}
import org.sunbird.telemetry.logger.TelemetryManager

import java.util
import scala.collection.convert.ImplicitConversions.{`collection AsScalaIterable`, `map AsJavaMap`, `map AsScala`}
import scala.collection.immutable.HashMap
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VertexOperations {

  val graphConnection = new JanusConnectionUtil
  def addVertex(graphId: String, node: Node): Future[Node] = {
    Future {
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
              DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Node Operation Failed.]")

      if (null == node)
        throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name,
              DACErrorMessageConstants.INVALID_NODE + " | [Create Node Operation Failed.]")

      val parameterMap = new util.HashMap[String, AnyRef]
      parameterMap.put(GraphDACParams.graphId.name, graphId)
      parameterMap.put(GraphDACParams.vertex.name, setPrimitiveData(node))

      try {
        val client = DriverUtil.getGraphClient(graphId, GraphOperation.WRITE)
        val query: String = VertexUtil.createVertexQuery(parameterMap)
        val results = client.submit(query).all().get()

        results.foreach(r => {
          val nodeElement = r.getVertex
          val identifier = nodeElement.value(SystemProperties.IL_UNIQUE_ID.name).asInstanceOf[String]
          val versionKey = nodeElement.value(GraphDACParams.versionKey.name).asInstanceOf[String]
          TransactionLog.createTxLog(VertexUtil.getElementMap(nodeElement.id, client))
          node.setGraphId(graphId)
          node.setIdentifier(identifier)
          node.getMetadata.put(GraphDACParams.versionKey.name, versionKey)
        })
        node
      }
      catch {
          case e: Throwable =>
            e.printStackTrace()
            e.getCause match {
              case _: org.apache.tinkerpop.gremlin.driver.exception.ResponseException =>
                throw new ClientException(
                  DACErrorCodeConstants.CONSTRAINT_VALIDATION_FAILED.name(),
                  DACErrorMessageConstants.CONSTRAINT_VALIDATION_FAILED + node.getIdentifier
                )
              case _ =>
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

          executeVertexDeletion(parameterMap, g)
      }
      catch {
        case e: Throwable =>
          e.printStackTrace()
          throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name,
            DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
      }
    }
  }

  private def executeVertexDeletion(parameterMap: util.Map[String, AnyRef], g: GraphTraversalSource): Boolean = {
    if (null != parameterMap) {
      val graphId = parameterMap.get(GraphDACParams.graphId.name).asInstanceOf[String]
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Remove Property Values Query Generation Failed.]")

      val nodeId = parameterMap.get(GraphDACParams.nodeId.name).asInstanceOf[String]
      if (StringUtils.isBlank(nodeId))
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
          DACErrorMessageConstants.INVALID_IDENTIFIER + " | [Remove Property Values Query Generation Failed.]")

      val traversal = g.V().hasLabel(graphId).has(SystemProperties.IL_UNIQUE_ID.name(), nodeId)
      if (traversal.hasNext) {
        traversal.drop().iterate()
        true
      } else {
        throw new ResourceNotFoundException(DACErrorCodeConstants.NOT_FOUND.name,
          DACErrorMessageConstants.NODE_NOT_FOUND + " | [Invalid Node Id.]: " + nodeId, nodeId)
      }
    } else {
      throw new ClientException(DACErrorCodeConstants.INVALID_PARAMETER.name, DACErrorMessageConstants.INVALID_PARAM_MAP)
    }
  }


  def upsertVertex(graphId: String, node: Node, request: Request): Future[Node] = {
    Future {
      setRequestContextToNode(node, request)
      validateAuthorization(graphId, node, request)
      node.getMetadata.remove(GraphDACParams.versionKey.name)

      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Node Operation Failed.]")

      if (null == node)
        throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name,
          DACErrorMessageConstants.INVALID_NODE + " | [Create Node Operation Failed.]")

      val parameterMap = new util.HashMap[String, AnyRef]
      parameterMap.put(GraphDACParams.graphId.name, graphId)
      parameterMap.put(GraphDACParams.node.name, setPrimitiveData(node))
      parameterMap.put(GraphDACParams.request.name, request)

      try {
        val client = DriverUtil.getGraphClient(graphId, GraphOperation.WRITE)
        val query: String = VertexUtil.updateVertexQuery(parameterMap)
        val results = client.submit(query).all().get()

        results.foreach(r => {
          val vertexElement = r.getVertex
          val identifier = vertexElement.value(SystemProperties.IL_UNIQUE_ID.name).asInstanceOf[String]
          val versionKey = vertexElement.value(GraphDACParams.versionKey.name).asInstanceOf[String]
//          TransactionLog.createTxLog(VertexUtil.getElementMap(vertexElement.id, client))
          node.setGraphId(graphId)
          node.setIdentifier(identifier)
          node.getMetadata.put(GraphDACParams.versionKey.name, versionKey)
        })
        node
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          e.getCause match {
            case _: org.apache.tinkerpop.gremlin.driver.exception.ResponseException =>
              throw new ClientException(
                DACErrorCodeConstants.CONSTRAINT_VALIDATION_FAILED.name(),
                DACErrorMessageConstants.CONSTRAINT_VALIDATION_FAILED + node.getIdentifier
              )
            case _ =>
              throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name, DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
          }
      }
    }
  }


  def upsertRootVertex(graphId: String, request: AnyRef): Future[Node] = {
    Future {
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Upsert Root Node Operation Failed.]")

      val g = graphConnection.getGraphTraversalSource

      val rootNodeUniqueId = Identifier.getIdentifier(graphId, SystemNodeTypes.ROOT_NODE.name())

      val vertex = new Node
      vertex.setIdentifier(rootNodeUniqueId)
      vertex.getMetadata.put(SystemProperties.IL_UNIQUE_ID.name, rootNodeUniqueId)
      vertex.getMetadata.put(SystemProperties.IL_SYS_NODE_TYPE.name, SystemNodeTypes.ROOT_NODE.name)
      vertex.getMetadata.put(AuditProperties.createdOn.name, DateUtils.formatCurrentDate())
      vertex.getMetadata.put(GraphDACParams.Nodes_Count.name, 0: Integer)
      vertex.getMetadata.put(GraphDACParams.Relations_Count.name, 0: Integer)

      val parameterMap = Map(
        GraphDACParams.graphId.name -> graphId,
        GraphDACParams.rootNode.name -> vertex,
        GraphDACParams.request.name -> request
      )

      try {
        val existingRootNode = g.V().has(SystemProperties.IL_UNIQUE_ID.name, rootNodeUniqueId).next()

        val updatedVertex = existingRootNode.property(AuditProperties.createdOn.name, DateUtils.formatCurrentDate())

        val identifier = updatedVertex.property(SystemProperties.IL_UNIQUE_ID.name).value().toString
        val versionKey = Option(updatedVertex.property(GraphDACParams.versionKey.name)).map(_.value().toString).getOrElse("")

        vertex.setIdentifier(identifier)
        vertex.setGraphId(graphId)
        if (StringUtils.isNotBlank(versionKey))
          vertex.getMetadata.put(GraphDACParams.versionKey.name, versionKey)

        vertex

      }
      catch {
        case e: Throwable =>
          throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name,
            DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
      }
    }
  }

  def updateVertexes(graphId: String, identifiers: java.util.List[String], data: java.util.Map[String, AnyRef]): Future[util.Map[String, Node]] = {
    Future {
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Invalid or 'null' Graph Id.]")


      if (identifiers.isEmpty)
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
        DACErrorMessageConstants.INVALID_IDENTIFIER + " | [Please Provide Node Identifier.]")

      if (MapUtils.isEmpty(data))
        throw new ClientException(DACErrorCodeConstants.INVALID_METADATA.name,
          DACErrorMessageConstants.INVALID_METADATA + " | [Please Provide Valid Node Metadata]")

      val parameterMap = generateUpdateVerticesQuery(graphId, identifiers, setPrimitiveData(data))

      try {
        graphConnection.initialiseGraphClient()
        val g: GraphTraversalSource = graphConnection.getGraphTraversalSource

        val updatedVertices = identifiers.foldLeft(List.empty[Node]) {
          (acc: List[Node], identifier: String) =>
            val existingVertex = g.V().has(SystemProperties.IL_UNIQUE_ID.toString, identifier)
            val finalMap = parameterMap.getOrDefault(GraphDACParams.paramValueMap.name, new util.HashMap[String, AnyRef]).asInstanceOf[util.Map[String, AnyRef]]

            finalMap.foreach { case (key, value) =>
              if (!key.equals(GraphDACParams.graphId.name) && !key.equals(GraphDACParams.request.name)) {
                existingVertex.property(key, value)
              }
            }

            val updatedVertex = existingVertex.toList().head.asInstanceOf[Node]

            acc :+ updatedVertex
        }


        val resultMap = updatedVertices.map(vertex => {
          val identifier = vertex.getMetadata.get(SystemProperties.IL_UNIQUE_ID.name).asInstanceOf[String]
          val newVertex = new Node
          newVertex.setIdentifier(identifier)
          newVertex
        })

        resultMap.asInstanceOf[Map[String, Node]]
      }
      catch {
        case e: Throwable =>
          e.printStackTrace()
          e match {
            case cause: org.apache.tinkerpop.gremlin.driver.exception.ResponseException =>
              throw new ClientException(
                DACErrorCodeConstants.CONSTRAINT_VALIDATION_FAILED.name(),
                DACErrorMessageConstants.CONSTRAINT_VALIDATION_FAILED + " | Updating multiple nodes failed."
              )
            case cause =>
              val errorMessage = DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage
              throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name, errorMessage, e)
          }
      }
    }
  }

  private def setPrimitiveData(metadata: java.util.Map[String, AnyRef]): mutable.Map[String, Object] = {
    metadata.flatMap { case (key, value) =>
      val processedValue = value match {
        case map: util.Map[Any, Any] =>
          try {
            JsonUtils.serialize(map)
          } catch {
            case e: Exception =>
              TelemetryManager.error("Exception Occurred While Processing Primitive Data Types | Exception is : " + e.getMessage(), e)
              value
          }
        case list: List[_] if list.nonEmpty && list.head.isInstanceOf[Map[Any, Any]] =>
          try {
            JsonUtils.serialize(list)
          } catch {
            case e: Exception =>
              TelemetryManager.error("Exception Occurred While Processing Primitive Data Types | Exception is : " + e.getMessage(), e)
              value
          }
        case _ => value
      }
      Some((key, processedValue))
    }
  }

  private def generateUpdateVerticesQuery(graphId: String, identifiers: java.util.List[String], data: mutable.Map[String, AnyRef]): Map[String, Object] = {
    val parameterMap = new HashMap[String, Object]
    parameterMap.put("identifiers", identifiers);
    parameterMap.putAll(data);
    parameterMap;
  }

  private def setPrimitiveData(vertex: Node): Node = {
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

  private def setRequestContextToNode(node: Node, request: Request): Unit = {
    if (null != request && null != request.getContext) {
      val channel = request.getContext.get(GraphDACParams.CHANNEL_ID.name).asInstanceOf[String]
      TelemetryManager.log("Channel from request: " + channel + " for content: " + node.getIdentifier)

      if (StringUtils.isNotBlank(channel))
        node.getMetadata.put(GraphDACParams.channel.name, channel)
      val consumerId = request.getContext.get(GraphDACParams.CONSUMER_ID.name).asInstanceOf[String]
      TelemetryManager.log("ConsumerId from request: " + consumerId + " for content: " + node.getIdentifier)

      if (StringUtils.isNotBlank(consumerId))
        node.getMetadata.put(GraphDACParams.consumerId.name, consumerId)
      val appId = request.getContext.get(GraphDACParams.APP_ID.name).asInstanceOf[String]
      TelemetryManager.log("App Id from request: " + appId + " for content: " + node.getIdentifier)

      if (StringUtils.isNotBlank(appId))
        node.getMetadata.put(GraphDACParams.appId.name, appId)
    }
  }

  private def validateAuthorization(graphId: String, node: Node, request: Request): Unit = {
    if (StringUtils.isBlank(graphId))
      throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
        DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Invalid or 'null' Graph Id.]")

    if (null == node)
      throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name,
        DACErrorMessageConstants.INVALID_NODE + " | [Invalid or 'null' Node.]")

    if (null == request)
      throw new ClientException(DACErrorCodeConstants.INVALID_REQUEST.name,
        DACErrorMessageConstants.INVALID_REQUEST + " | [Invalid or 'null' Request Object.]")
  }


}
