package controllers.v3

import scala.concurrent.{ExecutionContext, Future}
import org.sunbird.common.dto.ResponseHandler
import utils.{ActorNames, ApiId, JavaJsonUtils}
import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import javax.inject.{Inject, Named}
import play.api.mvc.ControllerComponents
import scala.collection.JavaConversions._

@Singleton
class FrameworkCategoryController @Inject()(@Named(ActorNames.FRAMEWORK_CATEGORY_ACTOR) frameworkCategoryActor: ActorRef,actorSystem: ActorSystem)(cc: ControllerComponents)(implicit exec: ExecutionContext)  extends BaseController(cc) {

    val objectType = "CategoryInstance"
    val schemaName: String = "categoryInstance"
    val version = "1.0"

    def create(framework: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val frameworkCategory = body.getOrElse("category", new java.util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]]
        frameworkCategory.putAll(headers)
        frameworkCategory.putAll(Map("identifier" -> framework).asInstanceOf[Map[String, Object]])
        val frameworkCategoryRequest = getRequest(frameworkCategory, headers, "createFrameworkCategory")
        setRequestContext(frameworkCategoryRequest, version, objectType, schemaName)
        getResult(ApiId.CREATE_FRAMEWORK_CATEGORY, frameworkCategoryActor, frameworkCategoryRequest)
    }

    def read(categoryInstanceId: String, framework: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val frameworkCategory = body.getOrElse("category", new java.util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]]
        frameworkCategory.putAll(headers)
        frameworkCategory.putAll(Map("categoryInstanceId" -> categoryInstanceId, "identifier" -> framework).asInstanceOf[Map[String, Object]])
        val frameworkCategoryRequest = getRequest(frameworkCategory, headers, "readFrameworkCategory")
        setRequestContext(frameworkCategoryRequest, version, objectType, schemaName)
        getResult(ApiId.READ_FRAMEWORK_CATEGORY, frameworkCategoryActor, frameworkCategoryRequest)
    }

    def update(identifier: String, framework: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def search(framework: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }
    def retire(identifier: String, framework: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }
}
