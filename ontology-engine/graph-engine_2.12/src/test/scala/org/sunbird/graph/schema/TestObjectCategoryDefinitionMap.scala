package org.sunbird.graph.schema

import org.sunbird.graph.BaseSpec

class TestObjectCategoryDefinitionMap extends BaseSpec {

  "CategoryDefinitionMap" should "store cache for given id and value" in {
    ObjectCategoryDefinitionMap.put("test-definition", Map("schema" -> Map(), "config" -> Map()))
    ObjectCategoryDefinitionMap.cache.occupancy shouldBe(1)
  }

  it  should "store cache with default ttl 10 sec" in {
    val tempKey = "test-definition"
    val tempValue = Map("schema" -> Map(), "config" -> Map())
    ObjectCategoryDefinitionMap.put(tempKey, tempValue)
    ObjectCategoryDefinitionMap.cache.occupancy shouldBe(1)
    ObjectCategoryDefinitionMap.get(tempKey) shouldBe(tempValue)
    Thread.sleep(10000)
    ObjectCategoryDefinitionMap.get(tempKey) shouldBe(null)
  }

}
