package org.sunbird.channel.actors

import java.util

import javax.inject.Inject
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
import org.sunbird.graph.OntologyEngineContext

class ChannelActor @Inject() (implicit oec: OntologyEngineContext) extends BaseActor {
    implicit val ec: ExecutionContext = getContext().dispatcher

    val suggestFrameworks = if(Platform.config.hasPath("channel.fetch.suggested_frameworks")) Platform.config.getBoolean("channel.fetch.suggested_frameworks") else true

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
        if (!request.getRequest.containsKey("code"))
            throw new ClientException("ERR_CODE_IS_REQUIRED", "Code is required for creating a channel")
        request.getRequest.put("identifier", request.getRequest.get("code").asInstanceOf[String])
        ChannelManager.validateTranslationMap(request)
        ChannelManager.validateObjectCategory(request)
        DataNode.create(request).map(node => {
            ChannelManager.channelLicenseCache(request, node.getIdentifier)
            ResponseHandler.OK.put("identifier", node.getIdentifier).put("node_id", node.getIdentifier)
        })
    }

    def read(request: Request): Future[Response] = {
        DataNode.read(request).map(node => {
            val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, null, request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
            if (suggestFrameworks && CollectionUtils.isEmpty(metadata.getOrDefault("frameworks", new util.ArrayList[AnyRef]()).asInstanceOf[util.List[AnyRef]])) {
                val frameworkList = ChannelManager.getAllFrameworkList()
                if (!frameworkList.isEmpty) metadata.put("suggested_frameworks", frameworkList)
            }
            ChannelManager.setPrimaryAndAdditionCategories(metadata)
            ResponseHandler.OK.put("channel", metadata)
        })
    }

    def update(request: Request): Future[Response] = {
        RequestUtil.restrictProperties(request)
        ChannelManager.validateTranslationMap(request)
        ChannelManager.validateObjectCategory(request)
        request.getRequest.put("status", "Live")
        DataNode.update(request).map(node => {
            val identifier: String = node.getIdentifier
            ChannelManager.channelLicenseCache(request, identifier)
            ResponseHandler.OK.put("node_id", identifier).put("identifier", identifier)
        })
    }

    def retire(request: Request): Future[Response] = {
        request.getRequest.put("status", "Retired")
        DataNode.update(request).map(node => {
            val identifier: String = node.getIdentifier
            ResponseHandler.OK.put("node_id", identifier).put("identifier", identifier)
        })
    }

}
