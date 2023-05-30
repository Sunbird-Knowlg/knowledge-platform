package org.sunbird.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.{Platform, Slug}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.mangers.CategoryManager
import org.sunbird.utils.{Constants, RequestUtil}

import java.util
import java.util.{ArrayList, List}
import javax.inject.Inject
import scala.collection.JavaConverters._
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.immutable.{HashMap, Map}
import scala.concurrent.{ExecutionContext, Future}

class TermActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {
  implicit val ec: ExecutionContext = getContext().dispatcher

  private final val TERM_CREATION_LIMIT: Int = if (Platform.config.hasPath("framework.max_term_creation_limit")) Platform.config.getInt("framework.max_term_creation_limit") else 200
  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case Constants.CREATE_TERM => create(request)
      case Constants.READ_TERM => read(request)
      case Constants.UPDATE_TERM => update(request)
      case Constants.RETIRE_TERM => retire(request)
      case _ => ERROR(request.getOperation)
    }
  }

  @throws[Exception]
  private def create(request: Request): Future[Response] = {
    val requestList: util.List[util.Map[String, AnyRef]] = getRequestData(request)
    if (TERM_CREATION_LIMIT < requestList.size) throw new ClientException("ERR_INVALID_TERM_REQUEST", "No. of request exceeded max limit of " + TERM_CREATION_LIMIT)
    RequestUtil.restrictProperties(request)
    val frameworkId = request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String]
    if (!frameworkId.isEmpty()) {
      validateCategoryInstance(request)
    } else {
      validateCategory(request)
    }
    val categoryId = request.getRequest.getOrDefault(Constants.CATEGORY, "").asInstanceOf[String]
    val code = request.getRequest.getOrDefault(Constants.CODE, "").asInstanceOf[String]
    request.getRequest.put(Constants.IDENTIFIER, generateIdentifier(categoryId, code))
    DataNode.create(request).map(node => {
      ResponseHandler.OK.put(Constants.IDENTIFIER, node.getIdentifier).put(Constants.NODE_ID, node.getIdentifier)
    })
  }

  private def read(request: Request): Future[Response] = {
    val frameworkId = request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String]
    if (!frameworkId.isEmpty()) {
      validateCategoryInstance(request)
    } else {
      validateCategory(request)
    }
    validateTerm(request).map(node => {
      val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, null, request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
      ResponseHandler.OK.put("term", metadata)
    })
  }

  private def update(request: Request): Future[Response] = {
    val termId = request.getContext.getOrDefault(Constants.TERM, "").asInstanceOf[String];
    val frameworkId = request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String]
    RequestUtil.restrictProperties(request)
    val categoryId = generateIdentifier(frameworkId, request.getRequest.getOrDefault(Constants.CATEGORY, "").asInstanceOf[String])
    if (!frameworkId.isEmpty()) {
      validateCategoryInstance(request)
    } else {
      validateCategory(request)
    }
    request.getContext.put(Constants.IDENTIFIER, generateIdentifier(categoryId, termId))
    DataNode.update(request).map(node => {
      ResponseHandler.OK.put(Constants.IDENTIFIER, node.getIdentifier).put(Constants.VERSION_KEY, node.getMetadata.get("versionKey"))
    })
  }

  private def retire(request: Request): Future[Response] = {
    val termId = request.getContext.getOrDefault(Constants.TERM, "").asInstanceOf[String];
    val frameworkId = request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String]
    val categoryId = generateIdentifier(frameworkId, request.getRequest.getOrDefault(Constants.CATEGORY, "").asInstanceOf[String])
    if (!frameworkId.isEmpty()) {
      validateCategoryInstance(request)
    } else {
      validateCategory(request)
    }
    request.getContext.put(Constants.IDENTIFIER, generateIdentifier(categoryId, termId))
    request.getRequest.put("status", "Retired")
    DataNode.update(request).map(node => {
      ResponseHandler.OK.put(Constants.IDENTIFIER, node.getIdentifier).put(Constants.VERSION_KEY, node.getMetadata.get("versionKey"))
    })
  }

  private def validateTerm(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext) = {
    val termId = request.getRequest.getOrDefault(Constants.TERM, "").asInstanceOf[String]
    if (termId.isEmpty()) throw new ClientException("ERR_INVALID_TERM_ID", s"Invalid TermId: '${termId}' for Term")
    val categoryInstanceId = generateIdentifier(request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String], request.getRequest.getOrDefault(Constants.CATEGORY, "").asInstanceOf[String])
    val getCategoryReq = new Request()
    getCategoryReq.setContext(new util.HashMap[String, AnyRef]() {
      {
        putAll(request.getContext)
      }
    })
    getCategoryReq.getContext.put(Constants.SCHEMA_NAME, Constants.TERM_SCHEMA_NAME)
    getCategoryReq.getContext.put(Constants.VERSION, Constants.TERM_SCHEMA_VERSION)
    getCategoryReq.put(Constants.IDENTIFIER, generateIdentifier(categoryInstanceId, termId))
    DataNode.read(getCategoryReq)(oec, ec).map(node => {
      if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, categoryInstanceId)) node
      else throw new ClientException("ERR_CHANNEL_NOT_FOUND/ ERR_FRAMEWORK_NOT_FOUND", s"Given channel/framework is not related to given category")
    })(ec)
  }

  private def validateCategoryInstance(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext) = {
    val frameworkId = request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String]
    val categoryId = request.getRequest.getOrDefault(Constants.CATEGORY, "").asInstanceOf[String]
    if (frameworkId.isEmpty()) throw new ClientException("ERR_INVALID_FRAMEWORK_ID", s"Invalid FrameworkId: '${frameworkId}' for Term ")
    if (categoryId.isEmpty()) throw new ClientException("ERR_INVALID_CATEGORY_ID", s"Invalid CategoryId: '${categoryId}' for Term")
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

  private def validateCategory(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext) = {
    val categoryId = request.getRequest.getOrDefault(Constants.CATEGORY, "").asInstanceOf[String]
    if (categoryId.isEmpty()) throw new ClientException("ERR_INVALID_CATEGORY_ID", s"Please provide valid category. It should not be empty.")
    val getCategoryReq = new Request()
    getCategoryReq.setContext(new util.HashMap[String, AnyRef]() {
      {
        putAll(request.getContext)
      }
    })
    getCategoryReq.getContext.put(Constants.SCHEMA_NAME, Constants.CATEGORY_SCHEMA_NAME)
    getCategoryReq.getContext.put(Constants.VERSION, Constants.CATEGORY_SCHEMA_VERSION)
    getCategoryReq.put(Constants.IDENTIFIER, categoryId)
    DataNode.read(getCategoryReq)(oec, ec).map(node => {
      if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, categoryId)) node
      else throw new ClientException("ERR_INVALID_CATEGORY_ID", s"Please provide valid category")
    })(ec)
  }

  private def getRequestData(request: Request): util.List[util.Map[String, AnyRef]] = {
    val req = request.getRequest.get(request.getObjectType.toLowerCase())
    req match {
      case req: util.List[util.Map[String, AnyRef]] => req
      case req: util.Map[String, AnyRef] => new util.ArrayList[util.Map[String, AnyRef]]() {
        {
          add(req)
        }
      }
      case _ => throw new ClientException("ERR_INVALID_TERM", "Invalid Request! Please Provide Valid Request.")
    }
  }

  private def generateIdentifier(scopeId: String, code: String): String = {
    var id: String = null
    if (StringUtils.isNotBlank(scopeId)) id = Slug.makeSlug(scopeId + "_" + code)
    id
  }

}
