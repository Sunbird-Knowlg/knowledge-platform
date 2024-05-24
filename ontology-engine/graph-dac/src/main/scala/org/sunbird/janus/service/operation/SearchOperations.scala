package org.sunbird.janus.service.operation

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.sunbird.janus.dac.util.GremlinVertexUtil
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__._
import org.sunbird.graph.dac.model.Vertex
import org.apache.tinkerpop.gremlin.structure.Edge
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, MiddlewareException, ResourceNotFoundException, ServerException}
import org.sunbird.graph.common.enums.GraphDACParams
import org.sunbird.graph.service.common.{CypherQueryConfigurationConstants, DACErrorCodeConstants, DACErrorMessageConstants}
import org.sunbird.janus.service.util.JanusConnectionUtil
import org.sunbird.telemetry.logger.TelemetryManager

import java.util
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global
import scala.collection.JavaConverters.asScalaBufferConverter
class SearchOperations {

  val graphConnection = new JanusConnectionUtil
  val gremlinVertexUtil = new GremlinVertexUtil

  def getNodeByUniqueId(graphId: String, vertexId: String, getTags: Boolean, request: Request): Future[Vertex] = {
    Future {
      TelemetryManager.log("Graph Id: " + graphId + "\nVertex Id: " + vertexId + "\nGet Tags:" + getTags)

      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Node By Unique Id' Operation Failed.]")

      if (StringUtils.isBlank(vertexId))
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
          DACErrorMessageConstants.INVALID_IDENTIFIER + " | ['Get Node By Unique Id' Operation Failed.]")

      TelemetryManager.log("Driver Initialised. | [Graph Id: " + graphId + "]")
      try {
        graphConnection.initialiseGraphClient()
        val g: GraphTraversalSource = graphConnection.getGraphTraversalSource

        val parameterMap = new util.HashMap[String, AnyRef]
        parameterMap.put(GraphDACParams.graphId.name, graphId)
        parameterMap.put(GraphDACParams.nodeId.name, vertexId)
        parameterMap.put(GraphDACParams.getTags.name, getTags.asInstanceOf[java.lang.Boolean])
        parameterMap.put(GraphDACParams.request.name, request)

        val retrievedVertices = getVertexByUniqueId(parameterMap, g)
        var newVertex: Vertex = null
        if (CollectionUtils.isEmpty(retrievedVertices))
          throw new ResourceNotFoundException(DACErrorCodeConstants.NOT_FOUND.name,
            DACErrorMessageConstants.NODE_NOT_FOUND + " | [Invalid Node Id.]: " + vertexId, vertexId)


        val vertexMap = new util.HashMap[Object, AnyRef]
        val relationMap = new util.HashMap[Object, AnyRef]
        val startNodeMap = new util.HashMap[Object, AnyRef]
        val endNodeMap = new util.HashMap[Object, AnyRef]

        retrievedVertices.forEach { result =>
          if (null != result)
            getRecordValues(result, vertexMap, relationMap, startNodeMap, endNodeMap)
        }

        if (!vertexMap.isEmpty) {
          val entry = vertexMap.entrySet().iterator().next()
          newVertex = gremlinVertexUtil.getNode(graphId, entry.getValue.asInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex], relationMap, startNodeMap, endNodeMap)
        }
        newVertex
      }

      catch {
        case ex: MiddlewareException => throw ex
        case e: Throwable =>
          e.printStackTrace()
          println("IN catch operation", e)
          throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
            DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
      }
    }
  }

  private def getVertexByUniqueId(parameterMap: util.Map[String, AnyRef], g: GraphTraversalSource): util.List[util.Map[String, AnyRef]] = {
    try {
        if (null != parameterMap) {
          val graphId = parameterMap.getOrDefault(GraphDACParams.graphId.name, "").asInstanceOf[String]
          if (StringUtils.isBlank(graphId))
            throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
              DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Node By Id' Query Generation Failed.]")

          val vertexId = parameterMap.get(GraphDACParams.nodeId.name).asInstanceOf[String]
          if (StringUtils.isBlank(vertexId))
            throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
              DACErrorMessageConstants.INVALID_IDENTIFIER + " | ['Get Node By Unique Id' Query Generation Failed.]")

//          g.V().hasLabel(graphId).has("IL_UNIQUE_ID", vertexId).as("ee").union(identity(), outE().as("r").inV().as("__endNode")
//            .select("ee", "r", "__endNode"), inE().as("r").outV().as("__startNode")
//            .select("ee", "r", "__startNode")).toList.asInstanceOf[util.List[util.Map[String, AnyRef]]]

          g.V().hasLabel(graphId).has("IL_UNIQUE_ID", vertexId).as("ee")
            .project("ee", "r", "__startNode", "__endNode")
            .by(identity())
            .by(bothE().elementMap().fold())
            .by(inE().outV().elementMap().fold())
            .by(outE().inV().elementMap().fold())
            .toList()

//          g.V().hasLabel(graphId).has("IL_UNIQUE_ID", vertexId).as("ee").union(project("id", "label").by(id()).by(label()), outE().as("r").inV().as("__endNode")
//            .select("ee", "r", "__endNode") .by(project("id", "label").by(id()).by(label())) .by(project("id", "label", "inVLabel", "outVLabel", "inV", "outV") .by(id()).by(label()).by(inV().label()).by(outV().label()).by(inV().id()).by(outV().id())) .by(project("id", "label").by(id()).by(label())), inE().as("r").outV().as("__startNode")
//            .select("ee", "r", "__startNode") .by(project("id", "label").by(id()).by(label())) .by(project("id", "label", "inVLabel", "outVLabel", "inV", "outV") .by(id()).by(label()).by(inV().label()).by(outV().label()).by(inV().id()).by(outV().id())) .by(project("id", "label").by(id()).by(label())) ).toList.asInstanceOf[util.List[util.Map[String, AnyRef]]]

        }
        else new util.ArrayList[util.Map[String, AnyRef]]
    }
    catch {
      case e :Exception =>
        throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name, "Error! Something went wrong while creating node object. ", e.getCause);
    }
  }

  private def getRecordValues(result: util.Map[String, AnyRef], nodeMap :util.Map[Object, AnyRef], relationMap :util.Map[Object, AnyRef], startNodeMap :util.Map[Object, AnyRef], endNodeMap :util.Map[Object, AnyRef] ): Unit = {
    if (null != nodeMap) {
        val vertexValue = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT)
        println("vertexValue ", vertexValue)
        if(null != vertexValue && vertexValue.isInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex]) {
            val gremlinVertex : org.apache.tinkerpop.gremlin.structure.Vertex = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT).asInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex]
            println("gremlinVertex ", gremlinVertex)
            nodeMap.put(gremlinVertex.id(), gremlinVertex)
        } else {
          println("In ELSE ", vertexValue)
        }
    }
    if (null != relationMap) {
        val edgeValue = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT)
        println("edgeValue ", edgeValue)
        if (null != edgeValue && edgeValue.isInstanceOf[org.apache.tinkerpop.gremlin.structure.Edge]) {
            val edge: org.apache.tinkerpop.gremlin.structure.Edge = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT).asInstanceOf[Edge]
            nodeMap.put(edge.id(), edge)
        }
    }
    if (null != startNodeMap) {
        val startVertexValue = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_START_NODE_OBJECT)
        println("startVertexValue ", startVertexValue)
        if (null != startVertexValue && startVertexValue.isInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex]) {
            val startVertex: org.apache.tinkerpop.gremlin.structure.Vertex = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_START_NODE_OBJECT).asInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex]
            nodeMap.put(startVertex.id(), startVertex)
        }
    }
    if (null != endNodeMap) {
        val endVertexValue = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_END_NODE_OBJECT)
        println("endVertexValue ", endVertexValue)
        if (null != endVertexValue && endVertexValue.isInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex]) {
            val endVertex: org.apache.tinkerpop.gremlin.structure.Vertex = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_END_NODE_OBJECT).asInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex]
            nodeMap.put(endVertex.id(), endVertex)
        }
    }
  }

}
