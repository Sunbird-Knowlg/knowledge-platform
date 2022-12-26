package org.sunbird.graph

import org.sunbird.common.Platform
import org.sunbird.common.dto.{Property, Request, Response, ResponseHandler}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.service.operation.{GraphAsyncOperations, Neo4JBoltSearchOperations, NodeAsyncOperations, SearchAsyncOperations}
import org.sunbird.graph.util.CSPMetaUtil

import java.lang
import scala.concurrent.{ExecutionContext, Future}

class GraphService {
    implicit  val ec: ExecutionContext = ExecutionContext.global
    val isrRelativePathEnabled: lang.Boolean = Platform.getBoolean("cloudstorage.metadata.replace_absolute_path", false)

    def addNode(graphId: String, node: Node): Future[Node] = {
        if(isrRelativePathEnabled) {
            val metadata = CSPMetaUtil.updateRelativePath(node.getMetadata)
            node.setMetadata(metadata)
        }
        NodeAsyncOperations.addNode(graphId, node).map(resNode => if(isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(resNode) else resNode)
    }

    def upsertNode(graphId: String, node: Node, request: Request): Future[Node] = {
        if(isrRelativePathEnabled) {
            val metadata = CSPMetaUtil.updateRelativePath(node.getMetadata)
            node.setMetadata(metadata)
        }
        NodeAsyncOperations.upsertNode(graphId, node, request).map(resNode => if(isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(resNode) else resNode)
    }

    def upsertRootNode(graphId: String, request: Request): Future[Node] = {
        NodeAsyncOperations.upsertRootNode(graphId, request)
    }

    def getNodeByUniqueId(graphId: String, nodeId: String, getTags: Boolean, request: Request): Future[Node] = {
        SearchAsyncOperations.getNodeByUniqueId(graphId, nodeId, getTags, request).map(node => if(isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(node) else node)
    }

    def deleteNode(graphId: String, nodeId: String, request: Request): Future[java.lang.Boolean] = {
        NodeAsyncOperations.deleteNode(graphId, nodeId, request)
    }

    def getNodeProperty(graphId: String, identifier: String, property: String): Future[Property] = {
        SearchAsyncOperations.getNodeProperty(graphId, identifier, property).map(property => if(isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(property) else property)
    }
    def updateNodes(graphId: String, identifiers:java.util.List[String], metadata:java.util.Map[String,AnyRef]):Future[java.util.Map[String, Node]] = {
        val updatedMetadata = if(isrRelativePathEnabled) CSPMetaUtil.updateRelativePath(metadata) else metadata
        NodeAsyncOperations.updateNodes(graphId, identifiers, updatedMetadata)
    }

    def getNodeByUniqueIds(graphId:String, searchCriteria: SearchCriteria): Future[java.util.List[Node]] = {
        SearchAsyncOperations.getNodeByUniqueIds(graphId, searchCriteria).map(nodes => if(isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(nodes) else nodes)
    }

    def readExternalProps(request: Request, fields: List[String]): Future[Response] = {
        ExternalPropsManager.fetchProps(request, fields).map(res => {
            if(isrRelativePathEnabled && res.getResponseCode == ResponseCode.OK) {
                val updatedResult = CSPMetaUtil.updateExternalAbsolutePath(res.getResult)
                val response = ResponseHandler.OK()
                response.putAll(updatedResult)
                response
            } else res})
    }

    def saveExternalProps(request: Request): Future[Response] = {
        val externalProps: java.util.Map[String, AnyRef] = request.getRequest
        val updatedExternalProps = if(isrRelativePathEnabled) CSPMetaUtil.saveExternalRelativePath(externalProps) else externalProps
        request.setRequest(updatedExternalProps)
        ExternalPropsManager.saveProps(request)
    }

    def updateExternalProps(request: Request): Future[Response] = {
        val externalProps: java.util.Map[String, AnyRef] = request.getRequest
        val updatedExternalProps = if (isrRelativePathEnabled) CSPMetaUtil.updateExternalRelativePath(externalProps) else externalProps
        request.setRequest(updatedExternalProps)
        ExternalPropsManager.update(request)
    }

    def deleteExternalProps(request: Request): Future[Response] = {
        ExternalPropsManager.deleteProps(request)
    }
    def checkCyclicLoop(graphId:String, endNodeId: String, startNodeId: String, relationType: String) = {
        Neo4JBoltSearchOperations.checkCyclicLoop(graphId, endNodeId, relationType, startNodeId)
    }

    def removeRelation(graphId: String, relationMap: java.util.List[java.util.Map[String, AnyRef]]) = {
        GraphAsyncOperations.removeRelation(graphId, relationMap)
    }

    def createRelation(graphId: String, relationMap: java.util.List[java.util.Map[String, AnyRef]]) = {
        GraphAsyncOperations.createRelation(graphId, relationMap)
    }
}

