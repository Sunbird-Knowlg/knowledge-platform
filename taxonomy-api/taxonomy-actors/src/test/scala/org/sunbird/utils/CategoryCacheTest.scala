package org.sunbird.utils

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers}
import org.sunbird.cache.impl.RedisCache
import java.util
import scala.collection.JavaConverters._

class CategoryCacheTest extends FlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  override def afterEach(): Unit = {
    RedisCache.deleteByPattern("cat_*")
  }

  "setFramework" should "store category terms into cache" in {
    val frameworkId = "framework_id"
    val framework = new util.HashMap[String, AnyRef]()
    val category1 = new util.HashMap[String, AnyRef]()
    category1.put("name", "Category1")
    val term1 = new util.HashMap[String, AnyRef]()
    term1.put("name", "Term1")
    val term2 = new util.HashMap[String, AnyRef]()
    term2.put("name", "Term2")
    val terms1 = List(term1, term2).asJava
    category1.put("terms", terms1)
    framework.put("categories", List(category1).asJava)
    CategoryCache.setFramework(frameworkId, framework)
    val cachedTerms = RedisCache.getList("cat_framework_idCategory1")
    cachedTerms should contain theSameElementsAs List("Term1", "Term2")
  }

  it should "not store category terms into cache if framework is null" in {
    val frameworkId = "framework_id"
    val framework: util.Map[String, AnyRef] = null

    CategoryCache.setFramework(frameworkId, framework)
    val cachedTerms = RedisCache.getList("cat_framework_idCategory1")
    cachedTerms shouldBe empty
  }

  it should "not store category terms into cache if framework is empty" in {
    val frameworkId = "framework_id"
    val framework = new util.HashMap[String, AnyRef]()
    CategoryCache.setFramework(frameworkId, framework)
    val cachedTerms = RedisCache.getList("cat_framework_idCategory1")
    cachedTerms shouldBe empty
  }

  it should "not store category terms into cache if category terms are empty" in {
    val frameworkId = "framework_id"
    val framework = new util.HashMap[String, AnyRef]()
    val category1 = new util.HashMap[String, AnyRef]()
    category1.put("name", "Category1")
    category1.put("terms", new util.ArrayList[util.Map[String, AnyRef]]())
    framework.put("categories", List(category1).asJava)
    CategoryCache.setFramework(frameworkId, framework)
    val cachedTerms = RedisCache.getList("cat_framework_idCategory1")
    cachedTerms shouldBe empty
  }

}
