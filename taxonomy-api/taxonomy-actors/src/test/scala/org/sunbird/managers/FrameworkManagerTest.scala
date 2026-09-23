package org.sunbird.managers

import org.scalatest.{FlatSpec, Matchers}
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.dto.Request
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.{Node, Relation, SearchCriteria, SubGraph}
import org.sunbird.utils.Constants

import java.util
import org.sunbird.managers.FrameworkManager._

import scala.collection.convert.ImplicitConversions._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global


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

  "FrameworkManager.copyHierarchy" should "honor context schemaName instead of the Framework hardcode" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node()
    node.setIdentifier("cf_source")
    node.setGraphId("domain")
    node.setObjectType("CompetencyFramework")
    node.setMetadata(new util.HashMap[String, AnyRef]() { { put("identifier", "cf_source"); put("objectType", "CompetencyFramework"); put("code", "cf_source"); put("name", "cf_source"); put("type", "K-12") } })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(node)).anyNumberOfTimes()

    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() {
      { put("graph_id", "domain"); put("schemaName", "competencyframework"); put("version", "1.0"); put("objectType", "CompetencyFramework") }
    })
    request.putAll(new util.HashMap[String, AnyRef]() { { put(Constants.IDENTIFIER, "cf_source"); put(Constants.CODE, "cf_copy") } })
    import scala.concurrent.Await
    import scala.concurrent.duration._
    val response = Await.result(FrameworkManager.copyHierarchy(request), 10.seconds)
    assert(response.getResult.containsKey("node_id"))
  }

  it should "default to the framework schema when context carries no schemaName key at all" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node()
    node.setIdentifier("fw_source")
    node.setGraphId("domain")
    node.setObjectType("Framework")
    node.setMetadata(new util.HashMap[String, AnyRef]() { { put("identifier", "fw_source"); put("objectType", "Framework"); put("code", "fw_source"); put("name", "fw_source"); put("type", "K-12") } })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(node)).anyNumberOfTimes()

    val request = new Request()
    // No "schemaName" key at all -- unlike an actor-level Request (see FrameworkActorTest's note),
    // copyHierarchy never calls RequestUtil.restrictProperties, so this genuinely exercises the
    // getOrDefault(SCHEMA_NAME, FRAMEWORK_SCHEMA_NAME) fallback rather than re-reading a pre-set value.
    request.setContext(new util.HashMap[String, AnyRef]() {
      { put("graph_id", "domain"); put("version", "1.0"); put("objectType", "Framework") }
    })
    request.putAll(new util.HashMap[String, AnyRef]() { { put(Constants.IDENTIFIER, "fw_source"); put(Constants.CODE, "fw_copy") } })
    import scala.concurrent.Await
    import scala.concurrent.duration._
    val response = Await.result(FrameworkManager.copyHierarchy(request), 10.seconds)
    assert(response.getResult.containsKey("node_id"))
  }

  }