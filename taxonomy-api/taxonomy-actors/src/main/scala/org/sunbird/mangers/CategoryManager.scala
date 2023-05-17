package org.sunbird.mangers

import org.sunbird.common.Platform
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException

import java.util
import java.util.Optional
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.mutable.ListBuffer

object CategoryManager {

  def validateTranslationMap(request: Request) = {
    val translations: util.Map[String, AnyRef] = Optional.ofNullable(request.get("translations").asInstanceOf[util.HashMap[String, AnyRef]]).orElse(new util.HashMap[String, AnyRef]())
    if (translations.isEmpty) request.getRequest.remove("translations")
    else {
      val languageCodes = Platform.getStringList("platform.language.codes", new util.ArrayList[String]())
      if (translations.asScala.exists(entry => !languageCodes.contains(entry._1)))
        throw new ClientException("ERR_INVALID_LANGUAGE_CODE", "Please Provide Valid Language Code For translations. Valid Language Codes are : " + languageCodes)
    }
  }



}
