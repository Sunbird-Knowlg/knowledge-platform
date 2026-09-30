package org.sunbird.managers

import org.sunbird.common.dto.{Response, ResponseHandler}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.utils.taxonomy.TaxonomyUtil

import java.util
import java.util.Locale
import scala.jdk.CollectionConverters._

/**
 * Pure classification of an uploaded term sheet against the framework's current live state, plus
 * serializing that classification into bulk/validate and bulk/commit API responses. No
 * OntologyEngineContext, no Future -- everything here is a pure function of already-fetched data
 * (TermBulkManager does all the graph reads/writes and hands the results in), which is what makes
 * classifyAndValidate directly unit-testable with plain fixtures, no mocks.
 */
object TermSheetClassifier {

  case class ActiveTerm(identifier: String, category: String, code: String, name: String, description: String, status: String, associations: List[String] = Nil)

  private[managers] case class RowIssue(code: String, msg: String, token: Option[String] = None)

  private[managers] case class ChangeSet(metadata: List[String], associationsAdded: List[String], associationsRemoved: List[String])
  private[managers] case class PlanCreate(category: String, code: String, name: String, associations: List[String])
  private[managers] case class PlanUpdate(category: String, code: String, currentStatus: String, changes: ChangeSet)
  private[managers] case class PlanRetire(category: String, code: String)

  private[managers] case class RowOutcome(index: Int, rowType: String, category: String, code: String, status: String,
                                           errCode: Option[String] = None, errMsg: Option[String] = None,
                                           warnings: List[RowIssue] = Nil)

  private[managers] case class ClassificationResult(valid: Boolean, summary: Map[String, Int],
                                                      planCreates: List[PlanCreate], planUpdates: List[PlanUpdate], planRetires: List[PlanRetire],
                                                      rows: List[RowOutcome],
                                                      creates: List[(TermSheetReader.SheetRow, List[String])],
                                                      updates: List[(ActiveTerm, TermSheetReader.SheetRow, List[String])],
                                                      retireIdentifiers: List[String],
                                                      // File-level warnings that have no data-row index to attach to
                                                      // (e.g. a skipped duplicate header row) -- surfaced at the top
                                                      // level instead of rows[]. Defaulted so existing hand-built
                                                      // ClassificationResults in tests keep compiling unmodified.
                                                      fileWarnings: List[RowIssue] = Nil)

  private[managers] def normKey(category: String, code: String): (String, String) =
    (category.trim.toLowerCase(Locale.ROOT), code.trim.toLowerCase(Locale.ROOT))

  private[managers] def classifyAndValidate(frameworkId: String, rows: List[TermSheetReader.SheetRow],
                                             activeTerms: List[ActiveTerm], attachedCategories: Set[String],
                                             retiredKeys: Set[(String, String)] = Set.empty,
                                             skippedHeaderRows: List[Int] = Nil): ClassificationResult = {
    val attachedLower = attachedCategories.map(_.toLowerCase(Locale.ROOT))
    val activeByKey: Map[(String, String), ActiveTerm] = activeTerms.map(t => normKey(t.category, t.code) -> t).toMap
    val rowsByKey: Map[(String, String), List[TermSheetReader.SheetRow]] = rows.filter(_.code.nonEmpty).groupBy(r => normKey(r.category, r.code))

    case class Prelim(row: TermSheetReader.SheetRow, rowType: String, matched: Option[ActiveTerm],
                       errors: List[String], errMsgs: Map[String, String])

    val prelims: List[Prelim] = rows.map { row =>
      val k = normKey(row.category, row.code)
      val isDup = row.code.nonEmpty && rowsByKey.getOrElse(k, Nil).sortBy(_.index).headOption.exists(_.index != row.index)
      val matched = if (isDup) None else activeByKey.get(k)
      val rowType = if (matched.isDefined) "update" else "create"

      val errs = scala.collection.mutable.LinkedHashMap.empty[String, String]
      if (row.category.isEmpty || !attachedLower.contains(row.category.toLowerCase(Locale.ROOT)))
        errs += "ERR_UNKNOWN_CATEGORY" -> s"Category '${row.category}' is not attached to this framework."
      if (row.code.isEmpty)
        errs += "ERR_TERM_CODE_REQUIRED" -> "Unique code is required for Term"
      if (isDup)
        errs += "ERR_DUPLICATE_CODE" -> s"Duplicate row for '${row.category}:${row.code}' in sheet."
      if (rowType == "create" && row.code.nonEmpty && retiredKeys.contains(k))
        errs += "ERR_DUPLICATE_CODE" -> s"Code '${row.category}:${row.code}' was previously used and retired -- codes are never reusable, even after retirement."
      row.rowErrors.foreach(ri => if (!errs.contains(ri.code)) errs += ri.code -> ri.msg)

      Prelim(row, rowType, matched, errs.keys.toList, errs.toMap)
    }

    val sheetIdentifiers: Map[(String, String), String] = prelims.filter(_.errors.isEmpty).map { p =>
      val cid = TaxonomyUtil.generateIdentifier(frameworkId, p.row.category)
      normKey(p.row.category, p.row.code) -> p.matched.map(_.identifier).getOrElse(TaxonomyUtil.generateIdentifier(cid, p.row.code))
    }.toMap

    val mentionedKeys: Set[(String, String)] = rows.filter(_.code.nonEmpty).map(r => normKey(r.category, r.code)).toSet
    val retireList: List[ActiveTerm] = activeByKey.keySet.diff(mentionedKeys).toList.map(activeByKey).sortBy(t => (t.category, t.code))
    val retiredIdSet: Set[String] = retireList.map(_.identifier).toSet

    def resolve(row: TermSheetReader.SheetRow): (List[String], List[String], List[RowIssue]) = {
      val ids = scala.collection.mutable.ListBuffer.empty[String]
      val tokensOk = scala.collection.mutable.ListBuffer.empty[String]
      val dangling = scala.collection.mutable.ListBuffer.empty[RowIssue]
      row.associatedTermsRaw.foreach { token =>
        val parts = token.split(":", 2)
        val k = normKey(parts(0), parts(1))
        val resolved = sheetIdentifiers.get(k)
          .orElse(activeByKey.get(k).filterNot(t => retiredIdSet.contains(t.identifier)).map(_.identifier))
        resolved match {
          case Some(id) => ids += id; tokensOk += token
          case None => dangling += RowIssue("ERR_DANGLING_ASSOCIATION", s"Associated term '$token' not found in sheet or framework.", Some(token))
        }
      }
      (ids.toList, tokensOk.toList, dangling.toList)
    }

    val resolvedByIndex: Map[Int, (List[String], List[String], List[RowIssue])] = prelims.map(p => p.row.index -> resolve(p.row)).toMap

    val finalized: List[(Prelim, List[String], List[String])] = prelims.map { p =>
      val (ids, tokens, dangling) = resolvedByIndex(p.row.index)
      if (p.errors.isEmpty && dangling.nonEmpty)
        (p.copy(errors = List(dangling.head.code), errMsgs = Map(dangling.head.code -> dangling.head.msg)), ids, tokens)
      else (p, ids, tokens)
    }

    val creates: List[(TermSheetReader.SheetRow, List[String])] = finalized.collect {
      case (p, ids, _) if p.rowType == "create" && p.errors.isEmpty => (p.row, ids)
    }
    val updates: List[(ActiveTerm, TermSheetReader.SheetRow, List[String])] = finalized.collect {
      case (p, ids, _) if p.rowType == "update" && p.errors.isEmpty => (p.matched.get, p.row, ids)
    }

    val updatedIdentifiers: Set[String] = updates.map(_._1.identifier).toSet
    val preEdges: Map[String, List[String]] = activeTerms.map(t => t.identifier -> t.associations).toMap
    val untouchedSurvivorEdges: Map[String, List[String]] =
      preEdges.filter { case (id, _) => !retiredIdSet.contains(id) && !updatedIdentifiers.contains(id) }
    val postEdges: Map[String, List[String]] =
      untouchedSurvivorEdges ++
        updates.map { case (term, _, ids) => term.identifier -> ids } ++
        creates.map { case (row, ids) => sheetIdentifiers(normKey(row.category, row.code)) -> ids }
    def incomingCount(edges: Map[String, List[String]], target: String): Int = edges.values.count(_.contains(target))
    val droppedToZero: Set[String] = postEdges.keySet.filter(id => incomingCount(preEdges, id) > 0 && incomingCount(postEdges, id) == 0)
    val updatedRowByIdentifier: Map[String, TermSheetReader.SheetRow] = updates.map { case (term, row, _) => term.identifier -> row }.toMap
    val createRowByIdentifier: Map[String, TermSheetReader.SheetRow] = creates.map { case (row, ids) => sheetIdentifiers(normKey(row.category, row.code)) -> row }.toMap
    val untouchedTermByIdentifier: Map[String, ActiveTerm] = activeTerms.filter(t => untouchedSurvivorEdges.contains(t.identifier)).map(t => t.identifier -> t).toMap
    val rowSecondOrderByIndex: Map[Int, RowIssue] = droppedToZero.flatMap { id =>
      (updatedRowByIdentifier.get(id) orElse createRowByIdentifier.get(id)).map(row =>
        row.index -> RowIssue("WARN_SECOND_ORDER_ORPHAN", s"${row.category}:${row.code} loses its only incoming reference, retired by this commit."))
    }.toMap
    val fileSecondOrderWarnings: List[RowIssue] = droppedToZero.flatMap(untouchedTermByIdentifier.get).toList.map(t =>
      RowIssue("WARN_SECOND_ORDER_ORPHAN", s"${t.category}:${t.code} (not in this sheet) loses its only incoming reference, retired by this commit."))

    def rowWarnings(p: Prelim, ids: List[String]): List[RowIssue] = {
      val base = p.row.rowWarnings.map(w => RowIssue(w.code, w.msg, w.token))
      val isCleanCreate = p.rowType == "create" && p.errors.isEmpty
      val selfKey = normKey(p.row.category, p.row.code)
      val referencedElsewhere = finalized.exists { case (other, otherIds, _) =>
        other.row.index != p.row.index && otherIds.contains(sheetIdentifiers.getOrElse(selfKey, ""))
      }
      val orphan =
        if (isCleanCreate && ids.isEmpty && !referencedElsewhere)
          List(RowIssue("WARN_ORPHAN_TERM", s"${p.row.category}:${p.row.code} has no incoming or outgoing associations."))
        else Nil
      base ++ orphan ++ rowSecondOrderByIndex.get(p.row.index).toList
    }

    val unintendedByCreateIndex: Map[Int, ActiveTerm] = retireList.flatMap { retired =>
      finalized.map(_._1).find(p => p.rowType == "create" && p.errors.isEmpty &&
        p.row.name.equalsIgnoreCase(retired.name) && p.row.description.equalsIgnoreCase(retired.description)
      ).map(p => p.row.index -> retired)
    }.toMap

    val rowOutcomes: List[RowOutcome] = finalized.map { case (p, ids, _) =>
      val errCode = p.errors.headOption
      val unintended = unintendedByCreateIndex.get(p.row.index).map(retired =>
        RowIssue("WARN_POSSIBLE_UNINTENDED_CODE_CHANGE",
          s"'${p.row.category}:${p.row.code}' shares its name with retiring '${retired.category}:${retired.code}' -- verify this isn't an unintended code or category change."))
      RowOutcome(p.row.index, p.rowType, p.row.category, p.row.code, if (errCode.isDefined) "FAILED" else "OK",
        errCode, errCode.map(c => p.errMsgs.getOrElse(c, "")), rowWarnings(p, ids) ++ unintended.toList)
    }

    val tokensByIndex: Map[Int, List[String]] = finalized.map { case (p, _, tokens) => p.row.index -> tokens }.toMap
    val planCreates = creates.map { case (row, _) => PlanCreate(row.category, row.code, row.name, tokensByIndex.getOrElse(row.index, Nil)) }
    val idToToken: Map[String, String] = activeTerms.map(t => t.identifier -> s"${t.category}:${t.code}").toMap
    val planUpdates = updates.map { case (term, row, ids) =>
      val metaChanges = List(
        if (row.name != term.name) Some("name") else None,
        if (row.description != term.description) Some("description") else None
      ).flatten
      val rowTokenById: Map[String, String] = ids.zip(tokensByIndex.getOrElse(row.index, Nil)).toMap
      val existingIds = term.associations.toSet
      val newIds = ids.toSet
      val added = ids.filterNot(existingIds.contains).map(id => rowTokenById.getOrElse(id, id))
      val removed = term.associations.filterNot(newIds.contains).map(id => idToToken.getOrElse(id, id))
      PlanUpdate(term.category, term.code, term.status, ChangeSet(metaChanges, added, removed))
    }
    val planRetires = retireList.map(t => PlanRetire(t.category, t.code))

    val fileWarnings: List[RowIssue] = skippedHeaderRows.map(r =>
      RowIssue("WARN_DUPLICATE_HEADER_ROW", s"Row $r is a duplicate of the header row and was skipped.")) ++ fileSecondOrderWarnings

    val errorCount = rowOutcomes.count(_.errCode.isDefined)
    val warningCount = rowOutcomes.map(_.warnings.size).sum + fileWarnings.size
    val summary = Map(
      "toCreate" -> creates.size,
      "toUpdate" -> updates.size,
      "toUpdateLive" -> updates.count(_._1.status == "Live"),
      "toUpdateDraft" -> updates.count(t => t._1.status == "Draft" || t._1.status == "Review"),
      "toRetire" -> retireList.size,
      "errors" -> errorCount,
      "warnings" -> warningCount
    )

    ClassificationResult(errorCount == 0, summary, planCreates, planUpdates, planRetires, rowOutcomes,
      creates, updates, retireList.map(_.identifier), fileWarnings)
  }

  private[managers] def buildDownloadRows(activeTerms: List[ActiveTerm], attachedCategories: Set[String]): List[List[String]] =
    if (activeTerms.nonEmpty) {
      val byIdentifier: Map[String, ActiveTerm] = activeTerms.map(t => t.identifier -> t).toMap
      activeTerms.sortBy(t => (t.category, t.code)).map { t =>
        val tokens = t.associations.flatMap(byIdentifier.get).map(target => s"${target.category}:${target.code}")
        List(t.category, t.name, t.code, tokens.mkString(","), t.description)
      }
    } else {
      attachedCategories.toList.sorted.map(category => List(category, "", "", "", ""))
    }

  private def toJavaWarningList(warnings: List[RowIssue]): util.List[util.Map[String, AnyRef]] =
    warnings.map[util.Map[String, AnyRef]](w => {
      val wm = new util.HashMap[String, AnyRef]()
      wm.put("code", w.code)
      wm.put("msg", w.msg)
      wm
    }).asJava

  private def toJavaRow(r: RowOutcome, termStatus: Option[String] = None): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    m.put("index", r.index.asInstanceOf[Integer])
    m.put("rowType", r.rowType)
    m.put("category", r.category)
    m.put("code", r.code)
    m.put("status", r.status)
    r.errCode.foreach(c => m.put("errCode", c))
    r.errMsg.foreach(msg => m.put("errMsg", msg))
    if (r.warnings.nonEmpty) m.put("warnings", toJavaWarningList(r.warnings))
    termStatus.foreach(s => m.put("termStatus", s))
    m
  }

  private def toJavaSummary(summary: Map[String, Int]): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    summary.foreach { case (k, v) => m.put(k, v.asInstanceOf[Integer]) }
    m
  }

  private def withFileWarnings(response: Response, result: ClassificationResult): Response = {
    if (result.fileWarnings.nonEmpty) response.put("fileWarnings", toJavaWarningList(result.fileWarnings))
    response
  }

  private[managers] def buildValidateResponse(result: ClassificationResult): Response = {
    val response =
      if (result.valid) ResponseHandler.OK
      else ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, "ERR_VALIDATION_FAILED", "One or more rows failed validation")
    response.put("valid", java.lang.Boolean.valueOf(result.valid))
      .put("summary", toJavaSummary(result.summary))
      .put("rows", result.rows.filter(r => r.errCode.isDefined || r.warnings.nonEmpty).map(r => toJavaRow(r)).asJava)
    withFileWarnings(response, result)
  }

  private[managers] def buildCommitFailureResponse(result: ClassificationResult,
                                                    createFailureByIndex: Map[Int, (String, String)] = Map.empty,
                                                    rolledBackIndices: Set[Int] = Set.empty,
                                                    errCode: String = "ERR_VALIDATION_FAILED",
                                                    errMsg: String = "One or more rows failed validation"): Response = {
    val errorCount = if (createFailureByIndex.nonEmpty) createFailureByIndex.size else result.summary.getOrElse("errors", 0)
    val summary = new util.HashMap[String, AnyRef]()
    summary.put("errors", errorCount.asInstanceOf[Integer])
    summary.put("warnings", result.summary.getOrElse("warnings", 0).asInstanceOf[Integer])
    val rows = result.rows.flatMap { r =>
      val finalRow = createFailureByIndex.get(r.index) match {
        case Some((ec, em)) => r.copy(status = "FAILED", errCode = Some(ec), errMsg = Some(em))
        case None if rolledBackIndices.contains(r.index) =>
          r.copy(status = "FAILED", errCode = Some("ERR_COMMIT_PARTIAL_WRITE"),
            errMsg = Some("This row was already written before a sibling row in this commit failed. It has " +
              "NOT been rolled back -- retiring it would permanently consume its code, since a " +
              "(framework, category, code) combination can never be reused once written, even for a Retired " +
              "term. No action is needed for this row."))
        case None => r
      }
      if (finalRow.errCode.isDefined || finalRow.warnings.nonEmpty) Some(toJavaRow(finalRow)) else None
    }.asJava
    val response = ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, errCode, errMsg)
      .put("committed", java.lang.Boolean.FALSE).put("summary", summary).put("rows", rows)
    withFileWarnings(response, result)
  }

  private[managers] def buildCommitSuccessResponse(result: ClassificationResult): Response = {
    val rows = result.rows.filter(_.warnings.nonEmpty).map { r =>
      val successRow = r.copy(status = "SUCCESS")
      if (r.rowType == "create") toJavaRow(successRow, Some("Live")) else toJavaRow(successRow)
    }
    val summary = new util.HashMap[String, AnyRef]()
    summary.put("created", result.creates.size.asInstanceOf[Integer])
    summary.put("updated", result.updates.size.asInstanceOf[Integer])
    summary.put("retired", result.retireIdentifiers.size.asInstanceOf[Integer])
    val response = ResponseHandler.OK.put("committed", java.lang.Boolean.TRUE).put("summary", summary).put("rows", rows.asJava)
    withFileWarnings(response, result)
  }
}