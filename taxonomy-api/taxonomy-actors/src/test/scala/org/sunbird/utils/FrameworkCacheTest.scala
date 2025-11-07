package org.sunbird.utils

import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.sunbird.cache.impl.RedisCache
import org.sunbird.graph.util.ScalaJsonUtil
import java.util
import scala.jdk.CollectionConverters._

class FrameworkCacheTest extends FlatSpec with Matchers with BeforeAndAfterAll{

  override def afterAll(): Unit = {
    RedisCache.deleteByPattern("fw_*")
  }

  "get" should "retrieve framework metadata from cache" in {
    FrameworkCache.cacheEnabled = true
    val id = "framework_id"
    val returnCategories = new util.ArrayList[String]()
    returnCategories.add("Category1")
    val cacheKey = FrameworkCache.getFwCacheKey(id, returnCategories)
    val frameworkMetadata = """{"name":"Framework1"}"""
    RedisCache.set(cacheKey, frameworkMetadata, FrameworkCache.cacheTtl)
    val result = FrameworkCache.get(id, returnCategories)
    assert(null != result)
    result.get("name") shouldEqual "Framework1"
  }

  it should "retrieve framework metadata from cache for empty return categories" in {
    FrameworkCache.cacheEnabled = true
    val id = "framework_id"
    val cacheKey = id
    val frameworkMetadata = """{"name":"Framework1"}"""
    RedisCache.set(cacheKey, frameworkMetadata, FrameworkCache.cacheTtl)
    val result = FrameworkCache.get(id, new util.ArrayList[String]())
    assert(null != result)
    result.get("name") shouldEqual "Framework1"
  }

  it should "return null when cache is not enabled" in {
    FrameworkCache.cacheEnabled = false
    val result = FrameworkCache.get("framework_id", new util.ArrayList[String]())
    assert(null == result)
  }

  "save" should "save framework metadata to cache" in {
    FrameworkCache.cacheEnabled = true
    val framework = Map[String,AnyRef]("identifier" -> "framework_id", "name" -> "Framework1")
    val categoryNames = new util.ArrayList[String]()
    categoryNames.add("Category1")
    val identifier = framework.getOrElse("identifier", "").asInstanceOf[String]
    val cacheKey = FrameworkCache.getFwCacheKey(identifier, categoryNames)
    RedisCache.set(cacheKey, "sample-cache-data", FrameworkCache.cacheTtl)
    FrameworkCache.save(framework, categoryNames)
    val cachedFramework = RedisCache.get(cacheKey)
    cachedFramework shouldEqual ScalaJsonUtil.serialize(framework)
  }


  "delete" should "delete framework metadata from cache" in {
    val id = "framework_id"
    val frameworkMetadata = """{"name":"Framework1"}"""
    val cacheKey = FrameworkCache.getFwCacheKey(id, new util.ArrayList[String]())
    RedisCache.set(cacheKey, frameworkMetadata, FrameworkCache.cacheTtl)
    FrameworkCache.delete(id)
    val deletedMetadata = RedisCache.get(cacheKey)
    deletedMetadata shouldBe ""
  }

}
