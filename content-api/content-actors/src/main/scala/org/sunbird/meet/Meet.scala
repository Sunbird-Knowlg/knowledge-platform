package org.sunbird.meet

trait Meet {

  /* Creates meeting */
  def createMeeting(meeting: Meeting): Meeting;

  /* Builds the join meeting url based on user role */
  def getJoinMeetingURL(meeting: Meeting): String;

  /* Defers the meeting creation */
  def deferEventCreation(): Boolean = false;
}
