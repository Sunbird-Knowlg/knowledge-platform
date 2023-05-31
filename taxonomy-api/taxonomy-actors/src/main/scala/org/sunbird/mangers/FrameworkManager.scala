package org.sunbird.mangers

import com.twitter.util.Config.intoOption
import org.sunbird.common.Platform
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException

import java.util.{List, Map}
import java.util
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConverters.mapAsScalaMapConverter

object FrameworkManager {
  private val languageCodes = Platform.getStringList("platform.language.codes", new util.ArrayList[String]())
  def validateTranslationMap(request: Request) = {
    val translations: util.Map[String, AnyRef] = request.getOrElse("translations", "").asInstanceOf[util.HashMap[String, AnyRef]]
    if (translations.isEmpty) request.getRequest.remove("translations")
    else {
      if (translations.asScala.exists(entry => !languageCodes.contains(entry._1)))
        throw new ClientException("ERR_INVALID_LANGUAGE_CODE", "Please Provide Valid Language Code For translations. Valid Language Codes are : " + languageCodes)
    }
  }


def filterFrameworkCategories(framework: Map[String, AnyRef], categoryNames: List[String]): Unit = {
  val categories = framework.get("categories").asInstanceOf[List[Map[String, AnyRef]]]
  if (categories.nonEmpty && categoryNames.nonEmpty) {
    val filteredCategories = categories.filter(p => categoryNames.contains(p.get("code")))
    framework.put("categories",filteredCategories)
    removeAssociations(framework, categoryNames)
  }
}

  def removeAssociations(responseMap: Map[String, AnyRef], returnCategories: List[String]): Unit = {
    val categories = responseMap.get("categories").asInstanceOf[List[Map[String, AnyRef]]]
    categories.foreach { category =>
      removeTermAssociations(category.get("terms").asInstanceOf[List[Map[String, AnyRef]]], returnCategories)
    }
  }

  def removeTermAssociations(terms: List[Map[String, AnyRef]], returnCategories: List[String]): Unit = {
    terms.foreach { term =>
      val associations = term.get("associations").asInstanceOf[List[Map[String, AnyRef]]]
      if (associations.nonEmpty) {
        val filteredAssociations = associations.filter(p => p != null && returnCategories.contains(p.get("category")))
        term.put("associations",filteredAssociations)
        if (filteredAssociations.isEmpty)
          term.remove("associations")

        removeTermAssociations(term.get("children").asInstanceOf[List[Map[String, AnyRef]]], returnCategories)
      }
    }
  }
}
