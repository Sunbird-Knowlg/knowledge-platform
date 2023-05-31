package org.sunbird.utils

import org.sunbird.cache.impl.RedisCache
import org.sunbird.telemetry.logger.TelemetryManager
import scala.collection.mutable.ListBuffer


object CategoryCache{
  def getTerms(framework: String, category: String): List[String] = {
    val key = getKey(framework, category)
    RedisCache.getList(key)
  }

  def setFramework(id: String, framework: Map[String, AnyRef]): Unit = {
    try if (null != framework && framework.nonEmpty) {
      val categories = framework.get("categories").asInstanceOf[List[Map[String, AnyRef]]]
      setFramework(id, categories)
    }
    catch {
      case e: Exception =>
        throw e
    }
  }

  private def getKey(framework: String, category: String) = "cat_" + framework + category

  private def setFramework(framework: String, categories: List[Map[String, AnyRef]]): Unit = {
    if (null != categories && categories.nonEmpty) {

      for (category <- categories) {
        val catName = category.get("code").asInstanceOf[String]
        val terms = getTerms(category, "terms")
        if (terms.nonEmpty) {
          val key = getKey(framework, catName)
          TelemetryManager.info("Setting framework category cache with key: " + key)
          RedisCache.saveList(key, terms)
        }
      }
    }
  }

  private def getTerms(category: Map[String, Any], key: String): List[String] = {
    val returnTerms = ListBuffer[String]()
    if (category != null && category.nonEmpty) {
      val terms = category.get(key).asInstanceOf[List[Map[String, Any]]]
      if (terms != null) {
        for (term <- terms) {
          val termName = term.getOrElse("name", "").asInstanceOf[String]
          if (termName != null && termName.trim.nonEmpty) {
            returnTerms += termName
            val childTerms = getTerms(term, "children")
            if (childTerms.nonEmpty)
              returnTerms ++= childTerms
          }
        }
      }
    }
    returnTerms.toList
  }
}