package org.sunbird.content.util

import java.util
import java.util.concurrent.CompletionException

import org.apache.commons.lang.StringUtils
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.content.upload.mgr.UploadManager
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil

import scala.concurrent.{ExecutionContext, Future}

object AssetCopyManager {
  implicit val oec: OntologyEngineContext = new OntologyEngineContext
  implicit val ss: StorageService = new StorageService

  def copy(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    request.getContext.put(AssetConstants.ASSET_COPY_SCHEME, request.getRequest.getOrDefault(AssetConstants.ASSET_COPY_SCHEME, ""))
    DataNode.read(request).map(node => {
      if (!StringUtils.equals(node.getObjectType, "Asset"))
        throw new ClientException(AssetConstants.ERR_INVALID_REQUEST, "Only asset can be copied")
      val copiedNodeFuture: Future[Node] = copyAsset(node, request)
      copiedNodeFuture.map(copiedNode => {
        val response = ResponseHandler.OK()
        response.put("node_id", new util.HashMap[String, AnyRef]() {
          {
            put(node.getIdentifier, copiedNode.getIdentifier)
          }
        })
        response.put(AssetConstants.VERSION_KEY, copiedNode.getMetadata.get(AssetConstants.VERSION_KEY))
        response
      })
    }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
  }

  def copyAsset(node: Node, request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
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
    metadata.put(AssetConstants.STATUS, "Draft")
    metadata.put(AssetConstants.IDENTIFIER, Identifier.getIdentifier(request.getContext.get("graph_id").asInstanceOf[String], Identifier.getUniqueIdFromTimestamp))
    metadata.putAll(requestMap.getOrDefault("asset", new util.HashMap()).asInstanceOf[util.Map[String, AnyRef]])
    request.getContext().put(AssetConstants.SCHEMA_NAME, node.getObjectType.toLowerCase.replace("image", ""))
    val req = new Request(request)
    req.setRequest(metadata)
    Future {
      req
    }
  }

  def artifactUpload(node: Node, copiedNode: Node, request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val artifactUrl = node.getMetadata.getOrDefault(AssetConstants.ARTIFACT_URL, "").asInstanceOf[String]
    if (StringUtils.isNotBlank(artifactUrl)) {
      val updatedReq = getUpdateRequest(request, copiedNode)
      val responseFuture = UploadManager.upload(updatedReq, copiedNode)
      responseFuture.map(result => {
        copiedNode.getMetadata.put(AssetConstants.ARTIFACT_URL, result.getResult.getOrDefault(AssetConstants.ARTIFACT_URL, "").asInstanceOf[String])
      })
    }
    Future(copiedNode)
  }

  def getUpdateRequest(request: Request, copiedNode: Node): Request = {
    val req = new Request()
    val context = request.getContext
    context.put(AssetConstants.IDENTIFIER, copiedNode.getIdentifier)
    req.setContext(context)
    req.put(AssetConstants.VERSION_KEY, copiedNode.getMetadata.get(AssetConstants.VERSION_KEY))
    req.put(AssetConstants.FILE_URL, copiedNode.getMetadata.get(AssetConstants.ARTIFACT_URL))
    req
  }
}
