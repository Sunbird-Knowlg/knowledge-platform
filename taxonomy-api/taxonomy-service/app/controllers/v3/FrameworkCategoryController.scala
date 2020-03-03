package controllers.v3

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import org.sunbird.common.dto.ResponseHandler
import play.api.mvc.ControllerComponents
import utils.JavaJsonUtils

import scala.concurrent.{ExecutionContext, Future}
    
@Singleton
class FrameworkCategoryController @Inject()(cc: ControllerComponents)(implicit exec: ExecutionContext)  extends BaseController(cc) {
    
    def create(framework: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }
    
    def read(identifier: String, framework: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
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
