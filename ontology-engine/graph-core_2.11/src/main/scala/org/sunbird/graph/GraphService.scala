package org.sunbird.graph

import org.sunbird.common.dto.Request
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.service.operation.{NodeAsyncOperations, SearchAsyncOperations}

import scala.concurrent.Future

class GraphService {

    def addNode(graphId: String, node: Node): Future[Node] = {
        NodeAsyncOperations.addNode(graphId, node)
    }

    def upsertRootNode(graphId: String, request: Request): Future[Node] = {
        NodeAsyncOperations.upsertRootNode(graphId, request)
    }

    def getNodeByUniqueId(graphId: String, nodeId: String, getTags: Boolean, request: Request): Future[Node] = {
        SearchAsyncOperations.getNodeByUniqueId(graphId, nodeId, getTags, request)
    }

}
