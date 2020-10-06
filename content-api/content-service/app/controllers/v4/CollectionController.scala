package controllers.v4

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import javax.inject.{Inject, Named}
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext}
@Singleton
class CollectionController  @Inject()(@Named(ActorNames.CONTENT_ACTOR) contentActor: ActorRef, @Named(ActorNames.COLLECTION_ACTOR) collectionActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc)  {
    val objectType = "Collection"
    val schemaName: String = "collection"
    val version = "1.0"
    val apiVersion = "4.0"

    /**
      * This Api end point takes a body
      * Content Identifier the unique identifier of a content, can either be provided or will be generated
      * primaryCategory, mimeType, name and code are mandatory
      *
      * @returns identifier and versionKey
      */
    def create() = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val content = body.getOrDefault(schemaName, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        content.putAll(headers)
        val contentRequest = getRequest(content, headers, "createContent", true)
        if(!validatePrimaryCategory(contentRequest.getRequest))
            getErrorResponse(ApiId.CREATE_ASSET, version = apiVersion)
        else {
            setRequestContext(contentRequest, version, objectType, schemaName)
            getResult(ApiId.CREATE_ASSET, contentActor, contentRequest, version = apiVersion)
        }
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
        content.putAll(Map("identifier" -> identifier, "mode" -> mode.getOrElse("read"), "fields" -> fields.getOrElse("")).asJava)
        val readRequest = getRequest(content, headers, "readContent")
        setRequestContext(readRequest, version, objectType, schemaName)
        getResult(ApiId.READ_ASSET, contentActor, readRequest, version = apiVersion)
    }

    def update(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val content = body.getOrDefault(schemaName, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        content.putAll(headers)
        val contentRequest = getRequest(content, headers, "updateContent")
        setRequestContext(contentRequest, version, objectType, schemaName)
        contentRequest.getContext.put("identifier", identifier)
        getResult(ApiId.UPDATE_ASSET, contentActor, contentRequest, version = apiVersion)
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
        val data = body.getOrDefault("data", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        data.putAll(headers)
        val contentRequest = getRequest(data, headers, "updateHierarchy")
        setRequestContext(contentRequest, version, objectType, schemaName)
        getResult(ApiId.UPDATE_HIERARCHY, collectionActor, contentRequest)
    }

    def getHierarchy(identifier: String, mode: Option[String]) = Action.async { implicit request =>
        val headers = commonHeaders()
        val content = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
        content.putAll(headers)
        content.putAll(Map("rootId" -> identifier, "mode" -> mode.getOrElse("")).asJava)
        val readRequest = getRequest(content, headers, "getHierarchy")
        setRequestContext(readRequest, version, objectType, null)
        getResult(ApiId.GET_HIERARCHY, collectionActor, readRequest, true)
    }

    def getBookmarkHierarchy(identifier: String, bookmarkId: String, mode: Option[String]) = Action.async { implicit request =>
        val headers = commonHeaders()
        val content = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
        content.putAll(headers)
        content.putAll(Map("rootId" -> identifier, "bookmarkId" -> bookmarkId, "mode" -> mode.getOrElse("")).asJava)
        val readRequest = getRequest(content, headers, "getHierarchy")
        setRequestContext(readRequest, version, objectType, null)
        getResult(ApiId.GET_HIERARCHY, collectionActor, readRequest, true)
    }

    def flag(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val content = body
        content.putAll(headers)
        content.putAll(Map("identifier" -> identifier).asJava)
        val contentRequest = getRequest(content, headers, "flagContent")
        setRequestContext(contentRequest, version, objectType, schemaName)
        contentRequest.getContext.put("identifier", identifier)
        getResult(ApiId.FlAG_CONTENT, contentActor, contentRequest)
    }

    def acceptFlag(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val content = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
        content.putAll(headers)
        content.putAll(Map("identifier" -> identifier).asJava)
        val acceptRequest = getRequest(content, headers, "acceptFlag")
        setRequestContext(acceptRequest, version, objectType, schemaName)
        getResult(ApiId.ACCEPT_FLAG, contentActor, acceptRequest)
    }

    def discard(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val content = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
        content.putAll(headers)
        content.putAll(Map("identifier" -> identifier).asJava)
        val discardRequest = getRequest(content, headers, "discardContent")
        setRequestContext(discardRequest, version, objectType, schemaName)
        getResult(ApiId.DISCARD_CONTENT, contentActor, discardRequest)
    }
    def retire(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val content = body.getOrDefault("content", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        content.put("identifier", identifier)
        content.putAll(headers)
        val contentRequest = getRequest(content, headers, "retireContent")
        setRequestContext(contentRequest, version, objectType, schemaName)
        getResult(ApiId.RETIRE_CONTENT, contentActor, contentRequest)
    }

    def linkDialCode() = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        body.putAll(headers)
        val contentRequest = getRequest(body, headers, "linkDIALCode")
        setRequestContext(contentRequest, version, objectType, schemaName)
        contentRequest.getContext.put("linkType", "content")
        getResult(ApiId.LINK_DIAL_CONTENT, contentActor, contentRequest)
    }

    def collectionLinkDialCode(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        body.putAll(headers)
        val contentRequest = getRequest(body, headers, "linkDIALCode")
        setRequestContext(contentRequest, version, objectType, schemaName)
        contentRequest.getContext.put("linkType", "collection")
        contentRequest.getContext.put("identifier", identifier)
        getResult(ApiId.LINK_DIAL_COLLECTION, contentActor, contentRequest)
    }
}
