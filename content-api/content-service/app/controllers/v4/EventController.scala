package controllers.v4

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import javax.inject.{Inject, Named}
import scala.concurrent.ExecutionContext

@Singleton
class EventController @Inject()(@Named(ActorNames.EVENT_ACTOR) contentActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends ContentController(contentActor, cc, actorSystem) {

    override val objectType = "Event"
    override val schemaName: String = "event"

    override def create() = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val content = body.getOrDefault(contentPath, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
        content.putAll(headers)
        if(validateContentType(content))
            getErrorResponse(ApiId.CREATE_EVENT, apiVersion, "VALIDATION_ERROR", "contentType cannot be set from request.")
        else {
            val contentRequest = getRequest(content, headers, "createContent", false)
            setRequestContext(contentRequest, version, objectType, schemaName)
            getResult(ApiId.CREATE_EVENT, contentActor, contentRequest, version = apiVersion)
        }
    }

}
