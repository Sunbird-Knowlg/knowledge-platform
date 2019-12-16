package org.sunbird.graph.schema.validator

import java.util
import java.util.concurrent.CompletionException

import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.JsonUtils
import org.sunbird.common.dto.{Request, ResponseHandler}
import org.sunbird.common.exception.ResourceNotFoundException
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.exception.GraphEngineErrorCodes
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


    abstract override def getNode(identifier: String, operation: String, mode: String)(implicit ec: ExecutionContext): Future[Node] = {
        operation match {
            case "update" => getNodeToUpdate(identifier);
            case "read" => getNodeToRead(identifier, mode)
            case _ => getNodeToRead(identifier, mode)
        }
    }

    private def getNodeToUpdate(identifier: String)(implicit ec: ExecutionContext): Future[Node] = {
        val nodeFuture: Future[Node] = super.getNode(identifier , "update", null)
        nodeFuture.map(node => {
            if(null == node)
                throw new ResourceNotFoundException(GraphEngineErrorCodes.ERR_INVALID_NODE.name, "Node Not Found With Identifier : " + identifier)
            if(schemaValidator.getConfig.hasPath("version") && "enable".equalsIgnoreCase(schemaValidator.getConfig.getString("version"))){
                getEditableNode(identifier, node)
            } else {
                Future{node}
            }
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause}
    }

    private def getNodeToRead(identifier: String, mode: String)(implicit ec: ExecutionContext) = {
        val node: Future[Node] = {
            if("edit".equalsIgnoreCase(mode)){
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
                if(schemaValidator.getConfig.hasPath("cacheEnabled") && schemaValidator.getConfig.getBoolean("cacheEnabled"))
                    getCachedNode(identifier)
                else
                    super.getNode(identifier , "read", mode)
            }
        }.map(dataNode => dataNode) recoverWith { case e: CompletionException => throw e.getCause}
        node
    }

    private def getEditableNode(identifier: String, node: Node)(implicit ec: ExecutionContext): Future[Node] = {
        val status = node.getMetadata.get("status").asInstanceOf[String]
        if(statusList.contains(status)) {
            val imageId = node.getIdentifier + IMAGE_SUFFIX
            try{
                val imageNode = SearchAsyncOperations.getNodeByUniqueId(node.getGraphId, imageId, false, new Request())
                imageNode recoverWith {
                    case e: CompletionException => {
                        TelemetryManager.error("Exception occured while fetching image node, may not be found", e.getCause)
                        if (e.getCause.isInstanceOf[ResourceNotFoundException]) {
                            node.setIdentifier(imageId)
                            node.setObjectType(node.getObjectType + IMAGE_OBJECT_SUFFIX)
                            node.getMetadata.put("status", "Draft")
                            NodeAsyncOperations.addNode(node.getGraphId, node).map(imgNode => {
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
            put("version", "1.0")
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

    def getCachedNode(identifier: String)(implicit ec: ExecutionContext): Future[Node] = {
        //TODO: Implement handler function and pass it
        val nodeString:String = RedisCache.get(identifier)
        if(null != nodeString && !nodeString.isEmpty) {
            val nodeMap:util.Map[String, AnyRef] = JsonUtils.deserialize(nodeString, classOf[java.util.Map[String, AnyRef]])
            val node:Node = NodeUtil.deserialize(nodeMap, getSchemaName(), schemaValidator.getConfig
                    .getAnyRef("relations").asInstanceOf[java.util.Map[String, AnyRef]])
            Future{node}
        } else {
            super.getNode(identifier , "read", null).map(node => {
                if(List("Live", "Unlisted").contains(node.getMetadata.get("status").asInstanceOf[String])) {
                    val nodeMap = NodeUtil.serialize(node, null, getSchemaName())
                    RedisCache.set(identifier, ScalaJsonUtils.serialize(nodeMap), 86400)
                }
                node
            })
        }

    }

}
