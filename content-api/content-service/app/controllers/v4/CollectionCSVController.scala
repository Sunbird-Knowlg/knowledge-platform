package controllers.v4

import akka.actor.ActorRef
import controllers.BaseController
import org.sunbird.collectioncsv.util.CollectionTOCConstants
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import javax.inject.{Inject, Named}
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.concurrent.ExecutionContext

class CollectionCSVController @Inject()(@Named(ActorNames.COLLECTION_CSV_ACTOR) collectionCSVActor: ActorRef, cc: ControllerComponents) (implicit exec: ExecutionContext) extends BaseController(cc) {
  val objectType = "Collection"
  val schemaName: String = "collection"
  val version = "1.0"
  val apiVersion = "4.0"

  def uploadTOC(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val content = requestFormData(identifier)
    content.putAll(headers)
    content.putAll(Map("identifier" -> identifier).asJava)
    val uploadRequest = getRequest(content, headers, CollectionTOCConstants.COLLECTION_CSV_TOC_UPLOAD)
    setRequestContext(uploadRequest, version, objectType, schemaName)
    uploadRequest.getContext.put("identifier", identifier)
    getResult(ApiId.UPLOAD_TOC, collectionCSVActor, uploadRequest, version = apiVersion)
  }

  def getTOCUrl(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val content = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    content.putAll(headers)
    content.putAll(Map("identifier" -> identifier).asJava)
    val downloadRequest = getRequest(content, headers, CollectionTOCConstants.COLLECTION_CSV_TOC_DOWNLOAD)
    setRequestContext(downloadRequest, version, objectType, schemaName)
    downloadRequest.getContext.put("identifier", identifier)
    getResult(ApiId.DOWNLOAD_TOC, collectionCSVActor, downloadRequest, version = apiVersion)
  }

}
