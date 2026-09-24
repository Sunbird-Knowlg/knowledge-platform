package org.sunbird.managers

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.scalatest.{FlatSpec, Matchers}

import java.io.{File, FileOutputStream}

class TermSheetReaderTest extends FlatSpec with Matchers {

  private val HEADERS = TermSheetReader.REQUIRED_HEADERS

  private def workbookFile(build: XSSFWorkbook => Unit): File = {
    val wb = new XSSFWorkbook()
    try {
      build(wb)
      val file = File.createTempFile("termsheet", ".xlsx")
      file.deleteOnExit()
      val fos = new FileOutputStream(file)
      try wb.write(fos) finally fos.close()
      file
    } finally wb.close()
  }

  private def setCell(row: Row, idx: Int, value: String): Unit = row.createCell(idx).setCellValue(value)

  /** A standard 5-column sheet: header row (in `headers` order) + one string-typed cell per `rows` entry,
   * columns given in the SAME order as `headers` (so a caller can build a reordered-header fixture too). */
  private def sheetFile(headers: List[String] = HEADERS, rows: List[List[String]] = Nil): File = workbookFile { wb =>
    val sheet = wb.createSheet("Sheet1")
    val headerRow = sheet.createRow(0)
    headers.zipWithIndex.foreach { case (h, i) => setCell(headerRow, i, h) }
    rows.zipWithIndex.foreach { case (values, r) =>
      val row = sheet.createRow(r + 1)
      values.zipWithIndex.foreach { case (v, c) => setCell(row, c, v) }
    }
  }

  private def dataRow(category: String, name: String, code: String, assoc: String, description: String): List[String] =
    List(category, name, code, assoc, description) 

  "TermSheetReader.read" should "fail with ERR_INVALID_FILE_TYPE for a non-.xlsx filename" in {
    val file = sheetFile(rows = List(dataRow("competency", "CM1", "cm1", "", "")))
    val result = TermSheetReader.read(file, "terms.csv")
    result shouldBe a[TermSheetReader.ParseFailure]
    result.asInstanceOf[TermSheetReader.ParseFailure].errCode shouldBe "ERR_INVALID_FILE_TYPE"
  }

  it should "fail with ERR_INVALID_WORKBOOK for a corrupt file carrying a .xlsx name" in {
    val file = File.createTempFile("garbage", ".xlsx")
    file.deleteOnExit()
    val fos = new FileOutputStream(file)
    try fos.write("not a real xlsx file".getBytes("UTF-8")) finally fos.close()
    val result = TermSheetReader.read(file, "terms.xlsx")
    result shouldBe a[TermSheetReader.ParseFailure]
    result.asInstanceOf[TermSheetReader.ParseFailure].errCode shouldBe "ERR_INVALID_WORKBOOK"
  }

  it should "parse successfully with reordered headers (header-name-driven, not positional)" in {
    val reordered = List("Name", "Category", "Description", "Code", "Associated Terms")
    val file = sheetFile(headers = reordered, rows = List(List("CM1", "competency", "desc", "cm1", "")))
    val result = TermSheetReader.read(file, "terms.xlsx")
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
    val file = sheetFile(headers = incomplete, rows = List(List("competency", "CM1", "cm1", "")))
    val result = TermSheetReader.read(file, "terms.xlsx")
    result shouldBe a[TermSheetReader.ParseFailure]
    result.asInstanceOf[TermSheetReader.ParseFailure].errCode shouldBe "ERR_MISSING_HEADER"
  }

  it should "skip fully-blank trailing rows silently and uncounted" in {
    val file = sheetFile(rows = List(
      dataRow("competency", "CM1", "cm1", "", "desc"),
      List("", "", "", "", ""),
      List("", "", "", "", "")
    ))
    val result = TermSheetReader.read(file, "terms.xlsx").asInstanceOf[TermSheetReader.ParseSuccess]
    result.rows should have size 1
    result.rows.head.index shouldBe 0
  }

  it should "detect a mid-file duplicate header row as WARN_DUPLICATE_HEADER_ROW, keyed by its original sheet row number, and not count it as data" in {
    val file = sheetFile(rows = List(
      dataRow("competency", "CM1", "cm1", "", "desc"),
      HEADERS, // physical row 2 -- a re-pasted header row mid-sheet
      dataRow("competency", "CM2", "cm2", "", "desc")
    ))
    val result = TermSheetReader.read(file, "terms.xlsx").asInstanceOf[TermSheetReader.ParseSuccess]
    result.rows should have size 2
    result.rows.map(_.code) shouldBe List("cm1", "cm2")
    result.skippedHeaderRows shouldBe List(2)
  }

  it should "flag a malformed association token (no single colon) as ERR_MALFORMED_ASSOCIATION and keep it out of associatedTermsRaw" in {
    val file = sheetFile(rows = List(dataRow("competency", "CM1", "cm1", "skill", "desc")))
    val result = TermSheetReader.read(file, "terms.xlsx").asInstanceOf[TermSheetReader.ParseSuccess]
    val row = result.rows.head
    row.associatedTermsRaw shouldBe empty
    row.rowErrors.map(_.code) should contain("ERR_MALFORMED_ASSOCIATION")
  }

  it should "flag a self-referencing association token as ERR_SELF_ASSOCIATION" in {
    val file = sheetFile(rows = List(dataRow("competency", "CM1", "cm1", "competency:cm1", "desc")))
    val result = TermSheetReader.read(file, "terms.xlsx").asInstanceOf[TermSheetReader.ParseSuccess]
    val row = result.rows.head
    row.associatedTermsRaw shouldBe empty
    row.rowErrors.map(_.code) should contain("ERR_SELF_ASSOCIATION")
  }

  it should "keep well-formed, non-self association tokens in associatedTermsRaw" in {
    val file = sheetFile(rows = List(dataRow("competency", "CM1", "cm1", "skill:sk1, skill:sk2", "desc")))
    val result = TermSheetReader.read(file, "terms.xlsx").asInstanceOf[TermSheetReader.ParseSuccess]
    result.rows.head.associatedTermsRaw shouldBe List("skill:sk1", "skill:sk2")
  }

  it should "flag WARN_SUSPICIOUS_CODE_FORMAT and set numericCodeCell=true for a NUMERIC-typed Code cell" in {
    val file = workbookFile { wb =>
      val sheet = wb.createSheet("Sheet1")
      val headerRow = sheet.createRow(0)
      HEADERS.zipWithIndex.foreach { case (h, i) => setCell(headerRow, i, h) }
      val row = sheet.createRow(1)
      setCell(row, 0, "competency"); setCell(row, 1, "CM1")
      row.createCell(2).setCellValue(7) // NUMERIC cell, not a string
      setCell(row, 3, ""); setCell(row, 4, "desc")
    }
    val result = TermSheetReader.read(file, "terms.xlsx").asInstanceOf[TermSheetReader.ParseSuccess]
    val r = result.rows.head
    r.numericCodeCell shouldBe true
    r.code shouldBe "7"
    r.rowWarnings.map(_.code) should contain("WARN_SUSPICIOUS_CODE_FORMAT")
  }

  it should "flag WARN_SUSPICIOUS_CODE_FORMAT (but numericCodeCell=false) for an all-digit STRING-typed Code cell" in {
    val file = sheetFile(rows = List(dataRow("competency", "CM1", "12345", "", "desc")))
    val result = TermSheetReader.read(file, "terms.xlsx").asInstanceOf[TermSheetReader.ParseSuccess]
    val r = result.rows.head
    r.numericCodeCell shouldBe false
    r.code shouldBe "12345"
    r.rowWarnings.map(_.code) should contain("WARN_SUSPICIOUS_CODE_FORMAT")
  }

  it should "not flag WARN_SUSPICIOUS_CODE_FORMAT for an ordinary alphanumeric string code" in {
    val file = sheetFile(rows = List(dataRow("competency", "CM1", "cm1", "", "desc")))
    val result = TermSheetReader.read(file, "terms.xlsx").asInstanceOf[TermSheetReader.ParseSuccess]
    result.rows.head.rowWarnings shouldBe empty
  }

  it should "fail with ERR_TOO_MANY_ROWS once kept rows exceed the configured cap" in {
    val rows = (1 to 10001).map(i => dataRow("competency", s"CM$i", s"cm$i", "", "")).toList
    val file = sheetFile(rows = rows)
    val result = TermSheetReader.read(file, "terms.xlsx")
    result shouldBe a[TermSheetReader.ParseFailure]
    result.asInstanceOf[TermSheetReader.ParseFailure].errCode shouldBe "ERR_TOO_MANY_ROWS"
  }

  it should "Unicode-trim NBSP (U+00A0) and zero-width space (U+200B) from Category and Code, never leaving them in the value" in {
    val nbsp = " "
    val zwsp = "​"
    val file = sheetFile(rows = List(dataRow(s"${nbsp}competency${nbsp}", "CM1", s"${zwsp}cm1${zwsp}", "", "desc")))
    val result = TermSheetReader.read(file, "terms.xlsx").asInstanceOf[TermSheetReader.ParseSuccess]
    val r = result.rows.head
    r.category shouldBe "competency"
    r.code shouldBe "cm1"
  }
}
