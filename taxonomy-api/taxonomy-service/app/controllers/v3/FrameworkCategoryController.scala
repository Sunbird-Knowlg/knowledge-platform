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
class FrameworkCategoryController @Inject()(cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext)  extends BaseController(cc) {
    val objectType = "CategoryInstance"
    val schemaName: String = "categoryInstance"
    val version = "1.0"

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
