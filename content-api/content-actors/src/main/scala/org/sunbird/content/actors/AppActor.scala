package org.sunbird.content.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.util.RequestUtil

import java.util
import javax.inject.Inject
import scala.collection.JavaConverters
import scala.concurrent.{ExecutionContext, Future}

/***
 * TODO: rewrite this Actor after merging the Event and EventSet code.
 */
class AppActor @Inject() (implicit oec: OntologyEngineContext) extends BaseActor {

  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case "create" => create(request)
      case "read" => read(request)
      case _ => ERROR(request.getOperation)
    }
  }

  def create(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    setIdentifier(request)
    DataNode.create(request, (node: Node) => node).map(node => {
      val response = ResponseHandler.OK
      response.put("identifier", node.getIdentifier)
      response
    })
  }

  @throws[Exception]
  private def read(request: Request): Future[Response] = {
    val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
    request.getRequest.put("fields", fields)
    DataNode.read(request).map(node => {
      if (NodeUtil.isRetired(node)) ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name, "App not found with identifier: " + node.getIdentifier)
      val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
      val response: Response = ResponseHandler.OK
      response.put("app", metadata)
      response
    })
  }

  @throws[Exception]
  private def update(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    DataNode.update(request).map(node => {
      val response: Response = ResponseHandler.OK
      response.put("identifier", node.getIdentifier)
      response
    })
  }

  private def setIdentifier(request: Request) = {
    val osType = request.getRequest.getOrDefault("osType", "").asInstanceOf[String]
    val packageId = request.getRequest.getOrDefault("osMetadata", new util.HashMap[String, AnyRef]())
      .asInstanceOf[java.util.Map[String, AnyRef]]
      .getOrDefault("packageId", "").asInstanceOf[String]
    val identifier = if (StringUtils.isNotBlank(osType) && StringUtils.isNotBlank(packageId)) s"$osType:$packageId" else ""
    request.getRequest.put("identifier", identifier)
  }

}
