package controllers.v4

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import utils.{ActorNames, ApiId, Constants}

import javax.inject.{Inject, Named}
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.concurrent.ExecutionContext

@Singleton
class EventSetController @Inject()(@Named(ActorNames.EVENT_SET_ACTOR) eventSetActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends CollectionController(eventSetActor, eventSetActor, eventSetActor, cc, actorSystem) {
  override val objectType = "EventSet"
  override val schemaName: String = "eventset"

  override def create() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val content = body.getOrDefault(schemaName, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    content.putAll(headers)
    if(validateContentType(content))
      getErrorResponse(ApiId.CREATE_EVENT_SET, apiVersion, "VALIDATION_ERROR", "contentType cannot be set from request.")
    else {
      val contentRequest = getRequest(content, headers, "createContent", false)
      setRequestContext(contentRequest, version, objectType, schemaName)
      getResult(ApiId.CREATE_EVENT_SET, eventSetActor, contentRequest, version = apiVersion)
    }
  }

  override def read(identifier: String, mode: Option[String], fields: Option[String]) = Action.async { implicit request =>
    val headers = commonHeaders()
    val content = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    content.putAll(headers)
    content.putAll(Map("identifier" -> identifier, "mode" -> mode.getOrElse("read"), "fields" -> fields.getOrElse("")).asJava)
    val readRequest = getRequest(content, headers, "readContent")
    setRequestContext(readRequest, version, objectType, schemaName)
    readRequest.getContext.put(Constants.RESPONSE_SCHEMA_NAME, schemaName);
    getResult(ApiId.READ_COLLECTION, eventSetActor, readRequest, version = apiVersion)
  }

  def getHierarchy(identifier: String, mode: Option[String], fields: Option[String]) = Action.async { implicit request =>
    val headers = commonHeaders()
    val content = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    content.putAll(headers)
    content.putAll(Map("identifier" -> identifier, "mode" -> mode.getOrElse("read"), "fields" -> fields.getOrElse("")).asJava)
    val readRequest = getRequest(content, headers, "getHierarchy")
    setRequestContext(readRequest, version, objectType, schemaName)
    getResult(ApiId.READ_COLLECTION, eventSetActor, readRequest, version = apiVersion)
  }

  override def update(identifier: String): Action[AnyContent] = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val content = body.getOrDefault(schemaName, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    if (content.containsKey("status")) {
      getErrorResponse(ApiId.UPDATE_EVENT_SET, apiVersion, "VALIDATION_ERROR", "status update is restricted, use status APIs.")
    } else {
      content.putAll(headers)
      val contentRequest = getRequest(content, headers, "updateContent")
      setRequestContext(contentRequest, version, objectType, schemaName)
      contentRequest.getContext.put("identifier", identifier)
      getResult(ApiId.UPDATE_EVENT_SET, eventSetActor, contentRequest, version = apiVersion)
    }
  }

  def publish(identifier: String): Action[AnyContent] = Action.async { implicit request =>
    val headers = commonHeaders()
    val content = new java.util.HashMap[String, Object]()
    content.put("identifier", identifier)
    content.putAll(headers)
    val contentRequest = getRequest(content, headers, "publishContent")
    setRequestContext(contentRequest, version, objectType, schemaName)
    getResult(ApiId.PUBLISH_EVENT_SET, eventSetActor, contentRequest, version = apiVersion)
  }


}