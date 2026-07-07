package org.sunbird.v5.managers

import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException

import java.util
import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class AssessmentV5ManagerTest extends FlatSpec with Matchers {

  // ─── helpers ───────────────────────────────────────────────────────────────

  private def mkMap(pairs: (String, AnyRef)*): util.Map[String, AnyRef] =
    new util.HashMap[String, AnyRef](pairs.toMap.asJava)

  private def mkList(items: util.Map[String, AnyRef]*): util.List[util.Map[String, AnyRef]] =
    new util.ArrayList[util.Map[String, AnyRef]](items.toList.asJava)

  private def option(value: String, label: AnyRef = null): util.Map[String, AnyRef] = {
    val m = mkMap("value" -> value)
    if (label != null) m.put("label", label)
    m
  }

  // ─── validateFTB ───────────────────────────────────────────────────────────

  "validateFTB" should "return empty list for valid FTB input" in {
    val rd = mkMap("response1" -> mkMap(
      "cardinality" -> "single",
      "type"        -> "string",
      "correctResponse" -> mkMap("value" -> "Paris")
    ))
    val interactions = mkMap("response1" -> mkMap("type" -> "text"))
    AssessmentV5Manager.validateFTB(rd, interactions) shouldBe empty
  }

  it should "report error when correctResponse.value is missing" in {
    val rd = mkMap("response1" -> mkMap(
      "cardinality" -> "single",
      "type"        -> "string",
      "correctResponse" -> mkMap()
    ))
    val interactions = mkMap("response1" -> mkMap("type" -> "text"))
    val errs = AssessmentV5Manager.validateFTB(rd, interactions)
    errs.exists(_.contains("correctResponse")) shouldBe true
  }

  it should "report error when cardinality is not single" in {
    val rd = mkMap("response1" -> mkMap(
      "cardinality" -> "multiple",
      "type"        -> "string",
      "correctResponse" -> mkMap("value" -> "Paris")
    ))
    val interactions = mkMap("response1" -> mkMap("type" -> "text"))
    val errs = AssessmentV5Manager.validateFTB(rd, interactions)
    errs.exists(_.contains("cardinality")) shouldBe true
  }

  it should "report error when interaction type is not text" in {
    val rd = mkMap("response1" -> mkMap(
      "cardinality" -> "single",
      "type"        -> "string",
      "correctResponse" -> mkMap("value" -> "Paris")
    ))
    val interactions = mkMap("response1" -> mkMap("type" -> "choice"))
    val errs = AssessmentV5Manager.validateFTB(rd, interactions)
    errs.exists(_.contains("interactions.response1.type")) shouldBe true
  }

  // ─── validateMTF ───────────────────────────────────────────────────────────

  "validateMTF" should "return empty list for valid MTF input" in {
    val correctVal: util.Map[String, AnyRef] = mkMap("A" -> "1", "B" -> "2")
    val rd = mkMap("response1" -> mkMap(
      "cardinality"     -> "single",
      "type"            -> "map",
      "correctResponse" -> mkMap("value" -> correctVal)
    ))
    val leftOpts  = mkList(option("A"), option("B"))
    val rightOpts = mkList(option("1"), option("2"))
    val interactions = mkMap("response1" -> mkMap(
      "type"    -> "match",
      "options" -> mkMap("left" -> leftOpts, "right" -> rightOpts)
    ))
    AssessmentV5Manager.validateMTF(rd, interactions) shouldBe empty
  }

  it should "report error when left options are missing" in {
    val correctVal: util.Map[String, AnyRef] = mkMap("A" -> "1")
    val rd = mkMap("response1" -> mkMap(
      "cardinality"     -> "single",
      "type"            -> "map",
      "correctResponse" -> mkMap("value" -> correctVal)
    ))
    val rightOpts = mkList(option("1"))
    val interactions = mkMap("response1" -> mkMap(
      "type"    -> "match",
      "options" -> mkMap("left" -> mkList(), "right" -> rightOpts)
    ))
    val errs = AssessmentV5Manager.validateMTF(rd, interactions)
    errs.exists(_.contains("left")) shouldBe true
  }

  it should "report error when RHS values are duplicated (one-to-one violation)" in {
    val correctVal: util.Map[String, AnyRef] = mkMap("A" -> "1", "B" -> "1")
    val rd = mkMap("response1" -> mkMap(
      "cardinality"     -> "single",
      "type"            -> "map",
      "correctResponse" -> mkMap("value" -> correctVal)
    ))
    val leftOpts  = mkList(option("A"), option("B"))
    val rightOpts = mkList(option("1"), option("2"))
    val interactions = mkMap("response1" -> mkMap(
      "type"    -> "match",
      "options" -> mkMap("left" -> leftOpts, "right" -> rightOpts)
    ))
    val errs = AssessmentV5Manager.validateMTF(rd, interactions)
    errs.exists(_.contains("one-to-one")) shouldBe true
  }

  it should "report error when a correctResponse key is not in left options" in {
    val correctVal: util.Map[String, AnyRef] = mkMap("X" -> "1")
    val rd = mkMap("response1" -> mkMap(
      "cardinality"     -> "single",
      "type"            -> "map",
      "correctResponse" -> mkMap("value" -> correctVal)
    ))
    val leftOpts  = mkList(option("A"))
    val rightOpts = mkList(option("1"))
    val interactions = mkMap("response1" -> mkMap(
      "type"    -> "match",
      "options" -> mkMap("left" -> leftOpts, "right" -> rightOpts)
    ))
    val errs = AssessmentV5Manager.validateMTF(rd, interactions)
    errs.exists(_.contains("not in left")) shouldBe true
  }

  // ─── validateOrdered ───────────────────────────────────────────────────────

  "validateOrdered" should "return empty list for valid SEQ/REO input" in {
    val correctArr: util.List[AnyRef] = new util.ArrayList[AnyRef](List("A", "B", "C").asJava)
    val rd = mkMap("response1" -> mkMap(
      "cardinality"     -> "ordered",
      "type"            -> "string",
      "correctResponse" -> mkMap("value" -> correctArr)
    ))
    val opts = mkList(option("A"), option("B"), option("C"))
    val interactions = mkMap("response1" -> mkMap("type" -> "order", "options" -> opts))
    AssessmentV5Manager.validateOrdered(rd, interactions) shouldBe empty
  }

  it should "report error when cardinality is not ordered" in {
    val correctArr: util.List[AnyRef] = new util.ArrayList[AnyRef](List("A").asJava)
    val rd = mkMap("response1" -> mkMap(
      "cardinality"     -> "single",
      "type"            -> "string",
      "correctResponse" -> mkMap("value" -> correctArr)
    ))
    val opts = mkList(option("A"))
    val interactions = mkMap("response1" -> mkMap("type" -> "order", "options" -> opts))
    val errs = AssessmentV5Manager.validateOrdered(rd, interactions)
    errs.exists(_.contains("cardinality")) shouldBe true
  }

  it should "report error when correctResponse length differs from options length" in {
    val correctArr: util.List[AnyRef] = new util.ArrayList[AnyRef](List("A", "B").asJava)
    val rd = mkMap("response1" -> mkMap(
      "cardinality"     -> "ordered",
      "type"            -> "string",
      "correctResponse" -> mkMap("value" -> correctArr)
    ))
    val opts = mkList(option("A"), option("B"), option("C"))
    val interactions = mkMap("response1" -> mkMap("type" -> "order", "options" -> opts))
    val errs = AssessmentV5Manager.validateOrdered(rd, interactions)
    errs.exists(_.contains("length")) shouldBe true
  }

  // ─── filterByLanguage — top-level fields ──────────────────────────────────

  "filterByLanguage" should "return the value for the requested language" in {
    val metadata: util.Map[String, AnyRef] = mkMap(
      "body" -> mkMap("en" -> "Hello", "hi" -> "नमस्ते")
    )
    AssessmentV5Manager.filterByLanguage(metadata, "hi")
    metadata.get("body") shouldBe "नमस्ते"
  }

  it should "fall back to the system default language when requested lang is absent" in {
    val metadata: util.Map[String, AnyRef] = mkMap(
      "body" -> mkMap("en" -> "Hello")
    )
    AssessmentV5Manager.filterByLanguage(metadata, "hi")
    metadata.get("body") shouldBe "Hello"
  }

  it should "leave string body unchanged" in {
    val metadata: util.Map[String, AnyRef] = mkMap("body" -> "Hello")
    AssessmentV5Manager.filterByLanguage(metadata, "hi")
    metadata.get("body") shouldBe "Hello"
  }

  // ─── filterByLanguage — flat option labels ────────────────────────────────

  it should "resolve i18n label maps in flat options array" in {
    val labelMap: util.Map[String, AnyRef] = mkMap("en" -> "Apple", "hi" -> "सेब")
    val opt = option("A", labelMap)
    val opts = mkList(opt)
    val interactions: util.Map[String, AnyRef] = mkMap(
      "response1" -> mkMap("type" -> "order", "options" -> opts)
    )
    val metadata: util.Map[String, AnyRef] = mkMap("interactions" -> interactions)
    AssessmentV5Manager.filterByLanguage(metadata, "hi")
    opt.get("label") shouldBe "सेब"
  }

  it should "resolve i18n label maps in MTF left/right options" in {
    val labelEn: util.Map[String, AnyRef] = mkMap("en" -> "Capital", "hi" -> "राजधानी")
    val leftOpt  = option("A", labelEn)
    val rightOpt = option("1", mkMap("en" -> "Paris", "hi" -> "पेरिस").asInstanceOf[AnyRef])
    val sidesMap: util.Map[String, AnyRef] = mkMap(
      "left"  -> mkList(leftOpt),
      "right" -> mkList(rightOpt)
    )
    val interactions: util.Map[String, AnyRef] = mkMap(
      "response1" -> mkMap("type" -> "match", "options" -> sidesMap)
    )
    val metadata: util.Map[String, AnyRef] = mkMap("interactions" -> interactions)
    AssessmentV5Manager.filterByLanguage(metadata, "hi")
    leftOpt.get("label")  shouldBe "राजधानी"
    rightOpt.get("label") shouldBe "पेरिस"
  }

  // ─── processInteractions — v1.1 guard ─────────────────────────────────────

  "processInteractions" should "leave v1.1 interactions unchanged" in {
    val data: util.Map[String, AnyRef] = mkMap(
      "qumlVersion"  -> 1.1.asInstanceOf[AnyRef],
      "interactions" -> mkMap("response1" -> mkMap("type" -> "order", "options" -> mkList()))
    )
    val before = data.get("interactions")
    AssessmentV5Manager.processInteractions(data)
    data.get("interactions") shouldBe before
  }

  // ─── processResponseDeclaration — v1.1 guard ──────────────────────────────

  "processResponseDeclaration" should "not rename mapping fields for v1.1 data" in {
    val mappingEntry: util.Map[String, AnyRef] = mkMap("value" -> "A", "score" -> 1.0.asInstanceOf[AnyRef])
    val mappingList: util.List[util.Map[String, AnyRef]] = mkList(mappingEntry)
    val rdEntry: util.Map[String, AnyRef] = mkMap(
      "cardinality"     -> "ordered",
      "type"            -> "string",
      "correctResponse" -> mkMap("value" -> new util.ArrayList[AnyRef]()),
      "mapping"         -> mappingList
    )
    val rd: util.Map[String, AnyRef] = mkMap("response1" -> rdEntry)
    val data: util.Map[String, AnyRef] = mkMap(
      "qumlVersion"         -> 1.1.asInstanceOf[AnyRef],
      "primaryCategory"     -> "Sequence Question",
      "responseDeclaration" -> rd
    )
    AssessmentV5Manager.processResponseDeclaration(data)
    val mapping = rdEntry.get("mapping").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    mapping.get(0).containsKey("value") shouldBe true
    mapping.get(0).containsKey("response") shouldBe false
  }

  // ─── filterByLanguage — id-keyed fields (hints/feedback/solutions) ─────────

  it should "resolve nested i18n maps in id-keyed hints" in {
    val hints: util.Map[String, AnyRef] = mkMap(
      "hint-1" -> mkMap("en" -> "Think again", "hi" -> "फिर से सोचें"),
      "hint-2" -> "Plain single-language hint" // plain string must be left untouched
    )
    val metadata: util.Map[String, AnyRef] = mkMap("hints" -> hints)
    AssessmentV5Manager.filterByLanguage(metadata, "hi")
    hints.get("hint-1") shouldBe "फिर से सोचें"
    hints.get("hint-2") shouldBe "Plain single-language hint"
  }

  it should "fall back to default language for id-keyed solutions" in {
    val solutions: util.Map[String, AnyRef] = mkMap(
      "sol-1" -> mkMap("en" -> "English only solution")
    )
    val metadata: util.Map[String, AnyRef] = mkMap("solutions" -> solutions)
    AssessmentV5Manager.filterByLanguage(metadata, "hi")
    solutions.get("sol-1") shouldBe "English only solution"
  }

  it should "parse and resolve a JSON-string i18n body (deserialize fallback)" in {
    // Simulates the read path delivering body as a JSON-encoded i18n map string
    val metadata: util.Map[String, AnyRef] = mkMap(
      "body" -> """{"en":"Hello","hi":"नमस्ते"}"""
    )
    AssessmentV5Manager.filterByLanguage(metadata, "hi")
    metadata.get("body") shouldBe "नमस्ते"
  }

  it should "leave a plain HTML string body unchanged (no false JSON parse)" in {
    val metadata: util.Map[String, AnyRef] = mkMap("body" -> "<p>Hello</p>")
    AssessmentV5Manager.filterByLanguage(metadata, "hi")
    metadata.get("body") shouldBe "<p>Hello</p>"
  }

  // ─── getAnswer: single cardinality ─────────────────────────────────────────

  "getAnswer" should "return HTML with escaped label for single cardinality" in {
    val data = buildSingleCardinalityData("Paris")
    val result = AssessmentV5Manager.getAnswer(data)
    result should include("<div class=\"anwser-container\">")
    result should include("Paris")
  }

  it should "escape script tag in single cardinality label" in {
    val data = buildSingleCardinalityData("<script>alert('xss')</script>")
    val result = AssessmentV5Manager.getAnswer(data)
    result should not include "<script>"
    result should include("&lt;script&gt;")
  }

  it should "escape img onerror XSS payload in single cardinality label" in {
    val data = buildSingleCardinalityData("<img src=x onerror=\"steal(document.cookie)\">")
    val result = AssessmentV5Manager.getAnswer(data)
    result should not include "<img"
    result should include("&lt;img")
    result should not include "onerror=\"steal"
    result should include("&quot;")
  }

  it should "escape nested XSS with event handler in single cardinality label" in {
    val data = buildSingleCardinalityData("<div onmouseover=\"alert(document.cookie)\">hover</div>")
    val result = AssessmentV5Manager.getAnswer(data)
    // Raw div tag must be escaped so onmouseover can't execute
    result should not include "<div onmouseover"
    result should include("&lt;div")
  }

  it should "preserve plain text label in single cardinality" in {
    val data = buildSingleCardinalityData("42 is the answer")
    val result = AssessmentV5Manager.getAnswer(data)
    result should include("42 is the answer")
    result should include("<div class=\"anwser-body\">")
  }

  it should "escape ampersand in single cardinality label" in {
    val data = buildSingleCardinalityData("Tom & Jerry")
    val result = AssessmentV5Manager.getAnswer(data)
    result should include("Tom &amp; Jerry")
  }

  // ─── getAnswer: multiple cardinality ───────────────────────────────────────

  it should "escape XSS payloads in multiple cardinality labels" in {
    val data = buildMultipleCardinalityData(
      List("<script>evil()</script>Option A", "<img src=x onerror=alert(1)>Option B"),
      List(0, 1)
    )
    val result = AssessmentV5Manager.getAnswer(data)
    result should not include "<script>"
    result should not include "<img"
    result should include("&lt;script&gt;")
    result should include("&lt;img")
  }

  it should "return correct HTML structure for multiple cardinality" in {
    val data = buildMultipleCardinalityData(
      List("Option A", "Option B"),
      List(0, 1)
    )
    val result = AssessmentV5Manager.getAnswer(data)
    result should include("<div class=\"anwser-container\">")
    result should include("Option A")
    result should include("Option B")
    // Should have two anwser-body divs
    result.split("anwser-body").length should be(3) // 2 occurrences = 3 splits
  }

  it should "escape only correct answers in multiple cardinality" in {
    val data = buildMultipleCardinalityData(
      List("<script>xss</script>Right", "Wrong", "<img src=x>Also Right"),
      List(0, 2)
    )
    val result = AssessmentV5Manager.getAnswer(data)
    result should not include "<script>"
    result should include("&lt;script&gt;")
    result should not include "Wrong" // Wrong is value=1, not in correctResponse
    result should include("&lt;img")
  }

  // ─── getAnswer: subjective question bypass ─────────────────────────────────

  it should "return raw answer field for Subjective Question" in {
    val data = new util.HashMap[String, AnyRef]()
    data.put("primaryCategory", "Subjective Question")
    data.put("answer", "This is a text answer")
    val result = AssessmentV5Manager.getAnswer(data)
    result shouldBe "This is a text answer"
  }

  it should "return empty string when no answer field for Subjective Question" in {
    val data = new util.HashMap[String, AnyRef]()
    data.put("primaryCategory", "Subjective Question")
    val result = AssessmentV5Manager.getAnswer(data)
    result shouldBe ""
  }

  // ─── getAnswer: cookie/JWT theft payload (exact from security report) ──────

  it should "prevent cookie theft XSS payload from security report XSS-VULN-02" in {
    val payload = "<img src=x onerror=\"document.title='XSS-VULN-02: '+document.cookie\">"
    val data = buildSingleCardinalityData(payload)
    val result = AssessmentV5Manager.getAnswer(data)
    // Raw HTML tags must be escaped — browser will render as text, not execute
    result should not include "<img"
    result should include("&lt;img")
    // onerror attribute must be escaped so it can't execute
    result should not include "onerror=\"document"
    result should include("onerror=&quot;")
  }

  it should "prevent JWT theft XSS payload" in {
    val payload = "<img src=x onerror=\"var d=document.createElement('div');d.id='xss-proof';" +
      "d.innerText='COOKIES:'+document.cookie+' JWT:'+localStorage.getItem('access_token');" +
      "document.body.appendChild(d)\">"
    val data = buildSingleCardinalityData(payload)
    val result = AssessmentV5Manager.getAnswer(data)
    // Raw HTML tags must be escaped — browser will render as text, not execute
    result should not include "<img"
    result should include("&lt;img")
    // Quotes must be escaped so onerror handler can't execute
    result should not include "onerror=\"var"
    result should include("onerror=&quot;")
  }

  // ─── getValidatedNodeForUpdateComment ──────────────────────────────────────

  it should "return failed future when reviewComment is null" in {
    val request = buildCommentRequest(null)
    val future = AssessmentV5Manager.getValidatedNodeForUpdateComment(request, "ERR_TEST")(global, null)
    val ex = intercept[ClientException] {
      Await.result(future, 5.seconds)
    }
    ex.getMessage shouldBe "Comment key is missing or value is empty in the request body."
  }

  it should "return failed future when reviewComment is empty string" in {
    val request = buildCommentRequest("")
    val future = AssessmentV5Manager.getValidatedNodeForUpdateComment(request, "ERR_TEST")(global, null)
    val ex = intercept[ClientException] {
      Await.result(future, 5.seconds)
    }
    ex.getMessage shouldBe "Comment key is missing or value is empty in the request body."
  }

  it should "return failed future when reviewComment is whitespace only" in {
    val request = buildCommentRequest("   ")
    val future = AssessmentV5Manager.getValidatedNodeForUpdateComment(request, "ERR_TEST")(global, null)
    val ex = intercept[ClientException] {
      Await.result(future, 5.seconds)
    }
    ex.getMessage shouldBe "Comment key is missing or value is empty in the request body."
  }

  // ─── helpers for getAnswer / comment tests ─────────────────────────────────

  private def buildSingleCardinalityData(label: String): util.Map[String, AnyRef] = {
    val option0 = new util.HashMap[String, AnyRef]()
    option0.put("value", Integer.valueOf(0))
    option0.put("label", label)

    val option1 = new util.HashMap[String, AnyRef]()
    option1.put("value", Integer.valueOf(1))
    option1.put("label", "Wrong answer")

    val options = new util.ArrayList[util.Map[String, AnyRef]]()
    options.add(option0)
    options.add(option1)

    val resp1Interactions = new util.HashMap[String, AnyRef]()
    resp1Interactions.put("options", options)

    val interactions = new util.HashMap[String, AnyRef]()
    interactions.put("response1", resp1Interactions)

    val correctResponse = new util.HashMap[String, AnyRef]()
    correctResponse.put("value", Integer.valueOf(0))

    val response1 = new util.HashMap[String, AnyRef]()
    response1.put("cardinality", "single")
    response1.put("correctResponse", correctResponse)

    val responseDeclaration = new util.HashMap[String, AnyRef]()
    responseDeclaration.put("response1", response1)

    val data = new util.HashMap[String, AnyRef]()
    data.put("primaryCategory", "Multiple Choice Question")
    data.put("interactions", interactions)
    data.put("responseDeclaration", responseDeclaration)
    data
  }

  private def buildMultipleCardinalityData(labels: List[String], correctValues: List[Int]): util.Map[String, AnyRef] = {
    val options = new util.ArrayList[util.Map[String, AnyRef]]()
    labels.zipWithIndex.foreach { case (label, idx) =>
      val option = new util.HashMap[String, AnyRef]()
      option.put("value", Integer.valueOf(idx))
      option.put("label", label)
      options.add(option)
    }

    val resp1Interactions = new util.HashMap[String, AnyRef]()
    resp1Interactions.put("options", options)

    val interactions = new util.HashMap[String, AnyRef]()
    interactions.put("response1", resp1Interactions)

    val correctResponse = new util.HashMap[String, AnyRef]()
    correctResponse.put("value", correctValues.map(Integer.valueOf).asJava)

    val response1 = new util.HashMap[String, AnyRef]()
    response1.put("cardinality", "multiple")
    response1.put("correctResponse", correctResponse)

    val responseDeclaration = new util.HashMap[String, AnyRef]()
    responseDeclaration.put("response1", response1)

    val data = new util.HashMap[String, AnyRef]()
    data.put("primaryCategory", "Multiple Choice Question")
    data.put("interactions", interactions)
    data.put("responseDeclaration", responseDeclaration)
    data
  }

  private def buildCommentRequest(comment: AnyRef): Request = {
    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() {
      { put("identifier", "do_1234") }
    })
    if (comment != null) {
      request.getRequest.put("reviewComment", comment)
    }
    request
  }

}
