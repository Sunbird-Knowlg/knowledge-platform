package org.sunbird.content.util


import java.io.{File, IOException}
import java.net.URL
import java.util
import java.util.UUID
import java.util.concurrent.CompletionException

import org.apache.commons.collections.CollectionUtils
import org.apache.commons.collections4.MapUtils
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
import org.sunbird.managers.{HierarchyManager, UpdateHierarchyManager}
import org.sunbird.mimetype.factory.MimeTypeManagerFactory

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


object CopyManager {
    implicit val oec: OntologyEngineContext = new OntologyEngineContext
    private val TEMP_FILE_LOCATION = Platform.getString("content.upload.temp_location", "/tmp/content")
    private val metadataNotTobeCopied = Platform.config.getStringList("content.copy.props_to_remove")
    private val invalidStatusList: util.List[String] = if (Platform.config.hasPath("content.copy.invalid_statusList"))
        Platform.config.getStringList("content.copy.invalid_statusList") else new util.ArrayList[String]()

    private val invalidContentTypes: util.List[String] = if (Platform.config.hasPath("content.copy.invalid_contentTypes"))
        Platform.config.getStringList("content.copy.invalid_contentTypes") else new util.ArrayList[String]()

    private val originMetadataKeys: util.List[String] = if (Platform.config.hasPath("content.copy.origin_data"))
        Platform.config.getStringList("content.copy.origin_data") else new util.ArrayList[String]()

    implicit val ss: StorageService = new StorageService

    def copy(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        validateRequest(request)
        DataNode.read(request).map(node => {
            validateExistingNode(node)
            val copiedNodeFuture: Future[Node] = node.getMetadata.get(CopyConstants.MIME_TYPE) match {
                case CopyConstants.COLLECTION_MIME_TYPE =>
                    node.setInRelations(null)
                    node.setOutRelations(null)
                    copyCollection(node, request)
                case _ =>
                    node.setInRelations(null)
                    copyContent(node, request)
            }
            copiedNodeFuture.map(copiedNode => {
                val response = ResponseHandler.OK()
                response.put(node.getIdentifier, copiedNode.getIdentifier)
                response
            })
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
    }

    def copyContent(node: Node, request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        //        cleanUpNodeRelations(node)
        val copyCreateReq = getCopyRequest(node, request)
        DataNode.create(copyCreateReq).map(copiedNode => {
            val mimeTypeManager = MimeTypeManagerFactory.getManager(node.getMetadata.get(CopyConstants.CONTENT_TYPE).asInstanceOf[String], node.getMetadata.get(CopyConstants.MIME_TYPE).asInstanceOf[String])
            if (StringUtils.isNotBlank(node.getMetadata.getOrDefault(CopyConstants.ARTIFACT_URL, "").asInstanceOf[String])) {
                val uploadFuture = if (isInternalUrl(node.getMetadata.getOrDefault(CopyConstants.ARTIFACT_URL, "").asInstanceOf[String])) {
                    val file = copyURLToFile(copiedNode.getIdentifier, node.getMetadata.getOrDefault(CopyConstants.ARTIFACT_URL, "").asInstanceOf[String])
                    mimeTypeManager.upload(copiedNode.getIdentifier, copiedNode, file)
                } else mimeTypeManager.upload(copiedNode.getIdentifier, copiedNode, node.getMetadata.getOrDefault(CopyConstants.ARTIFACT_URL, "").asInstanceOf[String])
                uploadFuture.map(uploadData => {
                    DataNode.update(getUpdateRequest(request, copiedNode, uploadData.getOrElse(CopyConstants.ARTIFACT_URL, "").asInstanceOf[String]))
                }).flatMap(f => f)
            } else Future(copiedNode)
        }).flatMap(f => f)
    }

    def copyCollection(originNode: Node, request: Request)(implicit ec:ExecutionContext):Future[Node] = {
        copyContent(originNode, request).map(node => {
            val req = new Request(request)
            req.getContext.put("schemaName", "collection")
            req.put(CopyConstants.ROOTID, request.get(CopyConstants.IDENTIFIER))
            req.put(CopyConstants.MODE, request.get(CopyConstants.MODE))
            HierarchyManager.getHierarchy(req).map(response => {
                val originHierarchy = response.get(CopyConstants.CONTENT).asInstanceOf[java.util.Map[String, AnyRef]]
                val updateHierarchyRequest = prepareHierarchyRequest(originHierarchy, originNode, node)
                val hierarchyRequest = new Request(request)
                hierarchyRequest.getContext.put("schemaName", "collection")
                hierarchyRequest.putAll(updateHierarchyRequest)
                UpdateHierarchyManager.updateHierarchy(hierarchyRequest)
                node
            })
        }).flatMap(f => f) recoverWith {case e: CompletionException => throw e.getCause}
    }

    def validateExistingNode(node: Node): Unit = {
        if (!CollectionUtils.isEmpty(invalidContentTypes) && invalidContentTypes.contains(node.getMetadata.get(CopyConstants.CONTENT_TYPE).asInstanceOf[String]))
            throw new ClientException(CopyConstants.CONTENT_TYPE_ASSET_CAN_NOT_COPY, "ContentType " + node.getMetadata.get(CopyConstants.CONTENT_TYPE).asInstanceOf[String] + " can not be copied.")
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
        metadata.put(CopyConstants.IDENTIFIER, Identifier.getIdentifier(request.getContext.get("graph_id").asInstanceOf[String], Identifier.getUniqueIdFromTimestamp))
        val originData: util.Map[String, AnyRef] = getOriginData(metadata)
        if (MapUtils.isNotEmpty(originData))
            metadata.put(CopyConstants.ORIGIN_DATA, originData)
        val req = new Request(request)
        req.setRequest(metadata)
        req
    }

    def getOriginData(metadata: util.Map[String, AnyRef]): util.Map[String, AnyRef] = if (CollectionUtils.isNotEmpty(originMetadataKeys))
            originMetadataKeys.asScala.filter(key => metadata.containsKey(key)).map(key => key -> metadata.get(key)).toMap.asJava else new util.HashMap[String, AnyRef]()
    
    def cleanUpCopiedData(metadata: util.Map[String, AnyRef]): util.Map[String, AnyRef] = {
        metadata.keySet().removeAll(metadataNotTobeCopied)
        metadata
    }

    def copyURLToFile(objectId: String, fileUrl: String): File = try {
        val file = new File(getBasePath(objectId) + File.separator + getFileNameFromURL(fileUrl))
        FileUtils.copyURLToFile(new URL(fileUrl), file)
        file
    } catch {case e: IOException => throw new ClientException("ERR_INVALID_FILE_URL", "Please Provide Valid File Url!")}

    def getBasePath(objectId: String): String = {
        if (!StringUtils.isBlank(objectId)) TEMP_FILE_LOCATION + File.separator + System.currentTimeMillis + "_temp" + File.separator + objectId else ""
    }

    //    def cleanUpNodeRelations(node: Node): Unit = {
    //        val relationsToDelete: util.List[Relation] = node.getOutRelations.asScala.filter(relation => CopyConstants.END_NODE_OBJECT_TYPES.contains(relation.getEndNodeObjectType)).toList.asJava
    //        node.getOutRelations.removeAll(relationsToDelete)
    //    }

    def getUpdateRequest(request: Request, copiedNode: Node, artifactUrl: String): Request = {
        val req = new Request()
        val context = request.getContext
        context.put(CopyConstants.IDENTIFIER, copiedNode.getIdentifier)
        req.setContext(context)
        req.put(CopyConstants.VERSION_KEY, copiedNode.getMetadata.get(CopyConstants.VERSION_KEY))
        req.put(CopyConstants.ARTIFACT_URL, artifactUrl)
        req
    }

    protected def getFileNameFromURL(fileUrl: String): String = if (!FilenameUtils.getExtension(fileUrl).isEmpty)
        FilenameUtils.getBaseName(fileUrl) + "_" + System.currentTimeMillis + "." + FilenameUtils.getExtension(fileUrl) else FilenameUtils.getBaseName(fileUrl) + "_" + System.currentTimeMillis

    protected def isInternalUrl(url: String): Boolean = url.contains(ss.getContainerName())

    def prepareHierarchyRequest(originHierarchy: util.Map[String, AnyRef], originNode: Node, node: Node):util.HashMap[String, AnyRef] = {
        val children:util.List[util.Map[String, AnyRef]] = originHierarchy.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]]
        if(null != children && !children.isEmpty) {
            val nodesModified = new util.HashMap[String, AnyRef]()
            val hierarchy = new util.HashMap[String, AnyRef]()
            hierarchy.put(node.getIdentifier, new util.HashMap[String, AnyRef](){{
                put(CopyConstants.CHILDREN, new util.ArrayList[String]())
                put(CopyConstants.ROOT, true.asInstanceOf[AnyRef])
                put(CopyConstants.CONTENT_TYPE, node.getMetadata.get(CopyConstants.CONTENT_TYPE))
            }})
            populateHierarchyRequest(children, nodesModified, hierarchy, node.getIdentifier)
            new util.HashMap[String, AnyRef](){{
                put(CopyConstants.NODES_MODIFIED, nodesModified)
                put(CopyConstants.HIERARCHY, hierarchy)
            }}
        } else new util.HashMap[String, AnyRef]()
    }

    def populateHierarchyRequest(children: util.List[util.Map[String, AnyRef]], nodesModified: util.HashMap[String, AnyRef], hierarchy: util.HashMap[String, AnyRef], parentId: String): Unit = {
        if (null != children && !children.isEmpty) {
            children.asScala.toList.foreach(child => {
                val id = if ("Parent".equalsIgnoreCase(child.get(CopyConstants.VISIBILITY).asInstanceOf[String])) {
                        val identifier = UUID.randomUUID().toString
                        nodesModified.put(identifier, new util.HashMap[String, AnyRef]() {{
                                put(CopyConstants.METADATA,  cleanUpCopiedData(new util.HashMap[String, AnyRef]() {{
                                    putAll(child)
                                    put(CopyConstants.CHILDREN, new util.ArrayList())
                                    List("identifier", "parent", "index", "depth").map(key => remove(key))
                                }}))
                                put(CopyConstants.ROOT, false.asInstanceOf[AnyRef])
                                put("isNew", true.asInstanceOf[AnyRef])
                                put("setDefaultValue", false.asInstanceOf[AnyRef])
                            }})
                        identifier
                    } else 
                        child.get(CopyConstants.IDENTIFIER).asInstanceOf[String]
                hierarchy.put(id, new util.HashMap[String, AnyRef]() {{
                        put(CopyConstants.CHILDREN, new util.ArrayList[String]())
                        put(CopyConstants.ROOT, false.asInstanceOf[AnyRef])
                        put(CopyConstants.CONTENT_TYPE, child.get(CopyConstants.CONTENT_TYPE))
                    }})
                hierarchy.get(parentId).asInstanceOf[util.Map[String, AnyRef]].get(CopyConstants.CHILDREN).asInstanceOf[util.List[String]].add(id)
                populateHierarchyRequest(child.get(CopyConstants.CHILDREN).asInstanceOf[util.List[util.Map[String, AnyRef]]], nodesModified, hierarchy, id)
            })
        }
    }
}
