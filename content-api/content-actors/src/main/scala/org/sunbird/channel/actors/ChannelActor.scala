package org.sunbird.channel.actors

import java.util

import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.util.RequestUtil
import org.sunbird.channel.managers.ChannelManager
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.common.Platform

import scala.concurrent.{ExecutionContext, Future}
import org.apache.commons.collections4.CollectionUtils

class ChannelActor extends BaseActor {
    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = {
        request.getOperation match {
            case "createChannel" => create(request)
            case "readChannel" => read(request)
            case "updateChannel" => update(request)
            case "retireChannel" => retire(request)
            case _ => ERROR(request.getOperation)
        }

    }

    def create(request: Request): Future[Response] = {
        RequestUtil.restrictProperties(request)
        if (request.getRequest.containsKey("code"))
            request.getRequest.put("identifier", request.getRequest.get("code").asInstanceOf[String])
        else
            throw new ClientException("ERR_CODE_IS_REQUIRED", "Code is required for creating a channel")
        ChannelManager.validateLicense(request).map(resp => {
            if (!ResponseHandler.checkError(resp)) {
                DataNode.create(request).map(node => {
                    val response = ResponseHandler.OK
                    response.put("identifier", node.getIdentifier)
                    response.put("node_id", node.getIdentifier)
                    ChannelManager.channelLicenseCache(response, request)
                    response
                })
            } else
                Future(resp)
        }).flatMap(f => f)
    }

    def read(request: Request): Future[Response] = {
        val response = ResponseHandler.OK
        DataNode.read(request).map(node => {
            val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, null, request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
            metadata.put("identifier", node.getIdentifier.replace(".img", ""))
            if (Platform.config.hasPath("channel.fetch.suggested_frameworks") && Platform.config.getBoolean("channel.fetch.suggested_frameworks")) {
                if (CollectionUtils.isEmpty(node.getMetadata.get("frameworks").asInstanceOf[util.List[AnyRef]])) {
                    val searchedFrameworkList = ChannelManager.getAllFrameworkList()
                    searchedFrameworkList.map(frameworkList => {
                        if (!frameworkList.isEmpty) {
                            metadata.put("suggested_frameworks", frameworkList)
                            response.put("channel", metadata)
                        }
                        response
                    })
                } else
                    Future(response)
            } else
                Future(response)
        }).flatMap(f => f)
    }

    def update(request: Request): Future[Response] = {
        RequestUtil.restrictProperties(request)
        ChannelManager.validateLicense(request).map(resp => {
            ChannelManager.validateTranslationMap(request)
            request.getRequest.put("status", "Live")
            DataNode.update(request).map(node => {
                val response: Response = ResponseHandler.OK
                val identifier: String = node.getIdentifier
                response.put("node_id", identifier)
                response.put("identifier", identifier)
                ChannelManager.channelLicenseCache(response, request)
                response
            })
        }).flatMap(f => f)
    }

    def retire(request: Request): Future[Response] = {
        request.getRequest.put("status", "Retired")
        DataNode.update(request).map(node => {
            val response: Response = ResponseHandler.OK
            val identifier: String = node.getIdentifier
            response.put("node_id", identifier)
            response.put("identifier", identifier)
            response
        })
    }

}
