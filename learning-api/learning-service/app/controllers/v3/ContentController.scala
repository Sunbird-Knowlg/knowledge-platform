package controllers.v3

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import javax.inject.{Inject, Named}
import play.api.mvc.ControllerComponents
import utils.ActorNames

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

@Singleton
class ContentController @Inject()(@Named(ActorNames.CONTENT_ACTOR) contentActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

    val objectType = "content"
    val version = "1.0"

    def create() = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val content = body.getOrElse(objectType, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
        content.putAll(headers)
        val contentRequest = getRequest(content, headers, "createContent")
        setRequestContext(contentRequest, version, objectType)
        getResult("org.sunbird.content.create", contentActor, contentRequest)
    }


}
