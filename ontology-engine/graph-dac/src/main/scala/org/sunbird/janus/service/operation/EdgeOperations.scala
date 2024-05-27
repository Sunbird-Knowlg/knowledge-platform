package org.sunbird.janus.service.operation

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.tinkerpop.gremlin.groovy.jsr223.dsl.credential.__
import org.apache.tinkerpop.gremlin.groovy.jsr223.dsl.credential.__.{bothE, outE}
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.structure.{Edge, Graph, Vertex}
import org.janusgraph.core.JanusGraph
import org.sunbird.common.dto.{Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model.{Edges, Node, VertexSubGraph}
import org.sunbird.graph.service.common.{CypherQueryConfigurationConstants, DACErrorCodeConstants, DACErrorMessageConstants}
import org.sunbird.janus.dac.util.GremlinVertexUtil
import org.sunbird.janus.service.util.JanusConnectionUtil
import org.sunbird.telemetry.logger.TelemetryManager

import java.util
import java.util.{HashMap, List, Map, Set}
import java.util.stream.Collectors
import scala.collection.JavaConverters.{asJavaIterableConverter, asScalaBufferConverter, asScalaIteratorConverter, mapAsScalaMapConverter}
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.collection.immutable.HashSet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConverters._

class EdgeOperations {

  val graphConnection = new JanusConnectionUtil
  val gremlinVertexUtil = new GremlinVertexUtil
  def createEdges(graphId: String, edgeData: util.List[util.Map[String, AnyRef]]): Future[Response] = {
    Future{
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Node Operation Failed.]")

      if (CollectionUtils.isEmpty(edgeData))
        throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name,
          DACErrorMessageConstants.INVALID_NODE + " | [Create Relation Operation Failed.]")

      graphConnection.initialiseGraphClient()
      val g: GraphTraversalSource = graphConnection.getGraphTraversalSource

      createBulkRelations(g, graphId, edgeData)
      ResponseHandler.OK()
    }
  }

  def removeEdges(graphId: String, edgeData: util.List[util.Map[String, AnyRef]]): Future[Response] = {
    Future {
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Node Operation Failed.]")

      if (CollectionUtils.isEmpty(edgeData))
        throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name,
          DACErrorMessageConstants.INVALID_NODE + " | [Create Relation Operation Failed.]")

      graphConnection.initialiseGraphClient()
      val g: GraphTraversalSource = graphConnection.getGraphTraversalSource

      deleteBulkRelations(g, graphId, edgeData)
      ResponseHandler.OK()
    }
  }

  def createBulkRelations(g: GraphTraversalSource, graphId: String, edgeData: util.List[util.Map[String, AnyRef]]): Unit = {
    for (row <- edgeData.asScala) {
      val startNodeId = row.get("startNodeId").toString
      val endNodeId = row.get("endNodeId").toString
      val relation = row.get("relation").toString
      val relMetadata = row.get("relMetadata").asInstanceOf[Map[String, AnyRef]]

      val startNode: Vertex = g.V().hasLabel(graphId).has(SystemProperties.IL_UNIQUE_ID.name, startNodeId).next()
      val endNode: Vertex = g.V().hasLabel(graphId).has(SystemProperties.IL_UNIQUE_ID.name, endNodeId).next()

      val edge: Edge = startNode.addEdge(relation, endNode)
      for (key <- relMetadata.keySet) {
        edge.property(key, relMetadata.get(key).toString)
      }
    }
  }

  private def deleteBulkRelations(g: GraphTraversalSource, graphId: String, edgeData: util.List[util.Map[String, AnyRef]]): Unit = {
    for (row <- edgeData.asScala) {
      val startNodeId = row.get("startNodeId").toString
      val endNodeId = row.get("endNodeId").toString
      val relation = row.get("relation").toString

      g.V().hasLabel(graphId).has(SystemProperties.IL_UNIQUE_ID.name, startNodeId)
        .outE().as(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT).inV().has(SystemProperties.IL_UNIQUE_ID.name, endNodeId)
        .select(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT).drop().iterate()

    }
  }

    def getSubGraph(graphId: String, nodeId: String, depth: Integer) = {
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name, DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Get SubGraph Operation Failed.]")
      if (StringUtils.isBlank(nodeId))
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name, DACErrorMessageConstants.INVALID_IDENTIFIER + " | [Please Provide Node Identifier.]")
      var effectiveDepth:Integer  = if (depth == null) 5 else depth

      graphConnection.initialiseGraphClient()
      val g: GraphTraversalSource = graphConnection.getGraphTraversalSource
      TelemetryManager.log("Driver Initialised. | [Graph Id: " + graphId + "]")

      val relationMap = new util.HashMap[Object, AnyRef]()
      var nodes = new util.HashSet[org.sunbird.graph.dac.model.Vertex]
      var relations = new util.HashSet[org.sunbird.graph.dac.model.Edges]
      val startNodeMap = new util.HashMap[Object, AnyRef]
      val endNodeMap = new util.HashMap[Object, AnyRef]

      var results = g.V().hasLabel(graphId).has(SystemProperties.IL_UNIQUE_ID.name, nodeId).as("n")
        .emit().repeat(outE().inV().simplePath()).times(5).as("m")
        .outE().as("r2").inV().as("l")
        .select("n", "m", "r2", "l")
        .unfold()
        .project("relationName", "relationMetadata", "startNode", "endNode")
        .by(__.select("r2").label())
        .by(__.select("r2").elementMap())
        .by(__.select("m"))
        .by(__.select("l"))
        .toList()


      for (result <- results.asScala) {
        val startNode = result.get("startNode").asInstanceOf[Vertex]
        val endNode = result.get("endNode").asInstanceOf[Vertex]
        val relationName = result.get("relationName").toString
        val relationMetadata = result.get("relationMetadata").asInstanceOf[util.Map[String, Object]]

        nodes.add(gremlinVertexUtil.getNode(graphId, startNode, relationMap, startNodeMap, endNodeMap))
        nodes.add(gremlinVertexUtil.getNode(graphId, endNode, relationMap, startNodeMap, endNodeMap))

        // Relation Metadata
        val relData = new Edges(
          startNode.property(SystemProperties.IL_UNIQUE_ID.name).value().toString,
          relationName,
          endNode.property(SystemProperties.IL_UNIQUE_ID.name).value().toString
        )
        relData.setMetadata(relationMetadata)
        relData.setStartVertexObjectType(startNode.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name).value().toString)
        relData.setStartVertexName(startNode.property("name").value().toString)
//        relData.setStartVertexType(startNode.property(SystemProperties.IL_SYS_NODE_TYPE.name).value().toString)
        relData.setEndVertexObjectType(endNode.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name).value().toString)
        relData.setEndVertexName(endNode.property("name").value().toString)
//        relData.setEndVertexType(endNode.property(SystemProperties.IL_SYS_NODE_TYPE.name).value().toString)
        relData.setEndVertexType("DATA_NODE")
        relData.setStartVertexType("DATA_NODE")
        relations.add(relData)
      }

      // Group nodes by their identifier and get the first node for each identifier
      val uniqNodes = nodes.groupBy(_.getIdentifier).mapValues(_.head).values.toSet

      // Create a map with the node identifier as the key and the node itself as the value
      val nodeMap= uniqNodes.map(node => node.getIdentifier -> node).toMap
      val relationsList= relations.toList

      // Convert Scala collections to Java collections
      val javaNodeMap: java.util.Map[String, org.sunbird.graph.dac.model.Vertex] = nodeMap.asJava
      val javaRelationsList: java.util.List[org.sunbird.graph.dac.model.Edges] = relationsList.asJava

      // Create a VertexSubGraph instance
      Future {
        new VertexSubGraph(javaNodeMap, javaRelationsList)
      }
    }
}
