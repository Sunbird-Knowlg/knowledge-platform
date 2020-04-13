package org.sunbird.graph

import java.util
import java.util.concurrent.CompletionException
import java.util.HashMap
import org.sunbird.common.dto.{Property, Request}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.service.operation.{NodeAsyncOperations, SearchAsyncOperations}
import scala.collection.JavaConversions._
import scala.concurrent.Future

class GraphService {

    def addNode(graphId: String, node: Node): Future[Node] = {
        NodeAsyncOperations.addNode(graphId, node)
    }

    def upsertNode(graphId: String, node: Node, request: Request): Future[Node] = {
        NodeAsyncOperations.upsertNode(graphId, node, request)
    }

    def upsertRootNode(graphId: String, request: Request): Future[Node] = {
        NodeAsyncOperations.upsertRootNode(graphId, request)
    }

    def getNodeByUniqueId(graphId: String, nodeId: String, getTags: Boolean, request: Request): Future[Node] = {
        SearchAsyncOperations.getNodeByUniqueId(graphId, nodeId, getTags, request)
    }

    def deleteNode(graphId: String, nodeId: String, request: Request): Future[java.lang.Boolean] = {
        NodeAsyncOperations.deleteNode(graphId, nodeId, request)
    }

    def getNodeProperty(graphId: String, identifier: String, property: String): Future[Property] = {
        SearchAsyncOperations.getNodeProperty(graphId, identifier, property)
    }
    def updateNodes(graphId: String, identifiers:List[String], metadata:HashMap[String,AnyRef]):Future[util.Map[String, Node]] = {
        NodeAsyncOperations.updateNodes(graphId, identifiers, metadata)
    }
}
