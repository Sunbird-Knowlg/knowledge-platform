package org.sunbird.content.upload.mgr

import org.apache.commons.lang3.StringUtils
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.{Platform, Slug}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.telemetry.logger.TelemetryManager

import java.io.File
import scala.collection.JavaConverters.mapAsJavaMap
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._
import org.sunbird.cloud.storage.factory.{StorageConfig, StorageServiceFactory}
import org.sunbird.telemetry.util.LogTelemetryEventUtil
import java.util

object SchemaUploadManager {
  implicit val ss: StorageService = new StorageService

  private val SCHEMA_FOLDER = "schemas/local"
  private val SCHEMA_VERSION_FOLDER = "1.0"

  private val storageType: String = if (Platform.config.hasPath("cloud_storage_type")) Platform.config.getString("cloud_storage_type") else ""
  private val storageKey = Platform.config.getString("cloud_storage_key")
  private val storageSecret = Platform.config.getString("cloud_storage_secret")
//   TODO: endPoint defined to support "cephs3". Make code changes after cloud-store-sdk 2.11 support it.
  val endPoint = if (Platform.config.hasPath("cloud_storage_endpoint")) Option(Platform.config.getString("cloud_storage_endpoint")) else None
  val storageContainer = Platform.config.getString("cloud_storage_container")

  def getContainerName: String = {
    if (Platform.config.hasPath("cloud_storage_container"))
      Platform.config.getString("cloud_storage_container")
    else
      throw new ServerException("ERR_INVALID_CLOUD_STORAGE", "Cloud Storage Container name not configured.")
  }

  def upload(request: Request, node: Node)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val identifier: String = node.getIdentifier
    val slug: String = node.getMetadata.get("slug").asInstanceOf[String]
    val file = request.getRequest.get("file").asInstanceOf[File]
    val reqFilePath: String = request.getRequest.getOrDefault("filePath", "").asInstanceOf[String].replaceAll("^/+|/+$", "")
    val filePath = if (StringUtils.isBlank(reqFilePath)) None else Option(reqFilePath)
    val uploadFuture: Future[Map[String, AnyRef]] = uploadFile(identifier, node, file, filePath, slug)
    uploadFuture.map(result => {
      updateNode(request, node.getIdentifier, node.getObjectType, result)
    }).flatMap(f => f)
  }


  def uploadFile(objectId: String, node: Node, uploadFile: File, filePath: Option[String], slug: String)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
    println("uploadFile objectId " + objectId + " node " + node + " uploadFile " + uploadFile + " filePath "+ filePath)
    validateUploadRequest(objectId, node, uploadFile)
    val result: Array[String] = uploadArtifactToCloud(uploadFile, slug, filePath)
    Future {
      Map("identifier" -> objectId, "artifactUrl" -> result(1), "downloadUrl" -> result(1), "cloudStorageKey" -> result(0), "s3Key" -> result(0), "size" -> getCloudStoredFileSize(result(0)).asInstanceOf[AnyRef])
    }
  }

  def uploadArtifactToCloud(uploadedFile: File, identifier: String, filePath: Option[String] = None, slug: Option[Boolean] = Option(true)): Array[String] = {
   var urlArray = new Array[String](2)
    try {
      val folder = if (filePath.isDefined) filePath.get + File.separator + Platform.getString(SCHEMA_FOLDER, "schemas/local") + File.separator + Slug.makeSlug(identifier, true) + File.separator + Platform.getString(SCHEMA_VERSION_FOLDER, "1.0") else Platform.getString(SCHEMA_FOLDER, "schemas/local") + File.separator + Slug.makeSlug(identifier, true) + File.separator + Platform.getString(SCHEMA_VERSION_FOLDER, "1.0")
      val cloudService = StorageServiceFactory.getStorageService(StorageConfig(storageType, storageKey, storageSecret))
      val slugFile = Slug.createSlugFile(uploadedFile)
      val objectKey = folder + "/" + slugFile.getName

      val url = cloudService.upload(getContainerName, slugFile.getAbsolutePath, objectKey, Option(false), Option(1), Option(2), None)
      urlArray = Array[String](objectKey, url)
    } catch {
      case e: Exception =>
        TelemetryManager.error("Error while uploading the file.", e)
        throw new ServerException("ERR_CONTENT_UPLOAD_FILE", "Error while uploading the File.", e)
    }
    urlArray
  }

  def updateNode(request: Request, identifier: String, objectType: String, result: Map[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val updatedResult = result - "identifier"
    val artifactUrl = updatedResult.getOrElse("artifactUrl", "").asInstanceOf[String]
    if (StringUtils.isNotBlank(artifactUrl)) {
      val updateReq = new Request(request)
      updateReq.getContext().put("identifier", identifier)
      updateReq.getRequest.putAll(mapAsJavaMap(updatedResult))
      println("updateReq "+ updateReq)
      DataNode.update(updateReq).map(node => {
        getUploadResponse(node)
      })
    } else {
      Future {
        ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, "ERR_UPLOAD_FILE", "Something Went Wrong While Processing Your Request.")
      }
    }
  }

  def getUploadResponse(node: Node)(implicit ec: ExecutionContext): Response = {
    val id = node.getIdentifier.replace(".img", "")
    val url = node.getMetadata.get("artifactUrl").asInstanceOf[String]
    ResponseHandler.OK.put("identifier", id).put("artifactUrl", url).put("versionKey", node.getMetadata.get("versionKey"))
  }

  private def validateUploadRequest(objectId: String, node: Node, data: AnyRef)(implicit ec: ExecutionContext): Unit = {
    if (StringUtils.isBlank(objectId))
      throw new ClientException("ERR_INVALID_ID", "Please Provide Valid Identifier!")
    if (null == node)
      throw new ClientException("ERR_INVALID_NODE", "Please Provide Valid Node!")
    if (null == data)
      throw new ClientException("ERR_INVALID_DATA", "Please Provide Valid File Or File Url!")
    data match {
      case file: File => validateFile(file)
      case _ =>
    }
  }

  protected def getCloudStoredFileSize(key: String)(implicit ss: StorageService): Double = {
    val size = 0
    if (StringUtils.isNotBlank(key)) try return ss.getObjectSize(key)
    catch {
      case e: Exception =>
        TelemetryManager.error("Error While getting the file size from Cloud Storage: " + key, e)
    }
    size
  }

  private def validateFile(file: File): Unit = {
    if (null == file || !file.exists())
      throw new ClientException("ERR_INVALID_FILE", "Please Provide Valid File!")
  }

}
