package org.sunbird.managers

import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ResponseCode}
import org.sunbird.graph.common.enums.GraphDACParams
import org.sunbird.graph.dac.model.{Node, Relation, SearchCriteria}
import org.sunbird.graph.service.common.DACErrorCodeConstants
import org.sunbird.graph.{GraphService, OntologyEngineContext}

import java.util
import java.util.concurrent.CompletionException
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._

class TermBulkManagerTest extends FlatSpec with Matchers with MockFactory {

  "TermBulkManager.bulkCreateTerm" should "aggregate per-row results with PARTIAL_SUCCESS, translating a duplicate-code collision to ERR_DUPLICATE_CODE and a blank code (real schema validation failure) to ERR_TERM_CODE_REQUIRED" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "ncf_board", *, *).returns(Future(categoryInstanceNode())).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    (graphDB.checkCyclicLoop _).expects(*, *, *, *).returns(noLoop()).anyNumberOfTimes()
    (graphDB.createRelation(_: String, _: java.util.List[java.util.Map[String, AnyRef]])).expects(*, *).returns(Future(new Response())).anyNumberOfTimes()

    // Row 2 (cm2) never reaches addNode with a blank code -- schema validation rejects it
    // first (see DataNode.create -> DefinitionNode.validate), so only cm1/cm2 need stubbing.
    (graphDB.addNode(_: String, _: Node)).expects(*, *).onCall((_: String, n: Node) => n.getIdentifier match {
      case "ncf_board_cm1" => Future(termNode("ncf_board_cm1", "cm1"))
      case "ncf_board_cm2" => Future.failed(new ClientException(DACErrorCodeConstants.CONSTRAINT_VALIDATION_FAILED.name(), "Node with this identifier already exists"))
      case other => Future.failed(new ClientException("ERR_UNEXPECTED_ADD_NODE_CALL", s"unexpected addNode call for $other"))
    }).anyNumberOfTimes()

    val terms = new util.ArrayList[util.Map[String, AnyRef]]()
    terms.add(termRow("cm1", "Competency1"))
    terms.add(termRow("cm2", "Competency2"))
    terms.add(termRow("", "Competency3")) // blank code -> real DataNode.create schema validation failure, not a stubbed addNode error

    val response = Await.result(TermBulkManager.bulkCreateTerm(bulkCreateRequest("NCF", "board", terms)), 10.seconds)

    assert(response.getResponseCode == ResponseCode.PARTIAL_SUCCESS)
    val results = response.getResult.get("results").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    assert(results.get(0).get("index") == 0)
    assert(results.get(0).get("code") == "cm1")
    assert(results.get(0).get("identifier") == "ncf_board_cm1")
    assert(results.get(0).get("status") == "SUCCESS")
    assert(results.get(1).get("status") == "FAILED")
    assert(results.get(1).get("errCode") == "ERR_DUPLICATE_CODE")
    assert(results.get(2).get("status") == "FAILED")
    assert(results.get(2).get("errCode") == "ERR_TERM_CODE_REQUIRED")
  }

  it should "return OK with an all-success results array when every row succeeds" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "ncf_board", *, *).returns(Future(categoryInstanceNode())).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    (graphDB.checkCyclicLoop _).expects(*, *, *, *).returns(noLoop()).anyNumberOfTimes()
    (graphDB.createRelation(_: String, _: java.util.List[java.util.Map[String, AnyRef]])).expects(*, *).returns(Future(new Response())).anyNumberOfTimes()
    (graphDB.addNode(_: String, _: Node)).expects(*, *).onCall((_: String, n: Node) => Future(termNode(n.getIdentifier, "cm1"))).anyNumberOfTimes()

    val terms = new util.ArrayList[util.Map[String, AnyRef]]()
    terms.add(termRow("cm1", "Competency1"))

    val response = Await.result(TermBulkManager.bulkCreateTerm(bulkCreateRequest("NCF", "board", terms)), 10.seconds)
    assert(response.getResponseCode == ResponseCode.OK)
  }

  "TermBulkManager.bulkUpdateTerm" should "aggregate per-row results, reporting RESOURCE_NOT_FOUND for an identifier that doesn't resolve" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    val validNode = termNode("ncf_board_cm1", "cm1")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "ncf_board_cm1", *, *).returns(Future(validNode)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "missing_term", *, *)
      .returns(Future.failed(new CompletionException(new ResourceNotFoundException("ERR_TERM_NOT_FOUND", "Term not found"))))
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(validNode)).anyNumberOfTimes()

    val rows = new util.ArrayList[util.Map[String, AnyRef]]()
    rows.add(updateRow("ncf_board_cm1", "description" -> "updated"))
    rows.add(updateRow("missing_term", "description" -> "x"))

    val response = Await.result(TermBulkManager.bulkUpdateTerm(bulkUpdateRequest(rows)), 10.seconds)
    assert(response.getResponseCode == ResponseCode.PARTIAL_SUCCESS)
    val results = response.getResult.get("results").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    assert(results.get(0).get("identifier") == "ncf_board_cm1")
    assert(results.get(0).get("status") == "SUCCESS")
    assert(results.get(1).get("identifier") == "missing_term")
    assert(results.get(1).get("status") == "FAILED")
    assert(results.get(1).get("errCode") == "RESOURCE_NOT_FOUND")
  }

  it should "wholesale-replace a row's associations (removeRelation for edges dropped from the row's list) and let status=Retired ride through" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    val dbNode = termNode("ncf_board_cm1", "cm1")
    dbNode.setOutRelations(util.Arrays.asList(associationRelation("ncf_board_cm1", "ncf_board_sk1"), associationRelation("ncf_board_cm1", "ncf_board_sk2")))
    val retireNode = termNode("ncf_board_sk3", "sk3")

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "ncf_board_cm1", *, *).returns(Future(dbNode)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "ncf_board_sk3", *, *).returns(Future(retireNode)).anyNumberOfTimes()
    // RelationValidator resolves each newly-added association's target by id before wiring it.
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "ncf_board_sk1", *, *).returns(Future(termNode("ncf_board_sk1", "sk1"))).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()

    val submitted = mutable.Map[String, util.Map[String, Object]]()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).onCall((_: String, n: Node, _: Request) => {
      submitted.put(n.getIdentifier, n.getMetadata)
      Future(n)
    }).anyNumberOfTimes()

    (graphDB.createRelation(_: String, _: java.util.List[java.util.Map[String, AnyRef]])).expects(*, *).onCall((_: String, rels: java.util.List[java.util.Map[String, AnyRef]]) => {
      assert(rels.asScala.exists(_.get("endNodeId") == "ncf_board_sk1"))
      Future(new Response())
    })
    (graphDB.removeRelation(_: String, _: java.util.List[java.util.Map[String, AnyRef]])).expects(*, *).onCall((_: String, rels: java.util.List[java.util.Map[String, AnyRef]]) => {
      assert(rels.size() == 1)
      assert(rels.get(0).get("endNodeId") == "ncf_board_sk2")
      Future(new Response())
    })

    val assoc = new util.ArrayList[util.Map[String, AnyRef]]()
    assoc.add(new util.HashMap[String, AnyRef]() { { put("identifier", "ncf_board_sk1") } })
    val rows = new util.ArrayList[util.Map[String, AnyRef]]()
    rows.add(updateRow("ncf_board_cm1", "associations" -> assoc))
    rows.add(updateRow("ncf_board_sk3", "status" -> "Retired"))

    val response = Await.result(TermBulkManager.bulkUpdateTerm(bulkUpdateRequest(rows)), 10.seconds)
    assert(response.getResponseCode == ResponseCode.OK)
    assert("Retired".equals(submitted("ncf_board_sk3").get("status")))
  }

  "TermBulkManager.associateTerms" should "wire mutually-referencing rows and a row referencing a pre-existing term, regardless of array order" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "ncf_board_cm1", *, *).returns(Future(termNode("ncf_board_cm1", "cm1"))).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "ncf_board_cm2", *, *).returns(Future(termNode("ncf_board_cm2", "cm2"))).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "ncf_board_cm3", *, *).returns(Future(termNode("ncf_board_cm3", "cm3"))).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).onCall((_: String, n: Node, _: Request) => Future(n)).anyNumberOfTimes()

    val createdPairs = mutable.Set[(String, String)]()
    (graphDB.createRelation(_: String, _: java.util.List[java.util.Map[String, AnyRef]])).expects(*, *).onCall((_: String, rels: java.util.List[java.util.Map[String, AnyRef]]) => {
      rels.asScala.foreach(r => createdPairs.add((r.get("startNodeId").asInstanceOf[String], r.get("endNodeId").asInstanceOf[String])))
      Future(new Response())
    }).anyNumberOfTimes()

    def associationRow(id: String, targets: String*): util.Map[String, AnyRef] = {
      val assoc = new util.ArrayList[util.Map[String, AnyRef]]()
      targets.foreach(t => assoc.add(new util.HashMap[String, AnyRef]() { { put("identifier", t) } }))
      updateRow(id, "associations" -> assoc)
    }

    val rows = new util.ArrayList[util.Map[String, AnyRef]]()
    rows.add(associationRow("ncf_board_cm1", "ncf_board_cm2"))
    rows.add(associationRow("ncf_board_cm2", "ncf_board_cm1"))
    rows.add(associationRow("ncf_board_cm3", "ncf_board_cm1"))

    val results = Await.result(TermBulkManager.associateTerms(rows), 10.seconds)
    assert(results.asScala.forall(_.get("status") == "SUCCESS"))
    assert(createdPairs.contains(("ncf_board_cm1", "ncf_board_cm2")))
    assert(createdPairs.contains(("ncf_board_cm2", "ncf_board_cm1")))
    assert(createdPairs.contains(("ncf_board_cm3", "ncf_board_cm1")))

    // Same wiring, rows submitted in reverse order -- each row's write is independent of the
    // others' completion order, so the outcome must be identical.
    val reversed = new util.ArrayList[util.Map[String, AnyRef]](rows)
    java.util.Collections.reverse(reversed)
    val reversedResults = Await.result(TermBulkManager.associateTerms(reversed), 10.seconds)
    assert(reversedResults.asScala.forall(_.get("status") == "SUCCESS"))
    assert(createdPairs.contains(("ncf_board_cm1", "ncf_board_cm2")))
    assert(createdPairs.contains(("ncf_board_cm2", "ncf_board_cm1")))
  }

  private def noLoop(): util.Map[String, Object] = {
    val loopResult = new util.HashMap[String, Object]()
    loopResult.put(GraphDACParams.loop.name, new java.lang.Boolean(false))
    loopResult
  }

  private def context(): util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {
    {
      put("graph_id", "domain")
      put("version", "1.0")
      put("objectType", "Term")
      put("schemaName", "term")
    }
  }

  private def bulkCreateRequest(framework: String, category: String, terms: util.List[util.Map[String, AnyRef]]): Request = {
    val request = new Request()
    request.setContext(context())
    request.setObjectType("Term")
    request.put("framework", framework)
    request.put("category", category)
    request.put("terms", terms)
    request
  }

  private def bulkUpdateRequest(terms: util.List[util.Map[String, AnyRef]]): Request = {
    val request = new Request()
    request.setContext(context())
    request.setObjectType("Term")
    request.put("terms", terms)
    request
  }

  private def termRow(code: String, name: String): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    m.put("code", code)
    m.put("name", name)
    m
  }

  private def updateRow(identifier: String, fields: (String, AnyRef)*): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    m.put("identifier", identifier)
    fields.foreach { case (k, v) => m.put(k, v) }
    m
  }

  private def categoryInstanceNode(): Node = {
    val node = new Node()
    node.setIdentifier("ncf_board")
    node.setObjectType("CategoryInstance")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "ncf_board")
        put("objectType", "CategoryInstance")
        put("name", "ncf_board")
      }
    })
    node
  }

  private def termNode(identifier: String, code: String): Node = {
    val node = new Node()
    node.setIdentifier(identifier)
    node.setNodeType("DATA_NODE")
    node.setObjectType("Term")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", identifier)
        put("objectType", "Term")
        put("code", code)
        put("name", code)
        put("category", "board")
        put("versionKey", "12345")
      }
    })
    node
  }

  private def associationRelation(start: String, end: String): Relation = {
    val r = new Relation(start, "associatedTo", end)
    r.setStartNodeObjectType("Term")
    r.setEndNodeObjectType("Term")
    r
  }
}
