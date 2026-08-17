package org.sunbird.content.transcript.mgr

import java.util
import org.scalatest.{FlatSpec, Matchers}

// Covers TranscriptManager's pure helper functions (no DataNode/graph/Kafka
// dependency, so no mocking harness needed) - normal, edge, and failure
// paths for the two bugs fixed alongside this: buildTranscriptJson/
// buildVttContent's null-"text" NPE, and isEcarReady's status-gating logic.
class TranscriptManagerTest extends FlatSpec with Matchers {

  private def segment(id: Int, start: Double, end: Double, text: AnyRef): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    m.put("id", id.asInstanceOf[AnyRef])
    m.put("start", start.asInstanceOf[AnyRef])
    m.put("end", end.asInstanceOf[AnyRef])
    m.put("text", text)
    m
  }

  private def transcript(status: String, sourceLanguage: Boolean): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    m.put("status", status)
    m.put("sourceLanguage", java.lang.Boolean.valueOf(sourceLanguage))
    m
  }

  "buildTranscriptJson" should "serialize normal segments with id/start/end/text" in {
    val segments = util.Arrays.asList(segment(0, 0.0, 1.5, "Hello"), segment(1, 1.5, 3.0, "World"))
    val json = TranscriptManager.buildTranscriptJson(segments)
    json should include(""""id":0""")
    json should include(""""text":"Hello"""")
    json should include(""""id":1""")
    json should include(""""text":"World"""")
  }

  it should "not NPE on an explicit null text value, treating it as empty" in {
    // Regression: seg.getOrDefault("text", "") only substitutes when the key
    // is absent, not when it's present with a null value - this used to
    // throw a NullPointerException from escapeJson(null).
    val segments = util.Arrays.asList(segment(0, 0.0, 1.0, null))
    noException should be thrownBy TranscriptManager.buildTranscriptJson(segments)
    TranscriptManager.buildTranscriptJson(segments) should include(""""text":""""")
  }

  it should "escape quotes and backslashes in text" in {
    val segments = util.Arrays.asList(segment(0, 0.0, 1.0, "she said \"hi\" \\ ok"))
    val json = TranscriptManager.buildTranscriptJson(segments)
    json should include("""\"hi\"""")
  }

  "buildVttContent" should "render normal segments as numbered VTT cues" in {
    val segments = util.Arrays.asList(segment(0, 0.0, 1.5, "Hello"))
    val vtt = TranscriptManager.buildVttContent(segments)
    vtt should startWith("WEBVTT")
    vtt should include("00:00:00.000 --> 00:00:01.500")
    vtt should include("Hello")
  }

  it should "not render the literal word null for an explicit null text value" in {
    val segments = util.Arrays.asList(segment(0, 0.0, 1.0, null))
    val vtt = TranscriptManager.buildVttContent(segments)
    vtt should not include "null"
  }

  "formatVttTimestamp" should "format sub-hour durations correctly" in {
    TranscriptManager.formatVttTimestamp("65.25") shouldBe "00:01:05.250"
  }

  it should "format durations spanning hours correctly" in {
    TranscriptManager.formatVttTimestamp("3661.0") shouldBe "01:01:01.000"
  }

  it should "return the input unchanged if it isn't a valid number" in {
    TranscriptManager.formatVttTimestamp("not-a-number") shouldBe "not-a-number"
  }

  "escapeJson" should "escape backslashes, quotes, newlines, and carriage returns" in {
    TranscriptManager.escapeJson("a\\b\"c\nd\re") shouldBe "a\\\\b\\\"c\\nd\\re"
  }

  "toBool" should "read a real Boolean" in {
    TranscriptManager.toBool(java.lang.Boolean.TRUE) shouldBe true
    TranscriptManager.toBool(java.lang.Boolean.FALSE) shouldBe false
  }

  it should "read a case-insensitive \"true\"/\"false\" string" in {
    TranscriptManager.toBool("TRUE") shouldBe true
    TranscriptManager.toBool("false") shouldBe false
  }

  it should "default to false for anything else" in {
    TranscriptManager.toBool(null) shouldBe false
    TranscriptManager.toBool("yes") shouldBe false
  }

  "isEcarReady" should "be false with no transcripts at all" in {
    TranscriptManager.isEcarReady(new util.ArrayList[util.Map[String, AnyRef]](), allowFailedLanguages = true) shouldBe false
  }

  it should "be false with no source-language transcript" in {
    val transcripts = util.Arrays.asList(transcript("Live", sourceLanguage = false))
    TranscriptManager.isEcarReady(transcripts, allowFailedLanguages = true) shouldBe false
  }

  it should "be false while the source transcript isn't Live yet" in {
    val transcripts = util.Arrays.asList(transcript("Review", sourceLanguage = true))
    TranscriptManager.isEcarReady(transcripts, allowFailedLanguages = true) shouldBe false
  }

  it should "be true when source is Live and there are no other languages" in {
    val transcripts = util.Arrays.asList(transcript("Live", sourceLanguage = true))
    TranscriptManager.isEcarReady(transcripts, allowFailedLanguages = true) shouldBe true
  }

  it should "be false if any target language is still Draft/Review/Processing" in {
    val transcripts = util.Arrays.asList(
      transcript("Live", sourceLanguage = true),
      transcript("Processing", sourceLanguage = false)
    )
    TranscriptManager.isEcarReady(transcripts, allowFailedLanguages = true) shouldBe false
  }

  it should "be false for a Failed target language when allowFailedLanguages is false" in {
    val transcripts = util.Arrays.asList(
      transcript("Live", sourceLanguage = true),
      transcript("Failed", sourceLanguage = false)
    )
    TranscriptManager.isEcarReady(transcripts, allowFailedLanguages = false) shouldBe false
  }

  it should "be true for a Failed target language when allowFailedLanguages is true" in {
    val transcripts = util.Arrays.asList(
      transcript("Live", sourceLanguage = true),
      transcript("Failed", sourceLanguage = false)
    )
    TranscriptManager.isEcarReady(transcripts, allowFailedLanguages = true) shouldBe true
  }
}
