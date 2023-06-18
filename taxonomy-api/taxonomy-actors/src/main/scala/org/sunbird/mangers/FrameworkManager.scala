package org.sunbird.mangers

import java.util
import com.twitter.util.Config.intoOption
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.common.dto.{Request, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.{Node, Relation, SubGraph}
import org.sunbird.graph.path.DataSubGraph
import org.sunbird.graph.schema.{DefinitionNode, ObjectCategoryDefinition}
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.graph.utils.NodeUtil.{convertJsonProperties, handleKeyNames}

import java.util
import java.util.concurrent.{CompletionException, Executors}
import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

object FrameworkManager {
  private val languageCodes = Platform.getStringList("platform.language.codes", new util.ArrayList[String]())
  val schemaVersion: String = "1.0"
  def validateTranslationMap(request: Request) = {
    val translations: util.Map[String, AnyRef] = request.getOrElse("translations", "").asInstanceOf[util.HashMap[String, AnyRef]]
    if (translations.isEmpty) request.getRequest.remove("translations")
    else {
      if (translations.asScala.exists(entry => !languageCodes.contains(entry._1)))
        throw new ClientException("ERR_INVALID_LANGUAGE_CODE", "Please Provide Valid Language Code For translations. Valid Language Codes are : " + languageCodes)
    }
  }


  def filterFrameworkCategories(framework: util.Map[String, AnyRef], categoryNames: util.List[String]): Unit = {
    val categories = framework.get("categories").asInstanceOf[util.List[Map[String, AnyRef]]]
    if (!categories.isEmpty && !categoryNames.isEmpty) {
      val filteredCategories = categories.filter(p => {
        val name = p.getOrElse("name", "").asInstanceOf[String]
        categoryNames.contains(name.toLowerCase())
      }).toList.asJava
      framework.put("categories",filteredCategories)
      removeAssociations(framework, categoryNames)
    }
  }

  private def removeAssociations(responseMap: util.Map[String, AnyRef], returnCategories: java.util.List[String]): Unit = {
    val categories = responseMap.get("categories").asInstanceOf[util.List[Map[String, AnyRef]]]
    categories.map( category => {
      removeTermAssociations(category.getOrDefault("terms", new util.ArrayList[Map[String, AnyRef]]).asInstanceOf[util.List[Map[String, AnyRef]]], returnCategories)
    })
  }

  private def removeTermAssociations(terms: util.List[Map[String, AnyRef]], returnCategories: java.util.List[String]): Unit = {
    terms.foreach { term =>
      val associations = term.get("associations").asInstanceOf[util.List[Map[String, AnyRef]]]
      if (associations.nonEmpty) {
        val filteredAssociations = associations.filter(p => p != null && returnCategories.contains(p.get("category")))
        term.put("associations",filteredAssociations)
        if (filteredAssociations.isEmpty)
          term.remove("associations")
        removeTermAssociations(term.get("children").asInstanceOf[List[Map[String, AnyRef]]], returnCategories)
      }
    }
  }

  def generateFrameworkHierarchy(rootId: String, subGraph: SubGraph)(implicit oec: OntologyEngineContext, ec: ExecutionContext): util.Map[String, AnyRef]  = {
    val nodes =  subGraph.getNodes
    val relations = subGraph.getRelations
    val rootNode = nodes.get(rootId)
    val nodeMetadata = rootNode.getMetadata

    val objectType = rootNode.getObjectType.toLowerCase().replace("image", "")
    val channel = rootNode.getMetadata.getOrDefault("channel", "all").asInstanceOf[String];
    val objectCategoryDefinition: ObjectCategoryDefinition = DefinitionNode.getObjectCategoryDefinition("", objectType, channel)
    val jsonProps = DefinitionNode.fetchJsonProps(rootNode.getGraphId, schemaVersion, objectType, objectCategoryDefinition)
    val updatedMetadata: util.Map[String, AnyRef] = nodeMetadata.entrySet().asScala.filter(entry => null != entry.getValue).map((entry: util.Map.Entry[String, AnyRef]) => handleKeyNames(entry, null) -> convertJsonProperties(entry, jsonProps)).toMap.asJava
    val finalMetadata = updatedMetadata.asScala ++ Map("objectType" -> rootNode.getObjectType, "identifier" -> rootNode.getIdentifier, "languageCode" -> NodeUtil.getLanguageCodes(rootNode))

    val definitionMap = DefinitionNode.getRelationDefinitionMap(rootNode.getGraphId, schemaVersion, objectType, objectCategoryDefinition)
    val filterRelations = relations.filter((rel: Relation) => { StringUtils.equals(rel.getStartNodeId.toString(), rootNode.getIdentifier) }).toList
    val relationMetadata = getRelationMap(definitionMap, filterRelations, "out")
    (finalMetadata ++ relationMetadata).asJava
  }

  private def getNodeDefinition(node: Node, filterRelations: List[Relation])(implicit oec: OntologyEngineContext, ec: ExecutionContext) = {
    val metadataMap = node.getMetadata
    val objectCategoryDefinition: ObjectCategoryDefinition = DefinitionNode.getObjectCategoryDefinition(node.getMetadata.getOrDefault("primaryCategory", "").asInstanceOf[String], node.getObjectType.toLowerCase().replace("image", ""), node.getMetadata.getOrDefault("channel", "all").asInstanceOf[String])
    val definitionMap = DefinitionNode.getRelationDefinitionMap(node.getGraphId, schemaVersion, node.getObjectType.toLowerCase().replace("image", ""), objectCategoryDefinition).asJava
    definitionMap
  }


  private def getRelationMap(definitionMap: Map[String, AnyRef], relationMap: util.List[Relation], direction: String) = {

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
      })).asJava ))
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
      println("response "+ response.toString)
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


}
