package org.sunbird.managers

import java.util
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model.{Filter, MetadataCriterion, Node, Relation, SearchConditions, SearchCriteria, SubGraph}
import org.sunbird.graph.nodes.DataNode

import org.sunbird.graph.schema.{DefinitionNode, ObjectCategoryDefinition}
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.graph.utils.NodeUtil.{convertJsonProperties, handleKeyNames}

import java.util
import java.util.{Collections, Optional}
import java.util.concurrent.{CompletionException, Executors}
import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}
import org.sunbird.utils.Constants

object FrameworkManager {
  val schemaVersion: String = "1.0"
  private val IMAGE_SUFFIX = ".img"

  /** Fetches a node, tolerating ResourceNotFoundException as None (used for the optional `.img` shadow). */
  def getOptionalNode(graphId: String, identifier: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Option[Node]] = {
    oec.graphService.getNodeByUniqueId(graphId, identifier, true, new Request()).map(node => Option(node)) recover {
      case e: CompletionException if e.getCause.isInstanceOf[ResourceNotFoundException] => None
    }
  }

  /** Hard-deletes `<frameworkId>.img` if it exists (in-progress uncommitted edit) — no-op otherwise. */
  def deleteImageNodeIfExists(graphId: String, frameworkId: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Boolean] = {
    getOptionalNode(graphId, frameworkId + IMAGE_SUFFIX).flatMap {
      case Some(_) =>
        val delRequest = new Request()
        delRequest.setContext(new util.HashMap[String, AnyRef]() {{ put("graph_id", graphId) }})
        delRequest.put(Constants.IDENTIFIER, frameworkId + IMAGE_SUFFIX)
        DataNode.deleteNode(delRequest).map(_ => true)
      case None => Future(false)
    }
  }

  private val PROMOTE_EXCLUDE_FIELDS: Set[String] =
    Set("identifier", "status", "objectType", "versionKey", "prevStatus", "isImageNodeCreated")

  private def filteredImageMetadata(imgNode: Node): util.Map[String, AnyRef] = {
    // Built as a real (mutable) java.util.HashMap -- the caller adds "version"/"status" onto it afterwards.
    val result = new util.HashMap[String, AnyRef]()
    imgNode.getMetadata.asScala.foreach { case (k, v) => if (!PROMOTE_EXCLUDE_FIELDS.contains(k)) result.put(k, v) }
    result
  }

  private val SEQUENCE_RELATION = "hasSequenceMember" // matches framework/competencyframework config.json

  /** Distinct target ids of `rels` whose type/objectType match, stripping any lingering ".img" suffix. */
  private def relationTargetIds(rels: util.List[Relation], useEndSide: Boolean, objectType: String): Set[String] = {
    if (rels == null) Set.empty
    else rels.asScala.filter(r =>
        StringUtils.equals(r.getRelationType, SEQUENCE_RELATION) &&
        StringUtils.equalsIgnoreCase(
          (if (useEndSide) r.getEndNodeObjectType else r.getStartNodeObjectType).replace("Image", ""),
          objectType))
      .map(r => (if (useEndSide) r.getEndNodeId else r.getStartNodeId).replace(IMAGE_SUFFIX, ""))
      .toSet
  }

  private def relationMap(startNodeId: String, endNodeId: String): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    m.put("startNodeId", startNodeId); m.put("endNodeId", endNodeId)
    m.put("relation", SEQUENCE_RELATION); m.put("relMetadata", new util.HashMap[String, AnyRef]())
    m
  }

  /** Diffs one relation type's desired (.img) vs current (live) target set and applies the delta on the
    * LIVE node. Per-type skip rule (see plan discrepancy D2): if .img carries ZERO edges of this type,
    * it means this edit session never touched that relation -- leave the live node's edges untouched,
    * rather than misreading "never touched" as "wants zero".
   */
  private def promoteOneRelationType(graphId: String, liveId: String, liveIds: Set[String], imgIds: Set[String],
                                      endpointIsTarget: Boolean)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Unit] = {
    if (imgIds.isEmpty) Future(())
    else {
      val toAdd = imgIds -- liveIds
      val toRemove = liveIds -- imgIds
      def maps(ids: Set[String]) = ids.map(id => if (endpointIsTarget) relationMap(liveId, id) else relationMap(id, liveId)).toList.asJava
      val addF = if (toAdd.nonEmpty) oec.graphService.createRelation(graphId, maps(toAdd)) else Future(new Response())
      addF.flatMap(_ => if (toRemove.nonEmpty) oec.graphService.removeRelation(graphId, maps(toRemove)) else Future(new Response())).map(_ => ())
    }
  }

  private def promoteRelations(graphId: String, liveNode: Node, imgNodeOpt: Option[Node])
                               (implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Unit] = imgNodeOpt match {
    case None => Future(())
    case Some(imgNode) =>
      val liveCategories = relationTargetIds(liveNode.getOutRelations, useEndSide = true, "CategoryInstance")
      val imgCategories = relationTargetIds(imgNode.getOutRelations, useEndSide = true, "CategoryInstance")
      val liveChannels = relationTargetIds(liveNode.getInRelations, useEndSide = false, "Channel")
      val imgChannels = relationTargetIds(imgNode.getInRelations, useEndSide = false, "Channel")
      promoteOneRelationType(graphId, liveNode.getIdentifier, liveCategories, imgCategories, endpointIsTarget = true)
        .flatMap(_ => promoteOneRelationType(graphId, liveNode.getIdentifier, liveChannels, imgChannels, endpointIsTarget = false))
  }

  def publishFramework(request: Request, frameworkId: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
    val graphId = request.getContext.getOrDefault("graph_id", "domain").asInstanceOf[String]
    getOptionalNode(graphId, frameworkId + IMAGE_SUFFIX).flatMap { imgNodeOpt =>
      oec.graphService.getNodeByUniqueId(graphId, frameworkId, true, new Request(request)).flatMap { liveNode =>
        promoteRelations(graphId, liveNode, imgNodeOpt).flatMap { _ =>
          val currentVersion: Int = Option(liveNode.getMetadata.get("version"))
            .map(_.asInstanceOf[Number].intValue()).getOrElse(0)
          val updateMetadata: util.Map[String, AnyRef] =
            imgNodeOpt.map(filteredImageMetadata).getOrElse(new util.HashMap[String, AnyRef]())
          updateMetadata.put("version", Integer.valueOf(currentVersion + 1)) // business publish-counter, not Constants.VERSION
          updateMetadata.put("status", "Live")

          val updateReq = new Request(request)
          updateReq.getContext.put(Constants.IDENTIFIER, frameworkId)
          updateReq.getContext.put("versioning", "disabled") // update the live node directly, never re-clone .img
          updateReq.setRequest(updateMetadata)
          DataNode.update(updateReq).flatMap { updatedLive =>
            deleteImageNodeIfExists(graphId, frameworkId).map(_ => updatedLive)
          }
        }
      }
    }
  }

  def publishDescendants(graphId: String, frameworkId: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[util.Map[String, Node]] = {
    val mc = MetadataCriterion.create(new util.ArrayList[Filter]() {{
      add(new Filter(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), SearchConditions.OP_IN,
        new util.ArrayList[String]() {{ add("Term"); add("CategoryInstance") }}))
      add(new Filter("status", SearchConditions.OP_IN,
        new util.ArrayList[String]() {{ add("Draft"); add("Review") }}))
    }})
    val criteria = new SearchCriteria {{ addMetadata(mc); setCountQuery(false); setGraphId(graphId) }}
    oec.graphService.getNodeByUniqueIds(graphId, criteria).flatMap { nodes =>
      val prefix = frameworkId.toLowerCase + "_"
      val ids: util.List[String] = nodes.asScala
        .filter(n => Option(n.getIdentifier).exists(_.toLowerCase.startsWith(prefix)))
        .map(_.getIdentifier).toList.asJava
      if (ids.isEmpty) Future(new util.HashMap[String, Node]())
      else {
        val bulkReq = new Request()
        bulkReq.setContext(new util.HashMap[String, AnyRef]() {{ put("graph_id", graphId) }})
        bulkReq.put("identifiers", ids)
        bulkReq.put("metadata", new util.HashMap[String, AnyRef]() {{ put("status", "Live") }})
        DataNode.bulkUpdate(bulkReq)
      }
    }
  }
  def validateTranslationMap(request: Request) = {
    val translations: util.Map[String, AnyRef] = Optional.ofNullable(request.get("translations").asInstanceOf[util.HashMap[String, AnyRef]]).orElse(new util.HashMap[String, AnyRef]())
    if (translations.isEmpty) request.getRequest.remove("translations")
    else {
      val languageCodes = Platform.getStringList("platform.language.codes", new util.ArrayList[String]())
      if (translations.asScala.exists(entry => !languageCodes.contains(entry._1)))
        throw new ClientException("ERR_INVALID_LANGUAGE_CODE", "Please Provide Valid Language Code For translations. Valid Language Codes are : " + languageCodes)
    }
  }

  def filterFrameworkCategories(framework: util.Map[String, AnyRef], categoryNames: util.List[String]): Map[String, AnyRef] = {
    val categories = framework.getOrDefault("categories", new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    val newCategoryNames = categoryNames.asScala.map(_.toLowerCase)
    if (!categories.isEmpty && !newCategoryNames.isEmpty) {
      val filteredCategories = categories.asScala.filter(category => {
        val code = category.get("code").asInstanceOf[String]
        newCategoryNames.contains(code.toLowerCase())
      }).toList.asJava
      val filteredData = framework.asScala.toMap - "categories" + ("categories" -> filteredCategories)
      val finalCategories = removeAssociations(filteredData, newCategoryNames.asJava)
      (filteredData - "categories" + ("categories" -> finalCategories))
    } else {
      framework.asScala.toMap
    }
  }

  private def removeAssociations(responseMap: Map[String, AnyRef], returnCategories: java.util.List[String]): util.List[util.Map[String, AnyRef]] = {
    val categories = responseMap.getOrElse("categories", new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    categories.asScala.map( category => {
      removeTermAssociations(category.getOrDefault("terms", new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]], returnCategories)
    })
    categories
  }

  private def removeTermAssociations(terms: util.List[util.Map[String, AnyRef]], returnCategories: java.util.List[String]): Unit = {
    terms.asScala.map(term => {
      val associations = term.getOrDefault("associations", new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
      if (!associations.isEmpty) {
        val filteredAssociations = associations.asScala.filter(p => p != null && returnCategories.contains(p.get("category"))).asJava
        term.put("associations", filteredAssociations)
        if (filteredAssociations.isEmpty)
          term.remove("associations")
        removeTermAssociations(term.getOrDefault("children", new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]], returnCategories)
      }
    })
  }

  def getCompleteMetadata(id: String, subGraph: SubGraph, includeRelations: Boolean)(implicit oec: OntologyEngineContext, ec: ExecutionContext): util.Map[String, AnyRef] = {
    val nodes = subGraph.getNodes
    val relations = subGraph.getRelations
    val node = nodes.get(id)
    if (null == node) {
       throw new ClientException("ERR_NODE_NOT_FOUND", s"Node with ID '$id' not found in SubGraph. Available nodes: ${nodes.keySet()}")
    }
    val metadata = node.getMetadata
    val objectType = node.getObjectType.toLowerCase().replace("image", "")
    val channel = node.getMetadata.getOrDefault("channel", "all").asInstanceOf[String]
    val definition: ObjectCategoryDefinition = DefinitionNode.getObjectCategoryDefinition("", objectType, channel)
    val jsonProps = DefinitionNode.fetchJsonProps(node.getGraphId, schemaVersion, objectType, definition)
    val updatedMetadata: util.Map[String, AnyRef] = (metadata.entrySet().asScala.filter(entry => null != entry.getValue)
      .map((entry: util.Map.Entry[String, AnyRef]) => handleKeyNames(entry, null) -> convertJsonProperties(entry, jsonProps)).toMap ++
      Map("objectType" -> node.getObjectType, "identifier" -> node.getIdentifier, "languageCode" -> NodeUtil.getLanguageCodes(node))).asJava

    val fields =DefinitionNode.getMetadataFields(node.getGraphId, schemaVersion, objectType, definition)
    val filteredData: util.Map[String, AnyRef] = if(fields.nonEmpty) updatedMetadata.asScala.filter(entry => fields.contains(entry._1)).asJava else updatedMetadata

    val relationDef = DefinitionNode.getRelationDefinitionMap(node.getGraphId, schemaVersion, objectType, definition)
    val outRelations = relations.asScala.filter((rel: Relation) => {
      StringUtils.equals(rel.getStartNodeId.toString(), node.getIdentifier)
    }).sortBy((rel: Relation) => {
      val index = if (rel.getMetadata != null) rel.getMetadata.get("IL_SEQUENCE_INDEX") else null
      if (index != null) index.asInstanceOf[Number].longValue() else 0L
    })(Ordering.Long).toList.asJava

    if(includeRelations){
      val relMetadata = getRelationAsMetadata(relationDef, outRelations, "out")
      val childHierarchy = relMetadata.map(x => (x._1, x._2.asScala.filter(a => {
        val childNode = nodes.get(a.getOrElse("identifier", ""))
        null == childNode || !StringUtils.equalsIgnoreCase(childNode.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Retired")
      }).map(a => {
        val identifier = a.getOrElse("identifier", "")
        val childNode = nodes.get(identifier)
        val index = a.getOrElse("index", 1).asInstanceOf[Number]
        val metaData = (childNode.getMetadata.asScala ++ Map("index" -> index)).asJava
        childNode.setMetadata(metaData)
        if("associations".equalsIgnoreCase(x._1)){
          getCompleteMetadata(childNode.getIdentifier, subGraph, false)
        } else {
          getCompleteMetadata(childNode.getIdentifier, subGraph, true)
        }
      }).toList.asJava))
      (filteredData.asScala ++ childHierarchy).asJava
    } else {
      filteredData
    }
  }

   def getRelationAsMetadata(definitionMap: Map[String, AnyRef], relationMap: util.List[Relation], direction: String) = {
    relationMap.asScala.map(rel =>
    {
      val endObjectType = rel.getEndNodeObjectType.replace("Image", "")
      val relKey: String = rel.getRelationType + "_" + direction + "_" + endObjectType
      if (definitionMap.contains(relKey)) {
        val relData =Map[String, Object]("identifier" -> rel.getEndNodeId.replace(".img", ""),
          "name"-> rel.getEndNodeName,
          "objectType"-> endObjectType,
          "relation"-> rel.getRelationType,
          "KEY" -> definitionMap.getOrElse(relKey, "").asInstanceOf[String]
        ) ++ rel.getMetadata.asScala
        val indexMap = if(rel.getRelationType.equals("hasSequenceMember")) Map("index" -> rel.getMetadata.getOrDefault("IL_SEQUENCE_INDEX",1.asInstanceOf[Number]).asInstanceOf[Number]) else Map()
        relData ++ indexMap
      } else Map[String, Object]()
    }).filter(x => x.nonEmpty)
      .groupBy(x => x.getOrElse("KEY", "").asInstanceOf[String])
      .map(x => (x._1, (x._2.toList.map(x => {
        x.-("KEY")
        x.-("IL_SEQUENCE_INDEX")
      })).distinct.asJava ))
  }

  def getFrameworkHierarchy(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Map[String, AnyRef]] = {
    val req = new Request(request)
    req.put("identifier", request.get("identifier"))
    val graph_id = req.getContext.getOrDefault("graph_id", "domain").asInstanceOf[String]
    val schemaName = req.getContext.getOrDefault("schemaName", "framework").asInstanceOf[String]
    val schemaVersion = req.getContext.getOrDefault("schemaVersion", "1.0").asInstanceOf[String]
    val externalProps = DefinitionNode.getExternalProps(graph_id, schemaVersion, schemaName)

    val responseFuture = oec.graphService.readExternalProps(request, externalProps)
    responseFuture.map(response => {
      if (!ResponseHandler.checkError(response)) {
        val hierarchyString = response.getResult.asScala.toMap.getOrElse("hierarchy", "").asInstanceOf[String]
        if (StringUtils.isNotEmpty(hierarchyString)) {
          Future(JsonUtils.deserialize(hierarchyString, classOf[java.util.Map[String, AnyRef]]).asScala.toMap)
        } else
          Future(Map[String, AnyRef]())
      } else if (ResponseHandler.checkError(response) && response.getResponseCode.code() == 404)
        Future(Map[String, AnyRef]())
      else
        throw new ServerException("ERR_WHILE_FETCHING_HIERARCHY_FROM_CASSANDRA", "Error while fetching hierarchy from cassandra")
    }).flatten recoverWith { case e: CompletionException => throw e.getCause }
  }

  def copyHierarchy(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val frameworkId = request.getRequest.getOrDefault(Constants.IDENTIFIER, "").asInstanceOf[String]
    val code = request.getRequest.getOrDefault(Constants.CODE, "").asInstanceOf[String]
    if (StringUtils.isBlank(code))
      throw new ClientException("ERR_FRAMEWORK_CODE_REQUIRED", "Unique code is mandatory for framework copy")

    if (StringUtils.equals(frameworkId, code))
      throw new ClientException("ERR_FRAMEWORKID_CODE_MATCHES", "FrameworkId and code should not be same.")

    val getFrameworkReq = new Request()
    getFrameworkReq.setContext(new util.HashMap[String, AnyRef]() {
      {
        putAll(request.getContext)
      }
    })
    getFrameworkReq.getContext.put(Constants.SCHEMA_NAME, request.getContext.getOrDefault(Constants.SCHEMA_NAME, Constants.FRAMEWORK_SCHEMA_NAME))
    getFrameworkReq.getContext.put(Constants.VERSION, request.getContext.getOrDefault(Constants.VERSION, Constants.FRAMEWORK_SCHEMA_VERSION))
    getFrameworkReq.getContext.put("frameworkId", code)
    copyRelationHierarchy(getFrameworkReq, frameworkId, code)
  }

  private def copyRelationHierarchy(request: Request, oldId: String, newId: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    request.put(Constants.IDENTIFIER, oldId)
    DataNode.read(request).map(node => {
      val schemaName = request.getContext.getOrDefault("schemaName", "framework").asInstanceOf[String]
      val schemaVersion = request.getContext.getOrDefault("schemaVersion", "1.0").asInstanceOf[String]
      val objectType = node.getObjectType.toLowerCase().replace("image", "")
      val channel = node.getMetadata.getOrDefault("channel", "all").asInstanceOf[String]
      val definition: ObjectCategoryDefinition = DefinitionNode.getObjectCategoryDefinition("", objectType, channel)
      val relationDef = DefinitionNode.getRelationDefinitionMap(node.getGraphId, schemaVersion, objectType, definition)
      val frameworkId = request.getContext.getOrDefault("frameworkId", "").asInstanceOf[String]
      val outRelations = node.getOutRelations.asScala.filter((rel: Relation) => {
        StringUtils.equals(rel.getStartNodeId, node.getIdentifier)
      }).toList

      node.setInRelations(null)
      node.setOutRelations(null)
      val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, new util.ArrayList(), schemaName, schemaVersion)
      val requestMap = request.getRequest
      if(metadata.get("framework").asInstanceOf[String] != null){
        metadata.put("framework", frameworkId)
      }
      metadata.putAll(requestMap)

      val req = getRequestMap(request, metadata, newId, relationDef)
      DataNode.create(req).map(copiedNode => {
        outRelations.map(rel => {
          if(!rel.getMetadata.isEmpty){
            val endObjectType = rel.getEndNodeObjectType.replace("Image", "")
            val StartObjectType = rel.getStartNodeObjectType.replace("Image", "")
            val relKey: String = rel.getRelationType + "_out_" + endObjectType
            var endNodeId = rel.getEndNodeId()
            endNodeId = endNodeId.replaceFirst(oldId.toLowerCase(), newId.toLowerCase())
            if (relationDef.contains(relKey)) {
              val relReq = new Request(request)
              relReq.getContext.put(Constants.SCHEMA_NAME, rel.getEndNodeObjectType)
              relReq.getContext.put(Constants.VERSION, schemaVersion)
              relReq.getContext.put("frameworkId", frameworkId)
              relReq.put("disableCache", Option(true))

              val inRelKey: String = rel.getRelationType + "_in_" + StartObjectType
              val relationMap: util.Map[String, Object] = new util.HashMap[String, Object]()
              relationMap.put("identifier", newId)
              val index: Integer = rel.getMetadata.getOrDefault("IL_SEQUENCE_INDEX", 1.asInstanceOf[Number]).asInstanceOf[Number].intValue()
              relationMap.put("index", index)
              relationMap.put("KEY", inRelKey)
              relReq.getContext.put("relationMap", relationMap)

              copyRelationHierarchy(relReq, rel.getEndNodeId, endNodeId)
            }
          }
        })
        ResponseHandler.OK.put("node_id", frameworkId)
      })
    }).flatten recoverWith { case e: CompletionException => throw e.getCause }
  }

  private def getRequestMap(request: Request, metadata: util.Map[String, AnyRef], objectId: String, relationDef: Map[String, AnyRef]): Request = {
    val req = new Request(request)
    req.setRequest(metadata)
    req.put("identifier", objectId)
    req.put("code", objectId)
    var relMap = request.getContext.getOrDefault("relationMap", new util.HashMap[String, Object]()).asInstanceOf[util.Map[String, Object]]
    if (!relMap.isEmpty) {
      val relKey = relMap.getOrDefault("KEY", "").asInstanceOf[String]
      relMap = (relMap.asScala.toMap - "KEY").asJava
      if (!relationDef.getOrElse(relKey, "").asInstanceOf[String].isEmpty) {
        val tempArr = new util.ArrayList[util.Map[String, Object]]()
        tempArr.add(relMap)
        req.put(relationDef.getOrElse(relKey, "").asInstanceOf[String], tempArr)
      }
    }
    req.getContext.remove("relationMap")
    req
  }


}
