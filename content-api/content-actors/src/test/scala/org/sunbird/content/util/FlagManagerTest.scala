package org.sunbird.content.util

import java.util

import org.scalatest.{FlatSpec, Matchers}
class FlagManagerTest extends FlatSpec with Matchers {

  "addFlaggedBy with metadata without flaggedBy" should "return flaggedList with only list with request flaggedBy value" in {
    val flaggedBy = "John"
    val metadata = new util.HashMap[String, AnyRef](){{}}
    val flaggedByList = FlagManager.addFlaggedBy(flaggedBy, metadata)
    assert(flaggedByList.size()==1)
    assert("John" == flaggedByList.get(0))
  }

  "addFlaggedBy with metadata with flaggedBy as array of string" should "return flaggedList with list of requestFlaggedBy and metadata flaggedBy value" in {
    val requestFlaggedBy = "John"
    var flaggedBy = Array("Tom")
    val metadata = new util.HashMap[String, AnyRef](){{put("flaggedBy", flaggedBy)}}
    val flaggedByList = FlagManager.addFlaggedBy(requestFlaggedBy, metadata)
    assert(flaggedByList.size()==2)
    assert("Tom" == flaggedByList.get(0))
    assert("John" == flaggedByList.get(1))
  }

  "addFlaggedBy with metadata with flaggedBy as list of string" should "return flaggedList with list of requestFlaggedBy and metadata flaggedBy value" in {
    val requestFlaggedBy = "John"
    val flaggedBy = java.util.Arrays.asList("Tom", "Jack")
    val metadata = new util.HashMap[String, AnyRef](){{put("flaggedBy", flaggedBy)}}
    val flaggedByList = FlagManager.addFlaggedBy(requestFlaggedBy, metadata)
    assert(flaggedByList.size()==3)
    assert(flaggedByList.containsAll(java.util.Arrays.asList("Tom", "Jack", "John")))
  }

  "addFlagReasons with metadata metadata without flagReasons" should "return flaggedList with only list with request flagReasons value" in {
    val requestFlagReasons = java.util.Arrays.asList("Not a valid content")
    val metadata = new util.HashMap[String, AnyRef]()
    val flaggedByList = FlagManager.addFlagReasons(requestFlagReasons, metadata)
    assert(flaggedByList.size()==1)
    assert(flaggedByList.containsAll(java.util.Arrays.asList("Not a valid content")))
  }

  "addFlagReasons with metadata with flagReasons as array of string" should "return flaggedList with list of requestFlagReasons and metadata flagReasons value" in {
    val requestFlagReasons = "Not a valid content"
    var flagReasons = Array("Others")
    val metadata = new util.HashMap[String, AnyRef](){{put("flaggedBy", flagReasons)}}
    val flaggedByList = FlagManager.addFlaggedBy(requestFlagReasons, metadata)
    assert(flaggedByList.size()==2)
    assert(flaggedByList.containsAll(java.util.Arrays.asList("Not a valid content", "Others")))
  }

  "addFlagReasons with metadata with flagReasons as list of string" should "return flaggedList with list of requestFlagReasons and metadata flagReasons value" in {
    val requestFlagReasons = java.util.Arrays.asList("Not a valid content")
    val flagReasons = java.util.Arrays.asList("Others")
    val metadata = new util.HashMap[String, AnyRef](){{
      put("flagReasons", flagReasons)
    }}
    val flaggedByList = FlagManager.addFlagReasons(requestFlagReasons, metadata)
    assert(flaggedByList.size()==2)
    assert(flaggedByList.containsAll(java.util.Arrays.asList("Not a valid content", "Others")))
  }
}
