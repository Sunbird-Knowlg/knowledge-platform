package controllers

import akka.actor.{ActorRef, ActorSystem}
import javax.inject._
import play.api.mvc._
import utils.{ActorNames, ApiId}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

class HealthController @Inject()(@Named(ActorNames.HEALTH_ACTOR) healthActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

    def health() = Action.async { implicit request =>
        getResult(ApiId.APPLICATION_HEALTH, healthActor, new org.sunbird.common.dto.Request())
    }
}
