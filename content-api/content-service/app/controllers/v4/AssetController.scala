package controllers.v4

import org.apache.pekko.actor.{ActorRef, ActorSystem}
import scala.jdk.CollectionConverters._
import com.google.inject.Singleton
import scala.jdk.CollectionConverters._
import controllers.BaseController
import scala.jdk.CollectionConverters._
import javax.inject.{Inject, Named}
import scala.jdk.CollectionConverters._
import org.sunbird.models.UploadParams
import scala.jdk.CollectionConverters._
import play.api.mvc.ControllerComponents
import scala.jdk.CollectionConverters._
import utils.{ActorNames, ApiId}
import scala.jdk.CollectionConverters._

import scala.concurrent.{ExecutionContext}
import scala.jdk.CollectionConverters._
@Singleton
class AssetController  @Inject()(@Named(ActorNames.CONTENT_ACTOR) contentActor: ActorRef, @Named(ActorNames.ASSET_ACTOR) assetActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc)  {
    val objectType = "Asset"
    val schemaName: String = "asset"
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
        if(!validatePrimaryCategory(content))
            getErrorResponse(ApiId.CREATE_ASSET, apiVersion, "VALIDATION_ERROR", "primaryCategory is a mandatory parameter.")
        else if(validateContentType(content))
            getErrorResponse(ApiId.CREATE_ASSET, apiVersion, "VALIDATION_ERROR", "contentType cannot be set from request.")
        else {
            val contentRequest = getRequest(content, headers, "createContent", true)
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
        getResult(ApiId.READ_ASSET, contentActor, readRequest,  version = apiVersion)
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

    def upload(identifier: String, fileFormat: Option[String], validation: Option[String]) = Action.async { implicit request =>
        val headers = commonHeaders()
        val content = requestFormData(identifier)
        content.putAll(headers)
        val contentRequest = getRequest(content, headers, "uploadContent")
        setRequestContext(contentRequest, version, objectType, schemaName)
        contentRequest.getContext.putAll(Map("identifier" ->  identifier, "params" -> UploadParams(fileFormat, validation.map(_.toBoolean))).asJava)
        getResult(ApiId.UPLOAD_ASSET, contentActor, contentRequest, version = apiVersion)
    }

    def uploadPreSigned(identifier: String, `type`: Option[String])= Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val content = body.getOrDefault(schemaName, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        content.putAll(headers)
        content.putAll(Map("identifier" -> identifier, "type" -> `type`.getOrElse("assets")).asJava)
        val contentRequest = getRequest(content, headers, "uploadPreSignedUrl")
        setRequestContext(contentRequest, version, objectType, schemaName)
        getResult(ApiId.UPLOAD_PRE_SIGNED_ASSET, contentActor, contentRequest, version = apiVersion)
    }

    def copy(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val content = body
        content.putAll(headers)
        content.putAll(Map("identifier" -> identifier).asJava)
        val contentRequest = getRequest(content, headers, "copy")
        setRequestContext(contentRequest, version, objectType, schemaName)
        getResult(ApiId.COPY_ASSET, assetActor, contentRequest, version = apiVersion)
    }

    def licenceValidate(field: Option[String]) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val asset = body.getOrDefault("asset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        asset.putAll(headers)
        asset.putAll(Map("field" -> field.getOrElse("")).asJava)
        val assetRequest = getRequest(asset, headers, "validateLicense")
        setRequestContext(assetRequest, version, objectType, schemaName)
        getResult(ApiId.ASSET_LICENSE_VALIDATE, assetActor, assetRequest, version = apiVersion)
    }
}
