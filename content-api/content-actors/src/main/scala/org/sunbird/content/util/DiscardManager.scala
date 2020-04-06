package org.sunbird.content.util

import java.util
import java.util.concurrent.CompletionException

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.managers.UpdateHierarchyManager.{fetchHierarchy, shouldImageBeDeleted}
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.utils.{HierarchyConstants, HierarchyErrorCodes}

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

object DiscardManager {
    private val CONTENT_DISCARD_STATUS = Platform.getStringList("content.discard.status", util.Arrays.asList("Draft", "FlagDraft"))
    private val REMOVE_PUBLISHED_DATA = Platform.getStringList("content.discard.remove_publish_data", util.Arrays.asList("compatibilityLevel", "lastPublishedOn", "pkgVersion", "leafNodesCount", "downloadUrl", "variants"))

    def discard(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[java.lang.Boolean] = {
        validateRequest(request)
        getNodeToDiscard(request).map(node => {
            request.put(ContentConstants.IDENTIFIER, node.getIdentifier)
            if (!CONTENT_DISCARD_STATUS.contains(node.getMetadata.get(ContentConstants.STATUS)))
                throw new ClientException(ContentConstants.ERR_CONTENT_NOT_DRAFT, "No changes to discard for content with content id: " + node.getIdentifier + " since content status isn't draft", node.getIdentifier)
            if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault(ContentConstants.MIME_TYPE, "").asInstanceOf[String], ContentConstants.COLLECTION_MIME_TYPE))
                discardForCollection(node, request)
            else
                DataNode.deleteNode(request)
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
    }

    private def getNodeToDiscard(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        val imageRequest = new Request(request)
        imageRequest.put(ContentConstants.MODE, ContentConstants.EDIT_MODE)
        imageRequest.put(ContentConstants.IDENTIFIER, request.get(ContentConstants.IDENTIFIER))
        DataNode.read(imageRequest)
    }

    private def validateRequest(request: Request): Unit = {
        if (StringUtils.isBlank(request.getRequest.getOrDefault(ContentConstants.IDENTIFIER, "").asInstanceOf[String])
            || StringUtils.endsWith(request.getRequest.getOrDefault(ContentConstants.IDENTIFIER, "").asInstanceOf[String], ContentConstants.IMAGE_SUFFIX))
            throw new ClientException(ContentConstants.ERR_INVALID_CONTENT_ID, "Please provide valid content identifier")
    }


    private def discardForCollection(node: Node, request: Request)(implicit executionContext: ExecutionContext, oec: OntologyEngineContext): Future[java.lang.Boolean] = {
        request.put(ContentConstants.IDENTIFIERS, if (node.getMetadata.containsKey(ContentConstants.PACKAGE_VERSION)) List(node.getIdentifier) else List(node.getIdentifier, node.getIdentifier + ContentConstants.IMAGE_SUFFIX))
        request.getContext.put(ContentConstants.SCHEMA_NAME, ContentConstants.COLLECTION_SCHEMA_NAME)
        ExternalPropsManager.deleteProps(request).map(resp => DataNode.deleteNode(request)).flatMap(f => f)
    }


}
