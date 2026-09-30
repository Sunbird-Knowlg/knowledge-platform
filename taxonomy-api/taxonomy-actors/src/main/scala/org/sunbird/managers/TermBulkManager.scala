package org.sunbird.managers

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.Platform
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ResponseCode}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model.{Filter, MetadataCriterion, Node, SearchConditions, SearchCriteria}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.utils.{Constants, CsvUtil, FutureUtil}
import org.sunbird.utils.taxonomy.TaxonomyUtil

import java.io.File
import java.util
import java.util.Locale
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object TermBulkManager {

  private val SEQUENCE_RELATION = "hasSequenceMember"
  private val ASSOCIATION_RELATION = "associatedTo"


  private def commitClassification(request: Request, graphId: String, frameworkId: String, result: TermSheetClassifier.ClassificationResult)
                                   (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val byCategory: Map[String, List[TermSheetReader.SheetRow]] = result.creates.map(_._1).groupBy(_.category)

    val createFutures: Future[List[(TermSheetReader.SheetRow, Either[(String, String), String])]] =
      FutureUtil.sequentially(byCategory.toList) { case (category, categoryRows) =>
        val categoryId = TaxonomyUtil.generateIdentifier(frameworkId, category)
        validateCategoryInstance(frameworkId, category).flatMap { categoryNode =>
          val startIndex: Int = TaxonomyUtil.getNextSequenceIndex(categoryNode)
          FutureUtil.sequentially(categoryRows.sortBy(_.index).zipWithIndex) { case (row, posInCategory) =>
            val rowData = new util.HashMap[String, AnyRef]()
            rowData.put(Constants.CODE, row.code)
            rowData.put("name", row.name)
            rowData.put("description", row.description)
            createRow(request, categoryId, category, startIndex + posInCategory, row.index, row.code, rowData)
              .map(result => (row, result))
          }
        }
      }.map(_.flatten)

    createFutures.flatMap { createResults =>
      val createIdByRowIndex: Map[Int, String] = createResults.collect {
        case (row, Right(identifier)) => row.index -> identifier
      }.toMap
      val createFailureByIndex: Map[Int, (String, String)] = createResults.collect {
        case (row, Left(err)) => row.index -> err
      }.toMap

      if (createFailureByIndex.nonEmpty) {
        Future(TermSheetClassifier.buildCommitFailureResponse(result, createFailureByIndex, createIdByRowIndex.keySet,
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

        val identifierToRowIndex: Map[String, Int] =
          createIdByRowIndex.map { case (idx, id) => id -> idx } ++
            result.updates.map { case (term, row, _) => term.identifier -> row.index }.toMap

        associateFuture.flatMap { associateResults =>
          val associateFailureByIndex: Map[Int, (String, String)] = associateResults.asScala.flatMap { row =>
            if ("FAILED".equals(row.get("status")))
              identifierToRowIndex.get(row.get("identifier").asInstanceOf[String])
                .map(idx => idx -> (row.get("errCode").asInstanceOf[String], row.get("errMsg").asInstanceOf[String]))
            else None
          }.toMap

          if (associateFailureByIndex.nonEmpty) {
            val associateSuccessIndices: Set[Int] = associateResults.asScala.collect {
              case row if "SUCCESS".equals(row.get("status")) => identifierToRowIndex.get(row.get("identifier").asInstanceOf[String])
            }.flatten.toSet
            Future(TermSheetClassifier.buildCommitFailureResponse(result, associateFailureByIndex,
              createIdByRowIndex.keySet ++ associateSuccessIndices,
              "ERR_ASSOCIATE_FAILED", "Commit aborted: one or more rows failed during metadata/association write. " +
                "Any sibling rows that already wrote (creates, and other association updates) were left as-is " +
                "(not rolled back) -- see each row's own errCode."))
          } else {
            val retireFuture: Future[util.Map[String, Node]] =
              if (result.retireIdentifiers.isEmpty) Future(new util.HashMap[String, Node]())
              else {
                val bulkReq = new Request()
                bulkReq.setContext(new util.HashMap[String, AnyRef]() {{ put("graph_id", graphId) }})
                bulkReq.put("identifiers", result.retireIdentifiers.asJava)
                bulkReq.put("metadata", new util.HashMap[String, AnyRef]() {{ put("status", "Retired") }})
                DataNode.bulkUpdate(bulkReq)
              }
            retireFuture.map(_ => TermSheetClassifier.buildCommitSuccessResponse(result))
          }
        }
      }
    }
  }

  private[managers] def createRow(request: Request, categoryId: String, category: String, seqIndex: Int, reportIndex: Int,
                                   code: String, row: util.Map[String, AnyRef], dataModifier: Node => Node = (n => n))
                                  (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Either[(String, String), String]] = {
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

    Future(rowRequest).flatMap(DataNode.create(_, dataModifier)).map(termNode => Right(termNode.getIdentifier)) recover {
      case e: ClientException if TaxonomyUtil.isDuplicateCode(e) =>
        Left("ERR_DUPLICATE_CODE" -> s"Term with code '$code' already exists")
      case e: ClientException =>
        Left("ERR_TERM_CODE_REQUIRED" -> "Unique code is required for Term")
      case e: Exception =>
        Left(ResponseCode.SERVER_ERROR.name -> "Internal Server Error")
    }
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

    def associateTerms(rows: util.List[util.Map[String, AnyRef]])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[util.List[util.Map[String, AnyRef]]] =
    Future.sequence(rows.asScala.zipWithIndex.map { case (row, i) => updateOneRow(row, i) }.toList).map(_.asJava)

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

    def failureRow(errCode: String, errMsg: String): util.Map[String, AnyRef] = {
      val row = new util.HashMap[String, AnyRef]()
      row.put("index", index.asInstanceOf[Integer])
      row.put("identifier", identifier)
      row.put("status", "FAILED")
      row.put("errCode", errCode)
      row.put("errMsg", errMsg)
      row
    }

    Future(rowRequest).flatMap(DataNode.update(_)).map { node =>
      val row = new util.HashMap[String, AnyRef]()
      row.put("index", index.asInstanceOf[Integer])
      row.put("identifier", node.getIdentifier)
      row.put("status", "SUCCESS")
      row
    } recover {
      case e: ResourceNotFoundException => failureRow("RESOURCE_NOT_FOUND", e.getMessage)
      case e: ClientException => failureRow(e.getErrCode, e.getMessage)
      case e: Exception => failureRow(ResponseCode.SERVER_ERROR.name, "Internal Server Error")
    }
  }

  def bulkUpdateTerm(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val rows: util.List[util.Map[String, AnyRef]] = request.getRequest.get("terms") match {
      case r: util.List[_] => r.asInstanceOf[util.List[util.Map[String, AnyRef]]]
      case _ => throw new ClientException("ERR_INVALID_TERM_REQUEST", "Invalid Request! Please Provide Valid Request.")
    }
    Future.sequence(rows.asScala.zipWithIndex.map { case (row, i) => updateOneRow(row, i) }.toList).map { results =>
      val resultsJava = results.asJava
      if (results.exists(r => "FAILED".equals(r.get("status"))))
        ResponseHandler.ERROR(ResponseCode.PARTIAL_SUCCESS, ResponseCode.PARTIAL_SUCCESS.name, "Partial Success", "results", resultsJava)
      else
        ResponseHandler.OK.put("results", resultsJava)
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

  private def queryFrameworkNodes(graphId: String, frameworkId: String, objectTypes: List[String], statusFilter: Filter,
                                   categories: Option[Set[String]] = None)
                                  (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[List[Node]] = {
    if (categories.exists(_.isEmpty)) Future(List.empty)
    else {
      val filters = new util.ArrayList[Filter]()
      filters.add(new Filter(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), SearchConditions.OP_IN, objectTypes.asJava))
      categories.foreach(cats => filters.add(new Filter("category", SearchConditions.OP_IN, new util.ArrayList[String](cats.asJavaCollection))))
      filters.add(statusFilter)
      val mc = MetadataCriterion.create(filters)
      val criteria = new SearchCriteria { { addMetadata(mc); setCountQuery(false); setGraphId(graphId) } }
      oec.graphService.getNodeByUniqueIds(graphId, criteria).map { nodes =>
        val prefix = frameworkId.toLowerCase(Locale.ROOT) + "_"
        nodes.asScala.filter(n => Option(n.getIdentifier).exists(_.toLowerCase(Locale.ROOT).startsWith(prefix))).toList
      }
    }
  }

  private[managers] def fetchActiveTerms(graphId: String, frameworkId: String, categories: Set[String])
                                         (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[List[TermSheetClassifier.ActiveTerm]] =
    queryFrameworkNodes(graphId, frameworkId, List("Term"), new Filter("status", SearchConditions.OP_NOT_EQUAL, "Retired"), Some(categories)).map { nodes =>
      nodes.map { n =>
        val md = n.getMetadata
        TermSheetClassifier.ActiveTerm(
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
      }
    }

  private[managers] def fetchRetiredTermKeys(graphId: String, frameworkId: String, categories: Set[String])
                                             (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Set[(String, String)]] =
    queryFrameworkNodes(graphId, frameworkId, List("Term"), new Filter("status", SearchConditions.OP_EQUAL, "Retired"), Some(categories)).map { nodes =>
      nodes.map { n =>
        val md = n.getMetadata
        TermSheetClassifier.normKey(md.getOrDefault("category", "").asInstanceOf[String], md.getOrDefault("code", "").asInstanceOf[String])
      }.toSet
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

  def bulkValidateTerm(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val frameworkId = request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String]
    val graphId = request.getContext.getOrDefault("graph_id", "domain").asInstanceOf[String]
    if (frameworkId.isEmpty) throw new ClientException("ERR_INVALID_FRAMEWORK_ID", "Please provide a valid framework identifier")
    FrameworkManager.assertFrameworkEditable(graphId, frameworkId).flatMap { _ =>
      TermSheetReader.readOrThrow(request) match {
        case TermSheetReader.ParseSuccess(rows, skippedHeaderRows) =>
          fetchAttachedCategories(graphId, frameworkId).flatMap { attachedCategories =>
            fetchActiveTerms(graphId, frameworkId, attachedCategories).flatMap { activeTerms =>
              fetchRetiredTermKeys(graphId, frameworkId, attachedCategories).map { retiredKeys =>
                TermSheetClassifier.buildValidateResponse(TermSheetClassifier.classifyAndValidate(frameworkId, rows, activeTerms, attachedCategories, retiredKeys, skippedHeaderRows))
              }
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
      TermSheetReader.readOrThrow(request) match {
        case TermSheetReader.ParseSuccess(rows, skippedHeaderRows) =>
          fetchAttachedCategories(graphId, frameworkId).flatMap { attachedCategories =>
            fetchActiveTerms(graphId, frameworkId, attachedCategories).flatMap { activeTerms =>
              fetchRetiredTermKeys(graphId, frameworkId, attachedCategories).flatMap { retiredKeys =>
                val result = TermSheetClassifier.classifyAndValidate(frameworkId, rows, activeTerms, attachedCategories, retiredKeys, skippedHeaderRows)
                if (!result.valid) Future(TermSheetClassifier.buildCommitFailureResponse(result))
                else commitClassification(request, graphId, frameworkId, result)
              }
            }
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
        val rows = TermSheetClassifier.buildDownloadRows(activeTerms, attachedCategories)
        val csvFile = buildDownloadCsv(frameworkId, rows)
        try {
          val folder = Platform.getString("cloud_storage.competencyframework.folder", "competencyframework/csv")
          val uploaded = ss.uploadFile(folder, csvFile)
          Future.successful(ResponseHandler.OK.put("fileUrl", uploaded(1))
            .put("ttl", Platform.getString("cloud_storage.upload.url.ttl", "86400")))
        } finally {
          FileUtils.deleteQuietly(csvFile)
        }
      }
    }
  }

  private def buildDownloadCsv(frameworkId: String, rows: List[List[String]]): File = {
    val tempDir = new File(Platform.getString("competencyframework.upload.temp_location", "/tmp/competencyframework"))
    tempDir.mkdirs()
    val file = new File(tempDir, s"$frameworkId.csv")
    CsvUtil.writeCsv(file, TermSheetReader.REQUIRED_HEADERS, rows)
  }
}
