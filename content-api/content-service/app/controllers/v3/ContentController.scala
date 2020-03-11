package controllers.v3

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import javax.inject.{Inject, Named}
import org.sunbird.common.dto.ResponseHandler
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId, JavaJsonUtils}
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ContentController @Inject()(@Named(ActorNames.CONTENT_ACTOR) contentActor: ActorRef, @Named(ActorNames.COLLECTION_ACTOR) collectionActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

    val objectType = "Content"
    val schemaName: String = "content"
    val version = "1.0"

    def create() = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val content = body.getOrElse("content", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
        content.putAll(headers)
        val contentRequest = getRequest(content, headers, "createContent")
        setRequestContext(contentRequest, version, objectType, schemaName)
        getResult(ApiId.CREATE_CONTENT, contentActor, contentRequest)
    }

    /**
     * This Api end point takes 3 parameters
     * Content Identifier the unique identifier of a content
     * Mode in which the content can be viewed (default read or edit)
     * Fields are metadata that should be returned to visualize
     *
     * @param identifier
     * @param mode
     * @param fields
     * @return
     */
    def read(identifier: String, mode: Option[String], fields: Option[String]) = Action.async { implicit request =>
        val headers = commonHeaders()
        val content = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
        content.putAll(headers)
        content.putAll(Map("identifier" -> identifier, "mode" -> mode.getOrElse("read"), "fields" -> fields.getOrElse("")).asInstanceOf[Map[String, Object]])
        val readRequest = getRequest(content, headers, "readContent")
        setRequestContext(readRequest, version, objectType, schemaName)
        getResult(ApiId.READ_CONTENT, contentActor, readRequest)
    }

    def update(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val content = body.getOrElse("content", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
        content.putAll(headers)
        val contentRequest = getRequest(content, headers, "updateContent")
        setRequestContext(contentRequest, version, objectType, schemaName)
        contentRequest.getContext.put("identifier", identifier);
        getResult(ApiId.UPDATE_CONTENT, contentActor, contentRequest)
    }

    def addHierarchy() = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        body.putAll(headers)
        val contentRequest = getRequest(body, headers, "addHierarchy")
        contentRequest.put("mode", "edit");
        setRequestContext(contentRequest, version, objectType, schemaName)
        getResult(ApiId.ADD_HIERARCHY, collectionActor, contentRequest)
    }

    def removeHierarchy() = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        body.putAll(headers)
        val contentRequest = getRequest(body, headers, "removeHierarchy")
        contentRequest.put("mode", "edit");
        setRequestContext(contentRequest, version, objectType, schemaName)
        getResult(ApiId.REMOVE_HIERARCHY, collectionActor, contentRequest)
    }

    def updateHierarchy() = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val data = body.getOrElse("data", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        data.putAll(headers)
        val contentRequest = getRequest(data, headers, "updateHierarchy")
        setRequestContext(contentRequest, version, objectType, schemaName)
        getResult(ApiId.UPDATE_HIERARCHY, collectionActor, contentRequest)
    }

    def getHierarchy(identifier: String, mode: Option[String]) = Action.async { implicit request =>
        val headers = commonHeaders()
        val content = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
        content.putAll(headers)
        content.putAll(Map("rootId" -> identifier, "mode" -> mode.getOrElse("")))
        val readRequest = getRequest(content, headers, "getHierarchy")
        setRequestContext(readRequest, version, objectType, null)
        getResult(ApiId.GET_HIERARCHY, collectionActor, readRequest)
    }

    def flag(identifier: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def acceptFlag(identifier: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def rejectFlag(identifier: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def bundle() = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def publish(identifier: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def review(identfier: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def discard(identifier: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def retire(identifier: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def linkDialCode() = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def reserveDialCode(identifier: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def releaseDialcodes(identifier: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def rejectContent(identifier: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def publishUnlisted(identifier: String) = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def upload(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val content = requestFormData()
        content.putAll(headers)
        val contentRequest = getRequest(content, headers, "uploadContent")
        setRequestContext(contentRequest, version, objectType, schemaName)
        contentRequest.getContext.put("identifier", identifier);
        getResult(ApiId.UPLOAD_CONTENT, contentActor, contentRequest)
    }

}
