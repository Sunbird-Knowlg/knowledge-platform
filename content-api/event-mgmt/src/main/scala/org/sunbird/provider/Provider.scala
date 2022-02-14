package org.sunbird.provider

import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.provider.bigBlueButton.api.BbbApi
import org.sunbird.util.ProviderConstants

import java.util
import scala.collection.JavaConverters._
import scala.language.postfixOps

object Provider {

  def getJoinEventUrlModerator(metadata: java.util.Map[String, AnyRef]): util.Map[String, Any] = {
    val onlineProvider = metadata.getOrDefault("onlineProvider", "").asInstanceOf[String]
    val meetingRequest = Meeting(metadata.get("identifier").asInstanceOf[String], metadata.get("name").asInstanceOf[String], userName = metadata.get("userName").asInstanceOf[String], userId = metadata.get("userId").asInstanceOf[String], record = true, allowStartStopRecording = true, muteOnStart = metadata.get("muteOnStart").asInstanceOf[java.lang.Boolean], logoutURL = metadata.get("logoutURL").asInstanceOf[String])
    val providerApiObject = onlineProvider toLowerCase match {
      case ProviderConstants.BIG_BLUE_BUTTON =>
        new BbbApi()
      case _ =>
        // Set response of Meeting URL for other onlineProviders
        throw new ClientException(ResponseCode.CLIENT_ERROR.name(), "No onlineProvider selected for the Event")
    }
    val meetingResponseWithPW: Meeting = if (providerApiObject.deferEventCreation()) { // Creating Meeting if deferred Event creation, and updating Event with Meeting details
      providerApiObject.createMeeting(meetingRequest)
    } else null
    val moderatorMeetingLink = providerApiObject.getModeratorJoinMeetingURL(meetingResponseWithPW)
    val meetingLink = new java.util.HashMap[String, Any]
    meetingLink.put("onlineProvider", onlineProvider)
    meetingLink.put("moderatorMeetingLink", moderatorMeetingLink)
    if (null != meetingResponseWithPW && meetingResponseWithPW.shouldUpdate) {
      // Converting object to map to get save in Event
      val mapMeetingResponse: Map[String, Any] = meetingResponseWithPW.getClass.getDeclaredFields.foldLeft(Map.empty[String, Any]) { (a, f) =>
        f.setAccessible(true)
        a + (f.getName -> f.get(meetingResponseWithPW))
      }
      meetingLink.put("onlineProviderData", mapMeetingResponse.asJava)
    }
    meetingLink
  }

  def getJoinEventUrlAttendee(metadata: java.util.Map[String, AnyRef]): util.Map[String, Any] = {
    val onlineProvider = metadata.getOrDefault("onlineProvider", "").asInstanceOf[String]
    val meetingRequest = Meeting(metadata.get("identifier").asInstanceOf[String], userName = metadata.get("userName").asInstanceOf[String], userId = metadata.get("userId").asInstanceOf[String])
    val providerApiObject = onlineProvider toLowerCase match {
      case ProviderConstants.BIG_BLUE_BUTTON =>
        new BbbApi()
      case _ =>
        // Set response of Meeting URL for other onlineProviders
        throw new ClientException(ResponseCode.CLIENT_ERROR.name(), "No onlineProvider selected for the Event")
    }
    val attendeeMeetingLink = providerApiObject.getAttendeeJoinMeetingURL(meetingRequest)
    val meetingLink = new java.util.HashMap[String, Any]
    meetingLink.put("onlineProvider", onlineProvider)
    meetingLink.put("attendeeMeetingLink", attendeeMeetingLink)
    meetingLink
  }
}