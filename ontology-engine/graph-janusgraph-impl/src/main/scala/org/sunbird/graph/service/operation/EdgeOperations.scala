package org.sunbird.graph.service.operation

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.sunbird.common.dto.{Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, MiddlewareException, ServerException}
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model.{Node, Relation, SubGraph}
import org.sunbird.graph.service.common.{DACErrorCodeConstants, DACErrorMessageConstants, GraphOperation}
import org.sunbird.graph.dac.util.GremlinVertexUtil
import org.sunbird.graph.service.util.{ClientUtil, EdgeUtil}
import org.sunbird.telemetry.logger.TelemetryManager

import java.util
import java.util.concurrent.CompletableFuture
import scala.concurrent.Future
import scala.collection.JavaConverters._
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.ExecutionContext.Implicits.global

object EdgeOperations {

  def createEdges(graphId: String, edgeData: util.List[util.Map[String, AnyRef]]): Future[Response] = {
    if (StringUtils.isBlank(graphId))
      throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
        DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Node Operation Failed.]")

    if (CollectionUtils.isEmpty(edgeData))
      throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name,
        DACErrorMessageConstants.INVALID_NODE + " | [Create Relation Operation Failed.]")

    try {
      val client = ClientUtil.getGraphClient(graphId, GraphOperation.WRITE)
      val createEdgesQueries = EdgeUtil.generateCreateBulkRelationsQuery(graphId, edgeData)

      val futures: List[Future[Unit]] = createEdgesQueries.map { query =>
        client.submitAsync(query).toCompletableFuture.toScala
          .map(_ => ())
          .recover {
            case error: Throwable =>
              error.printStackTrace()
              throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name,
                s"Error! Something went wrong while executing query: $query", error)
          }
      }
      Future.sequence(futures).map(_ => ResponseHandler.OK())
    } catch {
      case e: Throwable =>
        e.printStackTrace()
        if (!e.isInstanceOf[MiddlewareException])
          throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name,
            DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
        else throw e
    }
  }


  def removeEdges(graphId: String, edgeData: util.List[util.Map[String, AnyRef]]): Future[Response] = {
    if (StringUtils.isBlank(graphId))
      throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
        DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Node Operation Failed.]")

    if (CollectionUtils.isEmpty(edgeData))
      throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name,
        DACErrorMessageConstants.INVALID_NODE + " | [Create Relation Operation Failed.]")

    try {
      val client = ClientUtil.getGraphClient(graphId, GraphOperation.WRITE)
      val query = EdgeUtil.generateDeleteRelationsQuery(graphId, edgeData)

      val cs: CompletableFuture[Response] = client.submitAsync(query).toCompletableFuture
        .thenCompose[Response](resultSet => resultSet.all().thenApply[Response](resultSet => {
          ResponseHandler.OK()
        })).exceptionally { error =>
        error.printStackTrace()
        error.getCause match {
          case _ =>
            throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name,
              "Error! Something went wrong while creating node object. ", error.getCause)
        }
      }
      cs.toScala
    }
    catch {
      case e: Throwable =>
        e.printStackTrace()
        if (!e.isInstanceOf[MiddlewareException])
          throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name,
            DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
        else throw e
    }
  }

  def getSubGraph(graphId: String, nodeId: String, depth: Integer): Future[SubGraph] = {
    if (StringUtils.isBlank(graphId))
      throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name, DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Get SubGraph Operation Failed.]")
    if (StringUtils.isBlank(nodeId))
      throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name, DACErrorMessageConstants.INVALID_IDENTIFIER + " | [Please Provide Node Identifier.]")
    val effectiveDepth: Integer = if (depth == null) 5 else depth

    TelemetryManager.log("Driver Initialised. | [Graph Id: " + graphId + "]")

    val relationMap = new util.HashMap[AnyRef, AnyRef]()
    val nodes = new util.HashSet[Node]()
    val relations = new util.HashSet[Relation]()
    val startNodeMap = new util.HashMap[AnyRef, AnyRef]()
    val endNodeMap = new util.HashMap[AnyRef, AnyRef]()

    try {
      val subGraphQuery = EdgeUtil.generateSubGraphCypherQuery(graphId, nodeId, effectiveDepth)
      val client = ClientUtil.getGraphClient(graphId, GraphOperation.READ)
      val cs: CompletableFuture[SubGraph] = client.submitAsync(subGraphQuery).toCompletableFuture
        .thenCompose[SubGraph](resultSet => resultSet.all().thenApply[SubGraph](results => {
          for (result <- results.asScala) {
            val res = result.getObject.asInstanceOf[util.Map[String, AnyRef]]
            val startNode = res.get("startNode").asInstanceOf[Vertex]
            val endNode = res.get("endNode").asInstanceOf[Vertex]
            val relationName = res.get("relationName").toString
            val relationMetadata = res.get("relationMetadata").asInstanceOf[util.Map[String, AnyRef]]
            nodes.add(GremlinVertexUtil.getNode(graphId, startNode, relationMap, startNodeMap, endNodeMap))
            nodes.add(GremlinVertexUtil.getNode(graphId, endNode, relationMap, startNodeMap, endNodeMap))

            val relData = new Relation(
              startNode.property(SystemProperties.IL_UNIQUE_ID.name).value().toString,
              relationName,
              endNode.property(SystemProperties.IL_UNIQUE_ID.name).value().toString
            )
            relData.setMetadata(relationMetadata)
            relData.setStartNodeObjectType(startNode.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name).value().toString)
            relData.setStartNodeName(startNode.property("name").value().toString)
            relData.setStartNodeType(startNode.property(SystemProperties.IL_SYS_NODE_TYPE.name).value().toString)
            relData.setEndNodeObjectType(endNode.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name).value().toString)
            relData.setEndNodeName(endNode.property("name").value().toString)
            relData.setEndNodeType(endNode.property(SystemProperties.IL_SYS_NODE_TYPE.name).value().toString)
            relations.add(relData)
          }
          val uniqueNodes = nodes.asScala.groupBy(_.getIdentifier).mapValues(_.head).values.toSet
          val nodeMap = uniqueNodes.map(node => node.getIdentifier -> node).toMap
          val relationsList = relations.toList

          val javaNodeMap: util.Map[String, Node] = nodeMap.asJava
          val javaRelationsList: util.List[org.sunbird.graph.dac.model.Relation] = relationsList.asJava

          new SubGraph(javaNodeMap, javaRelationsList)
        })).exceptionally { error =>
        error.printStackTrace()
        throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name, "Error! Something went wrong while creating node object. ", error.getCause)
      }
      cs.toScala
    } catch {
      case e: Throwable =>
        e.printStackTrace()
        if (!e.isInstanceOf[MiddlewareException])
          throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name, DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
        else throw e
    }
  }

}
