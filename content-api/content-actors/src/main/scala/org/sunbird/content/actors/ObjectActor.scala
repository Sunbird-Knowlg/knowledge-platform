package org.sunbird.content.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import java.util
import javax.inject.Inject
import scala.collection.JavaConverters
import scala.concurrent.{ExecutionContext, Future}

class ObjectActor @Inject() (implicit oec: OntologyEngineContext, ss: StorageService) extends BaseActor {

  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case "readObject" => read(request)
      case _ => ERROR(request.getOperation)
    }
  }

  @throws[Exception]
  private def read(request: Request): Future[Response] = {
    val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
    request.getRequest.put("fields", fields)
    DataNode.read(request).map(node => {
      node.getObjectType
      if (NodeUtil.isRetired(node)) ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name, "Object not found with identifier: " + node.getIdentifier)
      val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields,null, request.getContext.get("version").asInstanceOf[String])
      ResponseHandler.OK.put("Object-Data", metadata)
    })
  }

}
