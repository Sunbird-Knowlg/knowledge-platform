package org.sunbird.graph

import org.sunbird.common.Platform
import org.sunbird.common.dto.{Property, Request, Response, ResponseHandler}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.dac.model.{Node, SearchCriteria, SubGraph}
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.service.operation.{GraphAsyncOperations, Neo4JBoltSearchOperations, NodeAsyncOperations, SearchAsyncOperations}
import org.sunbird.graph.util.CSPMetaUtil

import java.lang
import scala.concurrent.{ExecutionContext, Future}

trait BaseGraphService {

    implicit  val ec: ExecutionContext = ExecutionContext.global
    val isrRelativePathEnabled: lang.Boolean = Platform.getBoolean("cloudstorage.metadata.replace_absolute_path", false)

    def addNode(graphId: String, node: Node): Future[Node]

    def upsertNode(graphId: String, node: Node, request: Request): Future[Node]

    def upsertRootNode(graphId: String, request: Request): Future[Node]

    def getNodeByUniqueId(graphId: String, nodeId: String, getTags: Boolean, request: Request): Future[Node]

    def deleteNode(graphId: String, nodeId: String, request: Request): Future[java.lang.Boolean]

    def getNodeProperty(graphId: String, identifier: String, property: String): Future[Property]
    def updateNodes(graphId: String, identifiers:java.util.List[String], metadata:java.util.Map[String,AnyRef]):Future[java.util.Map[String, Node]]

    def getNodeByUniqueIds(graphId:String, searchCriteria: SearchCriteria): Future[java.util.List[Node]]

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

    def saveExternalPropsWithTtl(request: Request, ttl: Int): Future[Response] = {
        val externalProps: java.util.Map[String, AnyRef] = request.getRequest
        val updatedExternalProps = if (isrRelativePathEnabled) CSPMetaUtil.saveExternalRelativePath(externalProps) else externalProps
        request.setRequest(updatedExternalProps)
        ExternalPropsManager.savePropsWithTtl(request, ttl)
    }
    def updateExternalProps(request: Request): Future[Response] = {
        val externalProps: java.util.Map[String, AnyRef] = request.getRequest
        val updatedExternalProps = if (isrRelativePathEnabled) CSPMetaUtil.updateExternalRelativePath(externalProps) else externalProps
        request.setRequest(updatedExternalProps)
        ExternalPropsManager.update(request)
    }

    def updateExternalPropsWithTtl(request: Request, ttl: Int): Future[Response] = {
        val externalProps: java.util.Map[String, AnyRef] = request.getRequest
        val updatedExternalProps = if (isrRelativePathEnabled) CSPMetaUtil.updateExternalRelativePath(externalProps) else externalProps
        request.setRequest(updatedExternalProps)
        ExternalPropsManager.updateWithTtl(request, ttl)
    }

    def deleteExternalProps(request: Request): Future[Response] = {
        ExternalPropsManager.deleteProps(request)
    }
    def checkCyclicLoop(graphId:String, endNodeId: String, startNodeId: String, relationType: String): java.util.Map[String, Object]

    def removeRelation(graphId: String, relationMap: java.util.List[java.util.Map[String, AnyRef]]): Future[Response]

    def createRelation(graphId: String, relationMap: java.util.List[java.util.Map[String, AnyRef]]): Future[Response]

    def getSubGraph(graphId: String, nodeId: String, depth: Int): Future[SubGraph]
}

