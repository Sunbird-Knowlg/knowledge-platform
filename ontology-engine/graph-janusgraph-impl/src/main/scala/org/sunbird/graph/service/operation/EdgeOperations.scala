package org.sunbird.graph.service.operation

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.tinkerpop.gremlin.driver.{Result, ResultSet}
import org.apache.tinkerpop.gremlin.groovy.jsr223.dsl.credential.__
import org.apache.tinkerpop.gremlin.groovy.jsr223.dsl.credential.__.{bothE, outE}
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.structure.{Edge, Graph, Vertex}
import org.janusgraph.core.JanusGraph
import org.sunbird.common.dto.{Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model.{Node, Relation, SubGraph}
import org.sunbird.graph.service.common.{CypherQueryConfigurationConstants, DACErrorCodeConstants, DACErrorMessageConstants, GraphOperation}
import org.sunbird.graph.dac.util.GremlinVertexUtil
import org.sunbird.graph.service.util.{DriverUtil, EdgeUtil}
import org.sunbird.telemetry.logger.TelemetryManager

import java.util
import java.util.{HashMap, List, Map, Set}
import java.util.stream.Collectors
import scala.collection.JavaConverters.{asJavaIterableConverter, asScalaBufferConverter, asScalaIteratorConverter, mapAsScalaMapConverter}
import scala.collection.convert.ImplicitConversions.{`collection AsScalaIterable`, `map AsScala`}
import scala.collection.immutable.HashSet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConverters._

class EdgeOperations {

  val gremlinVertexUtil = new GremlinVertexUtil
  def createEdges(graphId: String, edgeData: util.List[util.Map[String, AnyRef]]): Future[Response] = {
    Future{

      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Node Operation Failed.]")

      if (CollectionUtils.isEmpty(edgeData))
        throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name,
          DACErrorMessageConstants.INVALID_NODE + " | [Create Relation Operation Failed.]")

      createBulkRelations(graphId, edgeData)
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

      deleteBulkRelations(graphId, edgeData)
      ResponseHandler.OK()
    }
  }

  private def createBulkRelations(graphId: String, edgeData: util.List[util.Map[String, AnyRef]]): Unit = {
    for (row <- edgeData.asScala.distinct) {
      val startNodeId = row.get("startNodeId").toString
      val endNodeId = row.get("endNodeId").toString
      val relation = row.get("relation").toString
      val relMetadata = row.get("relMetadata").asInstanceOf[util.Map[AnyRef, AnyRef]]

      val client = DriverUtil.getGraphClient(graphId, GraphOperation.WRITE)

      // Gremlin query to get the target vertex ID
      val startNodeQuery = "g.V().hasLabel('"+graphId+"').has('"+SystemProperties.IL_UNIQUE_ID.name+"', '"+startNodeId+"').id()"
      val startNodeResult = client.submit(startNodeQuery).one
      val startNode = startNodeResult.getObject

      // Gremlin query to get the target vertex ID
      val endNodeQuery = "g.V().hasLabel('"+graphId+"').has('"+SystemProperties.IL_UNIQUE_ID.name+"', '"+endNodeId+"').id()"
      val endNodeResult = client.submit(endNodeQuery).one
      val endNode = endNodeResult.getObject

      val createRelationsQuery = EdgeUtil.createRelationsQuery(startNode, endNode, relation, relMetadata)
      client.submit(createRelationsQuery)
    }

  }

  private def deleteBulkRelations(graphId: String, edgeData: util.List[util.Map[String, AnyRef]]): Unit = {
    for (row <- edgeData.asScala) {
      val startNodeId = row.get("startNodeId").toString
      val endNodeId = row.get("endNodeId").toString
      val relation = row.get("relation").toString

      val client = DriverUtil.getGraphClient(graphId, GraphOperation.WRITE)
      val deleteRelationsQuery = EdgeUtil.deleteRelationsQuery(graphId, startNodeId, endNodeId)
      client.submit(deleteRelationsQuery)
    }
  }

    def getSubGraph(graphId: String, nodeId: String, depth: Integer): Future[SubGraph] = {
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name, DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Get SubGraph Operation Failed.]")
      if (StringUtils.isBlank(nodeId))
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name, DACErrorMessageConstants.INVALID_IDENTIFIER + " | [Please Provide Node Identifier.]")
      var effectiveDepth:Integer  = if (depth == null) 5 else depth

      TelemetryManager.log("Driver Initialised. | [Graph Id: " + graphId + "]")

      val relationMap = new util.HashMap[Object, AnyRef]()
      var nodes = new util.HashSet[org.sunbird.graph.dac.model.Node]
      var relations = new util.HashSet[org.sunbird.graph.dac.model.Relation]
      val startNodeMap = new util.HashMap[Object, AnyRef]
      val endNodeMap = new util.HashMap[Object, AnyRef]

      val subGraphQuery = EdgeUtil.getSubGraphQuery(graphId: String, nodeId: String, effectiveDepth: Integer)
      val client = DriverUtil.getGraphClient(graphId, GraphOperation.READ)
      val resultSet: ResultSet = client.submit(subGraphQuery)
      val results: util.List[Result] = resultSet.all().get()

      for (result <- results) {
        val res = result.getObject.asInstanceOf[util.Map[String, AnyRef]]
        val startNode = res.get("startNode").asInstanceOf[Vertex]
        val endNode = res.get("endNode").asInstanceOf[Vertex]
        val relationName = res.get("relationName").toString
        val relationMetadata = res.get("relationMetadata").asInstanceOf[util.Map[String, Object]]

        nodes.add(gremlinVertexUtil.getNode(graphId, startNode, relationMap, startNodeMap, endNodeMap))
        nodes.add(gremlinVertexUtil.getNode(graphId, endNode, relationMap, startNodeMap, endNodeMap))

        // Relation Metadata
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

      // Group nodes by their identifier and get the first node for each identifier
      val uniqNodes = nodes.groupBy(_.getIdentifier).mapValues(_.head).values.toSet

      // Create a map with the node identifier as the key and the node itself as the value
      val nodeMap= uniqNodes.map(node => node.getIdentifier -> node).toMap
      val relationsList= relations.toList

      // Convert Scala collections to Java collections
      val javaNodeMap: java.util.Map[String, org.sunbird.graph.dac.model.Node] = nodeMap.asJava
      val javaRelationsList: java.util.List[org.sunbird.graph.dac.model.Relation] = relationsList.asJava

      // Create a VertexSubGraph instance
      Future {
        new SubGraph(javaNodeMap, javaRelationsList)
      }
    }
}
