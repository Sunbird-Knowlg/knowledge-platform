package taxonomy.controllers

import org.apache.pekko.actor.{ActorRef, ActorSystem}

import javax.inject._
import play.api.mvc._
import taxonomy.controllers
import taxonomy.utils.{ActorNames, ApiId}

import scala.concurrent.{ExecutionContext, Future}

class HealthController @Inject()(@Named(ActorNames.HEALTH_ACTOR) healthActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends controllers.BaseController(cc) {

    def health() = Action.async { implicit request =>
        getResult(ApiId.APPLICATION_HEALTH, healthActor, new org.sunbird.common.dto.Request())
    }
}

