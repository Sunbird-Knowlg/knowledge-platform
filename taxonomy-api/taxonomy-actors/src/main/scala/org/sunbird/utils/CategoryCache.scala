package org.sunbird.utils

import java.util
import org.sunbird.cache.impl.RedisCache
import org.sunbird.telemetry.logger.TelemetryManager
import scala.collection.JavaConversions._


object CategoryCache{

  def setFramework(id: String, framework: util.Map[String, AnyRef]): Unit = {
    if (null != framework && !framework.isEmpty) {
      val categories = framework.getOrDefault("categories", new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]].toList
      categories.map(category => {
        val catName = category.get("name").asInstanceOf[String]
        val terms = getTerms(category, "terms")
        if (terms.nonEmpty) {
          val key = getKey(id, catName)
          TelemetryManager.info("Setting framework category cache with key: " + key)
          RedisCache.saveList(key, terms)
        }
      })
    }
  }

  private def getKey(framework: String, category: String) = "cat_" + framework + category


  private def getTerms(category: util.Map[String, AnyRef], key: String): List[String] = {
    val returnTerms = new util.ArrayList[String]
    if (category != null && category.nonEmpty) {
      val terms = category.getOrDefault(key, new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]].toList
      if (terms != null && terms.nonEmpty) {
        for (term <- terms) {
          val termName = term.getOrElse("name", "").asInstanceOf[String]
          if (termName != null && termName.trim.nonEmpty) {
            returnTerms += termName
            val childTerms = getTerms(term, "associations")
            if (childTerms.nonEmpty)
              returnTerms ++= childTerms
          }
        }
      }
    }
    returnTerms.toList
  }
}