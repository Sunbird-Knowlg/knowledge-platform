package org.sunbird.managers

import org.apache.poi.openxml4j.exceptions.{InvalidFormatException, NotOfficeXmlFileException}
import org.apache.poi.ss.usermodel.{CellType, DataFormatter, Row}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.sunbird.common.Platform

import java.io.{File, FileInputStream, IOException}
import java.util.Locale
import scala.util.control.Breaks.{break, breakable}

/**
 * Pure `.xlsx` -> normalized-rows reader for the CompetencyFramework term bulk upload
 * (both bulk/validate and bulk/commit share this). No OntologyEngineContext, no
 * ExecutionContext, no Future -- it depends only on Apache POI and plain Scala, which is
 * what makes it unit-testable with in-memory XSSFWorkbook fixtures and zero mocks.
 *
 * It owns every check decidable from the workbook alone: file-type/corruption, header
 * mapping, row-count cap, blank-row skip, duplicate-header-row detection, and the two
 * purely-lexical per-row checks (malformed association token shape, self-association)
 * plus the POI-cell-type-driven suspicious-code signal. Everything that needs the
 * active-term set or other rows (unknown category, duplicate code, dangling association,
 * orphan warnings, ...) lives in TermBulkManager.classifyAndValidate instead.
 */
object TermSheetReader {

  val REQUIRED_HEADERS: List[String] = List("Category", "Name", "Code", "Associated Terms", "Description")

  case class RowIssue(code: String, msg: String, token: Option[String] = None)

  case class SheetRow(
    index: Int,
    category: String,
    name: String,
    code: String,
    description: String,
    associatedTermsRaw: List[String],
    numericCodeCell: Boolean,
    rowErrors: List[RowIssue],
    rowWarnings: List[RowIssue]
  )

  sealed trait ParseOutcome
  case class ParseFailure(errCode: String, errMsg: String) extends ParseOutcome
  case class ParseSuccess(rows: List[SheetRow], skippedHeaderRows: List[Int]) extends ParseOutcome

  // Unicode-aware whitespace, including NBSP (U+00A0) and zero-width space (U+200B) -- never
  // use bare String.trim(), which only strips ASCII control/space characters.
  private val TRIM_RE = "^[\\s\\u00A0\\u200B]+|[\\s\\u00A0\\u200B]+$"
  private def unicodeTrim(s: String): String = if (s == null) "" else s.replaceAll(TRIM_RE, "")

  def read(file: File, originalFileName: String): ParseOutcome = {
    if (originalFileName == null || !originalFileName.toLowerCase(Locale.ROOT).endsWith(".xlsx"))
      ParseFailure("ERR_INVALID_FILE_TYPE", s"Unsupported file type for '$originalFileName' -- only .xlsx is supported.")
    else openWorkbook(file) match {
      case Left(failure) => failure
      case Right(workbook) =>
        try readSheet(workbook) finally workbook.close()
    }
  }

  private def openWorkbook(file: File): Either[ParseFailure, XSSFWorkbook] = {
    try {
      val fis = new FileInputStream(file)
      try Right(new XSSFWorkbook(fis)) finally fis.close()
    } catch {
      case e: NotOfficeXmlFileException =>
        Left(ParseFailure("ERR_INVALID_WORKBOOK", s"The uploaded file is not a valid xlsx workbook: ${Option(e.getMessage).getOrElse("corrupt file")}"))
      case e: InvalidFormatException =>
        Left(ParseFailure("ERR_INVALID_WORKBOOK", s"The uploaded file is not a valid xlsx workbook: ${Option(e.getMessage).getOrElse("invalid format")}"))
      case e: IOException =>
        Left(ParseFailure("ERR_INVALID_WORKBOOK", s"The uploaded file could not be read: ${Option(e.getMessage).getOrElse("io error")}"))
    }
  }

  private def readSheet(workbook: XSSFWorkbook): ParseOutcome = {
    if (workbook.getNumberOfSheets == 0)
      ParseFailure("ERR_INVALID_WORKBOOK", "The workbook has no sheets.")
    else {
      val sheet = workbook.getSheetAt(0) // sheet 0 only -- any further tab is out of scope, ignored
      val formatter = new DataFormatter()
      val headerRow = sheet.getRow(0)
      if (headerRow == null)
        ParseFailure("ERR_MISSING_HEADER", s"missing header: ${REQUIRED_HEADERS.mkString(", ")}")
      else {
        val lastCell = math.max(0, headerRow.getLastCellNum.toInt)
        val headerMap: Map[String, Int] = (0 until lastCell).flatMap { i =>
          val cell = headerRow.getCell(i)
          val text = if (cell == null) "" else unicodeTrim(formatter.formatCellValue(cell))
          if (text.nonEmpty) Some(text.toLowerCase(Locale.ROOT) -> i) else None
        }.toMap

        val missing = REQUIRED_HEADERS.filterNot(h => headerMap.contains(h.toLowerCase(Locale.ROOT)))
        if (missing.nonEmpty)
          ParseFailure("ERR_MISSING_HEADER", s"missing header: ${missing.mkString(", ")}")
        else {
          val catIdx = headerMap(REQUIRED_HEADERS(0).toLowerCase(Locale.ROOT))
          val nameIdx = headerMap(REQUIRED_HEADERS(1).toLowerCase(Locale.ROOT))
          val codeIdx = headerMap(REQUIRED_HEADERS(2).toLowerCase(Locale.ROOT))
          val assocIdx = headerMap(REQUIRED_HEADERS(3).toLowerCase(Locale.ROOT))
          val descIdx = headerMap(REQUIRED_HEADERS(4).toLowerCase(Locale.ROOT))
          readRows(sheet.getLastRowNum, r => sheet.getRow(r), formatter, catIdx, nameIdx, codeIdx, assocIdx, descIdx)
        }
      }
    }
  }

  private def cellText(row: Row, idx: Int, formatter: DataFormatter): String = {
    val cell = if (row == null) null else row.getCell(idx)
    if (cell == null) "" else unicodeTrim(formatter.formatCellValue(cell))
  }

  private def extractCode(row: Row, idx: Int, formatter: DataFormatter): (String, Boolean) = {
    val cell = if (row == null) null else row.getCell(idx)
    if (cell != null && cell.getCellType == CellType.NUMERIC) {
      val num = cell.getNumericCellValue
      val codeStr =
        if (!num.isInfinite && !num.isNaN && num == Math.floor(num) && Math.abs(num) < 1e15) BigDecimal(num).toBigInt.toString
        else BigDecimal(num).bigDecimal.toPlainString
      (unicodeTrim(codeStr), true)
    } else {
      val text = if (cell == null) "" else formatter.formatCellValue(cell)
      (unicodeTrim(text), false)
    }
  }

  private def isHeaderRepeat(category: String, name: String, code: String, assoc: String, description: String): Boolean =
    category.equalsIgnoreCase(REQUIRED_HEADERS.head) && name.equalsIgnoreCase(REQUIRED_HEADERS(1)) &&
      code.equalsIgnoreCase(REQUIRED_HEADERS(2)) && assoc.equalsIgnoreCase(REQUIRED_HEADERS(3)) &&
      description.equalsIgnoreCase(REQUIRED_HEADERS(4))

  private def parseAssociations(raw: String, category: String, code: String): (List[String], List[RowIssue]) = {
    val tokens = raw.split(",", -1).map(unicodeTrim).filter(_.nonEmpty)
    val kept = scala.collection.mutable.ListBuffer.empty[String]
    val errors = scala.collection.mutable.ListBuffer.empty[RowIssue]
    tokens.foreach { token =>
      val colonIdx = token.indexOf(':')
      val lastColonIdx = token.lastIndexOf(':')
      val left = if (colonIdx >= 0) unicodeTrim(token.substring(0, colonIdx)) else ""
      val right = if (colonIdx >= 0) unicodeTrim(token.substring(colonIdx + 1)) else ""
      if (colonIdx < 0 || colonIdx != lastColonIdx || left.isEmpty || right.isEmpty)
        errors += RowIssue("ERR_MALFORMED_ASSOCIATION", s"'$token' is not in 'category:code' format.", Some(token))
      else if (left.equalsIgnoreCase(category) && right.equalsIgnoreCase(code))
        errors += RowIssue("ERR_SELF_ASSOCIATION", s"Term '$category:$code' cannot associate with itself.", Some(token))
      else
        kept += token
    }
    (kept.toList, errors.toList)
  }

  private def readRows(lastRowNum: Int, rowAt: Int => Row, formatter: DataFormatter,
                        catIdx: Int, nameIdx: Int, codeIdx: Int, assocIdx: Int, descIdx: Int): ParseOutcome = {
    val maxRows = Platform.getInteger("competencyframework.bulk.max_rows", 10000)
    val rowsBuf = scala.collection.mutable.ListBuffer.empty[SheetRow]
    val skippedHeaderBuf = scala.collection.mutable.ListBuffer.empty[Int]
    var keptCount = 0
    var failure: Option[ParseFailure] = None

    breakable {
      for (r <- 1 to lastRowNum) {
        val row = rowAt(r)
        val category = cellText(row, catIdx, formatter)
        val name = cellText(row, nameIdx, formatter)
        val (code, numericCodeCell) = extractCode(row, codeIdx, formatter)
        val assocRaw = cellText(row, assocIdx, formatter)
        val description = cellText(row, descIdx, formatter)

        if (category.isEmpty && name.isEmpty && code.isEmpty && assocRaw.isEmpty && description.isEmpty) {
          // blank row: skip silently, uncounted
        } else if (isHeaderRepeat(category, name, code, assocRaw, description)) {
          skippedHeaderBuf += r // 
        } else {
          val (assocList, assocErrors) = parseAssociations(assocRaw, category, code)
          val suspiciousCode = numericCodeCell || (code.nonEmpty && code.forall(_.isDigit))
          val rowWarnings =
            if (suspiciousCode) List(RowIssue("WARN_SUSPICIOUS_CODE_FORMAT",
              s"Code '$code' looks numeric; the original text may have been altered by spreadsheet auto-formatting.", Some(code)))
            else Nil
          rowsBuf += SheetRow(keptCount, category, name, code, description, assocList, numericCodeCell, assocErrors, rowWarnings)
          keptCount += 1
          if (keptCount > maxRows) {
            failure = Some(ParseFailure("ERR_TOO_MANY_ROWS", s"row limit $maxRows exceeded"))
            break
          }
        }
      }
    }

    failure.getOrElse(ParseSuccess(rowsBuf.toList, skippedHeaderBuf.toList))
  }
}
