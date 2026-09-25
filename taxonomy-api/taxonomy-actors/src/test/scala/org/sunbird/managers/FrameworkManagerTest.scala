package org.sunbird.managers

import org.scalatest.{FlatSpec, Matchers}
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.{Node, Relation, SearchCriteria, SubGraph}
import org.sunbird.utils.Constants

import java.util
import java.util.concurrent.CompletionException
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException}
import org.sunbird.managers.FrameworkManager._

import scala.collection.convert.ImplicitConversions._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
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

  private def notFoundFailure(): Future[Node] = Future.failed(new CompletionException(
    new ResourceNotFoundException("ERR_NODE_NOT_FOUND", "not found")))

  private def buildFrameworkNode(identifier: String, objectType: String = "Framework"): Node = {
    val node = new Node()
    node.setIdentifier(identifier)
    node.setGraphId("domain")
    node.setObjectType(objectType)
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      { put("code", "fw1"); put("objectType", objectType); put("name", "fw1"); put("channel", "sunbird") }
    })
    node
  }

  private def categoryRelation(endId: String): Relation = {
    val r = new Relation("fw1", "hasSequenceMember", endId)
    r.setStartNodeObjectType("Framework")
    r.setEndNodeObjectType("CategoryInstance")
    r
  }

  private def channelRelation(startId: String): Relation = {
    val r = new Relation(startId, "hasSequenceMember", "fw1")
    r.setStartNodeObjectType("Channel")
    r.setEndNodeObjectType("Framework")
    r
  }

  private def publishRequest(): Request = {
    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() {
      { put("graph_id", "domain"); put("schemaName", "framework"); put("version", "1.0") }
    })
    request
  }

  "FrameworkManager.deleteImageNodeIfExists" should "return true and call deleteNode when .img exists" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val imgNode = buildFrameworkNode("fw1.img", "FrameworkImage")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1.img", *, *).returns(Future(imgNode))
    (graphDB.deleteNode(_: String, _: String, _: Request)).expects(*, "fw1.img", *).returns(Future(true))
    val result = Await.result(FrameworkManager.deleteImageNodeIfExists("domain", "fw1"), 10.seconds)
    assert(result)
  }

  it should "return false and skip deleteNode when .img is absent" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1.img", *, *).returns(notFoundFailure())
    // graphDB.deleteNode is intentionally left un-stubbed: ScalaMock fails the test if it's called.
    val result = Await.result(FrameworkManager.deleteImageNodeIfExists("domain", "fw1"), 10.seconds)
    assert(!result)
  }

  "FrameworkManager.retireImageNode" should "soft-retire fw1.img via updateNodes with status=Retired" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.updateNodes(_: String, _: util.List[String], _: util.Map[String, AnyRef]))
      .expects(*, util.Collections.singletonList("fw1.img"), *)
      .onCall((_: String, ids: util.List[String], metadata: util.Map[String, AnyRef]) => {
        assert(ids.contains("fw1.img"))
        assert("Retired".equals(metadata.get("status")))
        Future(new util.HashMap[String, Node]())
      })
    Await.result(FrameworkManager.retireImageNode("domain", "fw1"), 10.seconds)
  }

  it should "still call updateNodes for fw1.img even when it doesn't exist (silently skipped downstream)" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.updateNodes(_: String, _: util.List[String], _: util.Map[String, AnyRef]))
      .expects(*, util.Collections.singletonList("fw1.img"), *)
      .returns(Future(new util.HashMap[String, Node]()))
    val result = Await.result(FrameworkManager.retireImageNode("domain", "fw1"), 10.seconds)
    assert(result.isEmpty)
  }

  "FrameworkManager.publishFramework" should "promote .img's metadata onto the live node but never copy identifier/status/objectType/versionKey/prevStatus/isImageNodeCreated" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    val liveNode = buildFrameworkNode("fw1")
    val imgNode = buildFrameworkNode("fw1.img", "FrameworkImage")
    imgNode.getMetadata.put("status", "Draft")
    imgNode.getMetadata.put("versionKey", "abc123")
    imgNode.getMetadata.put("prevStatus", "Live")
    imgNode.getMetadata.put("isImageNodeCreated", "yes")
    imgNode.getMetadata.put("description", "edited")

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1.img", *, *).returns(Future(imgNode)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1", *, *).returns(Future(liveNode)).anyNumberOfTimes()
    (graphDB.deleteNode(_: String, _: String, _: Request)).expects(*, "fw1.img", *).returns(Future(true))
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()

    var submitted: util.Map[String, Object] = null
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).onCall((_: String, n: Node, _: Request) => {
      submitted = n.getMetadata
      Future(n)
    })

    val result = Await.result(FrameworkManager.publishFramework(publishRequest(), "fw1"), 10.seconds)
    assert(submitted.get("description") == "edited")
    assert("Live".equals(submitted.get("status")))
    assert(submitted.get("versionKey") == null)
    assert(submitted.get("prevStatus") == null)
    assert(submitted.get("isImageNodeCreated") == null)
    assert(!"FrameworkImage".equals(submitted.get("objectType")))
  }

  it should "delete .img after a successful promote" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val liveNode = buildFrameworkNode("fw1")
    val imgNode = buildFrameworkNode("fw1.img", "FrameworkImage")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1.img", *, *).returns(Future(imgNode)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1", *, *).returns(Future(liveNode)).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(liveNode))
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    (graphDB.deleteNode(_: String, _: String, _: Request)).expects(*, "fw1.img", *).returns(Future(true))

    Await.result(FrameworkManager.publishFramework(publishRequest(), "fw1"), 10.seconds)
  }

  it should "treat a missing .img as a legitimate no-op for the metadata-promote sub-step (still increments version/sets Live)" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val liveNode = buildFrameworkNode("fw1")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1.img", *, *).returns(notFoundFailure()).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1", *, *).returns(Future(liveNode)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    // graphDB.deleteNode is intentionally left un-stubbed: ScalaMock fails the test if it's called.
    var submitted: util.Map[String, Object] = null
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).onCall((_: String, n: Node, _: Request) => {
      submitted = n.getMetadata
      Future(n)
    })

    Await.result(FrameworkManager.publishFramework(publishRequest(), "fw1"), 10.seconds)
    assert("Live".equals(submitted.get("status")))
    assert(submitted.get("version").asInstanceOf[Number].intValue() == 1)
  }

  it should "increment version by exactly 1 regardless of whether .img existed" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val liveNode = buildFrameworkNode("fw1")
    liveNode.getMetadata.put("version", Integer.valueOf(2))
    val imgNode = buildFrameworkNode("fw1.img", "FrameworkImage")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1.img", *, *).returns(Future(imgNode)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1", *, *).returns(Future(liveNode)).anyNumberOfTimes()
    (graphDB.deleteNode(_: String, _: String, _: Request)).expects(*, "fw1.img", *).returns(Future(true))
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    var submitted: util.Map[String, Object] = null
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).onCall((_: String, n: Node, _: Request) => {
      submitted = n.getMetadata
      Future(n)
    })

    Await.result(FrameworkManager.publishFramework(publishRequest(), "fw1"), 10.seconds)
    assert(submitted.get("version").asInstanceOf[Number].intValue() == 3)
  }

  it should "createRelation for a category added on .img, without calling removeRelation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val liveNode = buildFrameworkNode("fw1")
    liveNode.setOutRelations(util.Arrays.asList(categoryRelation("catA")))
    val imgNode = buildFrameworkNode("fw1.img", "FrameworkImage")
    imgNode.setOutRelations(util.Arrays.asList(categoryRelation("catA"), categoryRelation("catB")))
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1.img", *, *).returns(Future(imgNode)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1", *, *).returns(Future(liveNode)).anyNumberOfTimes()
    (graphDB.deleteNode(_: String, _: String, _: Request)).expects(*, "fw1.img", *).returns(Future(true))
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(liveNode))
    (graphDB.createRelation(_: String, _: java.util.List[java.util.Map[String, AnyRef]])).expects(*, *).onCall((_: String, rels: java.util.List[java.util.Map[String, AnyRef]]) => {
      assert(rels.size() == 1)
      assert(rels.get(0).get("startNodeId") == "fw1")
      assert(rels.get(0).get("endNodeId") == "catB")
      Future(new Response())
    })
    // graphDB.removeRelation is intentionally left un-stubbed: ScalaMock fails the test if it's called.

    Await.result(FrameworkManager.publishFramework(publishRequest(), "fw1"), 10.seconds)
  }

  it should "removeRelation for a category removed on .img" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val liveNode = buildFrameworkNode("fw1")
    liveNode.setOutRelations(util.Arrays.asList(categoryRelation("catA"), categoryRelation("catB")))
    val imgNode = buildFrameworkNode("fw1.img", "FrameworkImage")
    imgNode.setOutRelations(util.Arrays.asList(categoryRelation("catA")))
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1.img", *, *).returns(Future(imgNode)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1", *, *).returns(Future(liveNode)).anyNumberOfTimes()
    (graphDB.deleteNode(_: String, _: String, _: Request)).expects(*, "fw1.img", *).returns(Future(true))
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(liveNode))
    (graphDB.removeRelation(_: String, _: java.util.List[java.util.Map[String, AnyRef]])).expects(*, *).onCall((_: String, rels: java.util.List[java.util.Map[String, AnyRef]]) => {
      assert(rels.size() == 1)
      assert(rels.get(0).get("endNodeId") == "catB")
      Future(new Response())
    })
    // graphDB.createRelation is intentionally left un-stubbed: ScalaMock fails the test if it's called.

    Await.result(FrameworkManager.publishFramework(publishRequest(), "fw1"), 10.seconds)
  }

  it should "make no relation calls when .img carries zero categories/channels edges (renamed-only edit, idempotent)" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val liveNode = buildFrameworkNode("fw1")
    liveNode.setOutRelations(util.Arrays.asList(categoryRelation("catA")))
    val imgNode = buildFrameworkNode("fw1.img", "FrameworkImage") // no relations at all -- never touched this session
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1.img", *, *).returns(Future(imgNode)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1", *, *).returns(Future(liveNode)).anyNumberOfTimes()
    (graphDB.deleteNode(_: String, _: String, _: Request)).expects(*, "fw1.img", *).returns(Future(true))
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(liveNode))
    // Neither graphDB.createRelation nor graphDB.removeRelation is stubbed: ScalaMock fails if either is called.

    Await.result(FrameworkManager.publishFramework(publishRequest(), "fw1"), 10.seconds)
  }

  it should "diff the channels relation on the 'in' side (channel -> framework edge)" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val liveNode = buildFrameworkNode("fw1")
    liveNode.setInRelations(util.Arrays.asList(channelRelation("channelX")))
    val imgNode = buildFrameworkNode("fw1.img", "FrameworkImage")
    imgNode.setInRelations(util.Arrays.asList(channelRelation("channelY")))
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1.img", *, *).returns(Future(imgNode)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1", *, *).returns(Future(liveNode)).anyNumberOfTimes()
    (graphDB.deleteNode(_: String, _: String, _: Request)).expects(*, "fw1.img", *).returns(Future(true))
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(liveNode))
    (graphDB.createRelation(_: String, _: java.util.List[java.util.Map[String, AnyRef]])).expects(*, *).onCall((_: String, rels: java.util.List[java.util.Map[String, AnyRef]]) => {
      assert(rels.get(0).get("startNodeId") == "channelY")
      assert(rels.get(0).get("endNodeId") == "fw1")
      Future(new Response())
    })
    (graphDB.removeRelation(_: String, _: java.util.List[java.util.Map[String, AnyRef]])).expects(*, *).onCall((_: String, rels: java.util.List[java.util.Map[String, AnyRef]]) => {
      assert(rels.get(0).get("startNodeId") == "channelX")
      assert(rels.get(0).get("endNodeId") == "fw1")
      Future(new Response())
    })

    Await.result(FrameworkManager.publishFramework(publishRequest(), "fw1"), 10.seconds)
  }

  "FrameworkManager.getCompleteMetadata" should "exclude a Retired child from childHierarchy, keeping active children" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]

    val fw = new Node()
    fw.setIdentifier("fw1")
    fw.setGraphId("domain")
    fw.setObjectType("Framework")
    fw.setMetadata(new util.HashMap[String, AnyRef]() { { put("code", "fw1"); put("name", "fw1"); put("channel", "all") } })

    val cat = new Node()
    cat.setIdentifier("cat1")
    cat.setGraphId("domain")
    cat.setObjectType("CategoryInstance")
    cat.setMetadata(new util.HashMap[String, AnyRef]() { { put("code", "cat1"); put("name", "cat1"); put("status", "Live") } })

    val retiredTerm = new Node()
    retiredTerm.setIdentifier("term_retired")
    retiredTerm.setGraphId("domain")
    retiredTerm.setObjectType("Term")
    retiredTerm.setMetadata(new util.HashMap[String, AnyRef]() { { put("code", "term_retired"); put("name", "term_retired"); put("status", "Retired") } })

    val draftTerm = new Node()
    draftTerm.setIdentifier("term_draft")
    draftTerm.setGraphId("domain")
    draftTerm.setObjectType("Term")
    draftTerm.setMetadata(new util.HashMap[String, AnyRef]() { { put("code", "term_draft"); put("name", "term_draft"); put("status", "Draft") } })

    val fwToCat = new Relation("fw1", "hasSequenceMember", "cat1")
    fwToCat.setStartNodeObjectType("Framework"); fwToCat.setEndNodeObjectType("CategoryInstance")
    val catToRetired = new Relation("cat1", "hasSequenceMember", "term_retired")
    catToRetired.setStartNodeObjectType("CategoryInstance"); catToRetired.setEndNodeObjectType("Term")
    val catToDraft = new Relation("cat1", "hasSequenceMember", "term_draft")
    catToDraft.setStartNodeObjectType("CategoryInstance"); catToDraft.setEndNodeObjectType("Term")

    val nodeMap: util.Map[String, Node] = new util.HashMap[String, Node]() {
      { put("fw1", fw); put("cat1", cat); put("term_retired", retiredTerm); put("term_draft", draftTerm) }
    }
    val relations: util.List[Relation] = util.Arrays.asList(fwToCat, catToRetired, catToDraft)
    val subGraph = new SubGraph(nodeMap, relations)

    val result = FrameworkManager.getCompleteMetadata("fw1", subGraph, true)
    val categories = result.getOrDefault("categories", new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    assert(categories.size() == 1)
    val terms = categories.get(0).getOrDefault("terms", new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    val termIds = terms.map(_.get("identifier")).toSet
    assert(termIds.contains("term_draft"))
    assert(!termIds.contains("term_retired"))
  }

  "FrameworkManager.getLiveEditNode" should "return the .img node when present" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val imgNode = buildFrameworkNode("fw1.img", "FrameworkImage")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1.img", *, *).returns(Future(imgNode))
    val result = Await.result(FrameworkManager.getLiveEditNode("domain", "fw1"), 10.seconds)
    assert("fw1.img".equals(result.getIdentifier))
  }

  it should "fall back to the base node when .img is absent" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1.img", *, *).returns(notFoundFailure())
    val liveNode = buildFrameworkNode("fw1")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1", *, *).returns(Future(liveNode))
    val result = Await.result(FrameworkManager.getLiveEditNode("domain", "fw1"), 10.seconds)
    assert("fw1".equals(result.getIdentifier))
  }

  "FrameworkManager.assertFrameworkEditable" should "throw ERR_FRAMEWORK_REVIEW_IN_PROGRESS naming the status when the live-edit node is Review or Processing" in {
    List("Review", "Processing").foreach { status =>
      implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
      val graphDB = mock[GraphService]
      (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
      (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1.img", *, *).returns(notFoundFailure())
      val liveNode = buildFrameworkNode("fw1")
      liveNode.getMetadata.put("status", status)
      (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1", *, *).returns(Future(liveNode))
      val thrown = intercept[ClientException] {
        Await.result(FrameworkManager.assertFrameworkEditable("domain", "fw1"), 10.seconds)
      }
      assert("ERR_FRAMEWORK_REVIEW_IN_PROGRESS".equals(thrown.getErrCode))
      assert(thrown.getMessage.contains(status))
    }
  }

  it should "pass (no exception) when the live-edit node is Draft, Live, or Retired" in {
    List("Draft", "Live", "Retired").foreach { status =>
      implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
      val graphDB = mock[GraphService]
      (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
      (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1.img", *, *).returns(notFoundFailure())
      val liveNode = buildFrameworkNode("fw1")
      liveNode.getMetadata.put("status", status)
      (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1", *, *).returns(Future(liveNode))
      Await.result(FrameworkManager.assertFrameworkEditable("domain", "fw1"), 10.seconds) // no exception
    }
  }

  "FrameworkManager.rejectFrameworkTermsSweep" should "sweep exactly status==Review Term/CategoryInstance nodes under this framework to Draft, never touching Draft-status terms or another framework's nodes" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    def statusNode(id: String, status: String, objectType: String = "Term"): Node = {
      val n = new Node()
      n.setIdentifier(id)
      n.setObjectType(objectType)
      n.setMetadata(new util.HashMap[String, AnyRef]() { { put("status", status) } })
      n
    }
    val reviewTerm = statusNode("fw1_term_review", "Review")
    val reviewCategoryInstance = statusNode("fw1_category_review", "Review", "CategoryInstance")
    val otherFwReview = statusNode("otherfw_term_review", "Review")
    val nodes: util.List[Node] = util.Arrays.asList(reviewTerm, reviewCategoryInstance, otherFwReview)
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes))

    var capturedIds: util.List[String] = null
    var capturedMetadata: util.Map[String, AnyRef] = null
    (graphDB.updateNodes(_: String, _: java.util.List[String], _: java.util.Map[String, AnyRef])).expects(*, *, *).onCall((_: String, ids: java.util.List[String], metadata: java.util.Map[String, AnyRef]) => {
      capturedIds = ids
      capturedMetadata = metadata
      Future(new util.HashMap[String, Node]())
    })

    Await.result(FrameworkManager.rejectFrameworkTermsSweep("domain", "fw1"), 10.seconds)
    assert(capturedIds.size() == 2)
    assert(capturedIds.contains("fw1_term_review"))
    assert(capturedIds.contains("fw1_category_review"))
    assert(!capturedIds.contains("otherfw_term_review"))
    assert("Draft".equals(capturedMetadata.get("status")))
  }

  it should "make no bulkUpdate call when nothing under this framework is in Review" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]()))
    // graphDB.updateNodes is intentionally left un-stubbed: ScalaMock fails the test if it's called.

    val result = Await.result(FrameworkManager.rejectFrameworkTermsSweep("domain", "fw1"), 10.seconds)
    assert(result.isEmpty)
  }

  }