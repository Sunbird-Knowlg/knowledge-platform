package controllers.v3


import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import javax.inject.{Inject, Named}
import org.sunbird.common.dto.ResponseHandler
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId, JavaJsonUtils}
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}
@Singleton
class FrameworkController @Inject()(@Named(ActorNames.FRAMEWORK_ACTOR) frameworkActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext)  extends BaseController(cc) {
    val objectType = "Framework"
    val schemaName: String = "framework"
    val version = "1.0"
    
    def createFramework() = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def readFramework(identifier: String, categories: Option[String]) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }
    
//    def retire(identifier: String) = Action.async { implicit request =>
//        val result = ResponseHandler.OK()
//        val response = JavaJsonUtils.serialize(result)
//        Future(Ok(response).as("application/json"))
//    }
def retire(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val framework = body.getOrElse("content", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
    framework.putAll(headers)
    val frameworkRequest = getRequest(framework, headers, "retireFramework")
    setRequestContext(frameworkRequest, version, objectType, schemaName)
    frameworkRequest.getContext.put("identifier", identifier);
    getResult(ApiId.RETIRE_FRAMEWORK, frameworkActor, frameworkRequest)
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
