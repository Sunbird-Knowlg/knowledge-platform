package org.sunbird.graph

import org.sunbird.common.Platform
import org.sunbird.common.dto.{Property, Request, Response}
import org.sunbird.graph.dac.model.{Vertex, VertexSubGraph}
import org.sunbird.graph.util.CSPMetaUtil
import org.sunbird.janus.service.operation.{EdgeOperations, SearchOperations, VertexOperations}

import java.lang
import scala.concurrent.{ExecutionContext, Future}
class JanusGraphService {

  private val VertexOperations = new VertexOperations()
  private val EdgeOperations = new EdgeOperations()
  private val SearchOperations = new SearchOperations()

  implicit val ec: ExecutionContext = ExecutionContext.global
  val isrRelativePathEnabled: lang.Boolean = Platform.getBoolean("cloudstorage.metadata.replace_absolute_path", false)


  def addVertex(graphId: String, vertex: Vertex): Future[Vertex] = {
    if (isrRelativePathEnabled) {
      val metadata = CSPMetaUtil.updateRelativePath(vertex.getMetadata)
      vertex.setMetadata(metadata)
    }
    VertexOperations.addVertex(graphId, vertex).map(resVertex => if (isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(resVertex) else resVertex)

  }

  def createEdges(graphId: String, edgeMap: java.util.List[java.util.Map[String, AnyRef]]): Future[Response] = {
    EdgeOperations.createEdges(graphId, edgeMap)
  }

  def removeEdges(graphId: String, edgeMap: java.util.List[java.util.Map[String, AnyRef]]): Future[Response] = {
    EdgeOperations.removeEdges(graphId, edgeMap)
  }

  def getNodeByUniqueId(graphId: String, vertexId: String, getTags: Boolean, request: Request): Future[Vertex] = {
    SearchOperations.getNodeByUniqueId(graphId, vertexId, getTags, request).map(vertex => if (isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(vertex) else vertex)
  }

  def deleteNode(graphId: String, vertexId: String, request: Request): Future[java.lang.Boolean] = {
    VertexOperations.deleteVertex(graphId, vertexId, request)
  }

  def upsertVertex(graphId: String, vertex: Vertex, request: Request): Future[Vertex] = {
    if (isrRelativePathEnabled) {
      val metadata = CSPMetaUtil.updateRelativePath(vertex.getMetadata)
      vertex.setMetadata(metadata)
    }
    VertexOperations.upsertVertex(graphId, vertex, request)
      .map(resVertex => if (isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(resVertex) else resVertex)
  }

  def upsertRootNode(graphId: String, request: Request): Future[Vertex] = {
    VertexOperations.upsertRootVertex(graphId, request)
  }

  def updateVertexes(graphId: String, identifiers: java.util.List[String], metadata: java.util.Map[String, AnyRef]): Future[java.util.Map[String, Vertex]] = {
    val updatedMetadata = if (isrRelativePathEnabled) CSPMetaUtil.updateRelativePath(metadata) else metadata
    VertexOperations.updateVertexes(graphId, identifiers, updatedMetadata)
  }

  def getNodeProperty(graphId: String, identifier: String, property: String): Future[Property] = {
    SearchOperations.getNodeProperty(graphId, identifier, property).map(property => if (isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(property) else property)
  }

  def checkCyclicLoop(graphId: String, endNodeId: String, startNodeId: String, relationType: String) = {
    SearchOperations.checkCyclicLoop(graphId, endNodeId, relationType, startNodeId)
  }

  def getSubGraph(graphId: String, nodeId: String, depth: Int): Future[VertexSubGraph] = {
    EdgeOperations.getSubGraph(graphId, nodeId, depth)
  }
}