package org.sunbird.provider

import java.util.{Date, HashMap}

/**
 * Object for a meeting.
 */
case class Meeting(meetingID: String = null, name: String = null, attendeePW: String = null, moderatorPW: String = null, dialNumber: String = null, voiceBridge: String = null, webVoice: String = null, logoutURL: String = null, record: Boolean = false, duration: Long = 0L, meta: HashMap[String, String] = null, moderatorOnlyMessage: String = null, autoStartRecording: Boolean = false, allowStartStopRecording: Boolean = false, webcamsOnlyForModerator: Boolean = false, logo: String = null, copyright: String = null, muteOnStart: Boolean = false, welcome: String = null, startDate: Date = null, endDate: Date = null, userId: String = null, userName: String = null, isModerator: Boolean = false, returnCode: String = null, shouldUpdate: Boolean = false) {
}