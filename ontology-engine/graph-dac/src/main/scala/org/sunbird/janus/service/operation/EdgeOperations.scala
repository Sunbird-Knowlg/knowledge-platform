package org.sunbird.janus.service.operation

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.structure.{Edge, Vertex}
import org.janusgraph.core.JanusGraph
import org.sunbird.common.dto.{Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.service.common.{DACErrorCodeConstants, DACErrorMessageConstants}
import org.sunbird.janus.service.util.JanusConnectionUtil

import java.util
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EdgeOperations {

  val graphConnection = new JanusConnectionUtil
  def createEdges(graphId: String, relationData: util.List[util.Map[String, AnyRef]]): Future[Response] = {
    Future{
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Node Operation Failed.]")
      if (CollectionUtils.isEmpty(relationData))
        throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name,
          DACErrorMessageConstants.INVALID_NODE + " | [Create Relation Operation Failed.]")

      graphConnection.initialiseGraphClient()
      val g: GraphTraversalSource = graphConnection.getGts
      val graph: JanusGraph = graphConnection.getGraph

      createBulkRelations(g, graphId, relationData)
      ResponseHandler.OK()
    }
  }

  def createBulkRelations(g: GraphTraversalSource, graphId: String, relationData: util.List[util.Map[String, AnyRef]]): Unit = {
    for (row <- relationData.asScala) {
      val startNodeId = row.get("startNodeId").toString
      val endNodeId = row.get("endNodeId").toString
      val relation = row.get("relation").toString
      val relMetadata = row.get("relMetadata").asInstanceOf[Map[String, AnyRef]]

      val startNode: Vertex = g.V().hasLabel(graphId).has("IL_UNIQUE_ID", startNodeId).next()
      val endNode: Vertex = g.V().hasLabel(graphId).has("IL_UNIQUE_ID", endNodeId).next()

      val edge: Edge = startNode.addEdge(relation, endNode)
      for (key <- relMetadata.keySet) {
        edge.property(key, relMetadata.get(key).toString)
      }
    }
  }
}
