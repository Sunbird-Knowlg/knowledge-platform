package org.sunbird.graph.schema.validator

import java.util
import java.util.concurrent.CompletionException
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.{DateUtils, JsonUtils, Platform}
import org.sunbird.common.dto.{Request, ResponseHandler}
import org.sunbird.common.exception.ResourceNotFoundException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.enums.AuditProperties
import org.sunbird.graph.dac.model.{Node, Vertex}
import org.sunbird.graph.exception.GraphErrorCodes
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.schema.{DefinitionFactory, IDefinition}
import org.sunbird.graph.service.operation.{NodeAsyncOperations, SearchAsyncOperations}
import org.sunbird.graph.utils.{NodeUtil, ScalaJsonUtils}
import org.sunbird.telemetry.logger.TelemetryManager

import scala.collection.convert.ImplicitConversions._
import scala.concurrent.{ExecutionContext, Future}

trait VersioningNode extends IDefinition {

    val statusList = List("Live", "Unlisted", "Flagged")
    val IMAGE_SUFFIX = ".img"
    val IMAGE_OBJECT_SUFFIX = "Image"
    val COLLECTION_MIME_TYPE = "application/vnd.ekstep.content-collection"


    abstract override def getNode(identifier: String, operation: String, mode: String = "read", versioning: Option[String] = None, disableCache: Option[Boolean] = None)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        operation match {
            case "update" => getNodeToUpdate(identifier, versioning);
            case "read" => getNodeToRead(identifier, mode, disableCache)
            case _ => getNodeToRead(identifier, mode, disableCache)
        }
    }

    abstract override def getVertex(identifier: String, operation: String, mode: String = "read", versioning: Option[String] = None, disableCache: Option[Boolean] = None)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Vertex] = {
        operation match {
            case "update" => getVertexToUpdate(identifier, versioning);
            case "read" => getVertexToRead(identifier, mode, disableCache)
            case _ => getVertexToRead(identifier, mode, disableCache)
        }
    }

    private def getNodeToUpdate(identifier: String, versioning: Option[String] = None)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        val nodeFuture: Future[Node] = super.getNode(identifier , "update", null)
        nodeFuture.map(node => {
            val versioningEnable = versioning.getOrElse({if(schemaValidator.getConfig.hasPath("version"))schemaValidator.getConfig.getString("version") else "disable"})
            if(null == node)
                throw new ResourceNotFoundException(GraphErrorCodes.ERR_INVALID_NODE.toString, "Node Not Found With Identifier : " + identifier)
            else if("enable".equalsIgnoreCase(versioningEnable))
                getEditableNode(identifier, node)
            else
                Future{node}
        }).flatMap(f => f)
    }

    private def getVertexToUpdate(identifier: String, versioning: Option[String] = None)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Vertex] = {
        val vertexFuture: Future[Vertex] = super.getVertex(identifier, "update", null)
        vertexFuture.map(vertex => {
            val versioningEnable = versioning.getOrElse({
                if (schemaValidator.getConfig.hasPath("version")) schemaValidator.getConfig.getString("version") else "disable"
            })
            if (null == vertex)
                throw new ResourceNotFoundException(GraphErrorCodes.ERR_INVALID_NODE.toString, "Node Not Found With Identifier : " + identifier)
            else if ("enable".equalsIgnoreCase(versioningEnable))
                getEditableVertex(identifier, vertex)
            else
                Future {
                    vertex
                }
        }).flatMap(f => f)
    }

    private def getNodeToRead(identifier: String, mode: String, disableCache: Option[Boolean])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        if ("edit".equalsIgnoreCase(mode)) {
            val imageNode = super.getNode(identifier + IMAGE_SUFFIX, "read", mode)
            imageNode recoverWith {
                case e: CompletionException => {
                    if (e.getCause.isInstanceOf[ResourceNotFoundException])
                        super.getNode(identifier, "read", mode)
                    else
                        throw e.getCause
                }
            }
        } else {
            if(disableCache.nonEmpty){
                if(disableCache.get) super.getNode(identifier, "read", mode)
                else getNodeFromCache(identifier)
            } else{
                val cacheKey = getSchemaName().toLowerCase() + ".cache.enable"
                if (Platform.getBoolean(cacheKey, false)) getNodeFromCache(identifier)
                else super.getNode(identifier, "read", mode)
            }
        }
    }
    private def getVertexToRead(identifier: String, mode: String, disableCache: Option[Boolean])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Vertex] = {
        println("IN getVertexToRead")
        if ("edit".equalsIgnoreCase(mode)) {
            val imageVertex = super.getVertex(identifier + IMAGE_SUFFIX, "read", mode)
            imageVertex recoverWith {
                case e: CompletionException => {
                    if (e.getCause.isInstanceOf[ResourceNotFoundException])
                        super.getVertex(identifier, "read", mode)
                    else
                        throw e.getCause
                }
            }
        } else {
            if (disableCache.nonEmpty) {
                if (disableCache.get) super.getVertex(identifier, "read", mode)
                else getVertexFromCache(identifier)
            } else {
                val cacheKey = getSchemaName().toLowerCase() + ".cache.enable"
                if (Platform.getBoolean(cacheKey, false)) getVertexFromCache(identifier)
                else super.getVertex(identifier, "read", mode)
            }
        }
    }
    private def getNodeFromCache(identifier: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node]= {
        val ttl: Integer = if (Platform.config.hasPath(getSchemaName().toLowerCase() + ".cache.ttl")) Platform.config.getInt(getSchemaName().toLowerCase() + ".cache.ttl") else 86400
        getCachedNode(identifier, ttl)
    }

    private def getVertexFromCache(identifier: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Vertex] = {
        val ttl: Integer = if (Platform.config.hasPath(getSchemaName().toLowerCase() + ".cache.ttl")) Platform.config.getInt(getSchemaName().toLowerCase() + ".cache.ttl") else 86400
        getCachedVertex(identifier, ttl)
    }

    private def getEditableNode(identifier: String, node: Node)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        val status = node.getMetadata.get("status").asInstanceOf[String]
        if(statusList.contains(status)) {
            val imageId = node.getIdentifier + IMAGE_SUFFIX
            try{
                val imageNode = oec.graphService.getNodeByUniqueId(node.getGraphId, imageId, false, new Request())
                imageNode recoverWith {
                    case e: CompletionException => {
                        TelemetryManager.error("Exception occurred while fetching image node, may not be found", e.getCause)
                        if (e.getCause.isInstanceOf[ResourceNotFoundException]) {
                            node.setIdentifier(imageId)
                            node.setObjectType(node.getObjectType + IMAGE_OBJECT_SUFFIX)
                            node.getMetadata.put("status", "Draft")
                            node.getMetadata.put("prevStatus", status)
                            node.getMetadata.put(AuditProperties.lastStatusChangedOn.name, DateUtils.formatCurrentDate())
                            oec.graphService.addNode(node.getGraphId, node).map(imgNode => {
                                imgNode.getMetadata.put("isImageNodeCreated", "yes");
                                copyExternalProps(identifier, node.getGraphId, imgNode.getObjectType.toLowerCase().replace("image", "")).map(response => {
                                    if(!ResponseHandler.checkError(response)) {
                                        if(null != response.getResult && !response.getResult.isEmpty)
                                            imgNode.setExternalData(response.getResult)
                                    }
                                    imgNode
                                })
                            }).flatMap(f=>f)
                        } else
                            throw e.getCause
                    }
                }
            }
        } else
            Future{node}
    }

    private def getEditableVertex(identifier: String, vertex: Vertex)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Vertex] = {
        val status = vertex.getMetadata.get("status").asInstanceOf[String]
        if (statusList.contains(status)) {
            val imageId = vertex.getIdentifier + IMAGE_SUFFIX
            try {
                val imageVertex = oec.janusGraphService.getNodeByUniqueId(vertex.getGraphId, imageId, false, new Request())
                imageVertex recoverWith {
                    case e: CompletionException => {
                        TelemetryManager.error("Exception occurred while fetching image node, may not be found", e.getCause)
                        if (e.getCause.isInstanceOf[ResourceNotFoundException]) {
                            vertex.setIdentifier(imageId)
                            vertex.setObjectType(vertex.getObjectType + IMAGE_OBJECT_SUFFIX)
                            vertex.getMetadata.put("status", "Draft")
                            vertex.getMetadata.put("prevStatus", status)
                            vertex.getMetadata.put(AuditProperties.lastStatusChangedOn.name, DateUtils.formatCurrentDate())
                            oec.janusGraphService.addVertex(vertex.getGraphId, vertex).map(imgVertex => {
                                imgVertex.getMetadata.put("isImageNodeCreated", "yes");
                                copyExternalProps(identifier, vertex.getGraphId, imgVertex.getObjectType.toLowerCase().replace("image", "")).map(response => {
                                    if (!ResponseHandler.checkError(response)) {
                                        if (null != response.getResult && !response.getResult.isEmpty)
                                            imgVertex.setExternalData(response.getResult)
                                    }
                                    imgVertex
                                })
                            }).flatMap(f => f)
                        } else
                            throw e.getCause
                    }
                }
            }
        } else
            Future {
                vertex
            }
    }

    private def copyExternalProps(identifier: String, graphId: String, schemaName: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext) = {
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef](){{
            put("schemaName", schemaName)
            put("version", getSchemaVersion())
            put("graph_id", graphId)
        }})
        request.put("identifier", identifier)
        oec.graphService.readExternalProps(request, getExternalPropsList(graphId, schemaName, getSchemaVersion()))
    }

    private def getExternalPropsList(graphId: String, schemaName: String, version: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): List[String] ={
        val definition = DefinitionFactory.getDefinition(graphId, schemaName, version)
        if(definition.schemaValidator.getConfig.hasPath("external.properties")){
            new util.ArrayList[String](definition.schemaValidator.getConfig.getObject("external.properties").keySet()).toList
        }else{
            List[String]()
        }
    }

    def getCachedNode(identifier: String, ttl: Integer)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        val nodeStringFuture: Future[String] = RedisCache.getAsync(identifier, nodeCacheAsyncHandler, ttl)
        nodeStringFuture.map(nodeString => {
            if (null != nodeString && !nodeString.asInstanceOf[String].isEmpty) {
                val nodeMap: util.Map[String, AnyRef] = JsonUtils.deserialize(nodeString.asInstanceOf[String], classOf[java.util.Map[String, AnyRef]])
                val node: Node = NodeUtil.deserialize(nodeMap, getSchemaName(), schemaValidator.getConfig
                  .getAnyRef("relations").asInstanceOf[java.util.Map[String, AnyRef]])
                Future {node}
            } else {
                super.getNode(identifier, "read", null)
            }
        }).flatMap(f => f)
    }

    def getCachedVertex(identifier: String, ttl: Integer)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Vertex] = {
        val nodeStringFuture: Future[String] = RedisCache.getAsync(identifier, nodeCacheAsyncHandler, ttl)
        nodeStringFuture.map(nodeString => {
            if (null != nodeString && !nodeString.asInstanceOf[String].isEmpty) {
                val nodeMap: util.Map[String, AnyRef] = JsonUtils.deserialize(nodeString.asInstanceOf[String], classOf[java.util.Map[String, AnyRef]])
                val vertex: Vertex = NodeUtil.deserializeVertex(nodeMap, getSchemaName(), schemaValidator.getConfig
                  .getAnyRef("relations").asInstanceOf[java.util.Map[String, AnyRef]])
                Future {
                    vertex
                }
            } else {
                super.getVertex(identifier, "read", null)
            }
        }).flatMap(f => f)
    }

    private def nodeCacheAsyncHandler(objKey: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[String] = {
        super.getNode(objKey, "read", null).map(node => {
            if (List("Live", "Unlisted").contains(node.getMetadata.get("status").asInstanceOf[String])) {

                val nodeMap = NodeUtil.serialize(node, null, node.getObjectType.toLowerCase().replace("image", ""), getSchemaVersion())
                Future(ScalaJsonUtils.serialize(nodeMap))
            } else Future("")
        }).flatMap(f => f)
    }
    
    private def getSchemaNameFromMimeType(node: Node) : String = {
       node.getObjectType.replaceAll("Image", "").toLowerCase()
    }
}
