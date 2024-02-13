package org.sunbird.mangers

import java.util
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.{JsonUtils, Platform, Slug}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.enums.RelationTypes
import org.sunbird.graph.dac.model.{Node, Relation, SubGraph}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.path.DataSubGraph
import org.sunbird.graph.schema.{DefinitionNode, ObjectCategoryDefinition}
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.graph.utils.NodeUtil.{convertJsonProperties, handleKeyNames}

import java.util
import java.util.{Collections, Optional}
import java.util.concurrent.{CompletionException, Executors}
import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}
import org.sunbird.utils.{CategoryCache, Constants, FrameworkCache}

object FrameworkManager {
  val schemaVersion: String = "1.0"
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
    val newCategoryNames = categoryNames.map(_.toLowerCase)
    if (!categories.isEmpty && !newCategoryNames.isEmpty) {
      val filteredCategories = categories.filter(category => {
        val name = category.get("name").asInstanceOf[String]
        newCategoryNames.contains(name.toLowerCase())
      }).toList.asJava
      val filteredData = framework.-("categories") ++ Map("categories" -> filteredCategories)
      val finalCategories = removeAssociations(filteredData.toMap, newCategoryNames)
      (filteredData.-("categories") ++ Map("categories" -> finalCategories)).toMap
    } else {
      framework.toMap
    }
  }

  private def removeAssociations(responseMap: Map[String, AnyRef], returnCategories: java.util.List[String]): util.List[util.Map[String, AnyRef]] = {
    val categories = responseMap.getOrDefault("categories", new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    categories.map( category => {
      removeTermAssociations(category.getOrDefault("terms", new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]], returnCategories)
    })
    categories
  }

  private def removeTermAssociations(terms: util.List[util.Map[String, AnyRef]], returnCategories: java.util.List[String]): Unit = {
    terms.map(term => {
      val associations = term.getOrDefault("associations", new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
      if (associations.nonEmpty) {
        val filteredAssociations = associations.filter(p => p != null && returnCategories.contains(p.get("category")))
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
    val metadata = node.getMetadata
    val objectType = node.getObjectType.toLowerCase().replace("image", "")
    val channel = node.getMetadata.getOrDefault("channel", "all").asInstanceOf[String]
    val definition: ObjectCategoryDefinition = DefinitionNode.getObjectCategoryDefinition("", objectType, channel)
    val jsonProps = DefinitionNode.fetchJsonProps(node.getGraphId, schemaVersion, objectType, definition)
    val updatedMetadata: util.Map[String, AnyRef] = metadata.entrySet().asScala.filter(entry => null != entry.getValue)
      .map((entry: util.Map.Entry[String, AnyRef]) => handleKeyNames(entry, null) -> convertJsonProperties(entry, jsonProps)).toMap ++
      Map("objectType" -> node.getObjectType, "identifier" -> node.getIdentifier, "languageCode" -> NodeUtil.getLanguageCodes(node))

    val fields =DefinitionNode.getMetadataFields(node.getGraphId, schemaVersion, objectType, definition)
    val filteredData: util.Map[String, AnyRef] = if(fields.nonEmpty) updatedMetadata.filterKeys(key => fields.contains(key)) else updatedMetadata
  
    val relationDef = DefinitionNode.getRelationDefinitionMap(node.getGraphId, schemaVersion, objectType, definition)
    val outRelations = relations.filter((rel: Relation) => {
      StringUtils.equals(rel.getStartNodeId.toString(), node.getIdentifier)
    }).sortBy((rel: Relation) => rel.getMetadata.get("IL_SEQUENCE_INDEX").asInstanceOf[Long])(Ordering.Long).toList

    if(includeRelations){
      val relMetadata = getRelationAsMetadata(relationDef, outRelations, "out")
      val childHierarchy = relMetadata.map(x => (x._1, x._2.map(a => {
        val identifier = a.getOrElse("identifier", "")
        val childNode = nodes.get(identifier)
        val index = a.getOrElse("index", 1).asInstanceOf[Number]
        val metaData = (childNode.getMetadata ++ Map("index" -> index)).asJava
        childNode.setMetadata(metaData)
        if("associations".equalsIgnoreCase(x._1)){
          getCompleteMetadata(childNode.getIdentifier, subGraph, false)
        } else {
          getCompleteMetadata(childNode.getIdentifier, subGraph, true)
        }
      }).toList.asJava))
      (filteredData ++ childHierarchy).asJava
    } else {
      filteredData
    }
  }

   def getRelationAsMetadata(definitionMap: Map[String, AnyRef], relationMap: util.List[Relation], direction: String) = {
    relationMap.asScala.map(rel =>
    {
      val endObjectType = rel.getEndNodeObjectType.replace("Image", "")
      val relKey: String = rel.getRelationType + "_" + direction + "_" + endObjectType
      if (definitionMap.containsKey(relKey)) {
        val relData =Map[String, Object]("identifier" -> rel.getEndNodeId.replace(".img", ""),
          "name"-> rel.getEndNodeName,
          "objectType"-> endObjectType,
          "relation"-> rel.getRelationType,
          "KEY" -> definitionMap.getOrDefault(relKey, "").asInstanceOf[String]
        ) ++ rel.getMetadata.asScala
        val indexMap = if(rel.getRelationType.equals("hasSequenceMember")) Map("index" -> rel.getMetadata.getOrDefault("IL_SEQUENCE_INDEX",1.asInstanceOf[Number]).asInstanceOf[Number]) else Map()
        relData ++ indexMap
      } else Map[String, Object]()
    }).filter(x => x.nonEmpty)
      .groupBy(x => x.getOrDefault("KEY", "").asInstanceOf[String])
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
        val hierarchyString = response.getResult.toMap.getOrDefault("hierarchy", "").asInstanceOf[String]
        if (StringUtils.isNotEmpty(hierarchyString)) {
          Future(JsonUtils.deserialize(hierarchyString, classOf[java.util.Map[String, AnyRef]]).toMap)
        } else
          Future(Map[String, AnyRef]())
      } else if (ResponseHandler.checkError(response) && response.getResponseCode.code() == 404)
        Future(Map[String, AnyRef]())
      else
        throw new ServerException("ERR_WHILE_FETCHING_HIERARCHY_FROM_CASSANDRA", "Error while fetching hierarchy from cassandra")
    }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
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
    getFrameworkReq.getContext.put(Constants.SCHEMA_NAME, Constants.FRAMEWORK_SCHEMA_NAME)
    getFrameworkReq.getContext.put(Constants.VERSION, Constants.FRAMEWORK_SCHEMA_VERSION)
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
      val outRelations = node.getOutRelations.filter((rel: Relation) => {
        StringUtils.equals(rel.getStartNodeId.toString(), node.getIdentifier)
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
            if (relationDef.containsKey(relKey)) {
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
    }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
  }

  private def getRequestMap(request: Request, metadata: util.Map[String, AnyRef], objectId: String, relationDef: Map[String, AnyRef]): Request = {
    val req = new Request(request)
    req.setRequest(metadata)
    req.put("identifier", objectId)
    req.put("code", objectId)
    var relMap = request.getContext.getOrDefault("relationMap", new util.HashMap[String, Object]()).asInstanceOf[util.Map[String, Object]]
    if (!relMap.isEmpty) {
      val relKey = relMap.getOrDefault("KEY", "").asInstanceOf[String]
      relMap = relMap.toMap.-("KEY")
      if (!relationDef.getOrDefault(relKey, "").asInstanceOf[String].isEmpty) {
        val tempArr = new util.ArrayList[util.Map[String, Object]]()
        tempArr.add(relMap)
        req.put(relationDef.getOrDefault(relKey, "").asInstanceOf[String], tempArr)
      }
    }
    req.getContext.remove("relationMap")
    req
  }


}
