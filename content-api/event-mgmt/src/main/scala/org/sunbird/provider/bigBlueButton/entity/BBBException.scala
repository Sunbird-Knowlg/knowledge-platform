package org.sunbird.provider.bigBlueButton.entity

/**
 * Exception generated while communicating with BBB server
 */
@SerialVersionUID(2421100107566638321L)
object BBBException {
  val MESSAGE_KEY_HTTP_ERROR = "httpError"
  val MESSAGE_KEY_NOT_FOUND = "notFound"
  val MESSAGE_KEY_NO_ACTION = "noActionSpecified"
  val MESSAGE_KEY_ID_NOT_UNIQUE = "idNotUnique"
  val MESSAGE_KEY_NOT_STARTED = "notStarted"
  val MESSAGE_KEY_ALREADY_ENDED = "alreadyEnded"
  val MESSAGE_KEY_INTERNAL_ERROR = "internalError"
  val MESSAGE_KEY_UNREACHABLE = "unreachableServerError"
  val MESSAGE_KEY_INVALID_RESPONSE = "invalidResponseError"
  val MESSAGE_KEY_GENERAL_ERROR = "generalError"
}

@SerialVersionUID(2421100107566638321L)
case class BBBException(messageKey: String = "", message: String = "", cause: Throwable = None.orNull) extends Exception(message, cause) {

  def getMessageKey: String = messageKey

  def getPrettyMessage: String = {
    val _message = getMessage
    val _messageKey = getMessageKey
    val pretty = new StringBuilder
    if (_message != null) pretty.append(_message)
    if (_messageKey != null && "" != _messageKey.trim) {
      pretty.append(" (")
      pretty.append(_messageKey)
      pretty.append(")")
    }
    pretty.toString
  }
}