package controllers.v4

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import utils.{ActorNames, ApiId, Constants}

import javax.inject.{Inject, Named}
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.concurrent.ExecutionContext
import utils.DateValidationUtils._

@Singleton
class EventController @Inject()(@Named(ActorNames.EVENT_ACTOR) eventActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends ContentController(eventActor, cc, actorSystem) {

    override val objectType = "Event"
    override val schemaName: String = "event"

    override def create() = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val content = body.getOrDefault(schemaName, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
        content.putAll(headers)
        if(validateContentType(content))
            getErrorResponse(ApiId.CREATE_EVENT, apiVersion, "VALIDATION_ERROR", "contentType cannot be set from request.")
        else if(validateDatesAndTimes(content))
            getErrorResponse(ApiId.CREATE_EVENT, apiVersion, "VALIDATION_ERROR", "End Date/Time should be greater than Start Date/Time.")
        else {
            val contentRequest = getRequest(content, headers, "createContent", false)
            setRequestContext(contentRequest, version, objectType, schemaName)
            getResult(ApiId.CREATE_EVENT, eventActor, contentRequest, version = apiVersion)
        }
    }

    override def read(identifier: String, mode: Option[String], fields: Option[String]) = Action.async { implicit request =>
        val headers = commonHeaders()
        val content = new java.util.HashMap[String, Object]()
        content.putAll(headers)
        content.putAll(Map("identifier" -> identifier, "mode" -> mode.getOrElse("read"), "fields" -> fields.getOrElse("")).asJava)
        val readRequest = getRequest(content, headers, "readContent")
        setRequestContext(readRequest, version, objectType, schemaName)
        readRequest.getContext.put(Constants.RESPONSE_SCHEMA_NAME, schemaName);
        getResult(ApiId.READ_CONTENT, eventActor, readRequest, version = apiVersion)
    }

    override def update(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val content = body.getOrDefault(schemaName, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
        if (content.containsKey("status")) {
            getErrorResponse(ApiId.UPDATE_EVENT, apiVersion, "VALIDATION_ERROR", "status update is restricted, use status APIs.")
        } else {
            content.putAll(headers)
            val contentRequest = getRequest(content, headers, "updateContent")
            setRequestContext(contentRequest, version, objectType, schemaName)
            contentRequest.getContext.put("identifier", identifier);
            getResult(ApiId.UPDATE_EVENT, eventActor, contentRequest, version = apiVersion)
        }
    }

    def publish(identifier: String): Action[AnyContent] = Action.async { implicit request =>
        val headers = commonHeaders()
        val content = new java.util.HashMap[String, Object]()
        content.put("status", "Live")
        content.put("identifier", identifier)
        content.putAll(headers)
        val contentRequest = getRequest(content, headers, "publishContent")
        setRequestContext(contentRequest, version, objectType, schemaName)
        contentRequest.getContext.put("identifier", identifier);
        getResult(ApiId.PUBLISH_EVENT, eventActor, contentRequest, version = apiVersion)
    }

    def getModeratorJoinMeetingUrl(identifier: String, userId: Option[String], userName: Option[String], muteOnStart: Option[Boolean], logoutURL: Option[String]) = Action.async { implicit request =>
        val headers = commonHeaders()
        val content = new java.util.HashMap[String, Object]()
        content.putAll(headers)
        content.putAll(Map("identifier" -> identifier, "userId" -> userId.getOrElse(null), "userName" -> userName.getOrElse(Constants.USER), "muteOnStart" -> muteOnStart.getOrElse(false).asInstanceOf[java.lang.Boolean], "logoutURL" -> logoutURL.getOrElse("")).asJava)
        val readRequest = getRequest(content, headers, "joinEventModerator")
        setRequestContext(readRequest, version, objectType, schemaName)
        readRequest.getContext.put(Constants.RESPONSE_SCHEMA_NAME, schemaName);
        getResult(ApiId.JOIN_EVENT_MODERATOR, eventActor, readRequest, version = apiVersion)
    }

    def getAttendeeJoinMeetingUrl(identifier: String, userId: Option[String], userName: Option[String]) = Action.async { implicit request =>
        val headers = commonHeaders()
        val content = new java.util.HashMap[String, Object]()
        content.putAll(headers)
        content.putAll(Map("identifier" -> identifier, "userId" -> userId.getOrElse(null), "userName" -> userName.getOrElse(Constants.USER)).asJava)
        val readRequest = getRequest(content, headers, "joinEventAttendee")
        setRequestContext(readRequest, version, objectType, schemaName)
        readRequest.getContext.put(Constants.RESPONSE_SCHEMA_NAME, schemaName);
        getResult(ApiId.JOIN_EVENT_ATTENDEE, eventActor, readRequest, version = apiVersion)
    }
}
