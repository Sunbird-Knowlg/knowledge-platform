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
import org.sunbird.graph.schema.DefinitionNode
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

    implicit val ss: StorageService = new StorageService

    private var copySchemeMap: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]()

    def copy(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
        request.getContext.put(ContentConstants.COPY_SCHEME, request.getRequest.getOrDefault(ContentConstants.COPY_SCHEME, ""))
        validateRequest(request)
        DataNode.read(request).map(node => {
            validateExistingNode(node)
            copySchemeMap = DefinitionNode.getCopySchemeContentType(request)
            val copiedNodeFuture: Future[Node] = node.getMetadata.get(ContentConstants.MIME_TYPE) match {
                case ContentConstants.COLLECTION_MIME_TYPE =>
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
                response.put(ContentConstants.VERSION_KEY, copiedNode.getMetadata.get(ContentConstants.VERSION_KEY))
                response
            })
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
    }

    def copyContent(node: Node, request: Request)(implicit ec: ExecutionContext,  oec: OntologyEngineContext): Future[Node] = {
        //        cleanUpNodeRelations(node)
        val copyCreateReq: Future[Request] = getCopyRequest(node, request)
        copyCreateReq.map(req => {
            DataNode.create(req).map(copiedNode => {
                artifactUpload(node, copiedNode, request)
            }).flatMap(f => f)
        }).flatMap(f => f)
    }

    def copyCollection(originNode: Node, request: Request)(implicit ec:ExecutionContext, oec: OntologyEngineContext):Future[Node] = {
        val copyType = request.getRequest.get(ContentConstants.COPY_TYPE).asInstanceOf[String]
        copyContent(originNode, request).map(node => {
            val req = new Request(request)
            req.getContext.put(ContentConstants.SCHEMA_NAME, ContentConstants.COLLECTION_SCHEMA_NAME)
            req.getContext.put(ContentConstants.VERSION, ContentConstants.SCHEMA_VERSION)
            req.put(ContentConstants.ROOT_ID, request.get(ContentConstants.IDENTIFIER))
            req.put(ContentConstants.MODE, request.get(ContentConstants.MODE))
            HierarchyManager.getHierarchy(req).map(response => {
                val originHierarchy = response.getResult.getOrDefault(ContentConstants.CONTENT, new java.util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
                copyType match {
                    case ContentConstants.COPY_TYPE_SHALLOW => updateShallowHierarchy(request, node, originNode, originHierarchy)
                    case _ => updateHierarchy(request,node, originNode, originHierarchy, copyType)
                }
            }).flatMap(f=>f)
        }).flatMap(f => f) recoverWith {case e: CompletionException => throw e.getCause}
    }

    def updateHierarchy(request: Request, node: Node, originNode: Node, originHierarchy: util.Map[String, AnyRef], copyType:String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        val updateHierarchyRequest = prepareHierarchyRequest(originHierarchy, originNode, node, copyType, request)
        val hierarchyRequest = new Request(request)
        hierarchyRequest.putAll(updateHierarchyRequest)
        hierarchyRequest.getContext.put(ContentConstants.SCHEMA_NAME, ContentConstants.COLLECTION_SCHEMA_NAME)
        hierarchyRequest.getContext.put(ContentConstants.VERSION, ContentConstants.SCHEMA_VERSION)
        UpdateHierarchyManager.updateHierarchy(hierarchyRequest).map(response=>node)
    }

    def updateShallowHierarchy(request: Request, node: Node, originNode: Node, originHierarchy: util.Map[String, AnyRef])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        val childrenHierarchy = originHierarchy.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]]
        val req = new Request(request)
        req.getContext.put(ContentConstants.SCHEMA_NAME, ContentConstants.COLLECTION_SCHEMA_NAME)
        req.getContext.put(ContentConstants.VERSION, ContentConstants.SCHEMA_VERSION)
        req.getContext.put(ContentConstants.IDENTIFIER, node.getIdentifier)
        req.put(ContentConstants.HIERARCHY, ScalaJsonUtils.serialize(new java.util.HashMap[String, AnyRef](){{
            put(ContentConstants.IDENTIFIER, node.getIdentifier)
            put(ContentConstants.CHILDREN, childrenHierarchy)
        }}))
        DataNode.update(req).map(node=>node)
    }

    def validateExistingNode(node: Node): Unit = {
        if (!CollectionUtils.isEmpty(invalidContentTypes) && invalidContentTypes.contains(node.getMetadata.get(ContentConstants.CONTENT_TYPE).asInstanceOf[String]))
            throw new ClientException(ContentConstants.CONTENT_TYPE_ASSET_CAN_NOT_COPY, "ContentType " + node.getMetadata.get(ContentConstants.CONTENT_TYPE).asInstanceOf[String] + " can not be copied.")
        if (invalidStatusList.contains(node.getMetadata.get(ContentConstants.STATUS).asInstanceOf[String]))
            throw new ClientException(ContentConstants.ERR_INVALID_REQUEST, "Cannot Copy content which is in " + node.getMetadata.get(ContentConstants.STATUS).asInstanceOf[String].toLowerCase + " status")
    }

    def validateRequest(request: Request): Unit = {
        val keysNotPresent = ContentConstants.REQUIRED_KEYS.filter(key => emptyCheckFilter(request.getRequest.getOrDefault(key, "")))
        if (keysNotPresent.nonEmpty)
            throw new ClientException(ContentConstants.ERR_INVALID_REQUEST, "Please provide valid value for " + keysNotPresent)
        if (StringUtils.equalsIgnoreCase(request.getRequest.getOrDefault(ContentConstants.COPY_TYPE, ContentConstants.COPY_TYPE_DEEP).asInstanceOf[String], ContentConstants.COPY_TYPE_SHALLOW) &&
            StringUtils.isNotBlank(request.getContext.get(ContentConstants.COPY_SCHEME).asInstanceOf[String]))
            throw new ClientException(ContentConstants.ERR_INVALID_REQUEST, "Content can not be shallow copied with copy scheme.")
        if(StringUtils.isNotBlank(request.getContext.get(ContentConstants.COPY_SCHEME).asInstanceOf[String]) && !DefinitionNode.getAllCopyScheme(request).contains(request.getRequest.getOrDefault(ContentConstants.COPY_SCHEME, "").asInstanceOf[String]))
            throw new ClientException(ContentConstants.ERR_INVALID_REQUEST, "Invalid copy scheme, Please provide valid copy scheme")
    }

    def emptyCheckFilter(key: AnyRef): Boolean = key match {
        case k: String => k.asInstanceOf[String].isEmpty
        case k: util.Map[String, AnyRef] => MapUtils.isEmpty(k.asInstanceOf[util.Map[String, AnyRef]])
        case k: util.List[String] => CollectionUtils.isEmpty(k.asInstanceOf[util.List[String]])
        case _ => true
    }

    def getCopyRequest(node: Node, request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Request] = {
        val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, new util.ArrayList(), ContentConstants.CONTENT_SCHEMA_NAME, ContentConstants.SCHEMA_VERSION)
        val requestMap = request.getRequest
        requestMap.remove(ContentConstants.MODE)
        requestMap.remove(ContentConstants.COPY_SCHEME).asInstanceOf[String]
        val copyType = requestMap.remove(ContentConstants.COPY_TYPE).asInstanceOf[String]
        val originData: util.Map[String, AnyRef] = getOriginData(metadata, copyType)
        cleanUpCopiedData(metadata, copyType)
        metadata.putAll(requestMap)
        metadata.put(ContentConstants.STATUS, "Draft")
        metadata.put(ContentConstants.ORIGIN, node.getIdentifier)
        metadata.put(ContentConstants.IDENTIFIER, Identifier.getIdentifier(request.getContext.get("graph_id").asInstanceOf[String], Identifier.getUniqueIdFromTimestamp))
        if (MapUtils.isNotEmpty(originData))
            metadata.put(ContentConstants.ORIGIN_DATA, originData)
        updateToCopySchemeContentType(request, metadata.get(ContentConstants.CONTENT_TYPE).asInstanceOf[String], metadata)
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
            put(ContentConstants.COPY_TYPE, copyType)
        }}
    }

    def cleanUpCopiedData(metadata: util.Map[String, AnyRef], copyType:String): util.Map[String, AnyRef] = {
        if(StringUtils.equalsIgnoreCase(ContentConstants.COPY_TYPE_SHALLOW, copyType)) {
            metadata.keySet().removeAll(metadataNotTobeCopied.asScala.toList.filter(str => !str.contains("dial")).asJava)
        } else metadata.keySet().removeAll(metadataNotTobeCopied)
        metadata
    }

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

    //    def cleanUpNodeRelations(node: Node): Unit = {
    //        val relationsToDelete: util.List[Relation] = node.getOutRelations.asScala.filter(relation => ContentConstants.END_NODE_OBJECT_TYPES.contains(relation.getEndNodeObjectType)).toList.asJava
    //        node.getOutRelations.removeAll(relationsToDelete)
    //    }

    def getUpdateRequest(request: Request, copiedNode: Node, artifactUrl: String): Request = {
        val req = new Request()
        val context = request.getContext
        context.put(ContentConstants.IDENTIFIER, copiedNode.getIdentifier)
        req.setContext(context)
        req.put(ContentConstants.VERSION_KEY, copiedNode.getMetadata.get(ContentConstants.VERSION_KEY))
        req.put(ContentConstants.ARTIFACT_URL, artifactUrl)
        req
    }

    protected def getFileNameFromURL(fileUrl: String): String = if (!FilenameUtils.getExtension(fileUrl).isEmpty)
        FilenameUtils.getBaseName(fileUrl) + "_" + System.currentTimeMillis + "." + FilenameUtils.getExtension(fileUrl) else FilenameUtils.getBaseName(fileUrl) + "_" + System.currentTimeMillis

    protected def isInternalUrl(url: String): Boolean = url.contains(ss.getContainerName())

    def prepareHierarchyRequest(originHierarchy: util.Map[String, AnyRef], originNode: Node, node: Node, copyType: String, request: Request):util.HashMap[String, AnyRef] = {
        val children:util.List[util.Map[String, AnyRef]] = originHierarchy.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]]
        if(null != children && !children.isEmpty) {
            val nodesModified = new util.HashMap[String, AnyRef]()
            val hierarchy = new util.HashMap[String, AnyRef]()
            hierarchy.put(node.getIdentifier, new util.HashMap[String, AnyRef](){{
                put(ContentConstants.CHILDREN, new util.ArrayList[String]())
                put(ContentConstants.ROOT, true.asInstanceOf[AnyRef])
                put(ContentConstants.CONTENT_TYPE, node.getMetadata.get(ContentConstants.CONTENT_TYPE))
            }})
            populateHierarchyRequest(children, nodesModified, hierarchy, node.getIdentifier, copyType, request)
            new util.HashMap[String, AnyRef](){{
                put(ContentConstants.NODES_MODIFIED, nodesModified)
                put(ContentConstants.HIERARCHY, hierarchy)
            }}
        } else new util.HashMap[String, AnyRef]()
    }

    def populateHierarchyRequest(children: util.List[util.Map[String, AnyRef]], nodesModified: util.HashMap[String, AnyRef], hierarchy: util.HashMap[String, AnyRef], parentId: String, copyType: String, request: Request): Unit = {
        if (null != children && !children.isEmpty) {
            children.asScala.toList.foreach(child => {
                val id = if ("Parent".equalsIgnoreCase(child.get(ContentConstants.VISIBILITY).asInstanceOf[String])) {
                        val identifier = UUID.randomUUID().toString
                        updateToCopySchemeContentType(request, child.get(ContentConstants.CONTENT_TYPE).asInstanceOf[String],  child)
                        nodesModified.put(identifier, new util.HashMap[String, AnyRef]() {{
                                put(ContentConstants.METADATA,  cleanUpCopiedData(new util.HashMap[String, AnyRef]() {{
                                    putAll(child)
                                    put(ContentConstants.CHILDREN, new util.ArrayList())
                                    internalHierarchyProps.map(key => remove(key))
                                }}, copyType))
                                put(ContentConstants.ROOT, false.asInstanceOf[AnyRef])
                                put("isNew", true.asInstanceOf[AnyRef])
                                put("setDefaultValue", false.asInstanceOf[AnyRef])
                            }})
                        identifier
                    } else
                        child.get(ContentConstants.IDENTIFIER).asInstanceOf[String]
                hierarchy.put(id, new util.HashMap[String, AnyRef]() {{
                        put(ContentConstants.CHILDREN, new util.ArrayList[String]())
                        put(ContentConstants.ROOT, false.asInstanceOf[AnyRef])
                        put(ContentConstants.CONTENT_TYPE, child.get(ContentConstants.CONTENT_TYPE))
                    }})
                hierarchy.get(parentId).asInstanceOf[util.Map[String, AnyRef]].get(ContentConstants.CHILDREN).asInstanceOf[util.List[String]].add(id)
                populateHierarchyRequest(child.get(ContentConstants.CHILDREN).asInstanceOf[util.List[util.Map[String, AnyRef]]], nodesModified, hierarchy, id, copyType, request)
            })
        }
    }

    def artifactUpload(node: Node, copiedNode: Node, request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        val artifactUrl = node.getMetadata.getOrDefault(ContentConstants.ARTIFACT_URL, "").asInstanceOf[String]
        val mimeType = node.getMetadata.get(ContentConstants.MIME_TYPE).asInstanceOf[String]
        val contentType = node.getMetadata.get(ContentConstants.CONTENT_TYPE).asInstanceOf[String]
        if (StringUtils.isNotBlank(artifactUrl) && !restrictedMimeTypesForUpload.contains(mimeType)) {
            val mimeTypeManager = MimeTypeManagerFactory.getManager(contentType, mimeType)
            val uploadFuture = if (isInternalUrl(artifactUrl)) {
                val file = copyURLToFile(copiedNode.getIdentifier, artifactUrl)
                if (mimeTypeManager.isInstanceOf[H5PMimeTypeMgrImpl])
                    mimeTypeManager.asInstanceOf[H5PMimeTypeMgrImpl].copyH5P(file, copiedNode)
                else
                    mimeTypeManager.upload(copiedNode.getIdentifier, copiedNode, file, None)
            } else mimeTypeManager.upload(copiedNode.getIdentifier, copiedNode, node.getMetadata.getOrDefault(ContentConstants.ARTIFACT_URL, "").asInstanceOf[String], None)
            uploadFuture.map(uploadData => {
                DataNode.update(getUpdateRequest(request, copiedNode, uploadData.getOrElse(ContentConstants.ARTIFACT_URL, "").asInstanceOf[String]))
            }).flatMap(f => f)
        } else Future(copiedNode)
    }

    def validateShallowCopyReq(node: Node, request: Request) = {
        val copyType: String = request.getRequest.get("copyType").asInstanceOf[String]
        if(StringUtils.equalsIgnoreCase("shallow", copyType) && !StringUtils.equalsIgnoreCase("Live", node.getMetadata.get("status").asInstanceOf[String]))
            throw new ClientException(ContentConstants.ERR_INVALID_REQUEST, "Content with status " + node.getMetadata.get(ContentConstants.STATUS).asInstanceOf[String].toLowerCase + " cannot be partially (shallow) copied.")
        //TODO: check if need to throw client exception for combination of copyType=shallow and mode=edit
    }

    def updateToCopySchemeContentType(request: Request, contentType: String, metadata: util.Map[String, AnyRef]): Unit = {
        if (StringUtils.isNotBlank(request.getContext.getOrDefault(ContentConstants.COPY_SCHEME, "").asInstanceOf[String]))
            metadata.put(ContentConstants.CONTENT_TYPE, copySchemeMap.getOrDefault(contentType, contentType))
    }
}
