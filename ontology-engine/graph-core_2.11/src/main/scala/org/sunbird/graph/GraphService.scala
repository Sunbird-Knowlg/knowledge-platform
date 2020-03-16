package org.sunbird.graph

import org.sunbird.common.dto.Request
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.service.operation.NodeAsyncOperations

import scala.concurrent.Future

class GraphService {

    def addNode(graphId: String, node: Node): Future[Node] = {
        NodeAsyncOperations.addNode(graphId, node)
    }

    def upsertRootNode(graphId: String, request: Request): Future[Node] = {
        NodeAsyncOperations.upsertRootNode(graphId, request)
    }

}
