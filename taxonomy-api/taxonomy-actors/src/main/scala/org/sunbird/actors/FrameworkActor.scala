package org.sunbird.actors

import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.managers.FrameworkMgrImpl

class FrameworkActor @Inject() (implicit oec: OntologyEngineContext) extends BaseActor {

    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = request.getOperation match {
        case "createFramework" => create(request)
        case _ => ERROR(request.getOperation)
    }

    def create(request: Request): Future[Response] = {
        if (request.getRequest.containsKey("code"))
            request.put("identifier", request.getRequest.get("code").asInstanceOf[String])
        else
            throw new ClientException("ERR_CODE_IS_REQUIRED", "Code is required for creating a channel")
        FrameworkMgrImpl.validateCreateRequest(request).map(resp => {
            if (!ResponseHandler.checkError(resp)) {
                DataNode.create(request).map(node => {
                    val response = ResponseHandler.OK
                    response.put("identifier", node.getIdentifier)
                    response
                })
            } else
                Future(resp)
        }).flatMap(f => f)
    }
}



