package org.sunbird.mangers

import com.twitter.util.Config.intoOption
import org.sunbird.common.Platform
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.utils.Constants

import java.util
import java.util.{Map, Optional}
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.mutable.ListBuffer

object CategoryManager {

  def validateTranslationMap(request: Request) = {
    val translations: util.Map[String, AnyRef] = Optional.ofNullable(request.get("translations").asInstanceOf[util.HashMap[String, AnyRef]]).orElse(new util.HashMap[String, AnyRef]())
    println("translations: "+translations)
    if (translations.isEmpty) request.getRequest.remove("translations")
    else {
      val languageCodes = Platform.getStringList("platform.language.codes", new util.ArrayList[String]())
      if (translations.asScala.exists(entry => !languageCodes.contains(entry._1)))
        throw new ClientException("ERR_INVALID_LANGUAGE_CODE", "Please Provide Valid Language Code For translations. Valid Language Codes are : " + languageCodes)
    }
  }

  def setRelations(identifier: String, request: Request, objectType: String): Unit = {
    try {
      val relationList = new util.ArrayList[Map[String, AnyRef]]
      val relationMap = new util.HashMap[String, AnyRef]

      relationMap.put("identifier", identifier)
      relationMap.put("relation", "hasSequenceMember")
      if (request.getRequest.containsKey("index"))
        relationMap.put("index", request.get("index"))
      println("relationMap " + relationMap)
      relationList.add(relationMap)

      objectType.toLowerCase match {
        case "framework" =>
          request.put("frameworks", relationList)
          println("framework  == request " + request)
        case "category" =>
          request.put("categories", relationList)
        case "categoryinstance" =>
          request.put("categories", relationList)
          println(" categoryinstance == request " + request)
        case "channel" =>
          request.put("channels", relationList)
        case "term" =>
          request.put("terms", relationList)
          println("term == request " + request)
        case _ =>
      }
      println("relationList " + relationList)
    } catch {
      case e: Exception =>
        throw new ServerException("SERVER_ERROR", "Something went wrong while setting inRelations", e)
      }
  }



}