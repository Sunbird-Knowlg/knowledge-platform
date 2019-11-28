package org.sunbird.graph.schema.validator

import java.util.concurrent.CompletionException

import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ResourceNotFoundException
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.exception.GraphEngineErrorCodes
import org.sunbird.graph.schema.IDefinition
import org.sunbird.graph.service.operation.{NodeAsyncOperations, SearchAsyncOperations}
import org.sunbird.telemetry.logger.TelemetryManager

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
                        TelemetryManager.error("Exception occured while fetching image node, may not be found", e.getCause)
                        if (e.getCause.isInstanceOf[ResourceNotFoundException])
                            super.getNode(identifier, "read", mode)
                        else
                           throw e.getCause
                    }
                }
            } else {
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
                            NodeAsyncOperations.addNode(node.getGraphId, node)
                        } else
                            throw e.getCause
                    }
                }
            }
        } else
            Future{node}
    }

}
