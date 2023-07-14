package org.sunbird.actors

import org.apache.commons.lang3.StringUtils
import java.util
import javax.inject.Inject
import org.sunbird.actor.core.BaseActor
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.utils.{Constants, RequestUtil}
import org.sunbird.mangers.CategoryManager
import org.sunbird.cache.impl.RedisCache

import scala.concurrent.{ExecutionContext, Future}

class CategoryActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {
  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case Constants.CREATE_CATEGORY => create(request)
      case Constants.READ_CATEGORY => read(request)
      case Constants.UPDATE_CATEGORY => update(request)
      case Constants.RETIRE_CATEGORY => retire(request)
      case _ => ERROR(request.getOperation)
    }
  }

  @throws[Exception]
  private def create(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    val code = request.getRequest.getOrDefault(Constants.CODE, "").asInstanceOf[String]
    if (!request.getRequest.containsKey("code")) throw new ClientException("ERR_CATEGORY_CODE_REQUIRED", "Unique code is mandatory for category")
    request.getRequest.put(Constants.IDENTIFIER, code)
    RedisCache.delete("masterCategories")
    CategoryManager.validateTranslationMap(request)
    DataNode.create(request).map(node => {
      ResponseHandler.OK.put(Constants.IDENTIFIER, node.getIdentifier).put(Constants.NODE_ID, node.getIdentifier)
    })
  }

  private def read(request: Request): Future[Response] = {
    DataNode.read(request).map(node => {
      val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, null, request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
      ResponseHandler.OK.put("category", metadata)
    })
  }

  private def update(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    if (request.getRequest.containsKey(Constants.CODE)) throw new ClientException("ERR_CATEGORY_UPDATE", "code updation is not allowed.")
    RedisCache.delete("masterCategories")
    CategoryManager.validateTranslationMap(request)
    DataNode.update(request).map(node => {
      ResponseHandler.OK.put(Constants.IDENTIFIER, node.getIdentifier).put(Constants.NODE_ID, node.getIdentifier)
    })
  }

  private def retire(request: Request): Future[Response] = {
    request.getRequest.put("status", "Retired")
    RedisCache.delete("masterCategories")
    DataNode.update(request).map(node => {
      ResponseHandler.OK.put(Constants.IDENTIFIER, node.getIdentifier).put(Constants.NODE_ID, node.getIdentifier)
    })
  }
}