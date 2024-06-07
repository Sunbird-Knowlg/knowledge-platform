package org.sunbird.graph

import org.sunbird.common.Platform
import org.sunbird.common.dto.{Property, Request, Response}
import org.sunbird.graph.dac.model.{SearchCriteria, Node, SubGraph}
import org.sunbird.graph.util.CSPMetaUtil
import org.sunbird.graph.service.operation.{EdgeOperations, SearchOperations, VertexOperations}

import java.lang
import scala.concurrent.{ExecutionContext, Future}
class JanusGraphService extends BaseGraphService {

  private val VertexOperations = new VertexOperations()
  private val EdgeOperations = new EdgeOperations()
  private val SearchOperations = new SearchOperations()

  override implicit val ec: ExecutionContext = ExecutionContext.global
  override def addNode(graphId: String, vertex: Node): Future[Node] = {
    if (isrRelativePathEnabled) {
      val metadata = CSPMetaUtil.updateRelativePath(vertex.getMetadata)
      vertex.setMetadata(metadata)
    }
    VertexOperations.addVertex(graphId, vertex).map(resVertex => if (isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(resVertex) else resVertex)

  }

  override def createRelation(graphId: String, edgeMap: java.util.List[java.util.Map[String, AnyRef]]): Future[Response] = {
    EdgeOperations.createEdges(graphId, edgeMap)
  }

  override def removeRelation(graphId: String, edgeMap: java.util.List[java.util.Map[String, AnyRef]]): Future[Response] = {
    EdgeOperations.removeEdges(graphId, edgeMap)
  }

  override def getNodeByUniqueId(graphId: String, vertexId: String, getTags: Boolean, request: Request): Future[Node] = {
    SearchOperations.getNodeByUniqueId(graphId, vertexId, getTags, request).map(vertex => if (isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(vertex) else vertex)
  }

  override def deleteNode(graphId: String, vertexId: String, request: Request): Future[java.lang.Boolean] = {
    VertexOperations.deleteVertex(graphId, vertexId, request)
  }

  override def upsertNode(graphId: String, vertex: Node, request: Request): Future[Node] = {
    if (isrRelativePathEnabled) {
      val metadata = CSPMetaUtil.updateRelativePath(vertex.getMetadata)
      vertex.setMetadata(metadata)
    }
    VertexOperations.upsertVertex(graphId, vertex, request)
      .map(resVertex => if (isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(resVertex) else resVertex)
  }

  override def upsertRootNode(graphId: String, request: Request): Future[Node] = {
    VertexOperations.upsertRootVertex(graphId, request)
  }

  override def updateNodes(graphId: String, identifiers: java.util.List[String], metadata: java.util.Map[String, AnyRef]): Future[java.util.Map[String, Node]] = {
    val updatedMetadata = if (isrRelativePathEnabled) CSPMetaUtil.updateRelativePath(metadata) else metadata
    VertexOperations.updateVertices(graphId, identifiers, updatedMetadata)
  }

  override def getNodeProperty(graphId: String, identifier: String, property: String): Future[Property] = {
    SearchOperations.getNodeProperty(graphId, identifier, property).map(property => if (isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(property) else property)
  }

  override def checkCyclicLoop(graphId: String, endNodeId: String, startNodeId: String, relationType: String): java.util.Map[String, AnyRef] = {
    SearchOperations.checkCyclicLoop(graphId, endNodeId, relationType, startNodeId)
  }

  override def getSubGraph(graphId: String, nodeId: String, depth: Int): Future[SubGraph] = {
    EdgeOperations.getSubGraph(graphId, nodeId, depth)
  }

  override def getNodeByUniqueIds(graphId: String, searchCriteria: SearchCriteria): Future[java.util.List[Node]] = {
    SearchOperations.getNodeByUniqueIds(graphId, searchCriteria).map(nodes => if (isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(nodes) else nodes)
  }
}