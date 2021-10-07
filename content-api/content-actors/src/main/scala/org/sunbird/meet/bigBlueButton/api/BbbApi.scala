package org.sunbird.meet.bigBlueButton.api

import org.apache.commons.codec.digest.DigestUtils
import org.sunbird.common.Platform
import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.meet.bigBlueButton.entity
import org.sunbird.meet.bigBlueButton.entity.BBBException
import org.sunbird.meet.{Meet, Meeting}
import org.w3c.dom.{Document, Node}
import org.xml.sax.{InputSource, SAXException}

import java.io._
import java.net.{HttpURLConnection, URL, URLEncoder}
import java.util
import javax.xml.parsers.{DocumentBuilder, DocumentBuilderFactory, ParserConfigurationException}

class BbbApi extends Meet {

  // BBB server url
  protected var bbbUrl = Platform.config.getString("bbb_server_api_url")
  //BBB security salt
  protected var bbbSalt = Platform.config.getString("bbb_server_secure_salt")

  // API Server Path
  protected val API_SERVERPATH = "/api/"

  // API Calls
  protected val APICALL_CREATE = "create"
  protected val APICALL_GETMEETINGINFO = "getMeetingInfo"
  protected val APICALL_JOIN = "join"
  protected val APICALL_GETCONFIGXML = "getDefaultConfigXML"

  // API Response Codes
  protected val APIRESPONSE_SUCCESS = "SUCCESS"
  protected val APIRESPONSE_FAILED = "FAILED"

  @throws[UnsupportedEncodingException]
  private def encode(msg: String) = URLEncoder.encode(msg, getParametersEncoding)

  // --- BBB API implementation methods ------------------------------------

  /* Create BBB meeting */
  @throws[entity.BBBException]
  override def createMeeting(meeting: Meeting): Meeting = {
    var response: util.Map[String, AnyRef] = null
    try {
      val query = new StringBuilder
      query.append("meetingID=" + meeting.getMeetingID)
      if (meeting.getName != null) query.append("&name=" + encode(meeting.getName))
      query.append(getCheckSumParameterForQuery(APICALL_CREATE, query.toString))
      response = doAPICall(APICALL_CREATE, query.toString)
      meeting.setShouldUpdate(true)
    } catch {
      case e: entity.BBBException =>
        if ("idNotUnique" == e.getMessageKey) {
          response = getMeetingInfo(meeting.getMeetingID)
        } else {
          throw e
        }
      case e: IOException =>
        throw new entity.BBBException(BBBException.MESSAGEKEY_INTERNALERROR, e.getMessage, e)
    }
    // capture important information from returned response
    meeting.setMeetingID(response.get("meetingID").asInstanceOf[String])
    meeting.setModeratorPW(response.get("moderatorPW").asInstanceOf[String])
    meeting.setAttendeePW(response.get("attendeePW").asInstanceOf[String])
    meeting.setReturnCode(response.get("returncode").asInstanceOf[String])
    meeting
  }

  /* Build the join meeting url based on user role */
  override def getJoinMeetingURL(meeting: Meeting): String = {
    var url = null
    try {
      val joinQuery = new StringBuilder
      joinQuery.append("meetingID=" + meeting.getMeetingID)
      if (meeting.getUserId != null) joinQuery.append("&userID=" + encode(meeting.getUserId))
      joinQuery.append("&fullName=")
      try {
        if (meeting.getUserName == null)
          joinQuery.append(encode("user"))
        else
          joinQuery.append(encode(meeting.getUserName))
      }
      catch {
        case e: UnsupportedEncodingException =>
          joinQuery.append(meeting.getUserName)
      }
      if (meeting.getModerator)
        joinQuery.append("&password=" + meeting.getModeratorPW)
      else {
        try {
          val response = getMeetingInfo(meeting.getMeetingID)
          joinQuery.append("&password=" + response.get("attendeePW").asInstanceOf[String])
        } catch {
          case e: entity.BBBException =>
            if ("notFound" == e.getMessageKey) {
              throw new ClientException(ResponseCode.CLIENT_ERROR.name(), "Please wait for the Moderator to start Meeting")
            }
            throw e
        }
      }
      joinQuery.append(getCheckSumParameterForQuery(APICALL_JOIN, joinQuery.toString))
      val url = new java.lang.StringBuilder(bbbUrl)
      if (url.toString.endsWith("/api")) url.append("/")
      else url.append(API_SERVERPATH)
      url.append(APICALL_JOIN + "?" + joinQuery)
      url.toString
    } catch {
      case e: UnsupportedEncodingException => throw e
    }
  }

  /* Get BBB meeting information */
  @throws[entity.BBBException]
  def getMeetingInfo(meetingID: String): util.Map[String, AnyRef] = try {
    val query = new StringBuilder
    query.append("meetingID=" + meetingID)
    query.append(getCheckSumParameterForQuery(APICALL_GETMEETINGINFO, query.toString))
    val response = doAPICall(APICALL_GETMEETINGINFO, query.toString)
    response
  } catch {
    case e: entity.BBBException =>
      throw new entity.BBBException(e.getMessageKey, e.getMessage, e)
  }

  // --- BBB API utility methods -------------------------------------------

  /** Compute the query string checksum based on the security salt */
  protected def getCheckSumParameterForQuery(apiCall: String, queryString: String): String = if (bbbSalt != null) "&checksum=" + DigestUtils.shaHex(apiCall + queryString + bbbSalt)
  else ""

  /** Encoding used when encoding url parameters */
  protected def getParametersEncoding = "UTF-8"

  /* Make an API call */
  @throws[entity.BBBException]
  protected def doAPICall(apiCall: String, query: String): util.Map[String, AnyRef] = {
    val urlStr = new StringBuilder(bbbUrl)
    if (urlStr.toString.endsWith("/api")) urlStr.append("/")
    else urlStr.append(API_SERVERPATH)
    urlStr.append(apiCall)
    if (query != null) {
      urlStr.append("?")
      urlStr.append(query)
    }
    try { // open connection
      val url = new URL(urlStr.toString)
      val httpConnection = url.openConnection.asInstanceOf[HttpURLConnection]
      if (APICALL_CREATE eq apiCall) {
        httpConnection.setRequestMethod("POST")
        //                httpConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        httpConnection.setRequestProperty("Content-Length", "" + String.valueOf(0))
        httpConnection.setRequestProperty("Accept", "" + "*/*")
        httpConnection.setRequestProperty("Accept-Encoding", "" + "gzip, deflate, br")
        httpConnection.setRequestProperty("Connection", "" + "keep-alive")
      }
      else httpConnection.setRequestMethod("GET")
      httpConnection.connect()
      val responseCode = httpConnection.getResponseCode
      if (responseCode == HttpURLConnection.HTTP_OK) { // read response
        var isr: InputStreamReader = null
        var reader: BufferedReader = null
        val xml = new StringBuilder
        try {
          isr = new InputStreamReader(httpConnection.getInputStream, "UTF-8")
          var reader = new BufferedReader(isr)
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
        if (apiCall == APICALL_GETCONFIGXML) {
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
        val response = getNodesAsMap(dom, "response")
        val returnCode = response.get("returncode").asInstanceOf[String]
        if (APIRESPONSE_FAILED == returnCode) throw new entity.BBBException(response.get("messageKey").asInstanceOf[String], response.get("message").asInstanceOf[String], null)
        response
      }
      else throw new entity.BBBException(BBBException.MESSAGEKEY_HTTPERROR, "BBB server responded with HTTP status code " + responseCode, null)
    } catch {
      case e: entity.BBBException =>
        throw new entity.BBBException(e.getMessageKey, e.getMessage, e)
      case e: IOException =>
        throw new entity.BBBException(BBBException.MESSAGEKEY_UNREACHABLE, e.getMessage, e)
      case e: SAXException =>
        throw new entity.BBBException(BBBException.MESSAGEKEY_INVALIDRESPONSE, e.getMessage, e)
      case e: IllegalArgumentException =>
        throw new entity.BBBException(BBBException.MESSAGEKEY_INVALIDRESPONSE, e.getMessage, e)
      case e: Exception =>
        throw new entity.BBBException(BBBException.MESSAGEKEY_UNREACHABLE, e.getMessage, e)
    }
  }

  // --- BBB Other utility methods -----------------------------------------

  /** Get all nodes under the specified element tag name as a Java map */
  protected def getNodesAsMap(dom: Document, elementTagName: String): util.Map[String, AnyRef] = {
    val firstNode = dom.getElementsByTagName(elementTagName).item(0)
    processNode(firstNode)
  }

  protected def processNode(_node: Node): util.Map[String, AnyRef] = {
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
