package org.sunbird.meet

import java.util.{Date, HashMap, Map}

/**
 * Object for a meeting.
 */
class Meeting {
  private var meetingID: String = null
  private var name: String = null
  private var attendeePW: String = null
  private var moderatorPW: String = null
  private var dialNumber: String = null
  private var voiceBridge: String = null
  private var webVoice: String = null
  private var logoutURL: String = null
  private var record: Boolean = false
  private var duration: Long = 0L
  private var meta = new HashMap[String, String]
  private var moderatorOnlyMessage: String = null
  private var autoStartRecording: Boolean = false
  private var allowStartStopRecording: Boolean = false
  private var webcamsOnlyForModerator: Boolean = false
  private var logo: String = null
  private var copyright: String = null
  private var muteOnStart: Boolean = false
  private var welcome: String = null
  private var startDate: Date = null
  private var endDate: Date = null
  private var userId: String = null
  private var userName: String = null
  private var isModerator: Boolean = false
  private var returnCode: String = null
  private var shouldUpdate: Boolean = false

  def addMeta(key: String, value: String): Unit = {
    meta.put(key, value)
  }

  def removeMeta(key: String): Unit = {
    if (meta.containsKey(key)) meta.remove(key)
  }

  def getName: String = name

  def setName(name: String): Unit = {
    this.name = name
  }

  def getMeetingID: String = meetingID

  def setMeetingID(meetingID: String): Unit = {
    this.meetingID = meetingID
  }

  def getAttendeePW: String = attendeePW

  def setAttendeePW(attendeePW: String): Unit = {
    this.attendeePW = attendeePW
  }

  def getModeratorPW: String = moderatorPW

  def setModeratorPW(moderatorPW: String): Unit = {
    this.moderatorPW = moderatorPW
  }

  def getDialNumber: String = dialNumber

  def setDialNumber(dialNumber: String): Unit = {
    this.dialNumber = dialNumber
  }

  def getVoiceBridge: String = voiceBridge

  def setVoiceBridge(voiceBridge: String): Unit = {
    this.voiceBridge = voiceBridge
  }

  def getWebVoice: String = webVoice

  def setWebVoice(webVoice: String): Unit = {
    this.webVoice = webVoice
  }

  def getLogoutURL: String = logoutURL

  def setLogoutURL(logoutURL: String): Unit = {
    this.logoutURL = logoutURL
  }

  def getRecord: Boolean = record

  def setRecord(record: Boolean): Unit = {
    this.record = record
  }

  def getDuration: Long = duration

  def setDuration(duration: Long): Unit = {
    this.duration = duration
  }

  def getMeta: Map[String, String] = meta

  def setMeta(meta: HashMap[String, String]): Unit = {
    this.meta = meta
  }

  def getModeratorOnlyMessage: String = moderatorOnlyMessage

  def setModeratorOnlyMessage(moderatorOnlyMessage: String): Unit = {
    this.moderatorOnlyMessage = moderatorOnlyMessage
  }

  def getAutoStartRecording: Boolean = autoStartRecording

  def setAutoStartRecording(autoStartRecording: Boolean): Unit = {
    this.autoStartRecording = autoStartRecording
  }

  def getAllowStartStopRecording: Boolean = allowStartStopRecording

  def setAllowStartStopRecording(allowStartStopRecording: Boolean): Unit = {
    this.allowStartStopRecording = allowStartStopRecording
  }

  def getWebcamsOnlyForModerator: Boolean = webcamsOnlyForModerator

  def setWebcamsOnlyForModerator(webcamsOnlyForModerator: Boolean): Unit = {
    this.webcamsOnlyForModerator = webcamsOnlyForModerator
  }

  def getLogo: String = logo

  def setLogo(logo: String): Unit = {
    this.logo = logo
  }

  def getCopyright: String = copyright

  def setCopyright(copyright: String): Unit = {
    this.copyright = copyright
  }

  def getMuteOnStart: Boolean = muteOnStart

  def setMuteOnStart(muteOnStart: Boolean): Unit = {
    this.muteOnStart = muteOnStart
  }

  def getWelcome: String = welcome

  def setWelcome(welcome: String): Unit = {
    this.welcome = welcome
  }

  def getStartDate: Date = startDate

  def setStartDate(startDate: Date): Unit = {
    this.startDate = startDate
  }

  def getEndDate: Date = endDate

  def setEndDate(endDate: Date): Unit = {
    this.endDate = endDate
  }

  def getUserId: String = userId

  def setUserId(userId: String): Unit = {
    this.userId = userId
  }

  def getUserName: String = userName

  def setUserName(userName: String): Unit = {
    this.userName = userName
  }

  def getModerator: Boolean = isModerator

  def setModerator(moderator: Boolean): Unit = {
    isModerator = moderator
  }

  def getReturnCode: String = returnCode

  def setReturnCode(returnCode: String): Unit = {
    this.returnCode = returnCode
  }

  def getShouldUpdate: Boolean = shouldUpdate

  def setShouldUpdate(shouldUpdate: Boolean): Unit = {
    this.shouldUpdate = shouldUpdate
  }

  override def toString: String = "{" + "name=" + name + ", meetingID=" + meetingID + ", attendeePW=" + attendeePW + ", moderatorPW=" + moderatorPW + ", duration=" + duration + '}'
}
