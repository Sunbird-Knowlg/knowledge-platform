package org.sunbird.actors

import java.util

import javax.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.Slug
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.utils.{Constants, RequestUtil}

import scala.collection.JavaConverters
import scala.concurrent.{ExecutionContext, Future}

class ObjectCategoryActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {

    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = {
        request.getOperation match {
            case Constants.CREATE_OBJECT_CATEGORY => create(request)
            case Constants.READ_OBJECT_CATEGORY => read(request)
            case Constants.UPDATE_OBJECT_CATEGORY => update(request)
            case _ => ERROR(request.getOperation)
        }
    }


    @throws[Exception]
    private def create(request: Request): Future[Response] = {
        RequestUtil.restrictProperties(request)
        if (!request.getRequest.containsKey(Constants.NAME)) throw new ClientException("ERR_NAME_SET_AS_IDENTIFIER", "name will be set as identifier")
        request.getRequest.put(Constants.IDENTIFIER, Constants.CATEGORY_PREFIX + Slug.makeSlug(request.getRequest.get(Constants.NAME).asInstanceOf[String]))
        DataNode.create(request).map(node => {
            ResponseHandler.OK.put(Constants.IDENTIFIER, node.getIdentifier)
        })
    }

    @throws[Exception]
    private def read(request: Request): Future[Response] = {
        val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get(Constants.FIELDS).asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
        request.getRequest.put(Constants.FIELDS, fields)
        DataNode.read(request).map(node => {
            val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, request.getContext.get(Constants.SCHEMA_NAME).asInstanceOf[String], request.getContext.get(Constants.VERSION).asInstanceOf[String])
            ResponseHandler.OK.put(Constants.OBJECT_CATEGORY, metadata)
        })
    }

    @throws[Exception]
    private def update(request: Request): Future[Response] = {
        RequestUtil.restrictProperties(request)
        DataNode.update(request).map(node => {
            ResponseHandler.OK.put(Constants.IDENTIFIER, node.getIdentifier)
        })
    }

}
