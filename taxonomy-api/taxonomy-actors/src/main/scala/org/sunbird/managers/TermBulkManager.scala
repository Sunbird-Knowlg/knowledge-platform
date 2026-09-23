package org.sunbird.managers

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ResponseCode}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.service.common.DACErrorCodeConstants
import org.sunbird.utils.Constants
import org.sunbird.utils.taxonomy.TaxonomyUtil

import java.util
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

// Bulk-array counterpart to TermActor's single-term create/update/retire 
object TermBulkManager {

  def isDuplicateCode(e: ClientException): Boolean =
    StringUtils.equals(e.getErrCode, DACErrorCodeConstants.CONSTRAINT_VALIDATION_FAILED.name())

  def bulkCreateTerm(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val rows: util.List[util.Map[String, AnyRef]] = getBulkRequestData(request, "terms")
    val frameworkId = request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String]
    val category = request.getRequest.getOrDefault(Constants.CATEGORY, "").asInstanceOf[String]
    val categoryId = TaxonomyUtil.generateIdentifier(frameworkId, category)
    validateCategoryInstance(request).flatMap(node => {
      if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, categoryId)) {
        val startIndex: Integer = TaxonomyUtil.getNextSequenceIndex(node)
        val futures = rows.asScala.zipWithIndex.map { case (row, i) =>
          val code = row.getOrDefault(Constants.CODE, "").asInstanceOf[String]

          val categoryList = new util.ArrayList[util.Map[String, AnyRef]]()
          val relationMap = new util.HashMap[String, AnyRef]()
          relationMap.put("identifier", categoryId)
          relationMap.put("index", (startIndex + i).asInstanceOf[Integer])
          categoryList.add(relationMap)

          val rowRequest = new Request(request, request.getObjectType)
          rowRequest.setRequest(new util.HashMap[String, AnyRef](row))
          rowRequest.getRequest.put(Constants.CATEGORY, category)
          rowRequest.getRequest.put(Constants.IDENTIFIER, TaxonomyUtil.generateIdentifier(categoryId, code))
          rowRequest.put("categories", categoryList)

          // DataNode.create can throw synchronously (schema validation, e.g. a blank code)
          // rather than failing the Future -- Future(...).flatMap defers that throw so it
          // lands in this row's own recover instead of aborting the whole batch.
          Future(rowRequest).flatMap(DataNode.create(_)).map(termNode => successRow(i, code, termNode.getIdentifier)) recover {
            case e: ClientException if isDuplicateCode(e) =>
              failureRow(i, code, "ERR_DUPLICATE_CODE", s"Term with code '$code' already exists")
            case e: ClientException =>
              failureRow(i, code, "ERR_TERM_CODE_REQUIRED", "Unique code is required for Term")
            case e: Exception =>
              failureRow(i, code, ResponseCode.SERVER_ERROR.name, "Internal Server Error")
          }
        }
        Future.sequence(futures.toList).map(results => buildResponse(results.asJava))
      } else throw new ClientException("ERR_INVALID_CATEGORY_ID", "Please provide valid category")
    })
  }

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

  // Same body as TermActor's private validateCategoryInstance -- kept local since TermActor
  // stays untouched and there's only this one call site outside it.
  private def validateCategoryInstance(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
    val frameworkId = request.getRequest.getOrDefault(Constants.FRAMEWORK, "").asInstanceOf[String]
    val categoryId = request.getRequest.getOrDefault(Constants.CATEGORY, "").asInstanceOf[String]
    if (frameworkId.isEmpty()) throw new ClientException("ERR_INVALID_FRAMEWORK_ID", s"Invalid FrameworkId: '${frameworkId}' for Term ")
    if (categoryId.isEmpty()) throw new ClientException("ERR_INVALID_CATEGORY_ID", s"Invalid CategoryId: '${categoryId}' for Term")
    val categoryInstanceId = TaxonomyUtil.generateIdentifier(frameworkId, categoryId)
    val getCategoryInstanceReq = new Request()
    getCategoryInstanceReq.setContext(new util.HashMap[String, AnyRef]() {
      {
        putAll(request.getContext)
      }
    })
    getCategoryInstanceReq.getContext.put(Constants.SCHEMA_NAME, Constants.CATEGORY_INSTANCE_SCHEMA_NAME)
    getCategoryInstanceReq.getContext.put(Constants.VERSION, Constants.CATEGORY_INSTANCE_SCHEMA_VERSION)
    getCategoryInstanceReq.put(Constants.IDENTIFIER, categoryInstanceId)
    DataNode.read(getCategoryInstanceReq)(oec, ec).map(node => {
      if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, categoryInstanceId)) node
      else throw new ClientException("ERR_CHANNEL_NOT_FOUND/ ERR_FRAMEWORK_NOT_FOUND", s"Given channel/framework is not related to given category")
    })(ec)
  }
}
