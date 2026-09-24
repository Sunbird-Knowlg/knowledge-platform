package org.sunbird.content.enrichmentobject.mgr

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.{Filter, MetadataCriterion, Node, SearchConditions, SearchCriteria}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.{CategoryDefinitionValidator, ObjectCategoryDefinition}
import org.sunbird.util.RequestUtil

import java.util
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

/**
 * Implements the generic operations that apply to every EnrichmentObject category:
 * create, list, update, upload, approve, and reject. Category-specific fields are
 * resolved and merged onto the base schema through the platform's
 * ObjectCategoryDefinition mechanism, so this object never branches on which
 * category it is handling.
 */
object EnrichmentObjectManager {

  private val GRAPH_ID = "domain"
  private val SCHEMA_VERSION = "1.0"
  private val OBJECT_TYPE = "EnrichmentObject"
  private val SCHEMA_NAME = "enrichmentobject"
  private val PARENT_OBJECT_TYPE = "Content"
  private val PARENT_SCHEMA_NAME = "content"

  /**
   * Creates a new EnrichmentObject under the given parent, or returns an existing
   * match if one is found for the category's declared identity fields.
   *
   * @param request the create request; its metadata must include `enrichmentObjectType`
   *                and `parentId`
   * @param oec graph engine context
   * @param ec execution context
   * @return the newly created or matched EnrichmentObject
   * @throws ClientException if `enrichmentObjectType` or `parentId` is missing
   */
  def create(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val metadata = request.getRequest
    val enrichmentObjectType = metadata.getOrDefault("enrichmentObjectType", "").asInstanceOf[String]
    val parentId = metadata.getOrDefault("parentId", "").asInstanceOf[String]
    if (StringUtils.isBlank(enrichmentObjectType))
      throw new ClientException("ERR_ENRICHMENT_OBJECT_TYPE_REQUIRED", "enrichmentObjectType is required.")
    if (StringUtils.isBlank(parentId))
      throw new ClientException("ERR_PARENT_ID_REQUIRED", "parentId is required.")

    resolveParent(parentId).flatMap { parentNode =>
      val parentType = parentNode.getObjectType.replace("Image", "")
      val channel = metadata.getOrDefault("channel", "all").asInstanceOf[String]
      matchUniqueOn(parentId, enrichmentObjectType, channel, metadata).flatMap {
        case Some(existing) => Future.successful(toResponse(existing))
        case None => persist(request, enrichmentObjectType, parentId, parentType, channel)
      }
    }
  }

  /**
   * Writes fields onto an existing EnrichmentObject. Rejected outright if the node's
   * current status is Live. `status` may only be set to `Processing` or `Failed` here —
   * moving to `Live`/`Review` is exclusively `approve`'s job. Never emits an event.
   *
   * @param request the update request; its metadata is the set of fields to write
   * @param identifier the EnrichmentObject being updated
   * @param oec graph engine context
   * @param ec execution context
   * @return identifier plus the fields that were written
   * @throws org.sunbird.common.exception.ResourceNotFoundException if identifier does
   *         not resolve to a real node
   * @throws ClientException if the node's current status is Live, or if `status` is
   *         being set to anything other than `Processing`/`Failed`
   */
  def update(request: Request, identifier: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    resolveByIdentifier(identifier).flatMap { existing =>
      val currentStatus = existing.getMetadata.getOrDefault("status", "Draft").asInstanceOf[String]
      if (StringUtils.equalsIgnoreCase(currentStatus, "Live"))
        throw new ClientException("ERR_EDIT_LOCKED", "Cannot update — current status is Live and cannot be edited.")

      val metadata = request.getRequest
      val requestedStatus = metadata.getOrDefault("status", "").asInstanceOf[String]
      if (StringUtils.isNotBlank(requestedStatus) && !StringUtils.equalsAnyIgnoreCase(requestedStatus, "Processing", "Failed"))
        throw new ClientException("ERR_STATUS_TRANSITION_NOT_ALLOWED",
          "status can only be set to Processing or Failed via update; Live/Review require approve.")

      val requestedFields = metadata.keySet()
      val context = new util.HashMap[String, AnyRef]()
      context.put("graph_id", GRAPH_ID)
      context.put("version", SCHEMA_VERSION)
      context.put("objectType", OBJECT_TYPE)
      context.put("schemaName", SCHEMA_NAME)
      request.setContext(context)
      request.setObjectType(OBJECT_TYPE)
      request.getContext.put("identifier", identifier)

      RequestUtil.restrictProperties(request)
      DataNode.update(request).map { node =>
        val result = new util.HashMap[String, AnyRef]()
        result.put("identifier", node.getIdentifier)
        requestedFields.asScala.foreach(key => result.put(key, node.getMetadata.get(key)))
        ResponseHandler.OK.putAll(result)
      }
    }
  }

  /**
   * Lists EnrichmentObjects under a parent, optionally narrowed by any other
   * metadata fields present in the request (e.g. `enrichmentObjectType`, `status`).
   * Retired nodes are excluded unless the caller explicitly filters on `status`.
   *
   * @param request the list request; its metadata must include `parentId` and may
   *                include any additional metadata fields to filter on
   * @param oec graph engine context
   * @param ec execution context
   * @return the matching EnrichmentObjects and their count
   * @throws ClientException if `parentId` is missing
   */
  def list(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val metadata = request.getRequest
    val parentId = metadata.getOrDefault("parentId", "").asInstanceOf[String]
    if (StringUtils.isBlank(parentId))
      throw new ClientException("ERR_PARENT_ID_REQUIRED", "parentId is required.")

    val filters = metadata.asScala.toMap - "parentId"
    search(parentId, filters)
  }

  /**
   * Reads an existing EnrichmentObject by its own identifier.
   *
   * @param identifier the EnrichmentObject to resolve
   * @param oec graph engine context
   * @param ec execution context
   * @return the resolved node
   * @throws org.sunbird.common.exception.ResourceNotFoundException if identifier does
   *         not resolve to a real node
   */
  private def resolveByIdentifier(identifier: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
    val readReq = new Request()
    val context = new util.HashMap[String, AnyRef]()
    context.put("graph_id", GRAPH_ID)
    context.put("version", SCHEMA_VERSION)
    context.put("objectType", OBJECT_TYPE)
    context.put("schemaName", SCHEMA_NAME)
    readReq.setContext(context)
    readReq.put("identifier", identifier)
    readReq.put("fields", new util.ArrayList[String]())
    DataNode.read(readReq)
  }

  /**
   * Reads the parent node to confirm it exists and to determine its actual objectType.
   *
   * `parentType` is never accepted from the caller; it is always derived here. Content
   * is the only supported parent type today; supporting another type means extending
   * `relations.parent.objects` in the EnrichmentObject config and generalizing this
   * lookup accordingly.
   *
   * @param parentId identifier of the node this EnrichmentObject will attach to
   * @param oec graph engine context
   * @param ec execution context
   * @return the resolved parent node
   * @throws org.sunbird.common.exception.ResourceNotFoundException if parentId does
   *         not resolve to a real node
   */
  private def resolveParent(parentId: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
    val readReq = new Request()
    val context = new util.HashMap[String, AnyRef]()
    context.put("graph_id", GRAPH_ID)
    context.put("version", SCHEMA_VERSION)
    context.put("objectType", PARENT_OBJECT_TYPE)
    context.put("schemaName", PARENT_SCHEMA_NAME)
    readReq.setContext(context)
    readReq.put("identifier", parentId)
    readReq.put("fields", new util.ArrayList[String]())
    DataNode.read(readReq)
  }

  /**
   * Runs the idempotent-create check for the given category: resolves the category's
   * declared `uniqueOn` configuration and, if any identity fields are declared,
   * searches for a matching sibling under the parent.
   *
   * @param parentId parent under which siblings are matched
   * @param enrichmentObjectType category to resolve and match against
   * @param channel tenant scope used to resolve the category definition
   * @param requestMetadata raw request fields, checked against the category's
   *                        declared `uniqueOn` fields
   * @param oec graph engine context
   * @param ec execution context
   * @return an existing matching sibling, if any
   * @throws org.sunbird.common.exception.ResourceNotFoundException if
   *         enrichmentObjectType has no registered category definition
   * @throws ClientException if the category declares identity fields but the request
   *         matches none of them
   */
  private def matchUniqueOn(parentId: String, enrichmentObjectType: String, channel: String, requestMetadata: util.Map[String, AnyRef])
                            (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Option[Node]] = {
    val validator = new CategoryDefinitionValidator(SCHEMA_NAME, SCHEMA_VERSION)
      .loadSchema(ObjectCategoryDefinition(enrichmentObjectType, OBJECT_TYPE, channel))
    if (!validator.getConfig.hasPath("uniqueOn"))
      Future.successful(None)
    else {
      val uniqueOnEntries = validator.getConfig.getConfigList("uniqueOn").asScala.toList
      if (uniqueOnEntries.isEmpty) searchSiblings(parentId, enrichmentObjectType, Map.empty)
      else {
        val filter = buildFilter(uniqueOnEntries, requestMetadata)
        if (filter.isEmpty)
          throw new ClientException("ERR_NO_IDENTITY_SIGNAL", s"Request matches no identity field for category '$enrichmentObjectType'.")
        else searchSiblings(parentId, enrichmentObjectType, filter)
      }
    }
  }

  /**
   * Builds a match filter from the category's declared `uniqueOn` entries, applied
   * against the raw request. A field qualifies as an identity signal only if it is
   * present (non-blank) and, when the entry declares a `matchValue`, its request
   * value equals that exactly.
   *
   * @param entries the category's declared `uniqueOn` configuration entries
   * @param requestMetadata raw request fields
   * @return the fields, and their values, that qualify as identity signals
   */
  private def buildFilter(entries: List[com.typesafe.config.Config], requestMetadata: util.Map[String, AnyRef]): Map[String, AnyRef] = {
    entries.flatMap { entry =>
      val field = entry.getString("field")
      val requestValue = requestMetadata.get(field)
      val present = requestValue match {
        case s: String => StringUtils.isNotBlank(s)
        case null => false
        case _ => true
      }
      if (!present) None
      else if (entry.hasPath("matchValue")) {
        val required = entry.getAnyRef("matchValue")
        if (requestValue == required) Some(field -> requestValue) else None
      } else Some(field -> requestValue)
    }.toMap
  }

  /**
   * Searches for an existing, non-Retired sibling under the given parent and
   * category matching the given filter. A match against a Retired node is treated
   * the same as no match — a fresh node is created instead of reusing it.
   *
   * @param parentId parent to search under
   * @param enrichmentObjectType category to match against
   * @param filter field/value pairs the sibling must match
   * @param oec graph engine context
   * @param ec execution context
   * @return the matching node, if any
   */
  private def searchSiblings(parentId: String, enrichmentObjectType: String, filter: Map[String, AnyRef])
                             (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Option[Node]] = {
    val mc = MetadataCriterion.create(new util.ArrayList[Filter]() {{
      add(new Filter("parentId", SearchConditions.OP_EQUAL, parentId))
      add(new Filter("enrichmentObjectType", SearchConditions.OP_EQUAL, enrichmentObjectType))
      add(new Filter("status", SearchConditions.OP_NOT_EQUAL, "Retired"))
      filter.foreach { case (k, v) => add(new Filter(k, SearchConditions.OP_EQUAL, v)) }
    }})
    val criteria = new SearchCriteria {{ addMetadata(mc); setCountQuery(false); setGraphId(GRAPH_ID); setObjectType(OBJECT_TYPE) }}
    oec.graphService.getNodeByUniqueIds(GRAPH_ID, criteria).map { nodes =>
      if (nodes == null || nodes.isEmpty) None else Some(nodes.get(0))
    }
  }

  /**
   * Searches for EnrichmentObjects under a parent matching the given filter.
   * Retired nodes are excluded unless `filter` itself specifies `status`.
   *
   * @param parentId parent to search under
   * @param filter field/value pairs to match, in addition to `parentId`
   * @param oec graph engine context
   * @param ec execution context
   * @return the matching EnrichmentObjects and their count
   */
  private def search(parentId: String, filter: Map[String, AnyRef])
                     (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val mc = MetadataCriterion.create(new util.ArrayList[Filter]() {{
      add(new Filter("parentId", SearchConditions.OP_EQUAL, parentId))
      filter.foreach { case (k, v) => add(new Filter(k, SearchConditions.OP_EQUAL, v)) }
      if (!filter.contains("status")) add(new Filter("status", SearchConditions.OP_NOT_EQUAL, "Retired"))
    }})
    val criteria = new SearchCriteria {{ addMetadata(mc); setCountQuery(false); setGraphId(GRAPH_ID); setObjectType(OBJECT_TYPE) }}
    oec.graphService.getNodeByUniqueIds(GRAPH_ID, criteria).map { nodes =>
      val results = if (nodes == null) new util.ArrayList[util.Map[String, AnyRef]]()
      else nodes.asScala.map(toMap).asJava
      ResponseHandler.OK.put("enrichmentObjects", results).put("count", results.size.asInstanceOf[AnyRef])
    }
  }

  /**
   * Persists a new EnrichmentObject node and wires its `parent` relation to the
   * given parentId. Never publishes an event — only the status-transition operations
   * (approve/reject) do.
   *
   * `enrichmentObjectType` is stored on the node as-is; EnrichmentObject's own
   * config.json declares it as the schema's `categoryField`, so the platform's
   * category schema-merge resolves against it directly.
   *
   * @param request the original create request; its metadata is mutated in place
   *                before being persisted
   * @param enrichmentObjectType category of the node being created
   * @param parentId parent to attach the new node to
   * @param parentType objectType of the parent, as resolved by [[resolveParent]]
   * @param channel tenant scope
   * @param oec graph engine context
   * @param ec execution context
   * @return the newly created EnrichmentObject
   */
  private def persist(request: Request, enrichmentObjectType: String, parentId: String, parentType: String, channel: String)
                      (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val metadata = request.getRequest

    val context = new util.HashMap[String, AnyRef]()
    context.put("graph_id", GRAPH_ID)
    context.put("version", SCHEMA_VERSION)
    context.put("objectType", OBJECT_TYPE)
    context.put("schemaName", SCHEMA_NAME)
    if (StringUtils.isNotBlank(channel)) context.put("channel", channel)
    request.setContext(context)
    request.setObjectType(OBJECT_TYPE)

    // Must run before this method's own writes below (parentType/status/parent),
    // or restrictProperties would see those as caller-supplied and reject them.
    RequestUtil.restrictProperties(request)

    metadata.put("parentType", parentType)
    metadata.put("status", "Draft")
    metadata.put("parent", util.Arrays.asList(new util.HashMap[String, AnyRef]() {{ put("identifier", parentId) }}))

    DataNode.create(request).map(toResponse)
  }

  /**
   * Converts a persisted node into a response payload.
   *
   * Every single-node response-producing operation on this manager should route
   * through this method rather than reimplementing it.
   *
   * @param node the persisted or matched node
   * @return the response envelope's result payload
   */
  private def toResponse(node: Node): Response = ResponseHandler.OK.putAll(toMap(node))

  /**
   * Converts a node into its metadata map, plus its identifier.
   *
   * @param node the node to convert
   * @return the node's metadata, with `identifier` included
   */
  private def toMap(node: Node): util.Map[String, AnyRef] = {
    val result = new util.HashMap[String, AnyRef](node.getMetadata)
    result.put("identifier", node.getIdentifier)
    result
  }
}
