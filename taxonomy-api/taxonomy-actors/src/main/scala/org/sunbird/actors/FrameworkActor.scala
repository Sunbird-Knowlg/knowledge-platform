package org.sunbird.actors

import scala.concurrent.{ExecutionContext, Future}
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.nodes.DataNode
abstract class FrameworkActor extends BaseActor {

    implicit val ec: ExecutionContext = getContext().dispatcher 
    
    override def onReceive(request: Request): Future[Response] = request.getOperation match {
        case "createFramework" => create(request)
        case _ => ERROR(request.getOperation)
    }

    def create(request: Request): Future[Response]  = DataNode.create(request).map(node => {
        val response = ResponseHandler.OK
        response.put("identifier", node.getIdentifier)
        response
    })
    
}
