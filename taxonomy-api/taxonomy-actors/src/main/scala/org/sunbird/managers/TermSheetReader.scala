package org.sunbird.managers

import org.apache.commons.csv.{CSVFormat, CSVParser, CSVRecord}
import org.sunbird.common.Platform

import java.io.{File, IOException}
import java.nio.ByteBuffer
import java.nio.charset.{CharacterCodingException, CodingErrorAction, StandardCharsets}
import java.nio.file.Files
import java.util.Locale
import scala.jdk.CollectionConverters._
import scala.util.control.Breaks.{break, breakable}

/**
 * Pure `.csv` -> normalized-rows reader for the CompetencyFramework term bulk upload
 * (both bulk/validate and bulk/commit share this). No OntologyEngineContext, no
 * ExecutionContext, no Future -- it depends only on Apache Commons CSV and plain Scala,
 * which is what makes it unit-testable with plain CSV file fixtures and zero mocks.
 *
 * It owns every check decidable from the file alone: file-type/corruption, header
 * mapping, row-count cap, blank-row skip, duplicate-header-row detection, and the two
 * purely-lexical per-row checks (malformed association token shape, self-association,
 * suspicious/all-digit code). Everything that needs the active-term set or other rows
 * (unknown category, duplicate code, dangling association, orphan warnings, ...) lives in
 * TermBulkManager.classifyAndValidate instead.
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

  private val DATE_LIKE_RE = "^\\d{1,4}[-/]\\d{1,2}([-/]\\d{1,4})?$".r

  def read(file: File, originalFileName: String): ParseOutcome = {
    if (originalFileName == null || !originalFileName.toLowerCase(Locale.ROOT).endsWith(".csv"))
      ParseFailure("ERR_INVALID_FILE_TYPE", s"Unsupported file type for '$originalFileName' -- only .csv is supported.")
    else parseCsv(file) match {
      case Left(failure) => failure
      case Right(records) => readRecords(records)
    }
  }

  private def parseCsv(file: File): Either[ParseFailure, Vector[CSVRecord]] = {
    val bytes = Files.readAllBytes(file.toPath)
    val bomStripped =
      if (bytes.length >= 3 && bytes(0) == 0xEF.toByte && bytes(1) == 0xBB.toByte && bytes(2) == 0xBF.toByte) bytes.drop(3)
      else bytes

    val decodedOrFailure: Either[ParseFailure, String] =
      try {
        Right(StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bomStripped)).toString)
      } catch {
        case _: CharacterCodingException =>
          Left(ParseFailure("ERR_INVALID_ENCODING", "this file isn't valid UTF-8 -- in Excel, use 'CSV UTF-8 (Comma delimited)' when saving, not plain 'CSV'."))
      }

    decodedOrFailure.flatMap { decoded =>
      val firstLine = decoded.linesIterator.take(1).toList.headOption.getOrElse("")
      if (firstLine.contains(';') && !firstLine.contains(','))
        Left(ParseFailure("ERR_CSV_WRONG_DELIMITER", "this file appears to be semicolon-delimited -- please re-export as comma-delimited CSV."))
      else {
        var parser: CSVParser = null
        try {
          parser = CSVParser.parse(decoded, CSVFormat.DEFAULT)
          Right(parser.getRecords.asScala.toVector)
        } catch {
          case e: IOException =>
            Left(ParseFailure("ERR_INVALID_CSV", s"The uploaded file is not a valid csv file: ${Option(e.getMessage).getOrElse("corrupt file")}"))
        } finally {
          if (parser != null) parser.close()
        }
      }
    }
  }

  private def readRecords(records: Vector[CSVRecord]): ParseOutcome = {
    if (records.isEmpty)
      ParseFailure("ERR_MISSING_HEADER", s"missing header: ${REQUIRED_HEADERS.mkString(", ")}")
    else {
      val headerRecord = records.head
      val headerMap: Map[String, Int] = (0 until headerRecord.size()).flatMap { i =>
        val text = unicodeTrim(headerRecord.get(i))
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
        readRows(records, catIdx, nameIdx, codeIdx, assocIdx, descIdx)
      }
    }
  }

  private def fieldText(record: CSVRecord, idx: Int): String =
    if (idx < record.size()) unicodeTrim(record.get(idx)) else ""

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

  private def readRows(records: Vector[CSVRecord], catIdx: Int, nameIdx: Int, codeIdx: Int, assocIdx: Int, descIdx: Int): ParseOutcome = {
    val maxRows = Platform.getInteger("competencyframework.bulk.max_rows", 10000)
    val rowsBuf = scala.collection.mutable.ListBuffer.empty[SheetRow]
    val skippedHeaderBuf = scala.collection.mutable.ListBuffer.empty[Int]
    var keptCount = 0
    var failure: Option[ParseFailure] = None

    breakable {
      for (r <- 1 until records.size) {
        val record = records(r)
        val category = fieldText(record, catIdx)
        val name = fieldText(record, nameIdx)
        val code = fieldText(record, codeIdx)
        val assocRaw = fieldText(record, assocIdx)
        val description = fieldText(record, descIdx)

        if (category.isEmpty && name.isEmpty && code.isEmpty && assocRaw.isEmpty && description.isEmpty) {
          // blank row: skip silently, uncounted
        } else if (isHeaderRepeat(category, name, code, assocRaw, description)) {
          skippedHeaderBuf += r //
        } else {
          val (assocList, assocErrors) = parseAssociations(assocRaw, category, code)
          val suspiciousCode = code.nonEmpty && (code.forall(_.isDigit) || DATE_LIKE_RE.pattern.matcher(code).matches())
          val rowWarnings =
            if (suspiciousCode) List(RowIssue("WARN_SUSPICIOUS_CODE_FORMAT",
              s"Code '$code' looks numeric; the original text may have been altered by spreadsheet auto-formatting.", Some(code)))
            else Nil
          rowsBuf += SheetRow(keptCount, category, name, code, description, assocList, assocErrors, rowWarnings)
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
