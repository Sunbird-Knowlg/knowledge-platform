package controllers.v4

import akka.actor.{ActorRef, ActorSystem}
import controllers.BaseController
import org.sunbird.common.exception.ClientException
import org.sunbird.models.UploadParams
import play.api.mvc.{AnyContent, ControllerComponents, Request}
import utils.{ActorNames, ApiId}
import org.sunbird.content.util.SchemaConstants

import java.io.File
import java.util
import javax.inject.{Inject, Named}
import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._

class SchemaController @Inject()(@Named(ActorNames.SCHEMA_ACTOR) schemaActor: ActorRef, cc: ControllerComponents)(implicit exec: ExecutionContext) extends BaseController(cc) {

  val objectType = "Schema"
  val version = "1.0"
  val apiVersion = "1.0"

  def create() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val schema = body.getOrDefault(SchemaConstants.SCHEMA, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    schema.putAll(headers)
    val createSchemaRequest = getRequest(schema, headers, SchemaConstants.CREATE_SCHEMA)
    setRequestContext(createSchemaRequest, SchemaConstants.SCHEMA_VERSION, objectType, "schema")
    getResult(ApiId.CREATE_SCHEMA, schemaActor, createSchemaRequest)
  }

  def read(identifier: String, fields: Option[String]) = Action.async { implicit request =>
    val headers = commonHeaders()
    val schema = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    schema.putAll(headers)
    schema.putAll(Map(SchemaConstants.IDENTIFIER -> identifier, SchemaConstants.FIELDS -> fields.getOrElse("")).asJava)
    val readSchemaRequest = getRequest(schema, headers, SchemaConstants.READ_SCHEMA)
    setRequestContext(readSchemaRequest, SchemaConstants.SCHEMA_VERSION, objectType, "schema")
    getResult(ApiId.READ_SCHEMA, schemaActor, readSchemaRequest)
  }

  def upload(identifier: String, fileFormat: Option[String], validation: Option[String]) = Action.async { implicit request =>
    val headers = commonHeaders()
    val content = requestSchemaFormData(identifier)
    content.putAll(headers)
    val uploadRequest = getRequest(content, headers, SchemaConstants.UPLOAD_SCHEMA)
    setRequestContext(uploadRequest, SchemaConstants.SCHEMA_VERSION, objectType, "schema")
    uploadRequest.getContext.putAll(Map("identifier" -> identifier, "params" -> UploadParams(fileFormat, validation.map(_.toBoolean))).asJava)
    getResult(ApiId.UPDATE_SCHEMA, schemaActor, uploadRequest)
  }

  private def requestSchemaFormData(identifier: String)(implicit request: Request[AnyContent]) = {
    val reqMap = new util.HashMap[String, AnyRef]()
    if (!request.body.asMultipartFormData.isEmpty) {
      val multipartData = request.body.asMultipartFormData.get
      if (null != multipartData.files && !multipartData.files.isEmpty) {
        val file: File = new java.io.File(request.body.asMultipartFormData.get.files.head.filename)
        val copiedFile: File = multipartData.files.head.ref.copyTo(file, false).toFile
        reqMap.put("file", copiedFile)
        println("isFile " + copiedFile.isFile)
        println("getName " + copiedFile.getName)
        println("getAbsolutePath " + copiedFile.getAbsolutePath)
      }
    }
    if (null != reqMap.get("file").asInstanceOf[File]) {
      println("reqMap " + reqMap)
      reqMap
    } else {
      throw new ClientException("ERR_INVALID_DATA", "Please Provide Valid File Or File Url!")
    }
  }

}
