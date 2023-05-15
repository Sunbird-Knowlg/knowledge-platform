package controllers.v3

import akka.actor.{ActorRef, ActorSystem}
import scala.concurrent.{ExecutionContext, Future}
import controllers.BaseController
import javax.inject.{Inject, Named, Singleton}
import org.sunbird.common.dto.ResponseHandler
import org.sunbird.utils.Constants
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId, JavaJsonUtils}

import java.util
@Singleton
class FrameworkController @Inject()(@Named(ActorNames.FRAMEWORK_ACTOR) frameworkActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

    val objectType = "Framework"
    def createFramework()= Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val framework = body.getOrDefault(Constants.FRAMEWORK, new util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        framework.putAll(headers)
        val frameworkRequest = getRequest(framework, headers, Constants.CREATE_FRAMEWORK)
        setRequestContext(frameworkRequest, Constants.FRAMEWORK_SCHEMA_VERSION, objectType, Constants.FRAMEWORK_SCHEMA_NAME)
        getResult(ApiId.CREATE_FRAMEWORK, frameworkActor, frameworkRequest)
    }

    def readFramework(identifier: String, categories: Option[String]) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }
    
    def retire(identifier: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def updateFramework(identifier: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }
    
    def listFramework() = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def copyFramework(identifier: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def publish(identifier: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }
}
