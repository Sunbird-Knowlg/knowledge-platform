package org.sunbird.provider.bigBlueButton.api

import org.apache.commons.codec.digest.DigestUtils
import org.sunbird.common.Platform
import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.util.ProviderConstants
import org.sunbird.provider.bigBlueButton.entity.BBBException
import org.sunbird.provider.{Meet, Meeting}
import org.w3c.dom.{Document, Node}
import org.xml.sax.{InputSource, SAXException}

import java.io._
import java.net.{HttpURLConnection, URL, URLEncoder}
import java.util
import javax.xml.parsers.{DocumentBuilder, DocumentBuilderFactory, ParserConfigurationException}

class BbbApi extends Meet {

  // BBB server url
  private val bbbUrl = Platform.config.getString(ProviderConstants.PROVIDER_BBB_SERVER_URL)
  //BBB security salt
  private val bbbSalt = Platform.config.getString(ProviderConstants.PROVIDER_BBB_SECURE_SALT)

  @throws[UnsupportedEncodingException]
  private def encode(msg: String) = URLEncoder.encode(msg, getParametersEncoding)

  // --- BBB API implementation methods ------------------------------------

  /* Create BBB meeting */
  @throws[BBBException]
  override def createMeeting(meeting: Meeting): Meeting = {
    var response: util.Map[String, AnyRef] = null
    var shouldUpdateFlag = false
    try {
      val query = new StringBuilder
      query.append(ProviderConstants.QUERY_PARAM_MEETING_ID + meeting.meetingID)
      if (meeting.name != null) query.append(ProviderConstants.QUERY_PARAM_NAME + encode(meeting.name))
      query.append(getCheckSumParameterForQuery(ProviderConstants.API_CALL_CREATE, query.toString))
      response = doAPICall(ProviderConstants.API_CALL_CREATE, query.toString)
      shouldUpdateFlag = true
    } catch {
      case e: BBBException =>
        e.getMessageKey match {
          case BBBException.MESSAGE_KEY_ID_NOT_UNIQUE => response = getMeetingInfo(meeting.meetingID)
          case _ => throw e
        }
      case e: IOException =>
        throw new BBBException(BBBException.MESSAGE_KEY_INTERNAL_ERROR, e.getMessage, e)
    }
    // capture important information from returned response
    meeting.copy(shouldUpdate = shouldUpdateFlag, moderatorPW = response.get(ProviderConstants.RESPONSE_MODERATOR_PW).asInstanceOf[String], attendeePW = response.get(ProviderConstants.RESPONSE_ATTENDEE_PW).asInstanceOf[String], returnCode = response.get(ProviderConstants.RESPONSE_RETURN_CODE).asInstanceOf[String])
  }

  /* Builds the join meeting url for Moderator */
  override def getModeratorJoinMeetingURL(meeting: Meeting): String = {
    getJoinMeetingURL(meeting.copy(isModerator = true, userName = meeting.userName, userId = meeting.userId))
  }

  /* Builds the join meeting url for Attendee */
  override def getAttendeeJoinMeetingURL(meeting: Meeting): String = {
    getJoinMeetingURL(meeting.copy(isModerator = false, userName = meeting.userName, userId = meeting.userId))
  }

  /* Build the join meeting url based on user role */
  private def getJoinMeetingURL(meeting: Meeting): String = {
    try {
      val joinQuery = new StringBuilder
      joinQuery.append(ProviderConstants.QUERY_PARAM_MEETING_ID + meeting.meetingID)
      if (meeting.userId != null) joinQuery.append(ProviderConstants.QUERY_PARAM_USER_ID + encode(meeting.userId))
      joinQuery.append(ProviderConstants.QUERY_PARAM_FULL_NAME)
      try {
        joinQuery.append(encode(meeting.userName))
      }
      catch {
        case e: UnsupportedEncodingException =>
          joinQuery.append(meeting.userName)
      }
      meeting.isModerator match {
        case true => joinQuery.append(ProviderConstants.QUERY_PARAM_PASSWORD + meeting.moderatorPW)
        case false => try {
          val response = getMeetingInfo(meeting.meetingID)
          joinQuery.append(ProviderConstants.QUERY_PARAM_PASSWORD + response.get(ProviderConstants.RESPONSE_ATTENDEE_PW).asInstanceOf[String])
        } catch {
          case e: BBBException =>
            if (BBBException.MESSAGE_KEY_NOT_FOUND == e.getMessageKey) {
              throw new ClientException(ResponseCode.CLIENT_ERROR.name(), "Please wait for the Moderator to start Meeting")
            }
            throw e
        }
      }
      joinQuery.append(getCheckSumParameterForQuery(ProviderConstants.API_CALL_JOIN, joinQuery.toString))
      val url = new java.lang.StringBuilder(bbbUrl)
      if (url.toString.endsWith(ProviderConstants.API_SERVER_PATH_TO_CHECK)) url.append(ProviderConstants.URL_PATH_SLASH)
      else url.append(ProviderConstants.API_SERVER_PATH)
      url.append(ProviderConstants.API_CALL_JOIN + ProviderConstants.URL_PATH_QUESTION_MARK + joinQuery)
      url.toString
    } catch {
      case e: UnsupportedEncodingException => throw e
    }
  }

  /* Get BBB meeting information */
  @throws[BBBException]
  def getMeetingInfo(meetingID: String): util.Map[String, AnyRef] = try {
    val query = new StringBuilder
    query.append(ProviderConstants.QUERY_PARAM_MEETING_ID + meetingID)
    query.append(getCheckSumParameterForQuery(ProviderConstants.API_CALL_GET_MEETING_INFO, query.toString))
    val response = doAPICall(ProviderConstants.API_CALL_GET_MEETING_INFO, query.toString)
    response
  } catch {
    case e: BBBException =>
      throw new BBBException(e.getMessageKey, e.getMessage, e)
  }

  // --- BBB API utility methods -------------------------------------------

  /** Compute the query string checksum based on the security salt */
  private def getCheckSumParameterForQuery(apiCall: String, queryString: String): String = if (bbbSalt != null) ProviderConstants.QUERY_PARAM_CHECKSUM + DigestUtils.shaHex(apiCall + queryString + bbbSalt)
  else ""

  /** Encoding used when encoding url parameters */
  private def getParametersEncoding = ProviderConstants.ENCODE

  /* Make an API call */
  @throws[BBBException]
  private def doAPICall(apiCall: String, query: String): util.Map[String, AnyRef] = {
    val urlStr = new StringBuilder(bbbUrl)
    if (urlStr.toString.endsWith(ProviderConstants.API_SERVER_PATH_TO_CHECK)) urlStr.append(ProviderConstants.URL_PATH_SLASH)
    else urlStr.append(ProviderConstants.API_SERVER_PATH)
    urlStr.append(apiCall)
    if (query != null) {
      urlStr.append(ProviderConstants.URL_PATH_QUESTION_MARK)
      urlStr.append(query)
    }
    try { // open connection
      val url = new URL(urlStr.toString)
      val httpConnection = url.openConnection.asInstanceOf[HttpURLConnection]
      apiCall match {
        case ProviderConstants.API_CALL_CREATE => httpConnection.setRequestMethod(ProviderConstants.API_POST)
          httpConnection.setRequestProperty("Content-Length", "" + String.valueOf(0))
          httpConnection.setRequestProperty("Accept", "" + "*/*")
          httpConnection.setRequestProperty("Accept-Encoding", "" + "gzip, deflate, br")
          httpConnection.setRequestProperty("Connection", "" + "keep-alive")
        case _ => httpConnection.setRequestMethod(ProviderConstants.API_GET)
      }
      httpConnection.connect()
      val responseCode = httpConnection.getResponseCode
      if (responseCode == HttpURLConnection.HTTP_OK) { // read response
        var isr: InputStreamReader = null
        var reader: BufferedReader = null
        val xml = new StringBuilder
        try {
          isr = new InputStreamReader(httpConnection.getInputStream, ProviderConstants.ENCODE)
          val reader = new BufferedReader(isr)
          var line = reader.readLine()
          while ( {
            line != null
          }) {
            if (!line.startsWith("<?xml version=\"1.0\"?>")) xml.append(line.trim)
            line = reader.readLine
          }
        } finally {
          if (reader != null) reader.close()
          if (isr != null) isr.close()
        }
        httpConnection.disconnect()
        // parse response
        var stringXml = xml.toString

        stringXml = stringXml.replaceAll(">.\\s+?<", "><")
        if (apiCall == ProviderConstants.API_CALL_GET_CONFIG_XML) {
          val map = new util.HashMap[String, AnyRef]
          map.put("xml", stringXml)
          return map
        }
        var dom: Document = null
        // Initialize XML libraries
        var docBuilderFactory: DocumentBuilderFactory = null
        var docBuilder: DocumentBuilder = null
        docBuilderFactory = DocumentBuilderFactory.newInstance
        try {
          docBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false)
          docBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
          docBuilder = docBuilderFactory.newDocumentBuilder
          dom = docBuilder.parse(new InputSource(new StringReader(stringXml)))
        } catch {
          case e: ParserConfigurationException =>

        }
        val response = getNodesAsMap(dom, ProviderConstants.RESPONSE)
        val returnCode = response.get(ProviderConstants.RESPONSE_RETURN_CODE).asInstanceOf[String]
        if (ProviderConstants.API_RESPONSE_FAILED == returnCode) throw new BBBException(response.get(ProviderConstants.RESPONSE_MESSAGE_KEY).asInstanceOf[String], response.get(ProviderConstants.RESPONSE_MESSAGE).asInstanceOf[String], null)
        response
      }
      else throw new BBBException(BBBException.MESSAGE_KEY_HTTP_ERROR, "BBB server responded with HTTP status code " + responseCode, null)
    } catch {
      case e: BBBException =>
        throw new BBBException(e.getMessageKey, e.getMessage, e)
      case e: IOException =>
        throw new BBBException(BBBException.MESSAGE_KEY_UNREACHABLE, e.getMessage, e)
      case e: SAXException =>
        throw new BBBException(BBBException.MESSAGE_KEY_INVALID_RESPONSE, e.getMessage, e)
      case e: IllegalArgumentException =>
        throw new BBBException(BBBException.MESSAGE_KEY_INVALID_RESPONSE, e.getMessage, e)
      case e: Exception =>
        throw new BBBException(BBBException.MESSAGE_KEY_UNREACHABLE, e.getMessage, e)
    }
  }

  // --- BBB Other utility methods -----------------------------------------

  /** Get all nodes under the specified element tag name as a map */
  private def getNodesAsMap(dom: Document, elementTagName: String): util.Map[String, AnyRef] = {
    val firstNode = dom.getElementsByTagName(elementTagName).item(0)
    processNode(firstNode)
  }

  private def processNode(_node: Node): util.Map[String, AnyRef] = {
    val map = new util.HashMap[String, AnyRef]
    val responseNodes = _node.getChildNodes
    var images = 1 //counter for images (i.e image1, image2, image3)
    for (i <- 0 until responseNodes.getLength) {
      val node = responseNodes.item(i)
      val nodeName = node.getNodeName.trim
      if (node.getChildNodes.getLength == 1 && (node.getChildNodes.item(0).getNodeType == org.w3c.dom.Node.TEXT_NODE || node.getChildNodes.item(0).getNodeType == org.w3c.dom.Node.CDATA_SECTION_NODE)) {
        val nodeValue = node.getTextContent
        if ((nodeName eq "image") && node.getAttributes != null) {
          val imageMap = new util.HashMap[String, String]
          val heightAttr = node.getAttributes.getNamedItem("height")
          val widthAttr = node.getAttributes.getNamedItem("width")
          val altAttr = node.getAttributes.getNamedItem("alt")
          imageMap.put("height", heightAttr.getNodeValue)
          imageMap.put("width", widthAttr.getNodeValue)
          imageMap.put("title", altAttr.getNodeValue)
          imageMap.put("url", nodeValue)
          map.put(nodeName + images, imageMap)
          images += 1
        }
        else map.put(nodeName, if (nodeValue != null) nodeValue.trim
        else null)
      }
      else if (node.getChildNodes.getLength == 0 && node.getNodeType != org.w3c.dom.Node.TEXT_NODE && node.getNodeType != org.w3c.dom.Node.CDATA_SECTION_NODE) map.put(nodeName, "")
      // Below code could be needed when deep xml response read
      //      else if (node.getChildNodes.getLength >= 1) {
      //        var isList = false
      //        for (c <- 0 until node.getChildNodes.getLength) {
      //          try {
      //            val n = node.getChildNodes.item(c)
      //            if (n.getChildNodes.item(0).getNodeType != org.w3c.dom.Node.TEXT_NODE && n.getChildNodes.item(0).getNodeType != org.w3c.dom.Node.CDATA_SECTION_NODE) {
      //              isList = true
      //              break
      //
      //            }
      //          } catch {
      //            case e: Exception =>
      //              breakable {
      //                break
      //              }
      //          }
      //        }
      //        val list = new util.ArrayList[AnyRef]
      //        if (isList) {
      //          for (c <- 0 until node.getChildNodes.getLength) {
      //            val n = node.getChildNodes.item(c)
      //            list.add(processNode(n))
      //          }
      //          if (nodeName eq "preview") {
      //            val n = node.getChildNodes.item(0)
      //            map.put(nodeName, new util.ArrayList[AnyRef](processNode(n).values))
      //          }
      //          else map.put(nodeName, list)
      //        }
      //        else map.put(nodeName, processNode(node))
      //      }
      else map.put(nodeName, processNode(node))
    }
    map
  }

  /* Defers the event creation */
  override def deferEventCreation(): Boolean = true;
}
