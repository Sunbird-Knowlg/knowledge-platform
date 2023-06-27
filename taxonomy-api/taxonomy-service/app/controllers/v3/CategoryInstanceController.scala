package controllers.v3

import akka.actor.{ActorRef, ActorSystem}
import controllers.BaseController
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.exception.ClientException
import org.sunbird.utils.Constants
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import javax.inject.{Inject, Named}
import scala.collection.JavaConverters.asJavaIterableConverter
import scala.concurrent.ExecutionContext

class CategoryInstanceController  @Inject()(@Named(ActorNames.CATEGORY_INSTANCE_ACTOR) categoryInstanceActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

  val objectType = "CategoryInstance"
  def createCategoryInstance(framework: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val categoryInstance = body.getOrDefault(Constants.CATEGORY, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    categoryInstance.put(Constants.FRAMEWORK, framework)
    categoryInstance.putAll(headers)
    val categoryRequest = getRequest(categoryInstance, headers, Constants.CREATE_CATEGORY_INSTANCE)
    setRequestContext(categoryRequest, Constants.CATEGORY_INSTANCE_SCHEMA_VERSION, objectType, Constants.CATEGORY_INSTANCE_SCHEMA_NAME)
    getResult(ApiId.CREATE_CATEGORY_INSTANCE, categoryInstanceActor, categoryRequest)
  }

  def readCategoryInstance(category: String, framework: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val categoryInstance = body.getOrDefault(Constants.CATEGORY, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    categoryInstance.put(Constants.CATEGORY, category)
    categoryInstance.put(Constants.FRAMEWORK, framework)
    categoryInstance.putAll(headers)
    val readCategoryRequest = getRequest(categoryInstance, headers, Constants.READ_CATEGORY_INSTANCE)
    setRequestContext(readCategoryRequest, Constants.CATEGORY_INSTANCE_SCHEMA_VERSION, objectType, Constants.CATEGORY_INSTANCE_SCHEMA_NAME)
    getResult(ApiId.READ_CATEGORY_INSTANCE, categoryInstanceActor, readCategoryRequest)
  }

  def updateCategoryInstance(category: String, framework: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val categoryInstance = body.getOrDefault(Constants.CATEGORY, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    categoryInstance.put(Constants.CATEGORY, category)
    categoryInstance.put(Constants.FRAMEWORK, framework)
    categoryInstance.putAll(headers)
    val categoryInstanceRequest = getRequest(categoryInstance, headers, Constants.UPDATE_CATEGORY_INSTANCE)
    setRequestContext(categoryInstanceRequest, Constants.CATEGORY_INSTANCE_SCHEMA_VERSION, objectType, Constants.CATEGORY_INSTANCE_SCHEMA_NAME)
    categoryInstanceRequest.getContext.put(Constants.CATEGORY, category)
    getResult(ApiId.UPDATE_CATEGORY_INSTANCE, categoryInstanceActor, categoryInstanceRequest)
  }

  def retireCategoryInstance(category: String, framework: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val categoryInstance = body.getOrDefault(Constants.CATEGORY, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    categoryInstance.put(Constants.CATEGORY, category)
    categoryInstance.put(Constants.FRAMEWORK, framework)
    categoryInstance.putAll(headers)
    val categoryInstanceRequest = getRequest(categoryInstance, headers, Constants.RETIRE_CATEGORY_INSTANCE)
    setRequestContext(categoryInstanceRequest, Constants.CATEGORY_INSTANCE_SCHEMA_VERSION, objectType, Constants.CATEGORY_INSTANCE_SCHEMA_NAME)
    categoryInstanceRequest.getContext.put(Constants.CATEGORY, category)
    getResult(ApiId.RETIRE_CATEGORY_INSTANCE, categoryInstanceActor, categoryInstanceRequest)
  }

}