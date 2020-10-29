package org.sunbird.content.util

import java.io.{File, IOException}
import java.net.URL
import java.util
import java.util.concurrent.CompletionException

import org.apache.commons.io.{FileUtils, FilenameUtils}
import org.apache.commons.lang.StringUtils
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.Platform
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.mimetype.factory.MimeTypeManagerFactory
import org.sunbird.mimetype.mgr.impl.H5PMimeTypeMgrImpl
import org.sunbird.models.UploadParams

import scala.concurrent.{ExecutionContext, Future}

object AssetCopyManager {
  implicit val oec: OntologyEngineContext = new OntologyEngineContext
  implicit val ss: StorageService = new StorageService

  private val TEMP_FILE_LOCATION = Platform.getString("content.upload.temp_location", "/tmp/content")
  private val restrictedMimeTypesForUpload = List("application/vnd.ekstep.ecml-archive","application/vnd.ekstep.content-collection")

  def copy(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    request.getContext.put(AssetConstants.ASSET_COPY_SCHEME, request.getRequest.getOrDefault(AssetConstants.ASSET_COPY_SCHEME, ""))
    DataNode.read(request).map(node => {
      val copiedNodeFuture: Future[Node] = copyAsset(node, request)
      copiedNodeFuture.map(copiedNode => {
        val response = ResponseHandler.OK()
        response.put("node_id", new util.HashMap[String, AnyRef](){{
          put(node.getIdentifier, copiedNode.getIdentifier)
        }})
        response.put(AssetConstants.VERSION_KEY, copiedNode.getMetadata.get(AssetConstants.VERSION_KEY))
        response
      })
    }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
  }

  def copyAsset(node: Node, request: Request)(implicit ec: ExecutionContext,  oec: OntologyEngineContext): Future[Node] = {
    val copyCreateReq: Future[Request] = getCopyRequest(node, request)
    copyCreateReq.map(req => {
      DataNode.create(req).map(copiedNode => {
        artifactUpload(node, copiedNode, request)
      }).flatMap(f => f)
    }).flatMap(f => f)
  }

  def getCopyRequest(node: Node, request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Request] = {
    val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, new util.ArrayList(), node.getObjectType.toLowerCase.replace("image", ""), AssetConstants.SCHEMA_VERSION)
    val requestMap = request.getRequest
    requestMap.remove(AssetConstants.ASSET_COPY_SCHEME).asInstanceOf[String]
    metadata.put(AssetConstants.ORIGIN, node.getIdentifier)
    metadata.put(AssetConstants.NAME, "Copy_" + metadata.getOrDefault(AssetConstants.NAME, ""))
    metadata.put(AssetConstants.IDENTIFIER, Identifier.getIdentifier(request.getContext.get("graph_id").asInstanceOf[String], Identifier.getUniqueIdFromTimestamp))
    request.getContext().put(AssetConstants.SCHEMA_NAME, node.getObjectType.toLowerCase.replace("image", ""))
    val req = new Request(request)
    req.setRequest(metadata)
    Future {req}
  }

  def artifactUpload(node: Node, copiedNode: Node, request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val artifactUrl = node.getMetadata.getOrDefault(AssetConstants.ARTIFACT_URL, "").asInstanceOf[String]
    val mimeType = node.getMetadata.get(AssetConstants.MIME_TYPE).asInstanceOf[String]
    val contentType = node.getMetadata.get(AssetConstants.CONTENT_TYPE).asInstanceOf[String]
    if (StringUtils.isNotBlank(artifactUrl) && !restrictedMimeTypesForUpload.contains(mimeType)) {
      val mimeTypeManager = MimeTypeManagerFactory.getManager(contentType, mimeType)
      val uploadFuture = if (isInternalUrl(artifactUrl)) {
        val file = copyURLToFile(copiedNode.getIdentifier, artifactUrl)
        if (mimeTypeManager.isInstanceOf[H5PMimeTypeMgrImpl])
          mimeTypeManager.asInstanceOf[H5PMimeTypeMgrImpl].copyH5P(file, copiedNode)
        else
          mimeTypeManager.upload(copiedNode.getIdentifier, copiedNode, file, None, UploadParams())
      } else mimeTypeManager.upload(copiedNode.getIdentifier, copiedNode, node.getMetadata.getOrDefault(AssetConstants.ARTIFACT_URL, "").asInstanceOf[String], None, UploadParams())
      uploadFuture.map(uploadData => {
        DataNode.update(getUpdateRequest(request, copiedNode, uploadData.getOrElse(AssetConstants.ARTIFACT_URL, "").asInstanceOf[String]))
      }).flatMap(f => f)
    } else Future(copiedNode)
  }

  protected def isInternalUrl(url: String): Boolean = url.contains(ss.getContainerName())

  def copyURLToFile(objectId: String, fileUrl: String): File = try {
    val file = new File(getBasePath(objectId) + File.separator + getFileNameFromURL(fileUrl))
    FileUtils.copyURLToFile(new URL(fileUrl), file)
    file
  } catch {
    case e: IOException => throw new ClientException("ERR_INVALID_FILE_URL", "Please Provide Valid File Url!")
  }

  def getBasePath(objectId: String): String = {
    if (!StringUtils.isBlank(objectId)) TEMP_FILE_LOCATION + File.separator + System.currentTimeMillis + "_temp" + File.separator + objectId else ""
  }

  protected def getFileNameFromURL(fileUrl: String): String = if (!FilenameUtils.getExtension(fileUrl).isEmpty)
    FilenameUtils.getBaseName(fileUrl) + "_" + System.currentTimeMillis + "." + FilenameUtils.getExtension(fileUrl) else FilenameUtils.getBaseName(fileUrl) + "_" + System.currentTimeMillis

  def getUpdateRequest(request: Request, copiedNode: Node, artifactUrl: String): Request = {
    val req = new Request()
    val context = request.getContext
    context.put(AssetConstants.IDENTIFIER, copiedNode.getIdentifier)
    req.setContext(context)
    req.put(AssetConstants.VERSION_KEY, copiedNode.getMetadata.get(AssetConstants.VERSION_KEY))
    req.put(AssetConstants.ARTIFACT_URL, artifactUrl)
    req
  }
}
