package org.sunbird.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.Slug
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.utils.{Constants, RequestUtil}

import java.util
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class CategoryInstanceActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {
  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case Constants.CREATE_CATEGORY_INSTANCE => create(request)
//      case Constants.READ_CATEGORY => read(request)
//      case Constants.UPDATE_CATEGORY => update(request)
//      case Constants.RETIRE_CATEGORY => retire(request)
      case _ => ERROR(request.getOperation)
    }
  }

  def create(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    val frameworkId = request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String]
    val code = request.getRequest.getOrDefault(Constants.CODE, "").asInstanceOf[String]
    if (!request.getRequest.containsKey(Constants.FRAMEWORK)) throw new ClientException("ERR_INVALID_FRAMEWORK_ID", s"Invalid FrameworkId: '${frameworkId}' for Categoryinstance ")
    if (!request.getRequest.containsKey(Constants.CODE)) throw new ClientException("ERR_CATEGORY_CODE_REQUIRED", "Unique code is mandatory for categoryInstance")
    val getCategoryReq = new Request()
    getCategoryReq.setContext(new util.HashMap[String, AnyRef]() {{
        putAll(request.getContext)
    }})
    getCategoryReq.getContext.put(Constants.SCHEMA_NAME, Constants.FRAMEWORK_SCHEMA_NAME)
    getCategoryReq.getContext.put(Constants.VERSION, Constants.FRAMEWORK_SCHEMA_VERSION)
    getCategoryReq.put(Constants.IDENTIFIER, frameworkId)
    DataNode.read(getCategoryReq).map(node => {
      if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, frameworkId)) {
        val categoryId = Slug.makeSlug(frameworkId + "_" + code);
        request.getRequest.put(Constants.IDENTIFIER, categoryId)
        DataNode.create(request).map(node => {
          ResponseHandler.OK.put(Constants.IDENTIFIER, node.getIdentifier)
            .put(Constants.VERSION_KEY, node.getMetadata.get("versionKey"))
        })
      } else throw new ClientException("ERR_INVALID_FRAMEWORK_ID", s"Invalid FrameworkId: '${frameworkId}' for Categoryinstance ")
    }).flatMap(f => f)
  }

}
