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
import org.sunbird.graph.utils.{NodeUtil, ScalaJsonUtils}
import org.sunbird.managers.{HierarchyManager, UpdateHierarchyManager}
import org.sunbird.mimetype.factory.MimeTypeManagerFactory
import org.sunbird.mimetype.mgr.impl.H5PMimeTypeMgrImpl

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


object CopyManager {
    implicit val oec: OntologyEngineContext = new OntologyEngineContext
    private val TEMP_FILE_LOCATION = Platform.getString("content.upload.temp_location", "/tmp/content")
    private val metadataNotTobeCopied = Platform.config.getStringList("content.copy.props_to_remove")
    private val invalidStatusList: util.List[String] = Platform.getStringList("content.copy.invalid_statusList", new util.ArrayList[String]())
    private val invalidContentTypes: util.List[String] = Platform.getStringList("content.copy.invalid_contentTypes", new util.ArrayList[String]())
    private val originMetadataKeys: util.List[String] = Platform.getStringList("content.copy.origin_data", new util.ArrayList[String]())
    private val internalHierarchyProps = List("identifier", "parent", "index", "depth")
    private val restrictedMimeTypesForUpload = List("application/vnd.ekstep.ecml-archive","application/vnd.ekstep.content-collection")
    val collSchemaName: String = "collection"
    val version = "1.0"

    implicit val ss: StorageService = new StorageService

    def copy(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        validateRequest(request)
        DataNode.read(request).map(node => {
            validateExistingNode(node)
            val copiedNodeFuture: Future[Node] = node.getMetadata.get(CopyConstants.MIME_TYPE) match {
                case CopyConstants.COLLECTION_MIME_TYPE =>
                    node.setInRelations(null)
                    node.setOutRelations(null)
                    validateShallowCopyReq(node, request)
                    copyCollection(node, request)
                case _ =>
                    node.setInRelations(null)
                    copyContent(node, request)
            }
            copiedNodeFuture.map(copiedNode => {
                val response = ResponseHandler.OK()
                response.put("node_id", new util.HashMap[String, AnyRef](){{
                    put(node.getIdentifier, copiedNode.getIdentifier)
                }})
                response
            })
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
    }

    def copyContent(node: Node, request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        //        cleanUpNodeRelations(node)
        val copyCreateReq: Future[Request] = getCopyRequest(node, request)
        copyCreateReq.map(req => {
            DataNode.create(req).map(copiedNode => {
                artifactUpload(node, copiedNode, request)
            }).flatMap(f => f)
        }).flatMap(f => f)
    }

    def copyCollection(originNode: Node, request: Request)(implicit ec:ExecutionContext):Future[Node] = {
        val copyType = request.getRequest.get(CopyConstants.COPY_TYPE).asInstanceOf[String]
        copyContent(originNode, request).map(node => {
            val req = new Request(request)
            req.getContext.put("schemaName", collSchemaName)
            req.getContext.put("version", version)
            req.put(CopyConstants.ROOT_ID, request.get(CopyConstants.IDENTIFIER))
            req.put(CopyConstants.MODE, request.get(CopyConstants.MODE))
            HierarchyManager.getHierarchy(req).map(response => {
                val originHierarchy = response.getResult.getOrDefault(CopyConstants.CONTENT, new java.util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
                copyType match {
                    case CopyConstants.COPY_TYPE_SHALLOW => updateShallowHierarchy(request, node, originNode, originHierarchy)
                    case _ => updateHierarchy(request,node, originNode, originHierarchy, copyType)
                }
            }).flatMap(f=>f)
        }).flatMap(f => f) recoverWith {case e: CompletionException => throw e.getCause}
    }

    def updateHierarchy(request: Request, node: Node, originNode: Node, originHierarchy: util.Map[String, AnyRef], copyType:String)(implicit ec: ExecutionContext): Future[Node] = {
        val updateHierarchyRequest = prepareHierarchyRequest(originHierarchy, originNode, node, copyType)
        val hierarchyRequest = new Request(request)
        hierarchyRequest.putAll(updateHierarchyRequest)
        hierarchyRequest.getContext.put("schemaName", collSchemaName)
        hierarchyRequest.getContext.put("version", version)
        UpdateHierarchyManager.updateHierarchy(hierarchyRequest).map(response=>node)
    }

    def updateShallowHierarchy(request: Request, node: Node, originNode: Node, originHierarchy: util.Map[String, AnyRef])(implicit ec: ExecutionContext): Future[Node] = {
        val childrenHierarchy = originHierarchy.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]]
        val req = new Request(request)
        req.getContext.put("schemaName", collSchemaName)
        req.getContext.put("version", version)
        req.getContext.put(CopyConstants.IDENTIFIER, node.getIdentifier)
        req.put(CopyConstants.HIERARCHY, ScalaJsonUtils.serialize(new java.util.HashMap[String, AnyRef](){{
            put(CopyConstants.IDENTIFIER, node.getIdentifier)
            put(CopyConstants.CHILDREN, childrenHierarchy)
        }}))
        DataNode.update(req).map(node=>node)
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

    def getCopyRequest(node: Node, request: Request)(implicit ec: ExecutionContext): Future[Request] = {
        val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, new util.ArrayList(), CopyConstants.CONTENT_SCHEMA_NAME, CopyConstants.SCHEMA_VERSION)
        val requestMap = request.getRequest
        requestMap.remove(CopyConstants.MODE)
        val copyType = requestMap.remove(CopyConstants.COPY_TYPE).asInstanceOf[String]
        val originData: util.Map[String, AnyRef] = getOriginData(metadata, copyType)
        cleanUpCopiedData(metadata, copyType)
        metadata.putAll(requestMap)
        metadata.put(CopyConstants.STATUS, "Draft")
        metadata.put(CopyConstants.ORIGIN, node.getIdentifier)
        metadata.put(CopyConstants.IDENTIFIER, Identifier.getIdentifier(request.getContext.get("graph_id").asInstanceOf[String], Identifier.getUniqueIdFromTimestamp))

        if (MapUtils.isNotEmpty(originData))
            metadata.put(CopyConstants.ORIGIN_DATA, originData)
        val req = new Request(request)
        req.setRequest(metadata)
        if (StringUtils.equalsIgnoreCase("application/vnd.ekstep.ecml-archive", node.getMetadata.get("mimeType").asInstanceOf[String])) {
            val readReq = new Request()
            readReq.setContext(request.getContext)
            readReq.put("identifier", node.getIdentifier)
            readReq.put("fields", util.Arrays.asList("body"))
            DataNode.read(readReq).map(node => {
                if (null != node.getMetadata.get("body"))
                    req.put("body", node.getMetadata.get("body").asInstanceOf[String])
                req
            })
        } else Future {req}
    }

    def getOriginData(metadata: util.Map[String, AnyRef], copyType:String): util.Map[String, AnyRef] = {
        new java.util.HashMap[String, AnyRef](){{
            putAll(originMetadataKeys.asScala.filter(key => metadata.containsKey(key)).map(key => key -> metadata.get(key)).toMap.asJava)
            put(CopyConstants.COPY_TYPE, copyType)
        }}
    }
    
    def cleanUpCopiedData(metadata: util.Map[String, AnyRef], copyType:String): util.Map[String, AnyRef] = {
        if(StringUtils.equalsIgnoreCase(CopyConstants.COPY_TYPE_SHALLOW, copyType)) {
            metadata.keySet().removeAll(metadataNotTobeCopied.asScala.toList.filter(str => !str.contains("dial")).asJava)
        } else metadata.keySet().removeAll(metadataNotTobeCopied)
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

    def prepareHierarchyRequest(originHierarchy: util.Map[String, AnyRef], originNode: Node, node: Node, copyType: String):util.HashMap[String, AnyRef] = {
        val children:util.List[util.Map[String, AnyRef]] = originHierarchy.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]]
        if(null != children && !children.isEmpty) {
            val nodesModified = new util.HashMap[String, AnyRef]()
            val hierarchy = new util.HashMap[String, AnyRef]()
            hierarchy.put(node.getIdentifier, new util.HashMap[String, AnyRef](){{
                put(CopyConstants.CHILDREN, new util.ArrayList[String]())
                put(CopyConstants.ROOT, true.asInstanceOf[AnyRef])
                put(CopyConstants.CONTENT_TYPE, node.getMetadata.get(CopyConstants.CONTENT_TYPE))
            }})
            populateHierarchyRequest(children, nodesModified, hierarchy, node.getIdentifier, copyType)
            new util.HashMap[String, AnyRef](){{
                put(CopyConstants.NODES_MODIFIED, nodesModified)
                put(CopyConstants.HIERARCHY, hierarchy)
            }}
        } else new util.HashMap[String, AnyRef]()
    }

    def populateHierarchyRequest(children: util.List[util.Map[String, AnyRef]], nodesModified: util.HashMap[String, AnyRef], hierarchy: util.HashMap[String, AnyRef], parentId: String, copyType: String): Unit = {
        if (null != children && !children.isEmpty) {
            children.asScala.toList.foreach(child => {
                val id = if ("Parent".equalsIgnoreCase(child.get(CopyConstants.VISIBILITY).asInstanceOf[String])) {
                        val identifier = UUID.randomUUID().toString
                        nodesModified.put(identifier, new util.HashMap[String, AnyRef]() {{
                                put(CopyConstants.METADATA,  cleanUpCopiedData(new util.HashMap[String, AnyRef]() {{
                                    putAll(child)
                                    put(CopyConstants.CHILDREN, new util.ArrayList())
                                    internalHierarchyProps.map(key => remove(key))
                                }}, copyType))
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
                populateHierarchyRequest(child.get(CopyConstants.CHILDREN).asInstanceOf[util.List[util.Map[String, AnyRef]]], nodesModified, hierarchy, id, copyType)
            })
        }
    }

    def artifactUpload(node: Node, copiedNode: Node, request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        val artifactUrl = node.getMetadata.getOrDefault(CopyConstants.ARTIFACT_URL, "").asInstanceOf[String]
        val mimeType = node.getMetadata.get(CopyConstants.MIME_TYPE).asInstanceOf[String]
        val contentType = node.getMetadata.get(CopyConstants.CONTENT_TYPE).asInstanceOf[String]
        if (StringUtils.isNotBlank(artifactUrl) && !restrictedMimeTypesForUpload.contains(mimeType)) {
            val mimeTypeManager = MimeTypeManagerFactory.getManager(contentType, mimeType)
            val uploadFuture = if (isInternalUrl(artifactUrl)) {
                val file = copyURLToFile(copiedNode.getIdentifier, artifactUrl)
                if (mimeTypeManager.isInstanceOf[H5PMimeTypeMgrImpl])
                    mimeTypeManager.asInstanceOf[H5PMimeTypeMgrImpl].copyH5P(file, copiedNode)
                else
                    mimeTypeManager.upload(copiedNode.getIdentifier, copiedNode, file, None)
            } else mimeTypeManager.upload(copiedNode.getIdentifier, copiedNode, node.getMetadata.getOrDefault(CopyConstants.ARTIFACT_URL, "").asInstanceOf[String], None)
            uploadFuture.map(uploadData => {
                DataNode.update(getUpdateRequest(request, copiedNode, uploadData.getOrElse(CopyConstants.ARTIFACT_URL, "").asInstanceOf[String]))
            }).flatMap(f => f)
        } else Future(copiedNode)
    }

    def validateShallowCopyReq(node: Node, request: Request) = {
        val copyType: String = request.getRequest.get("copyType").asInstanceOf[String]
        if(StringUtils.equalsIgnoreCase("shallow", copyType) && !StringUtils.equalsIgnoreCase("Live", node.getMetadata.get("status").asInstanceOf[String]))
            throw new ClientException(CopyConstants.ERR_INVALID_REQUEST, "Content with status " + node.getMetadata.get(CopyConstants.STATUS).asInstanceOf[String].toLowerCase + " cannot be partially (shallow) copied.")
        //TODO: check if need to throw client exception for combination of copyType=shallow and mode=edit
    }
}
