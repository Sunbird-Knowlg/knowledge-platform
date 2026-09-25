package org.sunbird.managers

import org.apache.commons.csv.{CSVFormat, CSVParser, CSVPrinter, CSVRecord}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ResponseCode}
import org.sunbird.graph.common.enums.{GraphDACParams, SystemProperties}
import org.sunbird.graph.dac.model.{Node, Relation, SearchConditions, SearchCriteria}
import org.sunbird.graph.service.common.DACErrorCodeConstants
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.utils.taxonomy.TaxonomyUtil

import java.io.{File, FileOutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets
import java.util
import java.util.concurrent.CompletionException
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._

class TermBulkManagerTest extends FlatSpec with Matchers with MockFactory {

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

  private def bulkUpdateRequest(terms: util.List[util.Map[String, AnyRef]]): Request = {
    val request = new Request()
    request.setContext(context())
    request.setObjectType("Term")
    request.put("terms", terms)
    request
  }

  private def updateRow(identifier: String, fields: (String, AnyRef)*): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    m.put("identifier", identifier)
    fields.foreach { case (k, v) => m.put(k, v) }
    m
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

  // ===================================================================================
  // classifyAndValidate -- pure, no mocks
  // ===================================================================================

  private def sheetRow(idx: Int, category: String, name: String, code: String,
                        assoc: List[String] = Nil, description: String = "",
                        rowErrors: List[TermSheetReader.RowIssue] = Nil,
                        rowWarnings: List[TermSheetReader.RowIssue] = Nil): TermSheetReader.SheetRow =
    TermSheetReader.SheetRow(idx, category, name, code, description, assoc, rowErrors, rowWarnings)

  private def activeTerm(id: String, category: String, code: String, name: String, status: String = "Live", description: String = ""): TermBulkManager.ActiveTerm =
    TermBulkManager.ActiveTerm(id, category, code, name, description, status)

  "TermBulkManager.classifyAndValidate" should "classify a matched row as update, an unmatched row as create, and an unmentioned active term as retire" in {
    val active = List(activeTerm("fw1_competency_cm1", "competency", "cm1", "Old CM1"), activeTerm("fw1_competency_cm2", "competency", "cm2", "CM2"))
    val rows = List(sheetRow(0, "competency", "New CM1", "cm1"), sheetRow(1, "competency", "CM3", "cm3"))
    val result = TermBulkManager.classifyAndValidate("fw1", rows, active, Set("competency"))
    result.valid shouldBe true
    result.creates.map(_._1.code) shouldBe List("cm3")
    result.updates.map(_._2.code) shouldBe List("cm1")
    result.retireIdentifiers shouldBe List("fw1_competency_cm2")
    result.summary("toCreate") shouldBe 1
    result.summary("toUpdate") shouldBe 1
    result.summary("toRetire") shouldBe 1
  }

  it should "flag ERR_UNKNOWN_CATEGORY for a row whose category isn't attached to the framework, and never invent a create/update for it" in {
    val result = TermBulkManager.classifyAndValidate("fw1", List(sheetRow(0, "unknown", "X", "x1")), Nil, Set("competency"))
    result.valid shouldBe false
    result.rows.head.errCode shouldBe Some("ERR_UNKNOWN_CATEGORY")
    result.creates shouldBe empty
  }

  it should "match category/code case-insensitively even under a Turkish default locale (Locale.ROOT folding, never default-locale toLowerCase)" in {
    // Turkish case-folding maps 'I' -> dotless 'ı', not 'i' -- a bare toLowerCase() here would make
    // "SKILL" fail to match the attached "skill" category on a JVM running under this locale.
    val originalLocale = java.util.Locale.getDefault
    java.util.Locale.setDefault(new java.util.Locale("tr", "TR"))
    try {
      val result = TermBulkManager.classifyAndValidate("fw1", List(sheetRow(0, "SKILL", "X", "sk1")), Nil, Set("skill"))
      result.rows.head.errCode shouldBe None
    } finally {
      java.util.Locale.setDefault(originalLocale)
    }
  }

  it should "flag ERR_TERM_CODE_REQUIRED for a row with a blank code" in {
    val result = TermBulkManager.classifyAndValidate("fw1", List(sheetRow(0, "competency", "X", "")), Nil, Set("competency"))
    result.rows.head.errCode shouldBe Some("ERR_TERM_CODE_REQUIRED")
  }

  it should "flag ERR_DUPLICATE_CODE on the second occurrence of an in-sheet (category,code) duplicate, leaving the first clean" in {
    val rows = List(sheetRow(0, "competency", "CM1", "cm1"), sheetRow(1, "competency", "CM1 dup", "cm1"))
    val result = TermBulkManager.classifyAndValidate("fw1", rows, Nil, Set("competency"))
    result.rows(0).errCode shouldBe None
    result.rows(1).errCode shouldBe Some("ERR_DUPLICATE_CODE")
  }

  it should "flag ERR_DANGLING_ASSOCIATION when a token names a (category,code) absent from both sheet and framework" in {
    val rows = List(sheetRow(0, "competency", "CM1", "cm1", assoc = List("skill:doesnotexist")))
    val result = TermBulkManager.classifyAndValidate("fw1", rows, Nil, Set("competency", "skill"))
    result.valid shouldBe false
    result.rows.head.errCode shouldBe Some("ERR_DANGLING_ASSOCIATION")
  }

  it should "carry forward a TermSheetReader-level row error (e.g. malformed association) as this row's errCode" in {
    val rows = List(sheetRow(0, "competency", "CM1", "cm1", rowErrors = List(TermSheetReader.RowIssue("ERR_MALFORMED_ASSOCIATION", "bad token"))))
    val result = TermBulkManager.classifyAndValidate("fw1", rows, Nil, Set("competency"))
    result.rows.head.errCode shouldBe Some("ERR_MALFORMED_ASSOCIATION")
  }

  it should "resolve a forward reference to a sibling create row using its deterministic identifier, no second pass needed" in {
    val rows = List(
      sheetRow(0, "competency", "CM1", "cm1", assoc = List("competency:cm2")),
      sheetRow(1, "competency", "CM2", "cm2")
    )
    val result = TermBulkManager.classifyAndValidate("fw1", rows, Nil, Set("competency"))
    result.valid shouldBe true
    val cm2Identifier = TaxonomyUtil.generateIdentifier(TaxonomyUtil.generateIdentifier("fw1", "competency"), "cm2")
    result.creates.find(_._1.code == "cm1").get._2 shouldBe List(cm2Identifier)
  }

  it should "warn WARN_ORPHAN_TERM for a create row with no associations that no other row references" in {
    val result = TermBulkManager.classifyAndValidate("fw1", List(sheetRow(0, "competency", "CM3", "cm3")), Nil, Set("competency"))
    result.rows.head.warnings.map(_.code) should contain("WARN_ORPHAN_TERM")
  }

  it should "flag ERR_DANGLING_ASSOCIATION when a row still references a term being retired by omission in this same commit" in {
    // Mirrors csv-scenarios.md's "retire a hub row that has its own children": CA1 stays, but still
    // lists a hub (CM1/here cm2) that this same commit retires by omission -- that must be a hard
    // block, not a silent resolve or a mere warning.
    val active = List(activeTerm("fw1_competency_cm1", "competency", "cm1", "CM1"), activeTerm("fw1_competency_cm2", "competency", "cm2", "CM2"))
    // cm2 isn't mentioned anywhere in the sheet -> retired by omission; cm1 still references it.
    val rows = List(sheetRow(0, "competency", "CM1", "cm1", assoc = List("competency:cm2")))
    val result = TermBulkManager.classifyAndValidate("fw1", rows, active, Set("competency"))
    result.retireIdentifiers should contain("fw1_competency_cm2")
    result.valid shouldBe false
    result.rows.head.errCode shouldBe Some("ERR_DANGLING_ASSOCIATION")
  }

  it should "warn WARN_SECOND_ORDER_ORPHAN (cross-reference case) when a sheet row's ONLY incoming reference came from a hub that is retired by omission -- the row itself carries no association of its own" in {
    val t = TermBulkManager.ActiveTerm("fw1_competency_t", "competency", "t", "T", "", "Live")
    // h references t but is never mentioned in the sheet -> retired by omission, so t's only incoming reference disappears.
    val h = TermBulkManager.ActiveTerm("fw1_competency_h", "competency", "h", "H", "", "Live", associations = List("fw1_competency_t"))
    val rows = List(sheetRow(0, "competency", "T", "t")) // unchanged, no association of its own
    val result = TermBulkManager.classifyAndValidate("fw1", rows, List(t, h), Set("competency"))
    result.retireIdentifiers should contain("fw1_competency_h")
    result.rows.head.warnings.map(_.code) should contain("WARN_SECOND_ORDER_ORPHAN")
  }

  it should "NOT warn WARN_SECOND_ORDER_ORPHAN when a retired hub's target still has another live referrer" in {
    val t = TermBulkManager.ActiveTerm("fw1_competency_t", "competency", "t", "T", "", "Live")
    val h = TermBulkManager.ActiveTerm("fw1_competency_h", "competency", "h", "H", "", "Live", associations = List("fw1_competency_t"))
    // A second, sheet-mentioned row also references t -- t keeps an incoming reference even after h retires.
    val rows = List(sheetRow(0, "competency", "T", "t"), sheetRow(1, "competency", "OTHER", "o", assoc = List("competency:t")))
    val result = TermBulkManager.classifyAndValidate("fw1", rows, List(t, h), Set("competency"))
    result.retireIdentifiers should contain("fw1_competency_h")
    result.rows.find(_.code == "t").get.warnings.map(_.code) should not contain "WARN_SECOND_ORDER_ORPHAN"
  }

  it should "warn WARN_POSSIBLE_UNINTENDED_CODE_CHANGE when a retiring term and a same-category create row share an exact name" in {
    val active = List(activeTerm("fw1_competency_cm1", "competency", "cm1", "Infection Control"))
    val rows = List(sheetRow(0, "competency", "Infection Control", "cm1b")) // new code, same name+category as the retiring term
    val result = TermBulkManager.classifyAndValidate("fw1", rows, active, Set("competency"))
    result.retireIdentifiers shouldBe List("fw1_competency_cm1")
    result.rows.head.warnings.map(_.code) should contain("WARN_POSSIBLE_UNINTENDED_CODE_CHANGE")
  }

  it should "warn WARN_POSSIBLE_UNINTENDED_CODE_CHANGE for a name+description match even when the create row's category differs from the retiring term's category" in {
    val active = List(activeTerm("fw1_competency_cm1", "competency", "cm1", "Infection Control"))
    val rows = List(sheetRow(0, "skill", "Infection Control", "sk1")) // different category, same name as the retiring term
    val result = TermBulkManager.classifyAndValidate("fw1", rows, active, Set("competency", "skill"))
    result.retireIdentifiers shouldBe List("fw1_competency_cm1")
    result.rows.head.warnings.map(_.code) should contain("WARN_POSSIBLE_UNINTENDED_CODE_CHANGE")
  }

  it should "report a real added/removed association diff for an update row, not the row's whole resolved list" in {
    val active = List(
      TermBulkManager.ActiveTerm("fw1_competency_cm1", "competency", "cm1", "CM1", "", "Live", associations = List("fw1_skill_sk1")),
      TermBulkManager.ActiveTerm("fw1_skill_sk1", "skill", "sk1", "SK1", "", "Live"),
      TermBulkManager.ActiveTerm("fw1_skill_sk2", "skill", "sk2", "SK2", "", "Live")
    )
    // Row drops sk1 (still has its own row, just no longer referenced) and picks up sk2 -- a real
    // diff, not the full resolved list. sk1/sk2 each need their own sheet row too, or they'd be
    // classified as retired-by-omission (no row of their own) and the reference to sk2 would then
    // be a dangling reference to a term this same commit is retiring.
    val rows = List(
      sheetRow(0, "competency", "CM1", "cm1", assoc = List("skill:sk2")),
      sheetRow(1, "skill", "SK1", "sk1"),
      sheetRow(2, "skill", "SK2", "sk2")
    )
    val result = TermBulkManager.classifyAndValidate("fw1", rows, active, Set("competency", "skill"))
    result.valid shouldBe true
    val update = result.planUpdates.find(u => u.category == "competency" && u.code == "cm1").get
    update.changes.associationsAdded shouldBe List("skill:sk2")
    update.changes.associationsRemoved shouldBe List("skill:sk1")
  }

  it should "report no added/removed associations when a matched row's associations are unchanged from what's already live" in {
    val active = List(
      TermBulkManager.ActiveTerm("fw1_competency_cm1", "competency", "cm1", "CM1", "", "Live", associations = List("fw1_skill_sk1")),
      TermBulkManager.ActiveTerm("fw1_skill_sk1", "skill", "sk1", "SK1", "", "Live")
    )
    // sk1 needs its own row too, or it would be retired-by-omission and cm1's reference to it
    // would become a dangling reference instead of an unchanged association.
    val rows = List(
      sheetRow(0, "competency", "CM1", "cm1", assoc = List("skill:sk1")),
      sheetRow(1, "skill", "SK1", "sk1")
    )
    val result = TermBulkManager.classifyAndValidate("fw1", rows, active, Set("competency", "skill"))
    val update = result.planUpdates.find(u => u.category == "competency" && u.code == "cm1").get
    update.changes.associationsAdded shouldBe empty
    update.changes.associationsRemoved shouldBe empty
  }

  it should "count a Review-status match under toUpdateDraft, not toUpdateLive, so toUpdateLive+toUpdateDraft never undercounts toUpdate" in {
    val active = List(activeTerm("fw1_competency_cm1", "competency", "cm1", "CM1", status = "Review"))
    val rows = List(sheetRow(0, "competency", "CM1 edited", "cm1"))
    val result = TermBulkManager.classifyAndValidate("fw1", rows, active, Set("competency"))
    result.summary("toUpdate") shouldBe 1
    result.summary("toUpdateLive") shouldBe 0
    result.summary("toUpdateDraft") shouldBe 1
  }

  it should "surface a mid-sheet duplicate header row as a file-level WARN_DUPLICATE_HEADER_ROW, counted in summary.warnings, not silently dropped" in {
    // An update row (matched active term, no association changes) so the row itself carries no
    // warning of its own -- isolates the header-row warning's count/visibility.
    val active = List(activeTerm("fw1_competency_cm1", "competency", "cm1", "CM1"))
    val rows = List(sheetRow(0, "competency", "CM1", "cm1"))
    val result = TermBulkManager.classifyAndValidate("fw1", rows, active, Set("competency"), skippedHeaderRows = List(3))
    result.fileWarnings.map(_.code) shouldBe List("WARN_DUPLICATE_HEADER_ROW")
    result.rows.head.warnings shouldBe empty
    result.summary("warnings") shouldBe 1
  }

  it should "NOT warn WARN_POSSIBLE_UNINTENDED_CODE_CHANGE when only the Name matches but the Description differs" in {
    val active = List(activeTerm("fw1_competency_cm1", "competency", "cm1", "Infection Control", description = "old desc"))
    val rows = List(sheetRow(0, "competency", "Infection Control", "cm1b", description = "a completely different description"))
    val result = TermBulkManager.classifyAndValidate("fw1", rows, active, Set("competency"))
    result.rows.head.warnings.map(_.code) should not contain "WARN_POSSIBLE_UNINTENDED_CODE_CHANGE"
  }

  it should "flag ERR_DUPLICATE_CODE for a create row whose (Category, Code) matches a previously-retired term's key, fed in via fetchRetiredTermKeys" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    // Same mocking pattern as the fetchActiveTerms tests below -- a retired-status Term node scoped to this framework.
    val retired = termNodeWithCategory("fw1_competency_cm1", "competency", "cm1", "Old CM1", "Retired")
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(util.Arrays.asList(retired)))

    val retiredKeys = Await.result(TermBulkManager.fetchRetiredTermKeys("domain", "fw1", Set("competency")), 10.seconds)
    val rows = List(sheetRow(0, "competency", "Brand New Name", "cm1")) // reuses the retired code as a create
    val result = TermBulkManager.classifyAndValidate("fw1", rows, Nil, Set("competency"), retiredKeys)
    result.valid shouldBe false
    result.rows.head.errCode shouldBe Some("ERR_DUPLICATE_CODE")
  }

  // ===================================================================================
  // buildCommitFailureResponse -- unit-testable directly (private[managers]) since it's a pure
  // function of a hand-built ClassificationResult, same testability reasoning as classifyAndValidate.
  // ===================================================================================

  "TermBulkManager.buildCommitFailureResponse" should "drop the clean OK row and return only the FAILED one" in {
    val okRow = TermBulkManager.RowOutcome(0, "update", "competency", "cm1", "OK")
    val failedRow = TermBulkManager.RowOutcome(1, "create", "competency", "cm3", "FAILED", Some("ERR_DANGLING_ASSOCIATION"), Some("bad"))
    val result = TermBulkManager.ClassificationResult(valid = false, Map("errors" -> 1, "warnings" -> 0), Nil, Nil, Nil, List(okRow, failedRow), Nil, Nil, Nil)
    val response = TermBulkManager.buildCommitFailureResponse(result)
    val rows = response.getResult.get("rows").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    rows.size shouldBe 1
    rows.get(0).get("status") shouldBe "FAILED"
  }

  it should "mark an already-written sibling row FAILED/ERR_COMMIT_PARTIAL_WRITE (not retired/rolled back), alongside the row that actually failed to write" in {
    val alreadyWritten = TermBulkManager.RowOutcome(0, "create", "competency", "cm1", "OK")
    val failed = TermBulkManager.RowOutcome(1, "create", "competency", "cm2", "OK")
    val result = TermBulkManager.ClassificationResult(valid = true, Map("errors" -> 0, "warnings" -> 0), Nil, Nil, Nil, List(alreadyWritten, failed), Nil, Nil, Nil)
    val response = TermBulkManager.buildCommitFailureResponse(result, Map(1 -> ("ERR_DUPLICATE_CODE", "dup")), Set(0), "ERR_COMMIT_FAILED", "aborted")
    response.getResponseCode shouldBe ResponseCode.CLIENT_ERROR
    response.getResult.get("committed") shouldBe false
    val rows = response.getResult.get("rows").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    rows.get(0).get("status") shouldBe "FAILED"
    rows.get(0).get("errCode") shouldBe "ERR_COMMIT_PARTIAL_WRITE"
    rows.get(1).get("errCode") shouldBe "ERR_DUPLICATE_CODE"
    response.getResult.get("summary").asInstanceOf[util.Map[String, AnyRef]].get("errors") shouldBe 1
  }

  "TermBulkManager.buildCommitSuccessResponse" should "report every row's status as SUCCESS (per api-reference.md's commit-success shape), not the classify-time OK -- but only for rows carrying a warning" in {
    val updateRow = TermBulkManager.RowOutcome(0, "update", "competency", "cm1", "OK",
      warnings = List(TermBulkManager.RowIssue("WARN_ORPHAN_TERM", "orphan")))
    val createRow = TermBulkManager.RowOutcome(1, "create", "competency", "cm3", "OK",
      warnings = List(TermBulkManager.RowIssue("WARN_ORPHAN_TERM", "orphan")))
    val result = TermBulkManager.ClassificationResult(valid = true, Map("errors" -> 0, "warnings" -> 0), Nil, Nil, Nil,
      List(updateRow, createRow), List((sheetRow(1, "competency", "CM3", "cm3"), Nil)), Nil, Nil)
    val response = TermBulkManager.buildCommitSuccessResponse(result)
    val rows = response.getResult.get("rows").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    rows.get(0).get("status") shouldBe "SUCCESS"
    rows.get(1).get("status") shouldBe "SUCCESS"
    rows.get(1).get("termStatus") shouldBe "Review"
  }

  it should "drop a clean row with no error and no warning entirely" in {
    val cleanRow = TermBulkManager.RowOutcome(0, "update", "competency", "cm1", "OK")
    val result = TermBulkManager.ClassificationResult(valid = true, Map("errors" -> 0, "warnings" -> 0), Nil, Nil, Nil,
      List(cleanRow), Nil, Nil, Nil)
    val response = TermBulkManager.buildCommitSuccessResponse(result)
    val rows = response.getResult.get("rows").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    rows.size shouldBe 0
  }

  // ===================================================================================
  // fetchActiveTerms / hasPendingReview / fetchAttachedCategories -- mocked GraphService
  // ===================================================================================

  "TermBulkManager.fetchActiveTerms" should "return only Term nodes scoped to this framework (identifier-prefix filter), across every requested category in one query" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val inScope = termNodeWithCategory("fw1_competency_cm1", "competency", "cm1", "CM1", "Live")
    val outOfScope = termNodeWithCategory("otherfw_competency_cm9", "competency", "cm9", "CM9", "Live")
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(util.Arrays.asList(inScope, outOfScope)))

    val result = Await.result(TermBulkManager.fetchActiveTerms("domain", "fw1", Set("competency")), 10.seconds)
    result.map(_.identifier) shouldBe List("fw1_competency_cm1")
  }

  it should "make no query and return an empty list when no categories are given" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    // graphDB.getNodeByUniqueIds is intentionally left un-stubbed: this must short-circuit without a query.
    val result = Await.result(TermBulkManager.fetchActiveTerms("domain", "fw1", Set.empty), 10.seconds)
    result shouldBe empty
  }

  "TermBulkManager.hasPendingReview" should "return true when a Term/CategoryInstance under this framework is currently in Review" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val reviewNode = termNodeWithCategory("fw1_competency_cm1", "competency", "cm1", "CM1", "Review")
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(util.Arrays.asList(reviewNode)))
    Await.result(TermBulkManager.hasPendingReview("domain", "fw1"), 10.seconds) shouldBe true
  }

  it should "return false when there is no matching node" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]()))
    Await.result(TermBulkManager.hasPendingReview("domain", "fw1"), 10.seconds) shouldBe false
  }

  "TermBulkManager.fetchAttachedCategories" should "resolve the framework's attached CategoryInstance codes via its live-edit node's hasSequenceMember relations" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    stubFrameworkNode(graphDB, "Draft", List("cat_competency"))
    val catNode = categoryInstanceNodeWithCode("cat_competency", "competency")
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(util.Arrays.asList(catNode)))
    val result = Await.result(TermBulkManager.fetchAttachedCategories("domain", "fw1"), 10.seconds)
    result shouldBe Set("competency")
  }

  it should "preserve a CategoryInstance code's original case (never lowercase it), so downloadTerms' dropdown matches the Category cells it renders" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    stubFrameworkNode(graphDB, "Draft", List("cat_Competency"))
    val catNode = categoryInstanceNodeWithCode("cat_Competency", "Competency")
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(util.Arrays.asList(catNode)))
    val result = Await.result(TermBulkManager.fetchAttachedCategories("domain", "fw1"), 10.seconds)
    result shouldBe Set("Competency")
  }

  it should "return an empty set (never a query) when the framework has no attached categories" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    stubFrameworkNode(graphDB, "Draft", Nil)
    // graphDB.getNodeByUniqueIds is intentionally left un-stubbed: no categories means no query needed.
    val result = Await.result(TermBulkManager.fetchAttachedCategories("domain", "fw1"), 10.seconds)
    result shouldBe empty
  }

  // ===================================================================================
  // bulkValidateTerm / bulkCommitTerm / downloadTerms -- mocked orchestration
  // ===================================================================================

  private def routeSearchCriteria(criteria: SearchCriteria): String = {
    val filters = Option(criteria.getMetadata).map(_.asScala.flatMap(mc => Option(mc.getFilters).map(_.asScala).getOrElse(Nil))).getOrElse(Nil)
    if (filters.exists(_.getProperty == SystemProperties.IL_UNIQUE_ID.name())) "categoryCodes"
    else if (filters.exists(f => f.getProperty == "status" && f.getOperator == SearchConditions.OP_EQUAL)) "pendingReview"
    else "activeTerms"
  }

  private def stubGetNodeByUniqueIds(graphDB: GraphService, categoryInstances: util.List[Node] = new util.ArrayList[Node](),
                                      pendingReview: util.List[Node] = new util.ArrayList[Node](),
                                      activeTerms: util.List[Node] = new util.ArrayList[Node]()): Unit = {
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).onCall((_: String, criteria: SearchCriteria) =>
      routeSearchCriteria(criteria) match {
        case "categoryCodes" => Future(categoryInstances)
        case "pendingReview" => Future(pendingReview)
        case _ => Future(activeTerms)
      }
    ).anyNumberOfTimes()
  }

  private def frameworkNodeWithCategories(identifier: String, status: String, categoryInstanceIds: List[String]): Node = {
    val node = new Node()
    node.setIdentifier(identifier)
    node.setObjectType("Framework")
    node.setMetadata(new util.HashMap[String, AnyRef]() { { put("status", status) } })
    val rels = categoryInstanceIds.map { id =>
      val r = new Relation(identifier, "hasSequenceMember", id)
      r.setStartNodeObjectType("Framework")
      r.setEndNodeObjectType("CategoryInstance")
      r
    }
    node.setOutRelations(rels.asJava)
    node
  }

  private def stubFrameworkNode(graphDB: GraphService, status: String, categoryInstanceIds: List[String]): Unit = {
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1.img", *, *).returns(notFoundFailure()).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1", *, *).returns(Future(frameworkNodeWithCategories("fw1", status, categoryInstanceIds))).anyNumberOfTimes()
  }

  private def categoryInstanceNodeWithCode(identifier: String, code: String): Node = {
    val node = new Node()
    node.setIdentifier(identifier)
    node.setObjectType("CategoryInstance")
    node.setMetadata(new util.HashMap[String, AnyRef]() { { put("code", code) } })
    node
  }

  private def termNodeWithCategory(identifier: String, category: String, code: String, name: String, status: String): Node = {
    val node = new Node()
    node.setIdentifier(identifier)
    node.setObjectType("Term")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("category", category); put("code", code); put("name", name); put("status", status); put("description", "")
      }
    })
    node
  }

  private def notFoundFailure(): Future[Node] = Future.failed(new CompletionException(new ResourceNotFoundException("ERR_NODE_NOT_FOUND", "not found")))

  private def buildCsvFile(rows: List[List[String]]): (File, String) = {
    val file = File.createTempFile("bulkterms", ".csv")
    file.deleteOnExit()
    val fos = new FileOutputStream(file)
    val out = new OutputStreamWriter(fos, StandardCharsets.UTF_8)
    var printer: CSVPrinter = null
    try {
      printer = new CSVPrinter(out, CSVFormat.DEFAULT)
      printer.printRecord(TermSheetReader.REQUIRED_HEADERS.asJava)
      rows.foreach(values => printer.printRecord(values.asJava))
    } finally {
      if (printer != null) printer.close() else out.close()
    }
    (file, "terms.csv")
  }

  private def bulkFileRequest(frameworkId: String, file: File, fileName: String): Request = {
    val request = new Request()
    // Needs the same objectType/schemaName/version context() carries elsewhere in this file --
    // without it, DataNode.create's schema lookup NPEs the moment phase-1 create actually runs
    // (the real controller flow sets this via setRequestContext before dispatch).
    request.setContext(context())
    request.setObjectType("Term")
    request.put("framework", frameworkId)
    request.put("file", file)
    request.put("fileName", fileName)
    request
  }

  "TermBulkManager.bulkValidateTerm" should "reject with ERR_FRAMEWORK_REVIEW_IN_PROGRESS before ever parsing the file, when the framework is under Review" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    stubFrameworkNode(graphDB, "Review", Nil)
    val (file, name) = buildCsvFile(Nil)
    val thrown = intercept[ClientException] {
      Await.result(TermBulkManager.bulkValidateTerm(bulkFileRequest("fw1", file, name)), 10.seconds)
    }
    thrown.getErrCode shouldBe "ERR_FRAMEWORK_REVIEW_IN_PROGRESS"
  }

  it should "also block while the framework is Processing" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    stubFrameworkNode(graphDB, "Processing", Nil)
    val (file, name) = buildCsvFile(Nil)
    val thrown = intercept[ClientException] {
      Await.result(TermBulkManager.bulkValidateTerm(bulkFileRequest("fw1", file, name)), 10.seconds)
    }
    thrown.getErrCode shouldBe "ERR_FRAMEWORK_REVIEW_IN_PROGRESS"
  }

  it should "return OK/valid=true for a clean sheet" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    stubFrameworkNode(graphDB, "Draft", List("cat_competency"))
    val catNode = categoryInstanceNodeWithCode("cat_competency", "competency")
    stubGetNodeByUniqueIds(graphDB, categoryInstances = util.Arrays.asList(catNode))
    val (file, name) = buildCsvFile(List(List("competency", "CM1", "cm1", "", "")))
    val response = Await.result(TermBulkManager.bulkValidateTerm(bulkFileRequest("fw1", file, name)), 10.seconds)
    response.getResponseCode shouldBe ResponseCode.OK
    response.getResult.get("valid") shouldBe true
    file.exists() shouldBe false // the uploaded temp file must not leak past this call
  }

  it should "return CLIENT_ERROR/valid=false for a dirty sheet (unknown category), and still clean up the temp file" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    stubFrameworkNode(graphDB, "Draft", List("cat_competency"))
    val catNode = categoryInstanceNodeWithCode("cat_competency", "competency")
    stubGetNodeByUniqueIds(graphDB, categoryInstances = util.Arrays.asList(catNode))
    val (file, name) = buildCsvFile(List(List("unknown-category", "X", "x1", "", "")))
    val response = Await.result(TermBulkManager.bulkValidateTerm(bulkFileRequest("fw1", file, name)), 10.seconds)
    response.getResponseCode shouldBe ResponseCode.CLIENT_ERROR
    response.getResult.get("valid") shouldBe false
    file.exists() shouldBe false
  }

  "TermBulkManager.bulkCommitTerm" should "reject with ERR_PENDING_REVIEW_EXISTS when a Term/CategoryInstance under this framework is already in Review" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    stubFrameworkNode(graphDB, "Draft", Nil)
    val pending = termNodeWithCategory("fw1_competency_cmR", "competency", "cmR", "R", "Review")
    stubGetNodeByUniqueIds(graphDB, pendingReview = util.Arrays.asList(pending))
    val (file, name) = buildCsvFile(Nil)
    val thrown = intercept[ClientException] {
      Await.result(TermBulkManager.bulkCommitTerm(bulkFileRequest("fw1", file, name)), 10.seconds)
    }
    thrown.getErrCode shouldBe "ERR_PENDING_REVIEW_EXISTS"
  }

  it should "abort with zero writes when the sheet has even one bad row (all-or-nothing)" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    stubFrameworkNode(graphDB, "Draft", List("cat_competency"))
    val catNode = categoryInstanceNodeWithCode("cat_competency", "competency")
    stubGetNodeByUniqueIds(graphDB, categoryInstances = util.Arrays.asList(catNode))
    // graphDB.addNode/upsertNode/updateNodes are intentionally left un-stubbed: ScalaMock fails the test if any is called.
    val (file, name) = buildCsvFile(List(List("competency", "CM1", "cm1", "skill:doesnotexist", "")))
    val response = Await.result(TermBulkManager.bulkCommitTerm(bulkFileRequest("fw1", file, name)), 10.seconds)
    response.getResponseCode shouldBe ResponseCode.CLIENT_ERROR
    response.getResult.get("committed") shouldBe false
  }

  it should "NOT compensate (retire) sibling create rows that already wrote -- leave cm1 live, skip phase 2/3 entirely, and report committed:false when one row hits a write-time-only unique-constraint collision classifyAndValidate couldn't have seen" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    stubFrameworkNode(graphDB, "Draft", List("cat_competency"))
    val catNode = categoryInstanceNodeWithCode("cat_competency", "competency")
    stubGetNodeByUniqueIds(graphDB, categoryInstances = util.Arrays.asList(catNode))
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "cat_competency", *, *).returns(Future(catNode)).anyNumberOfTimes()
    // commitClassification's phase-1 create resolves the category instance a second way --
    // validateCategoryInstance looks it up by its OWN deterministic id (frameworkId_category),
    // distinct from the "cat_competency" id fetchAttachedCategories's relation-based lookup uses.
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1_competency", *, *).returns(Future(categoryInstanceNodeWithCode("fw1_competency", "competency"))).anyNumberOfTimes()

    (graphDB.checkCyclicLoop _).expects(*, *, *, *).returns(noLoop()).anyNumberOfTimes()
    (graphDB.createRelation(_: String, _: java.util.List[java.util.Map[String, AnyRef]])).expects(*, *).returns(Future(new Response())).anyNumberOfTimes()
    // cm1 writes fine; cm2 hits the unique-index collision only the actual write can catch
    // (classifyAndValidate has no way to see this ahead of time -- that's the whole point).
    (graphDB.addNode(_: String, _: Node)).expects(*, *).onCall((_: String, n: Node) => n.getIdentifier match {
      case "fw1_competency_cm1" => Future(termNode("fw1_competency_cm1", "cm1"))
      case "fw1_competency_cm2" => Future.failed(new ClientException(DACErrorCodeConstants.CONSTRAINT_VALIDATION_FAILED.name(), "Node with this identifier already exists"))
      case other => Future.failed(new ClientException("ERR_UNEXPECTED_ADD_NODE_CALL", s"unexpected addNode call for $other"))
    }).anyNumberOfTimes()
    // graphDB.updateNodes/upsertNode are intentionally left un-stubbed: ScalaMock fails the test if
    // either is called -- cm1 must NOT be retired (that would permanently burn its code, per §4.7's
    // permanent-code-uniqueness rule, purely as a side effect of cm2's unrelated write-time failure),
    // and phase 2/3 must never run once phase 1 has any failure.

    val (file, name) = buildCsvFile(List(
      List("competency", "CM1", "cm1", "", ""),
      List("competency", "CM2", "cm2", "", "")
    ))
    val response = Await.result(TermBulkManager.bulkCommitTerm(bulkFileRequest("fw1", file, name)), 10.seconds)
    response.getResponseCode shouldBe ResponseCode.CLIENT_ERROR
    response.getResult.get("committed") shouldBe false

    val rows = response.getResult.get("rows").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    rows.size shouldBe 2
    val byCode = rows.asScala.map(r => r.get("code") -> r).toMap
    // cm1 already exists (write succeeded) and is left exactly as-is -- flagged, not retired.
    byCode("cm1").get("errCode") shouldBe "ERR_COMMIT_PARTIAL_WRITE"
    byCode("cm2").get("errCode") shouldBe "ERR_DUPLICATE_CODE"
  }

  it should "commit a mixed create+update+retire sheet in order: create (status=Review) -> associate -> retire-by-omission" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    stubFrameworkNode(graphDB, "Draft", List("cat_competency"))

    val catNode = categoryInstanceNodeWithCode("cat_competency", "competency")
    val existingUpdate = termNodeWithCategory("fw1_competency_cm1", "competency", "cm1", "Old CM1", "Live")
    val existingRetire = termNodeWithCategory("fw1_competency_cm2", "competency", "cm2", "CM2", "Live")
    stubGetNodeByUniqueIds(graphDB, categoryInstances = util.Arrays.asList(catNode), activeTerms = util.Arrays.asList(existingUpdate, existingRetire))

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "cat_competency", *, *).returns(Future(catNode)).anyNumberOfTimes()
    // See the sibling "compensate" test above: validateCategoryInstance resolves the category
    // instance by its own deterministic id (frameworkId_category), not the "cat_competency" id
    // fetchAttachedCategories's relation-based lookup uses.
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1_competency", *, *).returns(Future(categoryInstanceNodeWithCode("fw1_competency", "competency"))).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1_competency_cm1", *, *).returns(Future(existingUpdate)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "fw1_competency_cm3", *, *).returns(Future(termNode("fw1_competency_cm3", "cm3"))).anyNumberOfTimes()

    val order = mutable.ListBuffer.empty[String]
    (graphDB.checkCyclicLoop _).expects(*, *, *, *).returns(noLoop()).anyNumberOfTimes()
    (graphDB.addNode(_: String, _: Node)).expects(*, *).onCall((_: String, n: Node) => {
      order += "create"
      Future(termNode(n.getIdentifier, n.getIdentifier.split("_").last))
    }).anyNumberOfTimes()
    (graphDB.createRelation(_: String, _: java.util.List[java.util.Map[String, AnyRef]])).expects(*, *).returns(Future(new Response())).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).onCall((_: String, n: Node, _: Request) => {
      order += "associate"
      Future(n)
    }).anyNumberOfTimes()
    (graphDB.updateNodes(_: String, _: java.util.List[String], _: java.util.Map[String, AnyRef])).expects(*, *, *).onCall((_: String, ids: java.util.List[String], metadata: java.util.Map[String, AnyRef]) => {
      order += "retire"
      assert(ids.asScala.contains("fw1_competency_cm2"))
      assert("Retired".equals(metadata.get("status")))
      Future(new util.HashMap[String, Node]())
    })

    val (file, name) = buildCsvFile(List(
      List("competency", "New CM1", "cm1", "", "updated"), // update cm1
      List("competency", "CM3", "cm3", "", "") // create cm3
      // cm2 omitted -> retire by omission
    ))
    val response = Await.result(TermBulkManager.bulkCommitTerm(bulkFileRequest("fw1", file, name)), 10.seconds)
    response.getResponseCode shouldBe ResponseCode.OK
    response.getResult.get("committed") shouldBe true
    val summary = response.getResult.get("summary").asInstanceOf[util.Map[String, AnyRef]]
    summary.get("created") shouldBe 1
    summary.get("updated") shouldBe 1
    summary.get("retired") shouldBe 1
    val rows = response.getResult.get("rows").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    rows.size shouldBe 0
    // Phase ordering: every "create" precedes every "associate" (2 -- one for the fresh cm3, one
    // for cm1's own metadata/association patch), which precede the single "retire" bulkUpdate.
    order.count(_ == "create") shouldBe 1
    order.count(_ == "associate") shouldBe 2
    order.count(_ == "retire") shouldBe 1
    order.indexOf("create") should be < order.indexOf("associate")
    order.lastIndexOf("associate") should be < order.indexOf("retire")
  }

  "TermBulkManager.downloadTerms" should "upload a .csv file with the header row and one row per active term when the framework already has terms" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    implicit val ss: StorageService = mock[StorageService]

    stubFrameworkNode(graphDB, "Live", List("cat_competency"))
    val catNode = categoryInstanceNodeWithCode("cat_competency", "competency")
    val term1 = termNodeWithCategory("fw1_competency_cm1", "competency", "cm1", "CM1", "Live")
    stubGetNodeByUniqueIds(graphDB, categoryInstances = util.Arrays.asList(catNode), activeTerms = util.Arrays.asList(term1))

    // Production code deletes the temp csv in a `finally` right after uploadFile returns (so a
    // real upload never leaks a file), so the file must be inspected INSIDE this mocked call,
    // while it still exists on disk -- not after Await.result returns below.
    var records: List[CSVRecord] = null
    (ss.uploadFile(_: String, _: File, _: Option[Boolean])).expects(*, *, *).onCall((_: String, f: File, _: Option[Boolean]) => {
      val parser = CSVParser.parse(f, StandardCharsets.UTF_8, CSVFormat.DEFAULT)
      try records = parser.getRecords.asScala.toList finally parser.close()
      Array[String]("competencyframework/csv/terms.csv", "https://cdn.example.com/competencyframework/csv/terms.csv")
    })

    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() { { put("graph_id", "domain") } })
    request.put("framework", "fw1")

    val response = Await.result(TermBulkManager.downloadTerms(request), 10.seconds)
    response.getResult.get("fileUrl") shouldBe "https://cdn.example.com/competencyframework/csv/terms.csv"
    response.getResult.get("ttl") shouldBe "86400"

    records.head.asScala.toList shouldBe TermSheetReader.REQUIRED_HEADERS
    val dataRow = records(1).asScala.toList
    dataRow(0) shouldBe "competency"
    dataRow(2) shouldBe "cm1"
  }

  it should "upload a .csv with one placeholder row per attached category (Category filled in, everything else blank) when the framework has zero terms" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    implicit val ss: StorageService = mock[StorageService]

    stubFrameworkNode(graphDB, "Live", List("cat_competency", "cat_skill"))
    val catCompetency = categoryInstanceNodeWithCode("cat_competency", "competency")
    val catSkill = categoryInstanceNodeWithCode("cat_skill", "skill")
    // activeTerms is intentionally left at its default (empty) -- this is the empty-framework case.
    stubGetNodeByUniqueIds(graphDB, categoryInstances = util.Arrays.asList(catCompetency, catSkill))

    var records: List[CSVRecord] = null
    (ss.uploadFile(_: String, _: File, _: Option[Boolean])).expects(*, *, *).onCall((_: String, f: File, _: Option[Boolean]) => {
      val parser = CSVParser.parse(f, StandardCharsets.UTF_8, CSVFormat.DEFAULT)
      try records = parser.getRecords.asScala.toList finally parser.close()
      Array[String]("competencyframework/csv/terms.csv", "https://cdn.example.com/competencyframework/csv/terms.csv")
    })

    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() { { put("graph_id", "domain") } })
    request.put("framework", "fw1")

    Await.result(TermBulkManager.downloadTerms(request), 10.seconds)

    records.head.asScala.toList shouldBe TermSheetReader.REQUIRED_HEADERS
    records.tail.map(_.asScala.toList) shouldBe List(
      List("competency", "", "", "", ""),
      List("skill", "", "", "", "")
    )
  }
}
