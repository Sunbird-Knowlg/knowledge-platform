package org.sunbird.channel.actors

import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.util.RequestUtil
import org.sunbird.channel.managers.ChannelManager
import scala.concurrent.{ExecutionContext, Future}

class ChannelActor extends BaseActor {
    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = {
        request.getOperation match {
            case "createChannel" => create(request)
            case "readChannel" => ???
            case "updateChannel" => ???
            case "listChannel" => ???
            case _ => ERROR(request.getOperation)
        }
    }

    def create(request: Request): Future[Response] = {
        RequestUtil.restrictProperties(request)
        if (request.getRequest.containsKey("code"))
            request.getRequest.put("identifier", request.getRequest.get("code").asInstanceOf[String])
        ChannelManager.validateLicense(request)
        DataNode.create(request).map(node => {
            val response = ResponseHandler.OK
            response.put("identifier", node.getIdentifier)
            response.put("node_id", node.getIdentifier)
            response.put("versionKey", node.getMetadata.get("versionKey"))
            ChannelManager.channelLicenseCache(response, request)
            response
        })
    }

    def read(request: Request): Future[Response] = ???

    def update(request: Request): Future[Response] = ???

    def list(request: Request): Future[Response] = ???
}
