package org.sunbird.content.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.util.RequestUtil

import java.util
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AppActor @Inject() (implicit oec: OntologyEngineContext, ss: StorageService) extends BaseActor {

  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case "create" => create(request)
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

  private def setIdentifier(request: Request) = {
    val osType = request.getRequest.getOrDefault("osType", "").asInstanceOf[String]
    val packageId = request.getRequest.getOrDefault("osMetadata", new util.HashMap[String, AnyRef]())
      .asInstanceOf[java.util.Map[String, AnyRef]]
      .getOrDefault("packageId", "").asInstanceOf[String]
    val identifier = if (StringUtils.isNotBlank(osType) && StringUtils.isNotBlank(packageId)) s"$osType:$packageId" else ""
    request.getRequest.put("identifier", identifier)
  }

}
