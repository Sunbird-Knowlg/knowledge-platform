package org.sunbird.provider

trait Meet {

  /* Creates meeting */
  def createMeeting(meeting: Meeting): Meeting;

  /* Builds the join meeting url for Moderator */
  def getModeratorJoinMeetingURL(meeting: Meeting): String;

  /* Builds the join meeting url for Attendee */
  def getAttendeeJoinMeetingURL(meeting: Meeting): String;

  /* Defers the meeting creation */
  def deferEventCreation(): Boolean = false;
}
