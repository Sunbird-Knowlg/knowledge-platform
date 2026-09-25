package org.sunbird.managers

import org.apache.commons.csv.{CSVFormat, CSVPrinter}
import org.scalatest.{FlatSpec, Matchers}

import java.io.{File, FileOutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.jdk.CollectionConverters._

class TermSheetReaderTest extends FlatSpec with Matchers {

  private val HEADERS = TermSheetReader.REQUIRED_HEADERS
  private val UTF8_BOM: Array[Byte] = Array(0xEF.toByte, 0xBB.toByte, 0xBF.toByte)

  private def csvFile(headers: List[String] = HEADERS, rows: List[List[String]] = Nil): File = {
    val file = File.createTempFile("termsheet", ".csv")
    file.deleteOnExit()
    val fos = new FileOutputStream(file)
    val out = new OutputStreamWriter(fos, StandardCharsets.UTF_8)
    var printer: CSVPrinter = null
    try {
      printer = new CSVPrinter(out, CSVFormat.DEFAULT)
      printer.printRecord(headers.asJava)
      rows.foreach(row => printer.printRecord(row.asJava))
    } finally {
      if (printer != null) printer.close() else out.close()
    }
    file
  }

  private def rawBytesFile(bytes: Array[Byte]): File = {
    val file = File.createTempFile("termsheet", ".csv")
    file.deleteOnExit()
    Files.write(file.toPath, bytes)
    file
  }

  private def dataRow(category: String, name: String, code: String, assoc: String, description: String): List[String] =
    List(category, name, code, assoc, description)

  "TermSheetReader.read" should "fail with ERR_INVALID_FILE_TYPE for a non-.csv filename" in {
    val file = csvFile(rows = List(dataRow("competency", "CM1", "cm1", "", "")))
    val result = TermSheetReader.read(file, "terms.xlsx")
    result shouldBe a[TermSheetReader.ParseFailure]
    result.asInstanceOf[TermSheetReader.ParseFailure].errCode shouldBe "ERR_INVALID_FILE_TYPE"
  }

  it should "fail with ERR_INVALID_CSV for a malformed file (unterminated quoted field) carrying a .csv name" in {
    val file = File.createTempFile("garbage", ".csv")
    file.deleteOnExit()
    val fos = new FileOutputStream(file)
    try fos.write("Category,Name,Code,Associated Terms,Description\n\"unterminated quote".getBytes("UTF-8")) finally fos.close()
    val result = TermSheetReader.read(file, "terms.csv")
    result shouldBe a[TermSheetReader.ParseFailure]
    result.asInstanceOf[TermSheetReader.ParseFailure].errCode shouldBe "ERR_INVALID_CSV"
  }

  it should "parse successfully with reordered headers (header-name-driven, not positional)" in {
    val reordered = List("Name", "Category", "Description", "Code", "Associated Terms")
    val file = csvFile(headers = reordered, rows = List(List("CM1", "competency", "desc", "cm1", "")))
    val result = TermSheetReader.read(file, "terms.csv")
    result shouldBe a[TermSheetReader.ParseSuccess]
    val rows = result.asInstanceOf[TermSheetReader.ParseSuccess].rows
    rows should have size 1
    rows.head.category shouldBe "competency"
    rows.head.name shouldBe "CM1"
    rows.head.code shouldBe "cm1"
    rows.head.description shouldBe "desc"
  }

  it should "fail with ERR_MISSING_HEADER when a required header is absent" in {
    val incomplete = List("Category", "Name", "Code", "Associated Terms") // "Description" missing
    val file = csvFile(headers = incomplete, rows = List(List("competency", "CM1", "cm1", "")))
    val result = TermSheetReader.read(file, "terms.csv")
    result shouldBe a[TermSheetReader.ParseFailure]
    result.asInstanceOf[TermSheetReader.ParseFailure].errCode shouldBe "ERR_MISSING_HEADER"
  }

  it should "skip fully-blank trailing rows silently and uncounted" in {
    val file = csvFile(rows = List(
      dataRow("competency", "CM1", "cm1", "", "desc"),
      List("", "", "", "", ""),
      List("", "", "", "", "")
    ))
    val result = TermSheetReader.read(file, "terms.csv").asInstanceOf[TermSheetReader.ParseSuccess]
    result.rows should have size 1
    result.rows.head.index shouldBe 0
  }

  it should "detect a mid-file duplicate header row as WARN_DUPLICATE_HEADER_ROW, keyed by its original row number, and not count it as data" in {
    val file = csvFile(rows = List(
      dataRow("competency", "CM1", "cm1", "", "desc"),
      HEADERS, // physical row 2 -- a re-pasted header row mid-file
      dataRow("competency", "CM2", "cm2", "", "desc")
    ))
    val result = TermSheetReader.read(file, "terms.csv").asInstanceOf[TermSheetReader.ParseSuccess]
    result.rows should have size 2
    result.rows.map(_.code) shouldBe List("cm1", "cm2")
    result.skippedHeaderRows shouldBe List(2)
  }

  it should "flag a malformed association token (no single colon) as ERR_MALFORMED_ASSOCIATION and keep it out of associatedTermsRaw" in {
    val file = csvFile(rows = List(dataRow("competency", "CM1", "cm1", "skill", "desc")))
    val result = TermSheetReader.read(file, "terms.csv").asInstanceOf[TermSheetReader.ParseSuccess]
    val row = result.rows.head
    row.associatedTermsRaw shouldBe empty
    row.rowErrors.map(_.code) should contain("ERR_MALFORMED_ASSOCIATION")
  }

  it should "flag a self-referencing association token as ERR_SELF_ASSOCIATION" in {
    val file = csvFile(rows = List(dataRow("competency", "CM1", "cm1", "competency:cm1", "desc")))
    val result = TermSheetReader.read(file, "terms.csv").asInstanceOf[TermSheetReader.ParseSuccess]
    val row = result.rows.head
    row.associatedTermsRaw shouldBe empty
    row.rowErrors.map(_.code) should contain("ERR_SELF_ASSOCIATION")
  }

  it should "keep well-formed, non-self association tokens in associatedTermsRaw" in {
    val file = csvFile(rows = List(dataRow("competency", "CM1", "cm1", "skill:sk1, skill:sk2", "desc")))
    val result = TermSheetReader.read(file, "terms.csv").asInstanceOf[TermSheetReader.ParseSuccess]
    result.rows.head.associatedTermsRaw shouldBe List("skill:sk1", "skill:sk2")
  }

  it should "flag WARN_SUSPICIOUS_CODE_FORMAT for an all-digit code (CSV has no cell types -- purely lexical)" in {
    val file = csvFile(rows = List(dataRow("competency", "CM1", "7", "", "desc")))
    val result = TermSheetReader.read(file, "terms.csv").asInstanceOf[TermSheetReader.ParseSuccess]
    val r = result.rows.head
    r.code shouldBe "7"
    r.rowWarnings.map(_.code) should contain("WARN_SUSPICIOUS_CODE_FORMAT")
  }

  it should "flag WARN_SUSPICIOUS_CODE_FORMAT for a longer all-digit code" in {
    val file = csvFile(rows = List(dataRow("competency", "CM1", "12345", "", "desc")))
    val result = TermSheetReader.read(file, "terms.csv").asInstanceOf[TermSheetReader.ParseSuccess]
    val r = result.rows.head
    r.code shouldBe "12345"
    r.rowWarnings.map(_.code) should contain("WARN_SUSPICIOUS_CODE_FORMAT")
  }

  it should "not flag WARN_SUSPICIOUS_CODE_FORMAT for an ordinary alphanumeric string code" in {
    val file = csvFile(rows = List(dataRow("competency", "CM1", "cm1", "", "desc")))
    val result = TermSheetReader.read(file, "terms.csv").asInstanceOf[TermSheetReader.ParseSuccess]
    result.rows.head.rowWarnings shouldBe empty
  }

  it should "fail with ERR_TOO_MANY_ROWS once kept rows exceed the configured cap" in {
    val rows = (1 to 10001).map(i => dataRow("competency", s"CM$i", s"cm$i", "", "")).toList
    val file = csvFile(rows = rows)
    val result = TermSheetReader.read(file, "terms.csv")
    result shouldBe a[TermSheetReader.ParseFailure]
    result.asInstanceOf[TermSheetReader.ParseFailure].errCode shouldBe "ERR_TOO_MANY_ROWS"
  }

  it should "Unicode-trim NBSP (U+00A0) and zero-width space (U+200B) from Category and Code, never leaving them in the value" in {
    val nbsp = " "
    val zwsp = "​"
    val file = csvFile(rows = List(dataRow(s"${nbsp}competency${nbsp}", "CM1", s"${zwsp}cm1${zwsp}", "", "desc")))
    val result = TermSheetReader.read(file, "terms.csv").asInstanceOf[TermSheetReader.ParseSuccess]
    val r = result.rows.head
    r.category shouldBe "competency"
    r.code shouldBe "cm1"
  }

  it should "parse a BOM-prefixed file identically to the same content without a BOM" in {
    val plain = csvFile(rows = List(dataRow("competency", "CM1", "cm1", "", "desc")))
    val withBom = rawBytesFile(UTF8_BOM ++ Files.readAllBytes(plain.toPath))
    val result = TermSheetReader.read(withBom, "terms.csv")
    result shouldBe a[TermSheetReader.ParseSuccess]
    val rows = result.asInstanceOf[TermSheetReader.ParseSuccess].rows
    rows should have size 1
    rows.head.category shouldBe "competency"
    rows.head.code shouldBe "cm1"
  }

  it should "fail with ERR_CSV_WRONG_DELIMITER for a semicolon-delimited file" in {
    val file = rawBytesFile("Category;Name;Code;Associated Terms;Description\ncompetency;CM1;cm1;;desc\n".getBytes(StandardCharsets.UTF_8))
    val result = TermSheetReader.read(file, "terms.csv")
    result shouldBe a[TermSheetReader.ParseFailure]
    result.asInstanceOf[TermSheetReader.ParseFailure].errCode shouldBe "ERR_CSV_WRONG_DELIMITER"
  }

  it should "fail with ERR_INVALID_ENCODING for a file containing invalid UTF-8 byte sequences" in {
    val invalidUtf8 = "Category,Name,Code,Associated Terms,Description\n".getBytes(StandardCharsets.UTF_8) ++
      Array[Byte](0xFF.toByte, 0xFE.toByte) ++ ",CM1,cm1,,desc\n".getBytes(StandardCharsets.UTF_8)
    val file = rawBytesFile(invalidUtf8)
    val result = TermSheetReader.read(file, "terms.csv")
    result shouldBe a[TermSheetReader.ParseFailure]
    result.asInstanceOf[TermSheetReader.ParseFailure].errCode shouldBe "ERR_INVALID_ENCODING"
  }

  it should "flag WARN_SUSPICIOUS_CODE_FORMAT for a date-shaped code (e.g. Google Sheets turning '1-2' into '2024-01-02')" in {
    val file = csvFile(rows = List(dataRow("competency", "CM1", "2024-01-02", "", "desc")))
    val result = TermSheetReader.read(file, "terms.csv").asInstanceOf[TermSheetReader.ParseSuccess]
    val r = result.rows.head
    r.code shouldBe "2024-01-02"
    r.rowWarnings.map(_.code) should contain("WARN_SUSPICIOUS_CODE_FORMAT")
  }
}
