package org.sunbird.graph

import org.sunbird.common.Platform
import org.sunbird.common.dto.{Property, Request, Response}
import org.sunbird.graph.dac.model.{Node, SearchCriteria, SubGraph}
import org.sunbird.graph.service.operation.{GraphAsyncOperations, Neo4JBoltSearchOperations, NodeAsyncOperations, SearchAsyncOperations}
import org.sunbird.graph.util.CSPMetaUtil

import java.lang
import scala.concurrent.{ExecutionContext, Future}

class Neo4jGraphService extends BaseGraphService {

  override implicit val ec: ExecutionContext = ExecutionContext.global
  override def addNode(graphId: String, node: Node): Future[Node] = {
    if (isrRelativePathEnabled) {
      val metadata = CSPMetaUtil.updateRelativePath(node.getMetadata)
      node.setMetadata(metadata)
    }
    NodeAsyncOperations.addNode(graphId, node).map(resNode => if (isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(resNode) else resNode)
  }

  override def upsertNode(graphId: String, node: Node, request: Request): Future[Node] = {
    if (isrRelativePathEnabled) {
      val metadata = CSPMetaUtil.updateRelativePath(node.getMetadata)
      node.setMetadata(metadata)
    }
    NodeAsyncOperations.upsertNode(graphId, node, request).map(resNode => if (isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(resNode) else resNode)
  }

  override def upsertRootNode(graphId: String, request: Request): Future[Node] = {
    NodeAsyncOperations.upsertRootNode(graphId, request)
  }

  override def getNodeByUniqueId(graphId: String, nodeId: String, getTags: Boolean, request: Request, propTypeMap: Option[Map[String, AnyRef]] = None): Future[Node] = {
    SearchAsyncOperations.getNodeByUniqueId(graphId, nodeId, getTags, request).map(node => if (isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(node) else node)
  }

  override def deleteNode(graphId: String, nodeId: String, request: Request): Future[java.lang.Boolean] = {
    NodeAsyncOperations.deleteNode(graphId, nodeId, request)
  }

  override def getNodeProperty(graphId: String, identifier: String, property: String): Future[Property] = {
    SearchAsyncOperations.getNodeProperty(graphId, identifier, property).map(property => if (isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(property) else property)
  }

  override def updateNodes(graphId: String, identifiers: java.util.List[String], metadata: java.util.Map[String, AnyRef]): Future[java.util.Map[String, Node]] = {
    val updatedMetadata = if (isrRelativePathEnabled) CSPMetaUtil.updateRelativePath(metadata) else metadata
    NodeAsyncOperations.updateNodes(graphId, identifiers, updatedMetadata)
  }

  override def getNodeByUniqueIds(graphId: String, searchCriteria: SearchCriteria): Future[java.util.List[Node]] = {
    SearchAsyncOperations.getNodeByUniqueIds(graphId, searchCriteria).map(nodes => if (isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(nodes) else nodes)
  }

  override def checkCyclicLoop(graphId: String, endNodeId: String, startNodeId: String, relationType: String): java.util.Map[String, Object] = {
    Neo4JBoltSearchOperations.checkCyclicLoop(graphId, endNodeId, relationType, startNodeId)
  }

  override def removeRelation(graphId: String, relationMap: java.util.List[java.util.Map[String, AnyRef]]): Future[Response] = {
    GraphAsyncOperations.removeRelation(graphId, relationMap)
  }

  override def createRelation(graphId: String, relationMap: java.util.List[java.util.Map[String, AnyRef]]): Future[Response] = {
    GraphAsyncOperations.createRelation(graphId, relationMap)
  }

  override def getSubGraph(graphId: String, nodeId: String, depth: Int): Future[SubGraph] = {
    GraphAsyncOperations.getSubGraph(graphId, nodeId, depth)
  }

}
