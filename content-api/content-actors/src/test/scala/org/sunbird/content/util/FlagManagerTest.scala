package org.sunbird.content.util

import java.util

import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
class FlagManagerTest extends FlatSpec with Matchers with MockFactory {

  /*"addFlaggedBy with metadata without flaggedBy" should "return flaggedList with only list with request flaggedBy value" in {
    val flaggedBy = "John"
    val metadata = new util.HashMap[String, AnyRef](){{}}
    val flaggedByList = FlagManager.addFlaggedBy(flaggedBy, metadata)
    assert(flaggedByList.size()==1)
    assert("John" == flaggedByList.get(0))
  }

  "addFlaggedBy with metadata with flaggedBy as list of string" should "return flaggedList with list of requestFlaggedBy and metadata flaggedBy value" in {
    val requestFlaggedBy = "John"
    val flaggedBy = new util.ArrayList[String]()
    flaggedBy.add("Tom")
    flaggedBy.add("Jack")
    val metadata = new util.HashMap[String, AnyRef](){{put("flaggedBy", flaggedBy)}}
    val flaggedByList = FlagManager.addFlaggedBy(requestFlaggedBy, metadata)
    assert(flaggedByList.size()==3)
    assert(flaggedByList.containsAll(java.util.Arrays.asList("Tom", "Jack", "John")))
  }*/

  "addFlagReasons with metadata metadata without flagReasons" should "return flaggedList with only list with request flagReasons value" in {
    val requestFlagReasons = java.util.Arrays.asList("Not a valid content")
    val metadata = new util.HashMap[String, AnyRef]()
    //val flaggedByList = FlagManager.addFlagReasons(requestFlagReasons, metadata)
    val flaggedByList = FlagManager.addDataIntoList(requestFlagReasons, metadata)
    assert(flaggedByList.size()==1)
    assert(flaggedByList.containsAll(java.util.Arrays.asList("Not a valid content")))
  }

  "addFlagReasons with metadata with flagReasons as list of string" should "return flaggedList with list of requestFlagReasons and metadata flagReasons value" in {
    val requestFlagReasons = new java.util.ArrayList[String]
    requestFlagReasons.add("Not a valid content")
    val flagReasons = new java.util.ArrayList[String]
    flagReasons.add("Others")
    val metadata = new util.HashMap[String, AnyRef](){{
      put("flagReasons", flagReasons)
    }}
    //val flaggedByList = FlagManager.addFlagReasons(requestFlagReasons, metadata)
    val flaggedByList = FlagManager.addDataIntoList(requestFlagReasons, metadata)
    assert(flaggedByList.size()==2)
    assert(flaggedByList.containsAll(java.util.Arrays.asList("Not a valid content", "Others")))
  }
}
