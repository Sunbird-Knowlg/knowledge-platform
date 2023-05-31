package org.sunbird.utils

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.Platform

import java.util
import java.util.Collections
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

    def get(id: String, returnCategories: util.List[String]): Map[String, AnyRef] = {
        if (cacheEnabled) {
            if (CollectionUtils.isNotEmpty(returnCategories)) {
              val categories = new util.ArrayList[String](returnCategories)
                Collections.sort(categories)
                 println("fwcachekey: " + getFwCacheKey(id, categories) )
                val cachedCategories: String = RedisCache.get(getFwCacheKey(id, categories))
                println("cachedCategories :" +cachedCategories)
                if (StringUtils.isNotBlank(cachedCategories)) {
                    return mapper.readValue(cachedCategories, new TypeReference[Map[String, AnyRef]](){})
                }
            }
            else {
                val frameworkMetadata: String = RedisCache.get(id)
                if (StringUtils.isNotBlank(frameworkMetadata)) {
                    return mapper.readValue(frameworkMetadata, new TypeReference[Map[String, AnyRef]](){})
                }
            }
        }
        null
    }


    def save(framework: Map[String, AnyRef], categoryNames: util.List[String]): Unit = {
        if (cacheEnabled && MapUtils.isNotEmpty(framework.asInstanceOf[java.util.Map[String, Object]]) && StringUtils.isNotBlank(framework.get("identifier").asInstanceOf[String]) && CollectionUtils.isNotEmpty(categoryNames)) {
            Collections.sort(categoryNames)
            val key: String = getFwCacheKey(framework.get("identifier").asInstanceOf[String], categoryNames)
            RedisCache.set(key, mapper.writeValueAsString(framework), cacheTtl)
        }
    }

    def delete(id: String): Unit = {
        if (StringUtils.isNotBlank(id)) {
            RedisCache.deleteByPattern(CACHE_PREFIX + id + "_*")
        }
    }

}