package org.sunbird.actors

import java.util.concurrent.CompletionException
import scala.concurrent.{ExecutionContext, Future}
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException}
import org.sunbird.graph.nodes.DataNode
class  FrameworkActor extends BaseActor {

    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = request.getOperation match {
        case "createFramework" => create(request)
        case "updateFramework" => update(request)
        case _ => ERROR(request.getOperation)
    }

    def create(request: Request): Future[Response] = {
        //TODO:  include RequestUtils.restrictproperties method
        if (request.getRequest.containsKey("code"))
            request.put("identifier", request.getRequest.get("code").asInstanceOf[String])
        else
            throw new ClientException("ERR_CODE_IS_REQUIRED", "Code is required for creating a channel")
        DataNode.create(request).map(node => {
            val response = ResponseHandler.OK
            response.put("identifier", node.getIdentifier)
            response
        })
    }

    def update(request: Request): Future[Response] = {
        val requestChannelId = request.get("channel")
        request.put("identifier", request.getContext.get("identifier"))
        DataNode.read(request).map(resp => {
            if (resp != null) {
                if (requestChannelId == resp.getMetadata.get("channel")) {
                    DataNode.update(request).map(node => {
                        val response = ResponseHandler.OK
                        response.put("identifier", node.getIdentifier)
                        response
                    })
                }
                else {
                    throw new ClientException("ERR_SERVER_ERROR_UPDATE_FRAMEWORK", "Invalid Request. Channel Id Not Matched." + requestChannelId)
                }
            }
            else {
                throw new ResourceNotFoundException("ERR_FRAMEWORK_NOT_FOUND", "Framework Not Found With Id : " + request.get("identifier"))
            }
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
    }

}