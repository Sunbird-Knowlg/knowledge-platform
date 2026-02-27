package org.sunbird.managers

import org.scalatest.{FlatSpec, Matchers}
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.{Node, Relation, SubGraph}

import java.util
import org.sunbird.mangers.FrameworkManager._

import scala.collection.convert.ImplicitConversions._
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._


class FrameworkManagerTest extends FlatSpec with Matchers with MockFactory{

  "FrameworkManager" should "correctly filter framework categories and remove associations" in {
    val framework = new util.HashMap[String, AnyRef]()
    framework.put("name", "framework_test")
    framework.put("code", "framework_test")
    framework.put("description", "desc_test")
    framework.put("channel", "channel_test")
    framework.put("languageCode", new util.ArrayList[String]())
    framework.put("systemDefault", "No")
    framework.put("objectType", "Framework")
    framework.put("status", "Live")
    framework.put("categories", new util.ArrayList[util.Map[String,AnyRef]]())
    framework.put("owner", "in.ekstep")
    framework.put("type", "K-12")

    val category1 = new util.HashMap[String, AnyRef]()
    category1.put("name", "Subject")
    category1.put("code", "subject")
    val category2 = new util.HashMap[String, AnyRef]()
    category2.put("name", "Grade")
    category2.put("code", "grade")
    val categories = new util.ArrayList[util.Map[String,AnyRef]]()
    categories.add(category1)
    categories.add(category2)


    val categoryNames = new util.ArrayList[String]()
    categoryNames.add("subject")
    

    val term1 = new util.HashMap[String, AnyRef]()
    term1.put("name", "Term1")
    term1.put("code", "term1")
    val associations1 = new util.ArrayList[util.Map[String,AnyRef]]()
    val association1 = new util.HashMap[String, AnyRef]()
    association1.put("category", "Category1")
    associations1.add(association1)
    term1.put("associations", associations1)

    val term2 = new util.HashMap[String, AnyRef]()
    term2.put("name", "Term2")
    term2.put("code", "term2")
    val associations2 = new util.ArrayList[util.Map[String,AnyRef]]()
    val association2 = new util.HashMap[String, AnyRef]()
    association2.put("category", "Category2")
    associations2.add(association2)
    term2.put("associations", associations2)

    category1.put("terms", new util.ArrayList[util.Map[String,AnyRef]]())
    category1.get("terms").asInstanceOf[util.List[util.Map[String,AnyRef]]].add(term1)
    category1.get("terms").asInstanceOf[util.List[util.Map[String,AnyRef]]].add(term2)

    val returnCategories = new util.ArrayList[String]()
    returnCategories.add("Category1")
    returnCategories.add("Category2")

    framework.put("categories", categories)

    val frameworkWithAssociationsRemoved = filterFrameworkCategories(framework, categoryNames)
    val filteredTerms = frameworkWithAssociationsRemoved
      .getOrElse("categories", new util.ArrayList[util.Map[String,AnyRef]]())
      .asInstanceOf[util.List[util.Map[String,AnyRef]]]
      .flatMap[util.Map[String,AnyRef]](_.getOrDefault("terms", new util.ArrayList[util.Map[String,AnyRef]]).asInstanceOf[util.List[util.Map[String,AnyRef]]])

    assert(filteredTerms.contains(term1))
    assert(filteredTerms.contains(term2))
    assert(!term1.containsKey("associations"))
    assert(!term2.containsKey("associations"))
  }

  // ─── getCompleteMetadata tests ─────────────────────────────────────────────
  // These exercise both buildFilteredNodeMetadata and sortedOutRelations helpers
  // (extracted as private methods) through the public getCompleteMetadata API.

  "FrameworkManager.getCompleteMetadata" should
    "throw ERR_NODE_NOT_FOUND when the requested id is absent from the SubGraph" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

    val emptyNodeMap: util.Map[String, Node] = new util.HashMap[String, Node]()
    val emptyRelations: util.List[Relation] = new util.ArrayList[Relation]()
    val subGraph = new SubGraph(emptyNodeMap, emptyRelations)

    val ex = intercept[ClientException] {
      getCompleteMetadata("missing_node", subGraph, includeRelations = false)
    }
    ex.getErrCode shouldEqual "ERR_NODE_NOT_FOUND"
    ex.getMessage should include("missing_node")
  }

  it should "return metadata map with identifier and objectType when includeRelations is false" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

    val node = buildFrameworkNode("NCF", "Framework", "sunbird")
    val nodeMap: util.Map[String, Node] = new util.HashMap[String, Node]()
    nodeMap.put("NCF", node)
    val subGraph = new SubGraph(nodeMap, new util.ArrayList[Relation]())

    val result = getCompleteMetadata("NCF", subGraph, includeRelations = false)

    result should not be null
    result.containsKey("identifier") shouldBe true
    result.get("identifier") shouldEqual "NCF"
    result.containsKey("objectType") shouldBe true
  }

  it should "return metadata without child collections when includeRelations=false even if relations exist" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

    val node = buildFrameworkNode("NCF", "Framework", "sunbird")
    val nodeMap: util.Map[String, Node] = new util.HashMap[String, Node]()
    nodeMap.put("NCF", node)

    // Add a relation, but includeRelations=false must ignore it
    val rel = buildSeqRelation("NCF", "hasSequenceMember", "NCF_board", 1L)
    val relations: util.List[Relation] = util.Arrays.asList(rel)
    val subGraph = new SubGraph(nodeMap, relations)

    val result = getCompleteMetadata("NCF", subGraph, includeRelations = false)

    // No child key should have been added when includeRelations=false
    result.containsKey("categories") shouldBe false
    result.containsKey("terms")      shouldBe false
  }

  it should "return metadata that does not contain null-value entries" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

    val node = new Node()
    node.setIdentifier("NCF")
    node.setGraphId("domain")
    node.setObjectType("Framework")
    val meta = new util.HashMap[String, AnyRef]()
    meta.put("name", "NCF")
    meta.put("nullField", null)   // should be filtered out
    meta.put("channel", "sunbird")
    node.setMetadata(meta)

    val nodeMap: util.Map[String, Node] = new util.HashMap[String, Node]()
    nodeMap.put("NCF", node)
    val subGraph = new SubGraph(nodeMap, new util.ArrayList[Relation]())

    val result = getCompleteMetadata("NCF", subGraph, includeRelations = false)

    result.containsKey("nullField") shouldBe false
  }

  // ─── Helper builders ───────────────────────────────────────────────────────

  private def buildFrameworkNode(id: String, objectType: String, channel: String): Node = {
    val node = new Node()
    node.setIdentifier(id)
    node.setGraphId("domain")
    node.setObjectType(objectType)
    node.setMetadata(new util.HashMap[String, AnyRef]() {{
      put("name",       id)
      put("channel",    channel)
      put("objectType", objectType)
    }})
    node
  }

  private def buildSeqRelation(startId: String, relType: String, endId: String, index: Long): Relation = {
    val rel = new Relation(startId, relType, endId)
    val meta = new util.HashMap[String, Object]()
    meta.put("IL_SEQUENCE_INDEX", index.asInstanceOf[java.lang.Long])
    rel.setMetadata(meta)
    rel
  }

  }