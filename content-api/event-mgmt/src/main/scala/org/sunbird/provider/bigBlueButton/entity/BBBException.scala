package org.sunbird.provider.bigBlueButton.entity

/**
 * Exception generated while communicating with BBB server
 */
@SerialVersionUID(2421100107566638321L)
object BBBException {
  val MESSAGEKEY_HTTPERROR = "httpError"
  val MESSAGEKEY_NOTFOUND = "notFound"
  val MESSAGEKEY_NOACTION = "noActionSpecified"
  val MESSAGEKEY_IDNOTUNIQUE = "idNotUnique"
  val MESSAGEKEY_NOTSTARTED = "notStarted"
  val MESSAGEKEY_ALREADYENDED = "alreadyEnded"
  val MESSAGEKEY_INTERNALERROR = "internalError"
  val MESSAGEKEY_UNREACHABLE = "unreachableServerError"
  val MESSAGEKEY_INVALIDRESPONSE = "invalidResponseError"
  val MESSAGEKEY_GENERALERROR = "generalError"
}

@SerialVersionUID(2421100107566638321L)
class BBBException(messageKey: String = "", message: String = "", cause: Throwable = None.orNull) extends Exception(message, cause) {

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