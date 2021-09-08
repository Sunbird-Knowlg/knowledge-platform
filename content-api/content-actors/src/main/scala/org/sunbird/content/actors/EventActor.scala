package org.sunbird.content.actors

import org.sunbird.meet.bigBlueButton.api.BbbApi
import org.sunbird.meet.Meeting
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
      case "joinEvent" => joinEvent(request)
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
      val inRelations = if (node.getInRelations == null) new util.ArrayList[Relation]() else node.getInRelations;
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

  def joinEvent(request: Request): Future[Response] = {
    val responseSchemaName: String = request.getContext.getOrDefault(ContentConstants.RESPONSE_SCHEMA_NAME, "").asInstanceOf[String]
    val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
    request.getRequest.put("fields", fields)
    DataNode.read(request).map(node => {
      val metadata: java.util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, node.getObjectType.toLowerCase.replace("image", ""), request.getContext.get("version").asInstanceOf[String])
      metadata.put("identifier", node.getIdentifier.replace(".img", ""))
      val response: Response = ResponseHandler.OK

      request.getRequest.remove("mode")
      request.getRequest.remove("fields")
      request.getRequest.putAll(metadata)
      request.getRequest.remove("status")
      request.setOperation("updateContent")
      request.getContext.put("identifier", request.getRequest.get("identifier"))
      val onlineProvider = request.getRequest.getOrDefault("onlineProvider", "BigBlueButton").asInstanceOf[String]
      request.getRequest.put("onlineProvider", onlineProvider)

      val meetingRequest = new Meeting()
      meetingRequest.setMeetingID(request.getRequest.getOrDefault("identifier", java.util.UUID.randomUUID.toString).asInstanceOf[String])
      meetingRequest.setName(request.getRequest.getOrDefault("name", "Test").asInstanceOf[String])
      val providerApiObject = if (StringUtils.equalsIgnoreCase(onlineProvider, "BigBlueButton")) {
        new BbbApi()
      } else {
        // TODO : Set response of Meeting URL for other onlineProviders
        new BbbApi()
      }

      if (providerApiObject.deferEventCreation()) { // Creating Meeting if deferred Event creation, and updating Event with Meeting details
        providerApiObject.createMeeting(meetingRequest)
        if (meetingRequest.getShouldUpdate) {
          request.getRequest.put("onlineProviderData", meetingRequest)
          update(request) // Here, this is update event actor is called by making request identical for Update call, but event is not getting updated
        }
      }
      meetingRequest.setModerator(true) // For moderator join meet link
      val moderatorMeetingLink = providerApiObject.getJoinMeetingURL(meetingRequest)
      meetingRequest.setModerator(false) // For attendee join meet link
      val attendeeMeetingLink = providerApiObject.getJoinMeetingURL(meetingRequest)
      val meetingLink = new java.util.HashMap[String, String]
      meetingLink.put("onlineProvider", onlineProvider)
      meetingLink.put("moderatorMeetingLink", moderatorMeetingLink)
      meetingLink.put("attendeeMeetingLink", attendeeMeetingLink)
      response.put(responseSchemaName, meetingLink)
      response
    })
  }
}