package org.sunbird.graph

import java.util
import org.sunbird.common.dto.{Property, Request, Response}
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.external.store.ExternalStore
import org.sunbird.graph.service.operation.{GraphAsyncOperations, Neo4JBoltSearchOperations, NodeAsyncOperations, SearchAsyncOperations}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class GraphService {
    implicit  val ec: ExecutionContext = ExecutionContext.global

    def addNode(graphId: String, node: Node): Future[Node] = {
        println("CALLEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEED addNode")
        println("prototype: (graphId: String, node: Node)")
        println("-------------------------------------------------------------------------")
        println("graphId: "+graphId)
        println("Node MetaData: "+node.getMetadata)
        println("Node ExternalData: "+node.getExternalData)
        println("Node Identifier: "+node.getIdentifier)
        println("Node ObjectType: "+node.getObjectType)
        println("Node : "+node.getNode)
        println("Node nodeType: "+node.getNodeType)
        println("-------------------------------------------------------------------------")
        val result = Await.ready(NodeAsyncOperations.addNode(graphId, node),Duration(5, TimeUnit.SECONDS))
        println("RESULT:"+result)
        result
    }

    def upsertNode(graphId: String, node: Node, request: Request): Future[Node] = {
        println("CALLEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEED upsertNode")
        println("prototype: (graphId: String, node: Node, request: Request)")
        println("-------------------------------------------------------------------------")
        println("graphId: "+graphId)
        println("Node MetaData: "+node.getMetadata)
        println("Node ExternalData: "+node.getExternalData)
        println("Node Identifier: "+node.getIdentifier)
        println("Node ObjectType: "+node.getObjectType)
        println("Node : "+node.getNode)
        println("Node nodeType: "+node.getNodeType)
        println("Request Context: "+request.getContext)
        println("Request Params: "+request.getParams)
        println("Request ObjectType: "+request.getObjectType)
        println("-------------------------------------------------------------------------")
        val result = Await.ready(NodeAsyncOperations.upsertNode(graphId, node, request),Duration(5, TimeUnit.SECONDS))
        println("RESULT:"+result)
        result
    }

    def upsertRootNode(graphId: String, request: Request): Future[Node] = {
        NodeAsyncOperations.upsertRootNode(graphId, request)
    }

    def getNodeByUniqueId(graphId: String, nodeId: String, getTags: Boolean, request: Request): Future[Node] = {
        println("CALLEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEED getNodeByUniqueId")
        println("prototype: (graphId: String, nodeId: String, getTags: Boolean, request: Request)")
        println("-------------------------------------------------------------------------")
        println("graphId: "+graphId)
        println("nodeId: "+nodeId)
        println("getTags: "+getTags)
        println("Request Context: "+request.getContext)
        println("Request Params: "+request.getParams)
        println("Request ObjectType: "+request.getObjectType)
        println("Request Params Sid: "+request.getParams.getSid)
        println("Request Params Cid: "+request.getParams.getCid)
        println("Request Params Did: "+request.getParams.getDid)
        println("Request Params Key: "+request.getParams.getKey)
        println("Request Params Uid: "+request.getParams.getUid)
        println("Request Params Msgid: "+request.getParams.getMsgid)
        println("-------------------------------------------------------------------------")
        val result = Await.ready(SearchAsyncOperations.getNodeByUniqueId(graphId, nodeId, getTags, request),Duration(5, TimeUnit.SECONDS))
        println("RESULT:"+result)
        result
    }

    def deleteNode(graphId: String, nodeId: String, request: Request): Future[java.lang.Boolean] = {
        NodeAsyncOperations.deleteNode(graphId, nodeId, request)
    }

    def getNodeProperty(graphId: String, identifier: String, property: String): Future[Property] = {
        SearchAsyncOperations.getNodeProperty(graphId, identifier, property)
    }
    def updateNodes(graphId: String, identifiers:util.List[String], metadata:util.Map[String,AnyRef]):Future[util.Map[String, Node]] = {
        NodeAsyncOperations.updateNodes(graphId, identifiers, metadata)
    }

    def getNodeByUniqueIds(graphId:String, searchCriteria: SearchCriteria): Future[util.List[Node]] = {
        println("CALLEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEED getNodeByUniqueIds")
        println("prototype: (graphId:String, searchCriteria: SearchCriteria)")
        println("-------------------------------------------------------------------------")
        println("graphId: "+graphId)
        println("searchCriteria Params: "+searchCriteria.getParams)
        println("searchCriteria GraphId: "+searchCriteria.getGraphId)
        println("searchCriteria ObjectType: "+searchCriteria.getObjectType)
        println("searchCriteria ObjectType: "+searchCriteria.getNodeType)
        println("searchCriteria Fields: "+searchCriteria.getFields)
        println("searchCriteria MetaData: "+searchCriteria.getMetadata)
        println("-------------------------------------------------------------------------")
        val result = Await.ready(SearchAsyncOperations.getNodeByUniqueIds(graphId, searchCriteria),Duration(5, TimeUnit.SECONDS))
        println("RESULT:"+result)
        result
    }

    def readExternalProps(request: Request, fields: List[String]): Future[Response] = {
        println("CALLEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEED readExternalProps")
        println("prototype: (request: Request, fields: List[String])")
        println("-------------------------------------------------------------------------")
        println("fields: "+fields)
        println("Request Context: "+request.getContext)
        println("Request Params Keys: "+request.getParams.getKey)
        println("Request Params Sid: "+request.getParams.getSid)
        println("Request Params Cid: "+request.getParams.getCid)
        println("Request Params Did: "+request.getParams.getDid)
        println("Request ObjectType: "+request.getObjectType)
        println("-------------------------------------------------------------------------")
        val result = Await.ready(ExternalPropsManager.fetchProps(request, fields),Duration(5, TimeUnit.SECONDS))
        println("RESULT:"+result)
        result
    }

    def saveExternalProps(request: Request): Future[Response] = {
        println("CALLEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEED saveExternalProps")
        println("prototype: (request: Request)")
        println("-------------------------------------------------------------------------")
        println("Request Context: "+request.getContext)
        println("Request Params: "+request.getParams)
        println("Request ObjectType: "+request.getObjectType)
        println("-------------------------------------------------------------------------")
        val result = Await.ready(ExternalPropsManager.saveProps(request),Duration(5, TimeUnit.SECONDS))
        println("RESULT:"+result)
        result
    }

    def updateExternalProps(request: Request): Future[Response] = {
        println("CALLEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEED updateExternalProps")
        println("prototype: (request: Request)")
        println("-------------------------------------------------------------------------")
        println("Request Context: "+request.getContext)
        println("Request Params: "+request.getParams)
        println("Request ObjectType: "+request.getObjectType)
        println("-------------------------------------------------------------------------")
        val result = Await.ready(ExternalPropsManager.update(request),Duration(5, TimeUnit.SECONDS))
        println("RESULT:"+result)
        result
    }

    def deleteExternalProps(request: Request): Future[Response] = {
        ExternalPropsManager.deleteProps(request)
    }
    def checkCyclicLoop(graphId:String, endNodeId: String, startNodeId: String, relationType: String) = {
        Neo4JBoltSearchOperations.checkCyclicLoop(graphId, endNodeId, relationType, startNodeId)
    }

    def removeRelation(graphId: String, relationMap: util.List[util.Map[String, AnyRef]]) = {
        GraphAsyncOperations.removeRelation(graphId, relationMap)
    }

    def createRelation(graphId: String, relationMap: util.List[util.Map[String, AnyRef]]) = {
        GraphAsyncOperations.createRelation(graphId, relationMap)
    }
}

