package org.sunbird.utils.taxonomy

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.Slug
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.enums.RelationTypes
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.service.common.DACErrorCodeConstants
import org.sunbird.utils.Constants

import java.util
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

/**
 * Shared utility methods used across taxonomy actors (TermActor, CategoryInstanceActor, etc.)
 * that were previously duplicated in each actor class.
 */
object TaxonomyUtil {

  /** True if `e` is the graph's unique-constraint violation (e.g. a duplicate term/category code). */
  def isDuplicateCode(e: ClientException): Boolean =
    StringUtils.equals(e.getErrCode, DACErrorCodeConstants.CONSTRAINT_VALIDATION_FAILED.name())

  /**
   * Generates a slugified composite identifier from a scope (e.g. frameworkId or categoryId)
   * and a code. Returns null when scopeId is blank to preserve backward compatibility.
   */
  def generateIdentifier(scopeId: String, code: String): String =
    if (StringUtils.isNotBlank(scopeId)) Slug.makeSlug(scopeId + "_" + code) else null

  /**
   * Calculates the next available sequence index from a node's SEQUENCE_MEMBERSHIP
   * out-relations. Returns 1 if the node has no existing sequence members,
   * otherwise returns max(existing index) + 1.
   */
  def getNextSequenceIndex(node: Node): Integer = {
    val indexList = (node.getOutRelations.asScala ++ node.getInRelations.asScala)
      .filter(r =>
        StringUtils.equals(r.getRelationType, RelationTypes.SEQUENCE_MEMBERSHIP.relationName()) &&
          StringUtils.equals(r.getStartNodeId, node.getIdentifier))
      .map(r => r.getMetadata
        .getOrDefault("IL_SEQUENCE_INDEX", 1.asInstanceOf[Number])
        .asInstanceOf[Number].intValue())
    if (indexList.nonEmpty) indexList.max + 1 else 1
  }

  def validateCategoryInstance(frameworkId: String, categoryId: String, notFoundErrCode: String, notFoundErrMsg: String)
                               (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
    val categoryInstanceId = generateIdentifier(frameworkId, categoryId)
    val getCategoryInstanceReq = new Request()
    getCategoryInstanceReq.setContext(new util.HashMap[String, AnyRef]() {
      {
        put("graph_id", "domain")
        put(Constants.SCHEMA_NAME, Constants.CATEGORY_INSTANCE_SCHEMA_NAME)
        put(Constants.VERSION, Constants.CATEGORY_INSTANCE_SCHEMA_VERSION)
      }
    })
    getCategoryInstanceReq.put(Constants.IDENTIFIER, categoryInstanceId)
    DataNode.read(getCategoryInstanceReq).map(node =>
      if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, categoryInstanceId)) node
      else throw new ClientException(notFoundErrCode, notFoundErrMsg)
    )
  }
}
