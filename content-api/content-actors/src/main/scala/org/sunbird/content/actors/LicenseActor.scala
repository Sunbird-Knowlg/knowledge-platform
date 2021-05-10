package org.sunbird.content.actors

import java.util

import javax.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.Slug
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.content.util.LicenseConstants
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.util.RequestUtil

import scala.collection.JavaConverters
import scala.concurrent.{ExecutionContext, Future}

class LicenseActor @Inject() (implicit oec: OntologyEngineContext) extends BaseActor {
    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = {
        request.getOperation match {
            case LicenseConstants.CREATE_LICENSE => create(request)
            case LicenseConstants.READ_LICENSE => read(request)
            case LicenseConstants.UPDATE_LICENSE => update(request)
            case LicenseConstants.RETIRE_LICENSE => retire(request)
            case _ => ERROR(request.getOperation)
        }
    }


    @throws[Exception]
    private def create(request: Request): Future[Response] = {
        RequestUtil.restrictProperties(request)
        if (request.getRequest.containsKey("identifier")) throw new ClientException("ERR_NAME_SET_AS_IDENTIFIER", "name will be set as identifier")
        if (request.getRequest.containsKey("name")) request.getRequest.put("identifier", Slug.makeSlug(request.getRequest.get("name").asInstanceOf[String]))
        DataNode.create(request).map(node => {
            ResponseHandler.OK.put("identifier", node.getIdentifier).put("node_id", node.getIdentifier)
        })
    }

    @throws[Exception]
    private def read(request: Request): Future[Response] = {
        val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
        request.getRequest.put("fields", fields)
        DataNode.read(request).map(node => {
            if (NodeUtil.isRetired(node)) ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name, "License not found with identifier: " + node.getIdentifier)
            val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
            ResponseHandler.OK.put("license", metadata)
        })
    }

    @throws[Exception]
    private def update(request: Request): Future[Response] = {
        RequestUtil.restrictProperties(request)
        request.getRequest.put("status", "Live")
        DataNode.update(request).map(node => {
            ResponseHandler.OK.put("node_id", node.getIdentifier).put("identifier", node.getIdentifier)
        })
    }

    @throws[Exception]
    private def retire(request: Request): Future[Response] = {
        request.getRequest.put("status", "Retired")
        DataNode.update(request).map(node => {
            ResponseHandler.OK.put("node_id", node.getIdentifier).put("identifier", node.getIdentifier)
        })
    }

}
