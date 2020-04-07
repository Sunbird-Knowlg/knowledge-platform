package org.sunbird.graph.schema.validator

import java.util
import java.util.concurrent.CompletionException

import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.{DateUtils, JsonUtils, Platform}
import org.sunbird.common.dto.{Request, ResponseHandler}
import org.sunbird.common.exception.ResourceNotFoundException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.enums.AuditProperties
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.exception.GraphErrorCodes
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.schema.IDefinition
import org.sunbird.graph.service.operation.{NodeAsyncOperations, SearchAsyncOperations}
import org.sunbird.graph.utils.{NodeUtil, ScalaJsonUtils}
import org.sunbird.telemetry.logger.TelemetryManager

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

trait VersioningNode extends IDefinition {

    val statusList = List("Live", "Unlisted")
    val IMAGE_SUFFIX = ".img"
    val IMAGE_OBJECT_SUFFIX = "Image"


    abstract override def getNode(identifier: String, operation: String, mode: String = "read", versioning: Option[String] = None)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        operation match {
            case "update" => getNodeToUpdate(identifier, versioning);
            case "read" => getNodeToRead(identifier, mode)
            case _ => getNodeToRead(identifier, mode)
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

    private def getNodeToRead(identifier: String, mode: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
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
            val cacheKey = getSchemaName().toLowerCase() + ".cache.enable"
            if (Platform.config.hasPath(cacheKey) && Platform.config.getBoolean(cacheKey)) {
                val ttl: Integer = if (Platform.config.hasPath(getSchemaName().toLowerCase() + ".cache.ttl")) Platform.config.getInt(getSchemaName().toLowerCase() + ".cache.ttl") else 86400
                getCachedNode(identifier, ttl)
            }
            else
                super.getNode(identifier, "read", mode)
        }
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
                                copyExternalProps(identifier, node.getGraphId).map(response => {
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

    private def copyExternalProps(identifier: String, graphId: String)(implicit ec : ExecutionContext) = {
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef](){{
            put("schemaName", getSchemaName())
            put("version", getSchemaVersion())
            put("graph_id", graphId)
        }})
        request.put("identifier", identifier)
        ExternalPropsManager.fetchProps(request, getExternalPropsList())
    }

    private def getExternalPropsList(): List[String] ={
        if(schemaValidator.getConfig.hasPath("external.properties")){
            new util.ArrayList[String](schemaValidator.getConfig.getObject("external.properties").keySet()).toList
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

    private def nodeCacheAsyncHandler(objKey: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[String] = {
        super.getNode(objKey, "read", null).map(node => {
            if (List("Live", "Unlisted").contains(node.getMetadata.get("status").asInstanceOf[String])) {
                val nodeMap = NodeUtil.serialize(node, null, getSchemaName(), getSchemaVersion())
                Future(ScalaJsonUtils.serialize(nodeMap))
            } else Future("")
        }).flatMap(f => f)
    }
}
