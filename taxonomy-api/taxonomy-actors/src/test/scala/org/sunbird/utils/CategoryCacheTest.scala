package org.sunbird.utils

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers}
import org.sunbird.cache.impl.RedisCache
import java.util

class CategoryCacheTest extends FlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  override def afterEach(): Unit = {
    RedisCache.deleteByPattern("cat_*")
  }

  "setFramework" should "store category terms into cache" in {
    val frameworkId = "framework_id"
    val framework = new util.Hashmutable.Map[String, AnyRef]()
    val category1 = new util.Hashmutable.Map[String, AnyRef]()
    category1.put("code", "Category1")
    val term1 = new util.Hashmutable.Map[String, AnyRef]()
    term1.put("name", "Term1")
    val term2 = new util.Hashmutable.Map[String, AnyRef]()
    term2.put("name", "Term2")
    val terms1 = util.Arrays.asList(term1, term2)
    category1.put("terms", terms1)
    framework.put("categories", util.Arrays.asList(category1))
    CategoryCache.setFramework(frameworkId, framework)
    val cachedTerms = RedisCache.getList("cat_framework_idCategory1")
    cachedTerms should contain theSameElementsAs List("Term1", "Term2")
  }

  it should "not store category terms into cache if framework is null" in {
    val frameworkId = "framework_id"
    val framework: util.mutable.Map[String, AnyRef] = null

    CategoryCache.setFramework(frameworkId, framework)
    val cachedTerms = RedisCache.getList("cat_framework_idCategory1")
    cachedTerms shouldBe empty
  }

  it should "not store category terms into cache if framework is empty" in {
    val frameworkId = "framework_id"
    val framework = new util.Hashmutable.Map[String, AnyRef]()
    CategoryCache.setFramework(frameworkId, framework)
    val cachedTerms = RedisCache.getList("cat_framework_idCategory1")
    cachedTerms shouldBe empty
  }

  it should "not store category terms into cache if category terms are empty" in {
    val frameworkId = "framework_id"
    val framework = new util.Hashmutable.Map[String, AnyRef]()
    val category1 = new util.Hashmutable.Map[String, AnyRef]()
    category1.put("name", "Category1")
    category1.put("terms", new util.ArrayList[util.mutable.Map[String, AnyRef]]())
    framework.put("categories", util.Arrays.asList(category1))
    CategoryCache.setFramework(frameworkId, framework)
    val cachedTerms = RedisCache.getList("cat_framework_idCategory1")
    cachedTerms shouldBe empty
  }

}
