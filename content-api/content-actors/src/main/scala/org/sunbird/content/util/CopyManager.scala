package org.sunbird.content.util

import java.io.{File, IOException}
import java.net.URL

import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.dac.model.Node
import org.sunbird.common.exception.ClientException
import java.util
import java.util.concurrent.CompletionException

import org.apache.commons.collections.CollectionUtils
import org.apache.commons.collections4.MapUtils
import org.apache.commons.io.{FileUtils, FilenameUtils}
import org.apache.commons.lang.StringUtils
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.Platform
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.mimetype.factory.MimeTypeManagerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

object CopyManager {

    implicit val ss: StorageService = new StorageService

    def copy(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        validateRequest(request)
        DataNode.read(request).map(node => {
            validateExistingNode(node)
            val copiedNodeFuture: Future[Node] = node.getMetadata.get(CopyConstants.MIME_TYPE) match {
                case CopyConstants.COLLECTION_MIME_TYPE => copyCollection(node, request)
                case _ => copyContent(node, request)
            }
            copiedNodeFuture.map(copiedNode => {
                val response = ResponseHandler.OK()
                response.put(node.getIdentifier, copiedNode.getIdentifier)
                response
            })
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
    }

    def copyContent(node: Node, request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        val copyCreateReq = getCopyRequest(node, request)
        DataNode.create(copyCreateReq).map(copiedNode => {
            val mimeTypeManager = MimeTypeManagerFactory.getManager(node.getMetadata.get(CopyConstants.CONTENT_TYPE).asInstanceOf[String], node.getMetadata.get(CopyConstants.MIME_TYPE).asInstanceOf[String])
            if (StringUtils.isNotBlank(node.getMetadata.getOrDefault("artifactUrl", "").asInstanceOf[String])) {
                if (isInternalUrl(node.getMetadata.getOrDefault("artifactUrl", "").asInstanceOf[String])) {
                    val file = copyURLToFile(node.getMetadata.getOrDefault("artifactUrl", "").asInstanceOf[String])
                    mimeTypeManager.upload(copiedNode.getIdentifier, copiedNode, file)
                } else
                    mimeTypeManager.upload(copiedNode.getIdentifier, copiedNode, node.getMetadata.getOrDefault("artifactUrl", "").asInstanceOf[String])
                copiedNode
            } else {
                node
            }
        })
    }

    def copyCollection(node: Node, request: Request)(implicit ec: ExecutionContext): Future[Node] = ???

    def validateExistingNode(node: Node): Unit = {
        val invalidContentTypes: util.List[String] = if (Platform.config.hasPath("invalid.copy.contentTypes"))
            Platform.config.getStringList("invalid.copy.contentTypes") else new util.ArrayList[String]()
        if (!CollectionUtils.isEmpty(invalidContentTypes) && invalidContentTypes.contains(node.getMetadata.get(CopyConstants.CONTENT_TYPE).asInstanceOf[String]))
            throw new ClientException(CopyConstants.CONTENT_TYPE_ASSET_CAN_NOT_COPY, "ContentType " + node.getMetadata.get(CopyConstants.CONTENT_TYPE).asInstanceOf[String] + " can not be copied.")
        val invalidStatusList: util.List[String] = if (Platform.config.hasPath("invalid.copy.statusList"))
            Platform.config.getStringList("invalid.copy.statusList") else new util.ArrayList[String]()
        if (invalidStatusList.contains(node.getMetadata.get(CopyConstants.STATUS).asInstanceOf[String]))
            throw new ClientException(CopyConstants.ERR_INVALID_REQUEST, "Cannot Copy content which is in " + node.getMetadata.get(CopyConstants.STATUS).asInstanceOf[String].toLowerCase + " status")

    }

    def validateRequest(request: Request): Unit = {
        val keysNotPresent = CopyConstants.REQUIRED_KEYS.filter(key => emptyCheckFilter(request.getRequest.getOrDefault(key, "")))
        if (keysNotPresent.nonEmpty)
            throw new ClientException(CopyConstants.ERR_INVALID_REQUEST, "Please provide valid value for " + keysNotPresent)
    }

    def emptyCheckFilter(key: AnyRef): Boolean = key match {
        case k: String => k.asInstanceOf[String].isEmpty
        case k: util.Map[String, AnyRef] => MapUtils.isEmpty(k.asInstanceOf[util.Map[String, AnyRef]])
        case k: util.List[String] => CollectionUtils.isEmpty(k.asInstanceOf[util.List[String]])
        case _ => true
    }

    def getCopyRequest(node: Node, request: Request): Request = {
        val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, new util.ArrayList(), CopyConstants.CONTENT_SCHEMA_NAME, CopyConstants.SCHEMA_VERSION)
        cleanUpCopiedData(metadata)
        val requestMap = request.getRequest
        requestMap.remove(CopyConstants.MODE)
        metadata.putAll(requestMap)
        metadata.put(CopyConstants.STATUS, "Draft")
        metadata.put(CopyConstants.ORIGIN, node.getIdentifier)
        metadata.put(CopyConstants.IDENTIFIER, Identifier.getIdentifier(node.getGraphId, Identifier.getUniqueIdFromTimestamp))
        val originData: util.Map[String, AnyRef] = getOriginData(metadata)
        if (MapUtils.isNotEmpty(originData))
            metadata.put(CopyConstants.ORIGIN_DATA, originData)
        val req = new Request(request)
        req.getRequest.clear()
        req.setRequest(metadata)
        req
    }

    def getOriginData(metadata: util.Map[String, AnyRef]): util.Map[String, AnyRef] = {
        val originMetadataKeys: util.List[String] = if (Platform.config.hasPath("learning.content.copy.origin_data"))
            Platform.config.getStringList("learning.content.copy.origin_data") else new util.ArrayList[String]()
        if (CollectionUtils.isNotEmpty(originMetadataKeys))
            originMetadataKeys.asScala.filter(key => metadata.containsKey(key)).map(key => key -> metadata.get(key)).toMap.asJava else new util.HashMap[String, AnyRef]()
    }

    def cleanUpCopiedData(metadata: util.Map[String, AnyRef]): Unit = {
        val propsToRemove: util.List[String] = if (Platform.config.hasPath("learning.content.copy.props_to_remove"))
            Platform.config.getStringList("learning.content.copy.props_to_remove") else new util.ArrayList[String]()
        metadata.keySet().removeAll(propsToRemove)
    }

    def copyURLToFile(fileUrl: String): File = try {
        val file = new File(getFileNameFromURL(fileUrl))
        FileUtils.copyURLToFile(new URL(fileUrl), file)
        file
    } catch {
        case e: IOException => throw new ClientException(CopyConstants.ERR_INVALID_UPLOAD_FILE_URL, "fileUrl is invalid.")
    }

    protected def getFileNameFromURL(fileUrl: String): String = if (!FilenameUtils.getExtension(fileUrl).isEmpty)
        FilenameUtils.getBaseName(fileUrl) + "_" + System.currentTimeMillis + "." + FilenameUtils.getExtension(fileUrl) else FilenameUtils.getBaseName(fileUrl) + "_" + System.currentTimeMillis

    def isInternalUrl(url: String): Boolean = url.contains(ss.getContainerName())

}
