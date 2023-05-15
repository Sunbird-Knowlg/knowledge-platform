package org.sunbird.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.mangers.FrameworkManager
import org.sunbird.utils.{Constants, RequestUtil}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FrameworkActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {

  implicit val ec: ExecutionContext = getContext().dispatcher
  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case Constants.CREATE_FRAMEWORK => create(request)
      //case Constants.READ_FRAMEWORK => read(request)
      case _ => ERROR(request.getOperation)
    }
  }


  @throws[Exception]
  private def create(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    val code = request.getRequest.getOrDefault(Constants.CODE, "").asInstanceOf[String]
    if (StringUtils.isBlank(code))
      throw new ClientException("ERR_FRAMEWORK_CODE_REQUIRED", "Unique code is mandatory for framework")
    request.getRequest.put(Constants.IDENTIFIER ,code)
    FrameworkManager.validateChannel(request)
    FrameworkManager.validateTranslations(request)
    DataNode.create(request).map(node => {
      ResponseHandler.OK.put(Constants.NODE_ID, node.getIdentifier).put("versionKey", node.getMetadata.get("versionKey"))
    })
  }

//  private def read(request: Request): Future[Response] = {
//  }


}