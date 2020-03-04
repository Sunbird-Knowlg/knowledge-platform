package controllers.v3

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import org.sunbird.common.dto.ResponseHandler
import play.api.mvc.ControllerComponents
import utils.JavaJsonUtils

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TermCategoryController @Inject()(cc: ControllerComponents)(implicit exec: ExecutionContext)  extends BaseController(cc) {

    def create(category: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def read(identifier: String, category: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def update(identifier: String, category: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }
    
    def search(category: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }
    def retire(identifier: String, category: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }
    
}
