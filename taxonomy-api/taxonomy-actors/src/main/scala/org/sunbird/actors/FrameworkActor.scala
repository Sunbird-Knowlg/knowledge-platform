package org.sunbird.actors

import java.util.concurrent.CompletionException

import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ResponseCode}
import org.sunbird.graph.nodes.DataNode

import scala.concurrent.{ExecutionContext, Future}

class FrameworkActor extends BaseActor {
  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      //      case "createFranework" => create(request)
      //      case "readFramework" => read(request)
      //  case "updateFramework" => update(request)
      case "retireFramework" => retire(request)
      case _ => ERROR(request.getOperation)
    }
  }

  def retire(request: Request): Future[Response] = {
    val ownerChannelId = request.get("channel")
    request.put("identifier", request.getContext.get("identifier"))
    DataNode.read(request).map(resp => {
      if (resp != null) {
        if (ownerChannelId == resp.getMetadata.get("channel")) {
          if (resp.getMetadata.get("status") == "Live") {
            request.put("status", "Retired")
            DataNode.update(request).map(node => {
              val response = ResponseHandler.OK
              response.put("identifier", node.getIdentifier)
              response
            })
          }
          else
            throw new ClientException("ERR_SERVER_ERROR_UPDATE_FRAMEWORK", "Cannot update.Status is already set to Retired.")

        }
        else {
          throw new ClientException("ERR_SERVER_ERROR_UPDATE_FRAMEWORK", "Invalid Request. Channel Id Not Matched." + ownerChannelId)
        }
      }
      else {
        throw new ResourceNotFoundException("ERR_FRAMEWORK_NOT_FOUND", "Framework Not Found With Id : " + request.get("identifier"))
      }
    }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }

  }
}