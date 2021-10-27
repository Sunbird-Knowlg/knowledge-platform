package org.sunbird.content.actors

import org.sunbird.provider.Provider
import org.apache.commons.lang.StringUtils
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.content.util.ContentConstants
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil

import java.util
import javax.inject.Inject
import scala.collection.JavaConverters
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Future

class EventActor @Inject()(implicit oec: OntologyEngineContext, ss: StorageService) extends ContentActor {

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case "createContent" => create(request)
      case "readContent" => read(request)
      case "updateContent" => update(request)
      case "retireContent" => retire(request)
      case "discardContent" => discard(request)
      case "publishContent" => publish(request)
      case "joinEventModerator" => joinEventModerator(request)
      case "joinEventAttendee" => joinEventAttendee(request)
      case _ => ERROR(request.getOperation)
    }
  }

  override def update(request: Request): Future[Response] = {
    verifyStandaloneEventAndApply(super.update, request, Some(node => {
      if (!"Draft".equalsIgnoreCase(node.getMetadata.getOrDefault("status", "").toString)) {
        throw new ClientException(ContentConstants.ERR_CONTENT_NOT_DRAFT, "Update not allowed! Event status isn't draft")
      }
    }))
  }

  def publish(request: Request): Future[Response] = {
    verifyStandaloneEventAndApply(super.update, request, Some(node => {
      if (!"Draft".equalsIgnoreCase(node.getMetadata.getOrDefault("status", "").toString)) {
        throw new ClientException(ContentConstants.ERR_CONTENT_NOT_DRAFT, "Publish not allowed! Event status isn't draft")
      }
      val versionKey = node.getMetadata.getOrDefault("versionKey", "").toString
      if (StringUtils.isNotBlank(versionKey))
        request.put("versionKey", versionKey)
    }))
  }

  override def discard(request: Request): Future[Response] = {
    verifyStandaloneEventAndApply(super.discard, request)
  }

  override def retire(request: Request): Future[Response] = {
    verifyStandaloneEventAndApply(super.retire, request)
  }

  private def verifyStandaloneEventAndApply(f: Request => Future[Response], request: Request, dataUpdater: Option[Node => Unit] = None): Future[Response] = {
    DataNode.read(request).flatMap(node => {
      val inRelations = if (node.getInRelations == null) new util.ArrayList[Relation]() else node.getInRelations
      val hasEventSetParent = inRelations.asScala.exists(rel => "EventSet".equalsIgnoreCase(rel.getStartNodeObjectType))
      if (hasEventSetParent)
        Future(ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, ResponseCode.CLIENT_ERROR.name(), "ERROR: Can't modify an Event which is part of an Event Set!"))
      else {
        if (dataUpdater.isDefined) {
          dataUpdater.get.apply(node)
        }
        f.apply(request)
      }
    })
  }

  override def dataModifier(node: Node): Node = {
    if (node.getMetadata.containsKey("trackable") &&
      node.getMetadata.getOrDefault("trackable", new java.util.HashMap[String, AnyRef]).asInstanceOf[java.util.Map[String, AnyRef]].containsKey("enabled") &&
      "Yes".equalsIgnoreCase(node.getMetadata.getOrDefault("trackable", new java.util.HashMap[String, AnyRef]).asInstanceOf[java.util.Map[String, AnyRef]].getOrDefault("enabled", "").asInstanceOf[String])) {
      node.getMetadata.put("contentType", "Event")
    }
    node
  }

  def joinEventModerator(request: Request): Future[Response] = {
    val responseSchemaName: String = request.getContext.getOrDefault(ContentConstants.RESPONSE_SCHEMA_NAME, "").asInstanceOf[String]
    DataNode.read(request).map(node => {
      val metadata: java.util.Map[String, AnyRef] = NodeUtil.serialize(node, null, node.getObjectType.toLowerCase.replace("image", ""), request.getContext.get("version").asInstanceOf[String])
      metadata.put("identifier", node.getIdentifier.replace(".img", ""))
      metadata.put("userName", request.getRequest.getOrDefault("userName", ContentConstants.USER).asInstanceOf[String])
      metadata.put("userId", request.getRequest.get("userId").asInstanceOf[String])

      val meetingLink = Provider.getJoinEventUrlModerator(metadata)
      val onlineProviderData = meetingLink.get("onlineProviderData").asInstanceOf[util.Map[String, Any]]
      if (null != onlineProviderData && !onlineProviderData.isEmpty) {
        request.getRequest.remove("mode")
        request.getRequest.remove("fields")
        request.getRequest.putAll(metadata)
        request.getRequest.remove("status")
        request.getRequest.remove("userName")
        request.getRequest.remove("userId")
        request.setOperation("updateContent")
        request.getContext.put("identifier", request.getRequest.get("identifier"))
        request.getRequest.put("onlineProvider", meetingLink.get("onlineProvider").asInstanceOf[String])
        request.getRequest.put("onlineProviderData", onlineProviderData)
        super.update(request) recoverWith {
          case e =>
            Future(ResponseHandler.getErrorResponse(e))
        }
      }
      val response: Response = ResponseHandler.OK
      meetingLink.remove("onlineProviderData")
      response.put(responseSchemaName, meetingLink)
    }
    )
  }

  def joinEventAttendee(request: Request): Future[Response] = {
    val responseSchemaName: String = request.getContext.getOrDefault(ContentConstants.RESPONSE_SCHEMA_NAME, "").asInstanceOf[String]
    DataNode.read(request).map(node => {
      val metadata: java.util.Map[String, AnyRef] = NodeUtil.serialize(node, null, node.getObjectType.toLowerCase.replace("image", ""), request.getContext.get("version").asInstanceOf[String])
      metadata.put("userName", request.getRequest.getOrDefault("userName", ContentConstants.USER).asInstanceOf[String])
      metadata.put("userId", request.getRequest.get("userId").asInstanceOf[String])
      val meetingLink = Provider.getJoinEventUrlAttendee(metadata)
      val response: Response = ResponseHandler.OK
      response.put(responseSchemaName, meetingLink)
    }
    )
  }
}