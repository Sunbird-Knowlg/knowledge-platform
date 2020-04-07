package controllers

import akka.actor.{ActorRef, ActorSystem}
import handlers.SignalHandler
import javax.inject.{Inject, Named}
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import scala.concurrent.{ExecutionContext, Future}

class HealthController @Inject()(@Named(ActorNames.HEALTH_ACTOR) healthActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem, signalHandler: SignalHandler)(implicit exec: ExecutionContext) extends SearchBaseController(cc) {

    def health() = Action.async { implicit request =>
        if (signalHandler.isShuttingDown) {
            Future { ServiceUnavailable }
        } else {
            getResult(ApiId.APPLICATION_HEALTH, healthActor, new org.sunbird.common.dto.Request())
        }

    }
}
