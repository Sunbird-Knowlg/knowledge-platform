package org.sunbird.util

object ProviderConstants {

  // BigBlueButton
  val BIG_BLUE_BUTTON = "bigbluebutton"
  val PROVIDER_BBB_SERVER_URL = "provider_bbb_server_url"
  val PROVIDER_BBB_SECURE_SALT = "provider_bbb_secure_salt"

  // API method
  val API_POST = "POST"
  val API_GET = "GET"

  // API Server Path
  val API_SERVER_PATH = "/api/"
  val API_SERVER_PATH_TO_CHECK = "/api"
  val URL_PATH_SLASH = "/"
  val URL_PATH_QUESTION_MARK = "?"

  // API Calls
  val API_CALL_CREATE = "create"
  val API_CALL_GET_MEETING_INFO = "getMeetingInfo"
  val API_CALL_JOIN = "join"
  val API_CALL_GET_CONFIG_XML = "getDefaultConfigXML"

  // API Response Codes
  val API_RESPONSE_SUCCESS = "SUCCESS"
  val API_RESPONSE_FAILED = "FAILED"

  // Query parameters
  val QUERY_PARAM_MEETING_ID = "meetingID="
  val QUERY_PARAM_NAME = "&name="
  val QUERY_PARAM_USER_ID = "&userID="
  val QUERY_PARAM_FULL_NAME = "&fullName="
  val QUERY_PARAM_PASSWORD = "&password="
  val QUERY_PARAM_CHECKSUM = "&checksum="
  val QUERY_PARAM_RECORD = "&record="
  val QUERY_PARAM_ALLOW_START_STOP_RECORDING = "&allowStartStopRecording="

  // Response parameters
  val RESPONSE = "response"
  val RESPONSE_MEETING_ID = "meetingID"
  val RESPONSE_MODERATOR_PW = "moderatorPW"
  val RESPONSE_ATTENDEE_PW = "attendeePW"
  val RESPONSE_RETURN_CODE = "returncode"
  val RESPONSE_MESSAGE_KEY = "messageKey"
  val RESPONSE_MESSAGE = "message"

  val ENCODE = "UTF-8"
  val MODERATOR_USER = "Moderator"
  val ATTENDEE_USER = "Attendee"
}
