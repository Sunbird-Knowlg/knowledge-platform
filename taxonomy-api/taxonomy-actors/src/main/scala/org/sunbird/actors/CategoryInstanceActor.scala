package org.sunbird.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.Slug
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.utils.{Constants, RequestUtil}

import java.util
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class CategoryInstanceActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {
  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case Constants.CREATE_CATEGORY_INSTANCE => create(request)
      case Constants.READ_CATEGORY_INSTANCE => read(request)
      case Constants.UPDATE_CATEGORY_INSTANCE => update(request)
      case Constants.RETIRE_CATEGORY_INSTANCE => retire(request)
      case _ => ERROR(request.getOperation)
    }
  }

  private def create(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    val frameworkId = request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String]
    val code = request.getRequest.getOrDefault(Constants.CODE, "").asInstanceOf[String]
    if (frameworkId.isEmpty()) throw new ClientException("ERR_INVALID_FRAMEWORK_ID", s"Invalid FrameworkId: '${frameworkId}' for Categoryinstance ")
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
        request.getRequest.put(Constants.IDENTIFIER, generateIdentifier(frameworkId, code))
        DataNode.create(request).map(node => {
          ResponseHandler.OK.put(Constants.IDENTIFIER, node.getIdentifier)
            .put(Constants.VERSION_KEY, node.getMetadata.get("versionKey"))
        })
      } else throw new ClientException("ERR_INVALID_FRAMEWORK_ID", s"Invalid FrameworkId: '${frameworkId}' for Categoryinstance ")
    }).flatMap(f => f)
  }

  private def read(request: Request): Future[Response] = {
    validateCategoryInstanceObject(request).map(node => {
      val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, null, request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
      ResponseHandler.OK.put("categoryInstance", metadata)
    } )
  }

  private def update(request: Request): Future[Response] = {
    val categoryId = request.getContext.getOrDefault(Constants.CATEGORY, "").asInstanceOf[String];
    RequestUtil.restrictProperties(request)
    validateCategoryInstanceObject(request)
    request.getContext.put(Constants.IDENTIFIER, generateIdentifier(request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String], categoryId))
    DataNode.update(request).map(node => {
      ResponseHandler.OK.put(Constants.IDENTIFIER, node.getIdentifier).put(Constants.VERSION_KEY, node.getMetadata.get("versionKey"))
    })
  }

  private def retire(request: Request): Future[Response] = {
    validateCategoryInstanceObject(request)
    request.getContext.put(Constants.IDENTIFIER, generateIdentifier(request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String], request.getRequest.getOrDefault(Constants.CATEGORY, "").asInstanceOf[String]))
    request.getRequest.put("status", "Retired")
    DataNode.update(request).map(node => {
      ResponseHandler.OK.put(Constants.IDENTIFIER, node.getIdentifier).put(Constants.VERSION_KEY, node.getMetadata.get("versionKey"))
    })
  }

  private def validateCategoryInstanceObject(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext) = {
    val frameworkId = request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String]
    val categoryId = request.getRequest.getOrDefault(Constants.CATEGORY, "").asInstanceOf[String]
    if (frameworkId.isEmpty()) throw new ClientException("ERR_INVALID_FRAMEWORK_ID", s"Invalid FrameworkId: '${frameworkId}' for CategoryInstance ")
    if (categoryId.isEmpty()) throw new ClientException("ERR_INVALID_CATEGORY_ID", s"Invalid CategoryId: '${categoryId}' for categoryInstance")
    val categoryInstanceId = generateIdentifier(frameworkId, categoryId)
    val getCategoryReq = new Request()
    getCategoryReq.setContext(new util.HashMap[String, AnyRef]() {
      {
        putAll(request.getContext)
      }
    })
    getCategoryReq.getContext.put(Constants.SCHEMA_NAME, Constants.CATEGORY_INSTANCE_SCHEMA_NAME)
    getCategoryReq.getContext.put(Constants.VERSION, Constants.CATEGORY_INSTANCE_SCHEMA_VERSION)
    getCategoryReq.put(Constants.IDENTIFIER, categoryInstanceId)
    DataNode.read(getCategoryReq)(oec, ec).map(node => {
      if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, categoryInstanceId)) node
      else throw new ClientException("ERR_CHANNEL_NOT_FOUND/ ERR_FRAMEWORK_NOT_FOUND", s"Given channel/framework is not related to given category")
    })(ec)
  }

  private def generateIdentifier(scopeId: String, code: String): String = {
    var id: String = null
    if (StringUtils.isNotBlank(scopeId)) id = Slug.makeSlug(scopeId + "_" + code)
    id
  }

}
