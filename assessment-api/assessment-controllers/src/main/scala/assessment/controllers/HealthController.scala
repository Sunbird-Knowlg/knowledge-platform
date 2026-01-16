package assessment.controllers

import org.apache.pekko.actor.{ActorRef, ActorSystem}
import javax.inject._
import org.sunbird.common.JsonUtils
import org.sunbird.common.dto.ResponseHandler
import play.api.mvc._
import assessment.utils.{ActorNames, ApiId}
import assessment.controllers.BaseController

import scala.concurrent.{ExecutionContext, Future}

class HealthController @Inject()(@Named(ActorNames.HEALTH_ACTOR) healthActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

    def health() = Action.async { implicit request =>
        getResult(ApiId.APPLICATION_HEALTH, healthActor, new org.sunbird.common.dto.Request())
    }

    def serviceHealth() = Action.async { implicit request =>
        val response = ResponseHandler.OK().setId(ApiId.APPLICATION_SERVICE_HEALTH).put("healthy", true)
        Future { Ok(JsonUtils.serialize(response)).as("application/json") }
    }
}
