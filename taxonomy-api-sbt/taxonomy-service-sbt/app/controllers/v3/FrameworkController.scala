package controllers.v3

import scala.concurrent.{ExecutionContext, Future}
import controllers.BaseController
import javax.inject.{Inject, Singleton}
import org.sunbird.common.dto.ResponseHandler
import play.api.mvc.ControllerComponents
import utils.JavaJsonUtils

@Singleton
class FrameworkController @Inject()(cc: ControllerComponents)(implicit exec: ExecutionContext)  extends BaseController(cc) {
    
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
