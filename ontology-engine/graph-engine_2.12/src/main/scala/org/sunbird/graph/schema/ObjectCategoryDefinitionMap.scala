package org.sunbird.graph.schema

import com.twitter.storehaus.cache.Cache
import com.twitter.util.Duration
import org.sunbird.common.{Platform, Slug}

object ObjectCategoryDefinitionMap {

  val ttlMS = Platform.getLong("object.categoryDefinition.cache.ttl", 10000l)
  var cache =  Cache.ttl[String, Map[String, AnyRef]](Duration.fromMilliseconds(ttlMS))
    
  def get(id: String):Map[String, AnyRef] = {
    cache.getNonExpired(id).getOrElse(null)
  }

  def put(id: String, data: Map[String, AnyRef]): Unit = {
    val updated = cache.putClocked(id, data)._2
    cache = updated
  }

  def containsKey(id: String): Boolean = {
    cache.contains(id)
  }

  def prepareCategoryId(categoryName: String, objectType: String, channel: String = "all") = {
      if(!categoryName.isBlank)
          "obj-cat"+ ":" + Slug.makeSlug(categoryName + "_" + objectType + "_" + channel, true)
      else ""
  }
}
