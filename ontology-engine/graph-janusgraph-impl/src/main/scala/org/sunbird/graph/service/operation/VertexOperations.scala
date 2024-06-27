package org.sunbird.graph.service.operation

import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, MiddlewareException, ResourceNotFoundException, ServerException}
import org.sunbird.common.{DateUtils, JsonUtils}
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.common.enums.{AuditProperties, GraphDACParams, SystemProperties}
import org.sunbird.graph.dac.enums.SystemNodeTypes
import org.sunbird.graph.dac.model.Node
import org.apache.tinkerpop.gremlin.driver.exception.ResponseException
import org.sunbird.graph.dac.util.GremlinVertexUtil
import org.sunbird.graph.service.common.{DACErrorCodeConstants, DACErrorMessageConstants, GraphOperation}
import org.sunbird.graph.service.util.{ClientUtil, VertexUtil}
import org.sunbird.telemetry.logger.TelemetryManager
import java.util
import java.util.concurrent.CompletableFuture
import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.convert.ImplicitConversions.{`map AsJavaMap`, `map AsScala`}
import scala.collection.mutable
import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.Future

class VertexOperations {

  val gremlinVertexUtil = new GremlinVertexUtil

  def addVertex(graphId: String, node: Node): Future[Node] = {
    if (StringUtils.isBlank(graphId))
      throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
        DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Node Operation Failed.]")

    if (node == null)
      throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name,
        DACErrorMessageConstants.INVALID_NODE + " | [Create Node Operation Failed.]")

    val parameterMap = new java.util.HashMap[String, AnyRef]
    parameterMap.put(GraphDACParams.graphId.name, graphId)
    parameterMap.put(GraphDACParams.node.name, setPrimitiveData(node))

    try {
      VertexUtil.createVertexQuery(parameterMap)
      val client= ClientUtil.getGraphClient(graphId, GraphOperation.WRITE)
      val query: String = parameterMap.getOrDefault(GraphDACParams.query.name, "").asInstanceOf[String]
      val statementParameters = parameterMap.getOrDefault(GraphDACParams.paramValueMap.name, new java.util.HashMap[String, AnyRef]).asInstanceOf[java.util.Map[String, AnyRef]]

      val cs: CompletableFuture[Node] = client.submitAsync(query, statementParameters).toCompletableFuture
        .thenCompose[Node](resultSet => resultSet.all().thenApply[Node](resultSet => {
          val nodeElement = resultSet.get(0).getVertex
          val identifier = nodeElement.value(SystemProperties.IL_UNIQUE_ID.name).asInstanceOf[String]
          val versionKey = nodeElement.value(GraphDACParams.versionKey.name).asInstanceOf[String]
          node.setGraphId(graphId)
          node.setIdentifier(identifier)
          node.getMetadata.put(GraphDACParams.versionKey.name, versionKey)
          node
        })).exceptionally { error =>
        error.printStackTrace()
        error.getCause match {
          case _: ResponseException =>
            throw new ClientException(
              DACErrorCodeConstants.CONSTRAINT_VALIDATION_FAILED.name,
              DACErrorMessageConstants.CONSTRAINT_VALIDATION_FAILED + node.getIdentifier
            )
          case _ =>
            throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name,
              "Error! Something went wrong while creating node object. ", error.getCause)
        }
      }
      cs.toScala
    } catch {
      case e: Throwable =>
        e.printStackTrace()
        if (!e.isInstanceOf[MiddlewareException])
          throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name,
            DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
        else throw e
    }
  }

  def deleteVertex(graphId: String, nodeId: String, request: Request): Future[java.lang.Boolean] = {
    if (StringUtils.isBlank(graphId))
      throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
        DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Remove Property Values Operation Failed.]")

    if (StringUtils.isBlank(nodeId))
      throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
        DACErrorMessageConstants.INVALID_IDENTIFIER + " | [Remove Property Values Operation Failed.]")

    val parameterMap = new java.util.HashMap[String, AnyRef]
    parameterMap.put(GraphDACParams.graphId.name, graphId)
    parameterMap.put(GraphDACParams.nodeId.name, nodeId)
    parameterMap.put(GraphDACParams.request.name, request)

    try {
      val query: String = VertexUtil.deleteVertexQuery(parameterMap)
      val client = ClientUtil.getGraphClient(graphId, GraphOperation.WRITE)

      val cs: CompletableFuture[java.lang.Boolean] = client.submitAsync(query).toCompletableFuture
        .thenCompose[java.lang.Boolean](resultSet => resultSet.all().thenApply[java.lang.Boolean](resultSet => {
          val isDeleted = resultSet.isEmpty
          java.lang.Boolean.valueOf(isDeleted)
        })).exceptionally { error =>
        error.printStackTrace()
        error.getCause match {
          case _: ResponseException =>
            throw new ResourceNotFoundException(DACErrorCodeConstants.NOT_FOUND.name,
              DACErrorMessageConstants.NODE_NOT_FOUND + " | [Invalid Node Id.]: " + nodeId, nodeId)
          case _ =>
            throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name,
              "Error! Something went wrong while creating node object. ", error.getCause)
        }
      }
      cs.toScala
    } catch {
      case e: Throwable =>
        throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name,
          DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage)
    }
  }

  def upsertVertex(graphId: String, node: Node, request: Request): Future[Node] = {
      setRequestContextToNode(node, request)
      validateAuthorization(graphId, node, request)
      node.getMetadata.remove(GraphDACParams.versionKey.name)

      val parameterMap = new util.HashMap[String, AnyRef]
      parameterMap.put(GraphDACParams.graphId.name, graphId)
      parameterMap.put(GraphDACParams.node.name, setPrimitiveData(node))
      parameterMap.put(GraphDACParams.request.name, request)

      try {
        VertexUtil.upsertVertexQuery(parameterMap)
        val client = ClientUtil.getGraphClient(graphId, GraphOperation.WRITE)
        val query: String = parameterMap.getOrDefault(GraphDACParams.query.name, "").asInstanceOf[String]
        val statementParameters = parameterMap.getOrDefault(GraphDACParams.paramValueMap.name, "").asInstanceOf[util.Map[String, AnyRef]]

        val cs: CompletableFuture[Node] = client.submitAsync(query, statementParameters).toCompletableFuture
          .thenCompose[Node](resultSet => resultSet.all().thenApply[Node](resultSet => {
            val vertexElement = resultSet.get(0).getVertex
            val identifier = vertexElement.value(SystemProperties.IL_UNIQUE_ID.name).asInstanceOf[String]
            val versionKey = vertexElement.value(GraphDACParams.versionKey.name).asInstanceOf[String]
            node.setGraphId(graphId)
            node.setIdentifier(identifier)
            if (StringUtils.isNotBlank(versionKey))
              node.getMetadata.put(GraphDACParams.versionKey.name, versionKey)
            node
          })).exceptionally { error =>
          error.printStackTrace()
          error.getCause match {
            case _ =>
              throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name,
                "Error! Something went wrong while creating node object. ", error.getCause)
          }
        }
        cs.toScala
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          if (!e.isInstanceOf[MiddlewareException])
            throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name,
              DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
          else throw e
      }
  }

  def upsertRootVertex(graphId: String, request: AnyRef): Future[Node] = {
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Upsert Root Node Operation Failed.]")

      val node = new Node
      node.setMetadata(new util.HashMap[String, AnyRef]())

      try {
        val rootNodeUniqueId = Identifier.getIdentifier(graphId, SystemNodeTypes.ROOT_NODE.name())
        node.setGraphId(graphId)
        node.setNodeType(SystemNodeTypes.ROOT_NODE.name)
        node.setIdentifier(rootNodeUniqueId)
        node.getMetadata.put(SystemProperties.IL_UNIQUE_ID.name, rootNodeUniqueId)
        node.getMetadata.put(SystemProperties.IL_SYS_NODE_TYPE.name, SystemNodeTypes.ROOT_NODE.name)
        node.getMetadata.put(AuditProperties.createdOn.name, DateUtils.formatCurrentDate)
        node.getMetadata.put(GraphDACParams.Nodes_Count.name, 0: Integer)
        node.getMetadata.put(GraphDACParams.Relations_Count.name(), 0: Integer)

        val parameterMap = Map(
          GraphDACParams.graphId.name -> graphId,
          GraphDACParams.rootNode.name -> node,
          GraphDACParams.request.name -> request
        )

        val client = ClientUtil.getGraphClient(graphId, GraphOperation.WRITE)
        val query: String = VertexUtil.upsertRootVertexQuery(parameterMap)

        val cs: CompletableFuture[Node] = client.submitAsync(query).toCompletableFuture
          .thenCompose[Node](resultSet => resultSet.all().thenApply[Node](resultSet => {
            val vertexElement = resultSet.get(0).getVertex
            val identifier = vertexElement.value(SystemProperties.IL_UNIQUE_ID.name).asInstanceOf[String]
            val versionKey = vertexElement.value(GraphDACParams.versionKey.name).asInstanceOf[String]
            node.setGraphId(graphId)
            node.setIdentifier(identifier)
            if (StringUtils.isNotBlank(versionKey))
              node.getMetadata.put(GraphDACParams.versionKey.name, versionKey)
            node
          })).exceptionally { error =>
          error.printStackTrace()
          error.getCause match {
            case _ =>
              throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name,
                "Error! Something went wrong while creating node object. ", error.getCause)
          }
        }
        cs.toScala
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          if (!e.isInstanceOf[MiddlewareException])
            throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name,
              DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
          else throw e
      }
  }

  def updateVertices(graphId: String, identifiers: util.List[String], data: util.Map[String, AnyRef]): Future[util.Map[String, Node]] = {
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Invalid or 'null' Graph Id.]")

      if (identifiers.isEmpty)
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
          DACErrorMessageConstants.INVALID_IDENTIFIER + " | [Please Provide Node Identifier.]")

      if (MapUtils.isEmpty(data))
        throw new ClientException(DACErrorCodeConstants.INVALID_METADATA.name,
          DACErrorMessageConstants.INVALID_METADATA + " | [Please Provide Valid Node Metadata]")

      val parameterMap = new util.HashMap[String, AnyRef]
      val output = new util.HashMap[String, Node]
      try {
        val client = ClientUtil.getGraphClient(graphId, GraphOperation.WRITE)
        val query: String = VertexUtil.upsertVerticesQuery(graphId, identifiers, setPrimitiveData(data), parameterMap)

        val cs: CompletableFuture[util.Map[String, Node]] = client.submitAsync(query, parameterMap).toCompletableFuture
          .thenCompose[util.Map[String, Node]] { resultSet =>
            resultSet.all().thenApply[util.Map[String, Node]] { result =>
              if (result != null) {
                result.asScala.foreach { record =>
                  if (record != null) {
                    val vertexElement = record.getVertex
                    val identifier = vertexElement.value(SystemProperties.IL_UNIQUE_ID.name).asInstanceOf[String]
                    val node = gremlinVertexUtil.getNode(graphId, vertexElement, null, null, null)
                    output.put(identifier, node)
                  }
                }
              }
              output
            }
          }.exceptionally { error =>
            error.printStackTrace()
            error.getCause match {
              case _ =>
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name,
                  "Error! Something went wrong while creating node object. ", error.getCause)
            }
          }

        cs.toScala

      } catch {
        case e: Throwable =>
          e.printStackTrace()
          if (!e.isInstanceOf[MiddlewareException])
            throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name,
              DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
          else throw e
      }
  }

  private def setPrimitiveData(metadata: util.Map[String, AnyRef]): mutable.Map[String, Object] = {
    metadata.flatMap { case (key, value) =>
      val processedValue = value match {
        case map: util.Map[Any, Any] =>
          try {
            JsonUtils.serialize(map)
          } catch {
            case e: Exception =>
              TelemetryManager.error("Exception Occurred While Processing Primitive Data Types | Exception is : " + e.getMessage, e)
              value
          }
        case list: List[_] if list.nonEmpty && list.head.isInstanceOf[Map[Any, Any]] =>
          try {
            JsonUtils.serialize(list)
          } catch {
            case e: Exception =>
              TelemetryManager.error("Exception Occurred While Processing Primitive Data Types | Exception is : " + e.getMessage, e)
              value
          }
        case _ => value
      }
      Some((key, processedValue))
    }
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
