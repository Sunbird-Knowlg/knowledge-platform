package org.sunbird.actors

import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.Slug
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.mangers.FrameworkManager
import org.sunbird.utils.{Constants, FrameworkCache, RequestUtil}

import java.util
import javax.inject.Inject
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConverters
import scala.concurrent.{ExecutionContext, Future}

class FrameworkActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {

  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case Constants.CREATE_FRAMEWORK => create(request)
      //      case Constants.READ_FRAMEWORK => read(request)
      case Constants.UPDATE_FRAMEWORK => update(request)
      case Constants.RETIRE_FRAMEWORK => retire(request)
      case _ => ERROR(request.getOperation)
    }
  }


  @throws[Exception]
  private def create(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    val code = request.getRequest.getOrDefault(Constants.CODE, "").asInstanceOf[String]
    val channel = request.getRequest.getOrDefault(Constants.CHANNEL, "").asInstanceOf[String]
    if (StringUtils.isBlank(code))
      throw new ClientException("ERR_FRAMEWORK_CODE_REQUIRED", "Unique code is mandatory for framework")
    request.getRequest.put(Constants.IDENTIFIER, code)
    //FrameworkManager.validateChannel(request)
    if (FrameworkManager.validateObject(request, channel)) {
      FrameworkManager.validateTranslations(request)
      DataNode.create(request).map(node => {
        ResponseHandler.OK.put(Constants.NODE_ID, node.getIdentifier).put("versionKey", node.getMetadata.get("versionKey"))
      })
    }
    else throw new ClientException("ERR_INVALID_CHANNEL_ID", "Invalid Channel Id. Channel doesn't exist.")
  }

//  @throws[Exception]
//    private def read(request: Request): Future[Response] = {
//      val categories = request.getRequest.getOrDefault(Constants.CATEGORIES, "")
//      val returnCategories: List[String] = if (categories == null) List.empty[String] else categories
//      val framework : util.Map[String, AnyRef] = FrameworkCache.get(request.getRequest.getOrDefault(Constants.IDENTIFIER, "").asInstanceOf[String], returnCategories).asInstanceOf[util.Map[String,AnyRef]]
//      if (MapUtils.isNotEmpty(framework)) {
//        ResponseHandler.OK.put("framework", framework)
//      }
//    }

  @throws[Exception]
  private def update(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    DataNode.update(request).map(node => {
      ResponseHandler.OK.put("node_id", node.getIdentifier).put("versionKey", node.getMetadata.get("versionKey"))
    })
  }

  def retire(request: Request): Future[Response] = {
    request.getRequest.put("status", "Retired")
    DataNode.update(request).map(node => {
      val identifier: String = node.getIdentifier
      ResponseHandler.OK.put("node_id", identifier).put("identifier", identifier)
    })
  }

//  @throws[Exception]
//  private def copy(request: Request): Future[Response] = {
//    RequestUtil.restrictProperties(request)
//    val code = request.getRequest.getOrDefault(Constants.CODE, "").asInstanceOf[String]
//    val channel = request.getContext.getOrDefault(Constants.CHANNEL, "").asInstanceOf[String]
//    val frameworkId = request.getRequest.getOrDefault(Constants.IDENTIFIER, "").asInstanceOf[String]
//    if (StringUtils.isBlank(code))
//      throw new ClientException("ERR_FRAMEWORK_CODE_REQUIRED", "Unique code is mandatory for framework")
//    if (FrameworkManager.validateObject(request, code))
//      throw new ClientException("ERR_FRAMEWORK_EXISTS", "Framework with code: " + code + ", already exists.")
//    if (FrameworkManager.validateObject(request,channel)){
//      val sluggifiedFrameworkId = Slug.makeSlug(frameworkId)
//      val sluggifiedCode = Slug.makeSlug(code)
//
//    }


//  }

}