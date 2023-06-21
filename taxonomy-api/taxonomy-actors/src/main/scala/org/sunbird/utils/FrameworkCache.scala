package org.sunbird.utils

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.graph.util.ScalaJsonUtil
import org.sunbird.graph.utils.ScalaJsonUtils

import java.util
import java.util.Collections
import scala.collection.JavaConverters._
import scala.collection.JavaConversions.{asJavaCollection, asScalaBuffer}
import scala.collection.JavaConverters.seqAsJavaListConverter

object FrameworkCache{

    private val cacheTtl: Int = if (Platform.config.hasPath("framework.cache.ttl")) Platform.config.getInt("framework.cache.ttl") else 86400
    protected var cacheEnabled: Boolean = if (Platform.config.hasPath("framework.cache.read")) Platform.config.getBoolean("framework.cache.read") else false
    private val CACHE_PREFIX: String = "fw_"
    protected var mapper: ObjectMapper = new ObjectMapper


    protected def getFwCacheKey(identifier: String, categoryNames: util.List[String]): String = {
        Collections.sort(categoryNames)
        CACHE_PREFIX + identifier.toLowerCase + "_" + categoryNames.map(_.toLowerCase).mkString("_")
    }

    def get(id: String, returnCategories: util.List[String]): util.Map[String, Object] = {
      println("cacheEnabled "+ cacheEnabled)
        if (cacheEnabled) {
            if (returnCategories.nonEmpty) {
              val categories = new util.ArrayList[String](returnCategories)
                Collections.sort(categories)
                 println("fwcachekey: " + getFwCacheKey(id, categories) )
                val cachedCategories: String = RedisCache.get(getFwCacheKey(id, categories))
                println("cachedCategories :" +cachedCategories)
                if (StringUtils.isNotBlank(cachedCategories))
                  return JsonUtils.deserialize(cachedCategories, classOf[util.Map[String, Object]])
            } else {
                val frameworkMetadata: String = RedisCache.get(id)
                if (StringUtils.isNotBlank(frameworkMetadata))
                  return JsonUtils.deserialize(frameworkMetadata, classOf[util.Map[String, Object]])
            }
        }
        null
    }


    def save(framework: Map[String, AnyRef], categoryNames: util.List[String]): Unit = {
      val identifier = framework.getOrElse("identifier", "").asInstanceOf[String]
        if (cacheEnabled && !framework.isEmpty && StringUtils.isNotBlank(identifier) && categoryNames.nonEmpty) {
          val categories = new util.ArrayList[String](categoryNames)
          Collections.sort(categories)
          val key: String = getFwCacheKey(identifier, categories)
          RedisCache.set(key, ScalaJsonUtil.serialize(framework), cacheTtl)
        }
    }

    def delete(id: String): Unit = {
        if (StringUtils.isNotBlank(id)) {
            RedisCache.deleteByPattern(CACHE_PREFIX + id + "_*")
        }
    }

}