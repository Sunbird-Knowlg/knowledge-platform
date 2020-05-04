package controllers

import akka.actor.{ActorRef, ActorSystem}
import handlers.SignalHandler
import javax.inject._
import org.sunbird.common.JsonUtils
import org.sunbird.common.dto.ResponseHandler
import play.api.mvc._
import utils.{ActorNames, ApiId}

import scala.concurrent.{ExecutionContext, Future}

class HealthController @Inject()(@Named(ActorNames.HEALTH_ACTOR) healthActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem, signalHandler: SignalHandler)(implicit exec: ExecutionContext) extends BaseController(cc) {

    def health() = Action.async { implicit request =>
        if (signalHandler.isShuttingDown) {
            Future { ServiceUnavailable }
        } else {
            getResult(ApiId.APPLICATION_HEALTH, healthActor, new org.sunbird.common.dto.Request())
        }
    }

    def serviceHealth() = Action.async { implicit request =>
        if (signalHandler.isShuttingDown)
            Future { ServiceUnavailable }
        else {
            val response = ResponseHandler.OK().setId(ApiId.APPLICATION_SERVICE_HEALTH).put("healthy", true)
            Future { Ok(JsonUtils.serialize(response)).as("application/json") }
        }
    }
}
