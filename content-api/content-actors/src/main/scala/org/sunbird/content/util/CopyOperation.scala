package org.sunbird.content.util

import java.io.{File, IOException}
import java.net.URL

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.graph.dac.model.{Node, Relation}
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.collections.MapUtils
import org.sunbird.common.Platform
import org.sunbird.graph.common.Identifier
import java.util
import java.util.concurrent.CompletionException

import akka.actor.ActorRef
import akka.pattern.Patterns
import org.apache.commons.io.{FileUtils, FilenameUtils}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.mimetype.mgr.BaseMimeTypeManager

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object CopyOperation  {

    implicit val ss = new StorageService
    val endNodeObjectTypes = List("Content", "ContentImage")

  def validateCopyContentRequest(existingNode: Node, requestMap: util.Map[String, AnyRef], mode: String): Node = {
    if (null == requestMap)
      throw new ClientException("ERR_INVALID_REQUEST", "Please provide valid request")
    val keys:List[String] = List("createdBy", "createdFor", "organisation", "framework")
    validateOrThrowExceptionForEmptyKeys(requestMap, "Content", keys)
    var notCoppiedContent:util.List[String] = null
    if (Platform.config.hasPath("learning.content.type.not.copied.list"))
      notCoppiedContent = Platform.config.getStringList("learning.content.type.not.copied.list")
    if (!CollectionUtils.isEmpty(notCoppiedContent) && notCoppiedContent.contains(existingNode.getMetadata.get("contentType").asInstanceOf[String]))
      throw new ClientException("CONTENTTYPE_ASSET_CAN_NOT_COPY", "ContentType " + existingNode.getMetadata.get("contentType").asInstanceOf[String] + " can not be copied.")
    val status = existingNode.getMetadata.get("status").asInstanceOf[String]
    val invalidStatusList = Platform.config.getStringList("learning.content.copy.invalid_status_list")
    if (invalidStatusList.contains(status))
      throw new ClientException("ERR_INVALID_REQUEST", "Cannot copy content in " + status.toLowerCase + " status")
    existingNode
  }

   def validateOrThrowExceptionForEmptyKeys(requestMap: util.Map[String, AnyRef], prefix: String, keys: List[String]): Boolean = {
    var errMsg = "Please provide valid value for "
    var flag = false
    val notFoundKeys:util.List[String] = null
    for (key <- keys) {
      if (null == requestMap.get(key))
        flag = true
      else if (requestMap.get(key).isInstanceOf[util.Map[String, AnyRef]])
        flag = MapUtils.isEmpty(requestMap.get(key).asInstanceOf[util.Map[String, AnyRef]])
      else if (requestMap.get(key).isInstanceOf[util.List[String]])
        flag = CollectionUtils.isEmpty(requestMap.get(key).asInstanceOf[util.List[String]])
      else
        flag = StringUtils.isEmpty(requestMap.get(key).asInstanceOf[String])
      if (flag) {
        notFoundKeys.add(key)
      }
    }
    if (CollectionUtils.isEmpty(notFoundKeys))
      return true
    else
      errMsg = errMsg + String.join(", ", notFoundKeys) + "."
    throw new ClientException("ERR_INVALID_REQUEST", errMsg.trim.substring(0, errMsg.length - 1))
  }

  def copyNode(existingNode: Node, requestMap: util.Map[String, AnyRef], mode: String): Node = {
    val newId = Identifier.getIdentifier(existingNode.getGraphId, Identifier.getUniqueIdFromTimestamp)
    val copyNode = new Node(newId, existingNode.getNodeType, existingNode.getObjectType)
    val metaData = new util.HashMap[String, AnyRef](existingNode.getMetadata)
    val originData:util.HashMap[String, AnyRef] = new util.HashMap[String, AnyRef]
    var originNodeMetadataList:util.List[String] = new java.util.ArrayList[String]()
      if (Platform.config.hasPath("learning.content.copy.origin_data"))
        originNodeMetadataList = Platform.config.getStringList("learning.content.copy.origin_data")
    if (CollectionUtils.isNotEmpty(originNodeMetadataList)) {
      originNodeMetadataList.asScala.foreach(meta => {
        if (metaData.containsKey(meta))
          originData.put(meta, metaData.get(meta))
      })
    }
    var nullPropList:util.List[String] = null
    if (Platform.config.hasPath("learning.content.copy.props_to_remove"))
      nullPropList =  Platform.config.getStringList("learning.content.copy.props_to_remove")
    if (CollectionUtils.isNotEmpty(nullPropList))
      nullPropList.asScala.foreach(prop => metaData.remove(prop))
    copyNode.setMetadata(metaData)
    copyNode.setGraphId(existingNode.getGraphId)
    requestMap.remove("mode")
    copyNode.getMetadata.putAll(requestMap)
    copyNode.getMetadata.put("status", "Draft")
    copyNode.getMetadata.put("origin", existingNode.getIdentifier)
    copyNode.getMetadata.put("identifier", newId)
    if (!originData.isEmpty())
      copyNode.getMetadata.put("originData", originData)
    val existingNodeOutRelations:util.List[Relation] = existingNode.getOutRelations.asInstanceOf[util.ArrayList[Relation]]
    val copiedNodeOutRelations:util.List[Relation] = new java.util.ArrayList[Relation]()
    if (!CollectionUtils.isEmpty(existingNodeOutRelations)) {
      for (rel <- existingNodeOutRelations.asScala) {
        if (!endNodeObjectTypes.contains(rel.getEndNodeObjectType))
          copiedNodeOutRelations.add(new Relation(newId, rel.getRelationType, rel.getEndNodeId))
      }
    }
    copyNode.setOutRelations(copiedNodeOutRelations)
    copyNode
  }

  def copyURLToFile(fileUrl: String): File = try {
    val fileName = getFileNameFromURL(fileUrl)
    val file = new File(fileName)
    FileUtils.copyURLToFile(new URL(fileUrl), file)
    file
  } catch {
    case e: IOException =>
      throw new ClientException("ERR_INVALID_UPLOAD_FILE_URL", "fileUrl is invalid.")
  }

  protected def getFileNameFromURL(fileUrl: String): String = {
    var fileName = FilenameUtils.getBaseName(fileUrl) + "_" + System.currentTimeMillis
    if (!FilenameUtils.getExtension(fileUrl).isEmpty) fileName += "." + FilenameUtils.getExtension(fileUrl)
    fileName
  }

  def copy(request: Request, existingNode: Node, collectionActor: ActorRef)(implicit ec: ExecutionContext): Future[util.HashMap[String, String]] = {
    val req = new Request(request)
    req.put("mode", request.get("mode").asInstanceOf[String])
    req.put("contentType", existingNode.getMetadata.get("contentType"))
    req.put("rootId", existingNode.getIdentifier)
    val validatedExistingNode: Node = validateCopyContentRequest(existingNode, request.getRequest, request.get("mode").asInstanceOf[String])
    existingNode.setGraphId(request.getContext.get("graph_id").asInstanceOf[String])
    val copiedNode = copyNode(validatedExistingNode, request.getRequest, request.getRequest.get("mode").asInstanceOf[String])
    request.getRequest.clear()
    request.setRequest(copiedNode.getMetadata)
    createCopyNode(request, existingNode).map(node => {
      if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("mimeType", "").asInstanceOf[String], "application/vnd.ekstep.content-collection")) {
        req.put("idMap", new util.HashMap[String, String]() {
            put(existingNode.getIdentifier, node.getIdentifier)
        })
        req.setOperation("copy")
        val collectionResponse = Patterns.ask(collectionActor, req, 30000) recoverWith { case e: Exception => Future(ResponseHandler.getErrorResponse(e)) }
        collectionResponse.map(response => {
          if (ResponseHandler.checkError(response.asInstanceOf[Response])) {
            throw new ServerException("ERR_WHILE_UPDATING_HIERARCHY_INTO_CASSANDRA", "Error while updating hierarchy into cassandra")
          } else {
            Future(new util.HashMap[String, String]() {
                put(existingNode.getIdentifier, node.getIdentifier)
            })
          }
        }).flatMap(f => f)
      } else {
        Future(new util.HashMap[String, String]() {
            put(existingNode.getIdentifier, node.getIdentifier)
        })
      }
    }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
  }

  def createCopyNode(request: Request, existingNode: Node)(implicit ec: ExecutionContext): Future[Node] = {
    var file: File = null
    DataNode.create(request).map(node => {
      request.getContext.put("identifier", node.getIdentifier)
      request.getRequest.clear()
      if (!existingNode.getMetadata.getOrDefault("artifactUrl", "").asInstanceOf[String].isEmpty) {
//        if (new BaseMimeTypeManager().isInternalUrl(existingNode.getMetadata.getOrDefault("artifactUrl", "").asInstanceOf[String])) {
//          file = copyURLToFile(existingNode.getMetadata.getOrDefault("artifactUrl", "").asInstanceOf[String])
//          request.getRequest.put("file", file)
//        } else
//          request.getRequest.put("fileUrl", existingNode.getMetadata.getOrDefault("artifactUrl", "").asInstanceOf[String])
//          upload(request).map(response => {
//          if (!ResponseHandler.checkError(response)) {
//            if (null != file && file.exists()) {
//              file.delete()
//            }
//            Future(node)
//          }
//          else
//            throw new ClientException("ARTIFACT_NOT_COPIED", "ArtifactUrl not coppied.")
//        }).flatMap(f => f)
        Future(node)
      } else {
        Future(node)
      }
    }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
  }

}
