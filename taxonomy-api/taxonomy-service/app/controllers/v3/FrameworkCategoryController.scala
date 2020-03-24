package controllers.v3

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import handlers.SignalHandler
import javax.inject.{Inject, Named}
import org.sunbird.common.dto.ResponseHandler
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId, JavaJsonUtils, TaxonomyOperations}

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FrameworkCategoryController @Inject()(@Named(ActorNames.FRAMEWORK_CATEGORY_ACTOR) frameworkCategoryActor: ActorRef,cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext)  extends BaseController(cc) {


  val objectType = "CategoryInstance"
  val schemaName: String = "categoryInstance"
  val version = "1.0"

  def create(framework: String) = Action.async { implicit request =>
    val result = ResponseHandler.OK()
    val response = JavaJsonUtils.serialize(result)
    Future(Ok(response).as("application/json"))

  }


  def read(categoryInstanceId: String, framework: String) = Action.async { implicit request =>
    val result = ResponseHandler.OK()
    val response = JavaJsonUtils.serialize(result)
    Future(Ok(response).as("application/json"))
  }

  def update(categoryInstanceId: String, framework: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val frameworkCategoryInstance = body.getOrElse("category", new java.util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]]
    frameworkCategoryInstance.putAll(headers)
    frameworkCategoryInstance.putAll(Map("categoryInstanceId" -> categoryInstanceId, "identifier" -> framework).asInstanceOf[Map[String, Object]])
    val frameworkCategoryRequest = getRequest(frameworkCategoryInstance, headers, "updateFrameworkCategory")
    setRequestContext(frameworkCategoryRequest, version, objectType, schemaName)
    getResult(ApiId.READ_FRAMEWORK_CATEGORY, frameworkCategoryActor, frameworkCategoryRequest)
  }


  def search(framework: String) = Action.async { implicit request =>
    val result = ResponseHandler.OK()
    val response = JavaJsonUtils.serialize(result)
    Future(Ok(response).as("application/json"))
  }

  def retire(categoryInstanceId: String, framework: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val frameworkCategory = body.getOrElse("category", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
    frameworkCategory.putAll(headers)
    frameworkCategory.putAll(Map("categoryInstanceId" -> categoryInstanceId, "identifier" -> framework).asInstanceOf[Map[String, Object]])
    val frameworkCategoryRequest = getRequest(frameworkCategory, headers, "retireFrameworkCategory")
    setRequestContext(frameworkCategoryRequest, version, objectType, schemaName)
    getResult(ApiId.RETIRE_FRAMEWORK_CATEGORY, frameworkCategoryActor, frameworkCategoryRequest)
  }
}

