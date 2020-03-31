package controllers.v3

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import javax.inject.{Inject, Named}
import play.api.mvc.ControllerComponents
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}
import org.sunbird.common.dto.ResponseHandler
import utils.{ActorNames, ApiId, JavaJsonUtils}

@Singleton
class FrameworkController @Inject()(@Named(ActorNames.FRAMEWORK_ACTOR) frameworkActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext)  extends BaseController(cc) {

    val objectType = "Framework"
    val schemaName: String = "framework"
    val version = "1.0"

    def createFramework() = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val framework = body.getOrElse("framework", new java.util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]]
        framework.putAll(headers)
        val frameworkRequest = getRequest(framework, headers, "createFramework")
        setRequestContext(frameworkRequest, version, objectType, schemaName)
        getResult(ApiId.CREATE_FRAMEWORK, frameworkActor, frameworkRequest)
    }

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

    def readFramework(identifier: String, categories: Option[String]) = Action.async { implicit request =>
        val headers = commonHeaders()
        val readFramework = new java.util.HashMap().asInstanceOf[java.util.Map[String, AnyRef]]
        readFramework.putAll(headers)
        readFramework.putAll(Map("identifier" -> identifier,"categories" ->categories.getOrElse("")).asInstanceOf[Map[String, AnyRef]])
        val readRequest = getRequest(readFramework, headers, "readFramework")
        setRequestContext(readRequest, version, objectType, schemaName)
        getResult(ApiId.READ_FRAMEWORK, frameworkActor, readRequest)
    }

    def updateFramework(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val framework = body.getOrElse("framework", new java.util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]]
        framework.putAll(headers)
        val frameworkRequest = getRequest(framework, headers, "updateFramework")
        setRequestContext(frameworkRequest, version, objectType, schemaName)
        frameworkRequest.getContext.put("identifier", identifier);
        getResult(ApiId.UPDATE_FRAMEWORK, frameworkActor, frameworkRequest)
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