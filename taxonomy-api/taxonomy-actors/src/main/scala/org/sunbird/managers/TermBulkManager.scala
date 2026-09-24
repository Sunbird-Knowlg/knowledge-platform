package org.sunbird.managers

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.poi.ss.usermodel.DataValidation
import org.apache.poi.ss.util.CellRangeAddressList
import org.apache.poi.xssf.usermodel.{XSSFDataValidationHelper, XSSFWorkbook}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.Platform
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ResponseCode}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model.{Filter, MetadataCriterion, Node, SearchConditions, SearchCriteria}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.service.common.DACErrorCodeConstants
import org.sunbird.utils.Constants
import org.sunbird.utils.taxonomy.TaxonomyUtil

import java.io.{File, FileOutputStream}
import java.util
import java.util.Locale
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object TermBulkManager {

  def isDuplicateCode(e: ClientException): Boolean =
    StringUtils.equals(e.getErrCode, DACErrorCodeConstants.CONSTRAINT_VALIDATION_FAILED.name())

  def bulkUpdateTerm(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val rows: util.List[util.Map[String, AnyRef]] = getBulkRequestData(request, "terms")
    val futures = rows.asScala.zipWithIndex.map { case (row, i) => updateOneRow(row, i) }
    Future.sequence(futures.toList).map(results => buildResponse(results.asJava))
  }

  def associateTerms(rows: util.List[util.Map[String, AnyRef]])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[util.List[util.Map[String, AnyRef]]] = {
    Future.sequence(rows.asScala.zipWithIndex.map { case (row, i) => updateOneRow(row, i) }.toList).map(_.asJava)
  }

  private def updateOneRow(row: util.Map[String, AnyRef], index: Int)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[util.Map[String, AnyRef]] = {
    val identifier = row.getOrDefault(Constants.IDENTIFIER, "").asInstanceOf[String]
    val metadata = new util.HashMap[String, AnyRef](row)
    metadata.remove(Constants.IDENTIFIER)

    val rowRequest = new Request()
    rowRequest.setObjectType("Term")
    rowRequest.setContext(new util.HashMap[String, AnyRef]() {
      {
        put("graph_id", "domain")
        put(Constants.VERSION, Constants.TERM_SCHEMA_VERSION)
        put(Constants.SCHEMA_NAME, Constants.TERM_SCHEMA_NAME)
        put("objectType", "Term")
      }
    })
    rowRequest.setRequest(metadata)
    rowRequest.getContext.put(Constants.IDENTIFIER, identifier)

    Future(rowRequest).flatMap(DataNode.update(_)).map(node => successRowById(index, node.getIdentifier)) recover {
      case e: ResourceNotFoundException =>
        failureRowById(index, identifier, "RESOURCE_NOT_FOUND", e.getMessage)
      case e: ClientException =>
        failureRowById(index, identifier, e.getErrCode, e.getMessage)
      case e: Exception =>
        failureRowById(index, identifier, ResponseCode.SERVER_ERROR.name, "Internal Server Error")
    }
  }

  private def successRow(index: Int, code: String, identifier: String): util.Map[String, AnyRef] = {
    val row = new util.HashMap[String, AnyRef]()
    row.put("index", index.asInstanceOf[Integer])
    row.put("code", code)
    row.put("identifier", identifier)
    row.put("status", "SUCCESS")
    row
  }

  private def failureRow(index: Int, code: String, errCode: String, errMsg: String): util.Map[String, AnyRef] = {
    val row = new util.HashMap[String, AnyRef]()
    row.put("index", index.asInstanceOf[Integer])
    row.put("code", code)
    row.put("identifier", null)
    row.put("status", "FAILED")
    row.put("errCode", errCode)
    row.put("errMsg", errMsg)
    row
  }

  private def successRowById(index: Int, identifier: String): util.Map[String, AnyRef] = {
    val row = new util.HashMap[String, AnyRef]()
    row.put("index", index.asInstanceOf[Integer])
    row.put("identifier", identifier)
    row.put("status", "SUCCESS")
    row
  }

  private def failureRowById(index: Int, identifier: String, errCode: String, errMsg: String): util.Map[String, AnyRef] = {
    val row = new util.HashMap[String, AnyRef]()
    row.put("index", index.asInstanceOf[Integer])
    row.put("identifier", identifier)
    row.put("status", "FAILED")
    row.put("errCode", errCode)
    row.put("errMsg", errMsg)
    row
  }

  private def buildResponse(results: util.List[util.Map[String, AnyRef]]): Response = {
    if (results.asScala.exists(r => "FAILED".equals(r.get("status"))))
      ResponseHandler.ERROR(ResponseCode.PARTIAL_SUCCESS, ResponseCode.PARTIAL_SUCCESS.name, "Partial Success", "results", results)
    else
      ResponseHandler.OK.put("results", results)
  }

  private def getBulkRequestData(request: Request, key: String): util.List[util.Map[String, AnyRef]] = {
    request.getRequest.get(key) match {
      case rows: util.List[_] => rows.asInstanceOf[util.List[util.Map[String, AnyRef]]]
      case _ => throw new ClientException("ERR_INVALID_TERM_REQUEST", "Invalid Request! Please Provide Valid Request.")
    }
  }

  private[managers] def validateCategoryInstance(frameworkId: String, category: String)
                                                 (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
    if (frameworkId.isEmpty) throw new ClientException("ERR_INVALID_FRAMEWORK_ID", s"Invalid FrameworkId: '${frameworkId}' for Term ")
    if (category.isEmpty) throw new ClientException("ERR_INVALID_CATEGORY_ID", s"Invalid CategoryId: '${category}' for Term")
    val categoryInstanceId = TaxonomyUtil.generateIdentifier(frameworkId, category)
    val getCategoryInstanceReq = new Request()
    getCategoryInstanceReq.setContext(new util.HashMap[String, AnyRef]() {
      {
        put("graph_id", "domain")
        put("objectType", "CategoryInstance")
        put(Constants.SCHEMA_NAME, Constants.CATEGORY_INSTANCE_SCHEMA_NAME)
        put(Constants.VERSION, Constants.CATEGORY_INSTANCE_SCHEMA_VERSION)
      }
    })
    getCategoryInstanceReq.put(Constants.IDENTIFIER, categoryInstanceId)
    DataNode.read(getCategoryInstanceReq).map(node => {
      if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, categoryInstanceId)) node
      else throw new ClientException("ERR_CHANNEL_NOT_FOUND/ ERR_FRAMEWORK_NOT_FOUND", s"Given channel/framework is not related to given category")
    })
  }

  private[managers] def createRow(request: Request, categoryId: String, category: String, seqIndex: Int, reportIndex: Int,
                                   code: String, row: util.Map[String, AnyRef], dataModifier: Node => Node = (n => n))
                                  (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[util.Map[String, AnyRef]] = {
    val categoryList = new util.ArrayList[util.Map[String, AnyRef]]()
    val relationMap = new util.HashMap[String, AnyRef]()
    relationMap.put("identifier", categoryId)
    relationMap.put("index", seqIndex.asInstanceOf[Integer])
    categoryList.add(relationMap)

    val rowRequest = new Request(request, request.getObjectType)
    rowRequest.setRequest(new util.HashMap[String, AnyRef](row))
    rowRequest.getRequest.put(Constants.CATEGORY, category)
    rowRequest.getRequest.put(Constants.IDENTIFIER, TaxonomyUtil.generateIdentifier(categoryId, code))
    rowRequest.put("categories", categoryList)

    Future(rowRequest).flatMap(DataNode.create(_, dataModifier)).map(termNode => successRow(reportIndex, code, termNode.getIdentifier)) recover {
      case e: ClientException if isDuplicateCode(e) =>
        failureRow(reportIndex, code, "ERR_DUPLICATE_CODE", s"Term with code '$code' already exists")
      case e: ClientException =>
        failureRow(reportIndex, code, "ERR_TERM_CODE_REQUIRED", "Unique code is required for Term")
      case e: Exception =>
        failureRow(reportIndex, code, ResponseCode.SERVER_ERROR.name, "Internal Server Error")
    }
  }

  private def sheetRowToMap(row: TermSheetReader.SheetRow): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    m.put(Constants.CODE, row.code)
    m.put("name", row.name)
    m.put("description", row.description)
    m
  }

  private def buildAssociateRow(identifier: String, resolvedIdentifiers: List[String], name: Option[String], description: Option[String]): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    m.put(Constants.IDENTIFIER, identifier)
    val assoc = new util.ArrayList[util.Map[String, AnyRef]]()
    resolvedIdentifiers.foreach(id => assoc.add(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", id)
      }
    }))
    m.put("associations", assoc)
    name.foreach(n => m.put("name", n))
    description.foreach(d => m.put("description", d))
    m
  }

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

  private val SEQUENCE_RELATION = "hasSequenceMember"
  private val ASSOCIATION_RELATION = "associatedTo"

  private def normKey(category: String, code: String): (String, String) =
    (category.trim.toLowerCase(Locale.ROOT), code.trim.toLowerCase(Locale.ROOT))

  private[managers] def fetchActiveTerms(graphId: String, frameworkId: String, categories: Set[String])
                                         (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[List[ActiveTerm]] = {
    if (categories.isEmpty) Future(List.empty)
    else {
      val mc = MetadataCriterion.create(new util.ArrayList[Filter]() {
        {
          add(new Filter(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), SearchConditions.OP_IN, new util.ArrayList[String]() {{ add("Term") }}))
          add(new Filter("category", SearchConditions.OP_IN, new util.ArrayList[String](categories.asJavaCollection)))
          add(new Filter("status", SearchConditions.OP_NOT_EQUAL, "Retired"))
        }
      })
      val criteria = new SearchCriteria {
        {
          addMetadata(mc); setCountQuery(false); setGraphId(graphId)
        }
      }
      oec.graphService.getNodeByUniqueIds(graphId, criteria).map { nodes =>
        val prefix = frameworkId.toLowerCase(Locale.ROOT) + "_"
        nodes.asScala.filter(n => Option(n.getIdentifier).exists(_.toLowerCase(Locale.ROOT).startsWith(prefix))).map { n =>
          val md = n.getMetadata
          ActiveTerm(
            identifier = n.getIdentifier,
            category = md.getOrDefault("category", "").asInstanceOf[String],
            code = md.getOrDefault("code", "").asInstanceOf[String],
            name = md.getOrDefault("name", "").asInstanceOf[String],
            description = md.getOrDefault("description", "").asInstanceOf[String],
            status = md.getOrDefault("status", "").asInstanceOf[String],
            associations = Option(n.getOutRelations).map(_.asScala
              .filter(r => StringUtils.equals(r.getRelationType, ASSOCIATION_RELATION))
              .map(_.getEndNodeId).toList).getOrElse(Nil)
          )
        }.toList
      }
    }
  }

  private[managers] def hasPendingReview(graphId: String, frameworkId: String)
                                         (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Boolean] = {
    val mc = MetadataCriterion.create(new util.ArrayList[Filter]() {
      {
        add(new Filter(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), SearchConditions.OP_IN, new util.ArrayList[String]() {{ add("Term"); add("CategoryInstance") }}))
        add(new Filter("status", SearchConditions.OP_EQUAL, "Review"))
      }
    })
    val criteria = new SearchCriteria {
      {
        addMetadata(mc); setCountQuery(false); setGraphId(graphId)
      }
    }
    oec.graphService.getNodeByUniqueIds(graphId, criteria).map { nodes =>
      val prefix = frameworkId.toLowerCase(Locale.ROOT) + "_"
      nodes.asScala.exists(n => Option(n.getIdentifier).exists(_.toLowerCase(Locale.ROOT).startsWith(prefix)))
    }
  }

  private[managers] def fetchAttachedCategories(graphId: String, frameworkId: String)
                                                (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Set[String]] = {
    FrameworkManager.getLiveEditNode(graphId, frameworkId).flatMap { fwNode =>
      val categoryInstanceIds: List[String] = Option(fwNode.getOutRelations).map(_.asScala.filter(r =>
        StringUtils.equals(r.getRelationType, SEQUENCE_RELATION) &&
          StringUtils.equalsIgnoreCase(Option(r.getEndNodeObjectType).getOrElse("").replace("Image", ""), "CategoryInstance")
      ).map(_.getEndNodeId.replace(".img", "")).toList.distinct).getOrElse(Nil)
      if (categoryInstanceIds.isEmpty) Future(Set.empty[String])
      else {
        val mc = MetadataCriterion.create(new util.ArrayList[Filter]() {
          {
            add(new Filter(SystemProperties.IL_UNIQUE_ID.name(), SearchConditions.OP_IN, categoryInstanceIds.asJava))
          }
        })
        val criteria = new SearchCriteria {
          {
            addMetadata(mc); setCountQuery(false); setGraphId(graphId)
          }
        }
        oec.graphService.getNodeByUniqueIds(graphId, criteria).map(_.asScala
          .map(_.getMetadata.getOrDefault("code", "").asInstanceOf[String])
          .filter(_.nonEmpty).toSet)
      }
    }
  }

  private[managers] def classifyAndValidate(frameworkId: String, rows: List[TermSheetReader.SheetRow],
                                             activeTerms: List[ActiveTerm], attachedCategories: Set[String],
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
        val ci = token.indexOf(':')
        val k = normKey(token.substring(0, ci), token.substring(ci + 1))
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
    val createRowByIdentifier: Map[String, TermSheetReader.SheetRow] = creates.map { case (row, _) => sheetIdentifiers(normKey(row.category, row.code)) -> row }.toMap
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
        p.row.category.equalsIgnoreCase(retired.category) && p.row.name.equalsIgnoreCase(retired.name) &&
        p.row.description.equalsIgnoreCase(retired.description)
      ).map(p => p.row.index -> retired)
    }.toMap

    val rowOutcomes: List[RowOutcome] = finalized.map { case (p, ids, _) =>
      val errCode = p.errors.headOption
      val unintended = unintendedByCreateIndex.get(p.row.index).map(retired =>
        RowIssue("WARN_POSSIBLE_UNINTENDED_CODE_CHANGE",
          s"'${p.row.category}:${p.row.code}' shares its name with retiring '${retired.category}:${retired.code}' -- verify this isn't an unintended code change."))
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

  private def buildValidateResponse(result: ClassificationResult): Response = {
    val response =
      if (result.valid) ResponseHandler.OK
      else ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, "ERR_VALIDATION_FAILED", "One or more rows failed validation")
    response.put("valid", java.lang.Boolean.valueOf(result.valid))
      .put("summary", toJavaSummary(result.summary))
      .put("rows", result.rows.filter(r => r.errCode.isDefined || r.warnings.nonEmpty).map(r => toJavaRow(r)).asJava)
    if (result.fileWarnings.nonEmpty) response.put("fileWarnings", toJavaWarningList(result.fileWarnings))
    response
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
    if (result.fileWarnings.nonEmpty) response.put("fileWarnings", toJavaWarningList(result.fileWarnings))
    response
  }

  private[managers] def buildCommitSuccessResponse(result: ClassificationResult): Response = {
    val rows = result.rows.filter(_.warnings.nonEmpty).map { r =>
      val successRow = r.copy(status = "SUCCESS")
      if (r.rowType == "create") toJavaRow(successRow, Some("Review")) else toJavaRow(successRow)
    }
    val summary = new util.HashMap[String, AnyRef]()
    summary.put("created", result.creates.size.asInstanceOf[Integer])
    summary.put("updated", result.updates.size.asInstanceOf[Integer])
    summary.put("retired", result.retireIdentifiers.size.asInstanceOf[Integer])
    val response = ResponseHandler.OK.put("committed", java.lang.Boolean.TRUE).put("summary", summary).put("rows", rows.asJava)
    if (result.fileWarnings.nonEmpty) response.put("fileWarnings", toJavaWarningList(result.fileWarnings))
    response
  }

  def bulkValidateTerm(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val frameworkId = request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String]
    val graphId = request.getContext.getOrDefault("graph_id", "domain").asInstanceOf[String]
    if (frameworkId.isEmpty) throw new ClientException("ERR_INVALID_FRAMEWORK_ID", "Please provide a valid framework identifier")
    FrameworkManager.assertFrameworkEditable(graphId, frameworkId).flatMap { _ =>
      readSheetOrThrow(request) match {
        case TermSheetReader.ParseSuccess(rows, skippedHeaderRows) =>
          fetchAttachedCategories(graphId, frameworkId).flatMap { attachedCategories =>
            fetchActiveTerms(graphId, frameworkId, attachedCategories).map { activeTerms =>
              buildValidateResponse(classifyAndValidate(frameworkId, rows, activeTerms, attachedCategories, skippedHeaderRows))
            }
          }
      }
    }
  }

  def bulkCommitTerm(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val frameworkId = request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String]
    val graphId = request.getContext.getOrDefault("graph_id", "domain").asInstanceOf[String]
    if (frameworkId.isEmpty) throw new ClientException("ERR_INVALID_FRAMEWORK_ID", "Please provide a valid framework identifier")
    FrameworkManager.assertFrameworkEditable(graphId, frameworkId).flatMap { _ =>
      hasPendingReview(graphId, frameworkId).flatMap { pending =>
        if (pending) throw new ClientException("ERR_PENDING_REVIEW_EXISTS", "A previous bulk commit is still awaiting review for this framework")
        readSheetOrThrow(request) match {
          case TermSheetReader.ParseSuccess(rows, skippedHeaderRows) =>
            fetchAttachedCategories(graphId, frameworkId).flatMap { attachedCategories =>
              fetchActiveTerms(graphId, frameworkId, attachedCategories).flatMap { activeTerms =>
                val result = classifyAndValidate(frameworkId, rows, activeTerms, attachedCategories, skippedHeaderRows)
                if (!result.valid) Future(buildCommitFailureResponse(result))
                else commitClassification(request, graphId, frameworkId, result)
              }
            }
        }
      }
    }
  }

  private def readSheetOrThrow(request: Request): TermSheetReader.ParseSuccess = {
    val file = request.getRequest.get("file").asInstanceOf[File]
    val fileName = request.getRequest.getOrDefault("fileName", "").asInstanceOf[String]
    if (file == null) throw new ClientException("ERR_INVALID_DATA", "Please provide a valid file.")
    try {
      TermSheetReader.read(file, fileName) match {
        case f: TermSheetReader.ParseFailure =>
          throw new ClientException(f.errCode, f.errMsg)
        case s: TermSheetReader.ParseSuccess =>
          s
      }
    } finally {
      FileUtils.deleteQuietly(file)
    }
  }

  private def sequentially[A, B](items: List[A])(f: A => Future[B])(implicit ec: ExecutionContext): Future[List[B]] =
    items.foldLeft(Future.successful(List.empty[B])) { (accFut, item) =>
      accFut.flatMap(acc => f(item).map(b => acc :+ b))
    }

  private def commitClassification(request: Request, graphId: String, frameworkId: String, result: ClassificationResult)
                                   (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val byCategory: Map[String, List[TermSheetReader.SheetRow]] = result.creates.map(_._1).groupBy(_.category)

    val createFutures: Future[List[(TermSheetReader.SheetRow, util.Map[String, AnyRef])]] =
      sequentially(byCategory.toList) { case (category, categoryRows) =>
        val categoryId = TaxonomyUtil.generateIdentifier(frameworkId, category)
        validateCategoryInstance(frameworkId, category).flatMap { categoryNode =>
          val startIndex: Int = TaxonomyUtil.getNextSequenceIndex(categoryNode)
          sequentially(categoryRows.sortBy(_.index).zipWithIndex) { case (row, posInCategory) =>
            createRow(request, categoryId, category, startIndex + posInCategory, row.index, row.code, sheetRowToMap(row),
              (n: Node) => { n.getMetadata.put(Constants.STATUS, "Review"); n }
            ).map(m => (row, m))
          }
        }
      }.map(_.flatten)

    createFutures.flatMap { createResults =>
      val createIdByRowIndex: Map[Int, String] = createResults.collect {
        case (row, m) if m.get("identifier") != null => row.index -> m.get("identifier").asInstanceOf[String]
      }.toMap
      val createFailureByIndex: Map[Int, (String, String)] = createResults.collect {
        case (row, m) if "FAILED".equals(m.get("status")) =>
          row.index -> (Option(m.get("errCode")).map(_.toString).getOrElse("ERR_TERM_CODE_REQUIRED"),
            Option(m.get("errMsg")).map(_.toString).getOrElse("Internal Server Error"))
      }.toMap

      if (createFailureByIndex.nonEmpty) {
        Future(buildCommitFailureResponse(result, createFailureByIndex, createIdByRowIndex.keySet,
          "ERR_COMMIT_FAILED", "Commit aborted: one or more rows failed to write. Any sibling rows that " +
            "already wrote were left as-is (not rolled back) -- see each row's own errCode."))
      } else {
        val createAssocRows: List[util.Map[String, AnyRef]] = result.creates.flatMap { case (row, ids) =>
          createIdByRowIndex.get(row.index).map(id => buildAssociateRow(id, ids, None, None))
        }
        val updateAssocRows: List[util.Map[String, AnyRef]] = result.updates.map { case (term, row, ids) =>
          buildAssociateRow(term.identifier, ids,
            if (row.name != term.name) Some(row.name) else None,
            if (row.description != term.description) Some(row.description) else None)
        }
        val combinedRows: util.List[util.Map[String, AnyRef]] = (createAssocRows ++ updateAssocRows).asJava

        val associateFuture: Future[util.List[util.Map[String, AnyRef]]] =
          if (combinedRows.isEmpty) Future(new util.ArrayList[util.Map[String, AnyRef]]()) else associateTerms(combinedRows)

        associateFuture.flatMap { _ =>
          val retireFuture: Future[util.Map[String, Node]] =
            if (result.retireIdentifiers.isEmpty) Future(new util.HashMap[String, Node]())
            else {
              val bulkReq = new Request()
              bulkReq.setContext(new util.HashMap[String, AnyRef]() {{ put("graph_id", graphId) }})
              bulkReq.put("identifiers", result.retireIdentifiers.asJava)
              bulkReq.put("metadata", new util.HashMap[String, AnyRef]() {{ put("status", "Retired") }})
              DataNode.bulkUpdate(bulkReq)
            }
          retireFuture.map(_ => buildCommitSuccessResponse(result))
        }
      }
    }
  }

  def downloadTerms(request: Request)(implicit oec: OntologyEngineContext, ss: StorageService, ec: ExecutionContext): Future[Response] = {
    val frameworkId = request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String]
    val graphId = request.getContext.getOrDefault("graph_id", "domain").asInstanceOf[String]
    if (frameworkId.isEmpty) throw new ClientException("ERR_INVALID_FRAMEWORK_ID", "Please provide a valid framework identifier")
    fetchAttachedCategories(graphId, frameworkId).flatMap { attachedCategories =>
      fetchActiveTerms(graphId, frameworkId, attachedCategories).flatMap { activeTerms =>
        val byIdentifier: Map[String, ActiveTerm] = activeTerms.map(t => t.identifier -> t).toMap
        val rows = activeTerms.map { t =>
          val tokens = t.associations.flatMap(byIdentifier.get).map(target => s"${target.category}:${target.code}")
          (t, tokens)
        }
        val xlsxFile = buildDownloadWorkbook(frameworkId, attachedCategories, rows)
        try {
          val folder = Platform.getString("cloud_storage.competencyframework.folder", "competencyframework/xlsx")
          val uploaded = ss.uploadFile(folder, xlsxFile)
          Future.successful(ResponseHandler.OK.put("fileUrl", uploaded(1))
            .put("ttl", Platform.getString("cloud_storage.upload.url.ttl", "86400")))
        } finally {
          FileUtils.deleteQuietly(xlsxFile)
        }
      }
    }
  }

  private def buildDownloadWorkbook(frameworkId: String, attachedCategories: Set[String], rows: List[(ActiveTerm, List[String])]): File = {
    val workbook = new XSSFWorkbook()
    try {
      val sheet = workbook.createSheet("Terms")
      val headerRow = sheet.createRow(0)
      TermSheetReader.REQUIRED_HEADERS.zipWithIndex.foreach { case (h, i) => headerRow.createCell(i).setCellValue(h) }

      val textFormatStyle = workbook.createCellStyle()
      textFormatStyle.setDataFormat(workbook.createDataFormat().getFormat("@")) // Code column stays Text -- see §4.9

      rows.sortBy(t => (t._1.category, t._1.code)).zipWithIndex.foreach { case ((term, assocTokens), i) =>
        val row = sheet.createRow(i + 1)
        row.createCell(0).setCellValue(term.category)
        row.createCell(1).setCellValue(term.name)
        val codeCell = row.createCell(2)
        codeCell.setCellStyle(textFormatStyle)
        codeCell.setCellValue(term.code)
        row.createCell(3).setCellValue(assocTokens.mkString(","))
        row.createCell(4).setCellValue(term.description)
      }

      if (attachedCategories.nonEmpty) {
        val dvHelper = new XSSFDataValidationHelper(sheet)
        val constraint = dvHelper.createExplicitListConstraint(attachedCategories.toArray)
        // Rows 1..1000: a generous range so new rows a user adds while editing also get the dropdown.
        val addressList = new CellRangeAddressList(1, 1000, 0, 0)
        val validation = dvHelper.createValidation(constraint, addressList)
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP)
        validation.setShowErrorBox(true)
        validation.createErrorBox("Invalid Category", "Please choose a category from the dropdown list.")
        sheet.addValidationData(validation)
      }

      val tempDir = new File(Platform.getString("competencyframework.upload.temp_location", "/tmp/competencyframework"))
      tempDir.mkdirs()
      val file = new File(tempDir, s"$frameworkId.xlsx")
      val fos = new FileOutputStream(file)
      try workbook.write(fos) finally fos.close()
      file
    } finally workbook.close()
  }
}
