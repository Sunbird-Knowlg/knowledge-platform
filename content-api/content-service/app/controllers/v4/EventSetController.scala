package controllers.v4

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import javax.inject.{Inject, Named}
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.concurrent.ExecutionContext

@Singleton
class EventSetController @Inject()(@Named(ActorNames.EVENT_SET_ACTOR) eventSetActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends CollectionController(eventSetActor, eventSetActor, cc, actorSystem) {
  override val objectType = "EventSet"
  override val schemaName: String = "eventSet"

  override def create() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val content = body.getOrDefault(contentPath, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    content.putAll(headers)
    if(validateContentType(content))
      getErrorResponse(ApiId.CREATE_EVENT_SET, apiVersion, "VALIDATION_ERROR", "contentType cannot be set from request.")
    else {
      val contentRequest = getRequest(content, headers, "createContent", false)
      setRequestContext(contentRequest, version, objectType, schemaName)
      getResult(ApiId.CREATE_EVENT_SET, eventSetActor, contentRequest, version = apiVersion)
    }
  }

  override def getHierarchy(identifier: String, mode: Option[String]) = Action.async { implicit request =>
    val headers = commonHeaders()
    val content = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    content.putAll(headers)
    content.putAll(Map("identifier" -> identifier, "mode" -> mode.getOrElse("read")).asJava)
    val readRequest = getRequest(content, headers, "getHierarchy")
    setRequestContext(readRequest, version, objectType, schemaName)
    getResult(ApiId.READ_COLLECTION, eventSetActor, readRequest, version = apiVersion)
  }

}
