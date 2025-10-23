package org.sunbird.graph.schema

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import org.sunbird.common.{Platform, Slug}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

object ObjectCategoryDefinitionMap {

  val ttlMS = Platform.getLong("object.categoryDefinition.cache.ttl", 10000l)
  val cache: Cache[String, Map[String, AnyRef]] = Caffeine.newBuilder()
    .expireAfterWrite(ttlMS, TimeUnit.MILLISECONDS)
    .build[String, Map[String, AnyRef]]()
    
  def get(id: String): Map[String, AnyRef] = {
    Option(cache.getIfPresent(id)).orNull
  }

  def put(id: String, data: Map[String, AnyRef]): Unit = {
    cache.put(id, data)
  }

  def containsKey(id: String): Boolean = {
    cache.getIfPresent(id) != null
  }

  def prepareCategoryId(categoryName: String, objectType: String, channel: String = "all") = {
      if(!categoryName.isBlank)
          "obj-cat"+ ":" + Slug.makeSlug(categoryName + "_" + objectType + "_" + channel, true)
      else ""
  }
}
