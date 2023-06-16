package org.sunbird.mangers

import java.util
import com.twitter.util.Config.intoOption
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.Platform
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.{Node, Relation, SubGraph}
import org.sunbird.graph.schema.{DefinitionNode, ObjectCategoryDefinition}
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.graph.utils.NodeUtil.{convertJsonProperties, handleKeyNames}

import java.util
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


def filterFrameworkCategories(framework: Map[String, AnyRef], categoryNames: List[String]): Unit = {
  val categories = framework.get("categories").asInstanceOf[List[Map[String, AnyRef]]]
  if (categories.nonEmpty && categoryNames.nonEmpty) {
    val filteredCategories = categories.filter(p => categoryNames.contains(p.get("code")))
    framework.put("categories",filteredCategories)
    removeAssociations(framework, categoryNames)
  }
}

  def removeAssociations(responseMap: Map[String, AnyRef], returnCategories: List[String]): Unit = {
    val categories = responseMap.get("categories").asInstanceOf[List[Map[String, AnyRef]]]
    categories.foreach { category =>
      removeTermAssociations(category.get("terms").asInstanceOf[List[Map[String, AnyRef]]], returnCategories)
    }
  }

  def removeTermAssociations(terms: List[Map[String, AnyRef]], returnCategories: List[String]): Unit = {
    terms.foreach { term =>
      val associations = term.get("associations").asInstanceOf[List[Map[String, AnyRef]]]
      if (associations.nonEmpty) {
        val filteredAssociations = associations.filter(p => p != null && returnCategories.contains(p.get("category")))
        term.put("associations",filteredAssociations)
        if (filteredAssociations.isEmpty)
          term.remove("associations")

        removeTermAssociations(term.get("children").asInstanceOf[List[Map[String, AnyRef]]], returnCategories)
      }
    }
  }

  def generateFrameworkHierarchy(frameworkId: String, subGraph: SubGraph)(implicit oec: OntologyEngineContext, ec: ExecutionContext): util.Map[String, AnyRef]  = {
    val nodes =  subGraph.getNodes
    val relations = subGraph.getRelations
    val node = nodes.get(frameworkId)
    val metadataMap = node.getMetadata
    val objectCategoryDefinition: ObjectCategoryDefinition = DefinitionNode.getObjectCategoryDefinition(node.getMetadata.getOrDefault("primaryCategory", "").asInstanceOf[String], node.getObjectType.toLowerCase().replace("image", ""), node.getMetadata.getOrDefault("channel", "all").asInstanceOf[String])
    val jsonProps = DefinitionNode.fetchJsonProps(node.getGraphId, schemaVersion, node.getObjectType.toLowerCase().replace("image", ""), objectCategoryDefinition)
    val updatedMetadataMap: util.Map[String, AnyRef] = metadataMap.entrySet().asScala.filter(entry => null != entry.getValue).map((entry: util.Map.Entry[String, AnyRef]) => handleKeyNames(entry, null) -> convertJsonProperties(entry, jsonProps)).toMap.asJava
    val definitionMap = DefinitionNode.getRelationDefinitionMap(node.getGraphId, schemaVersion, node.getObjectType.toLowerCase().replace("image", ""), objectCategoryDefinition)
    val finalMetadata = new util.HashMap[String, AnyRef]()
    finalMetadata.put("objectType", node.getObjectType)
    finalMetadata.putAll(updatedMetadataMap)
    val filterRelations = relations.filter((rel: Relation) => { StringUtils.equals(rel.getStartNodeId.toString(), frameworkId) }).toList.asJava
    println("filterRelations "+ filterRelations)

    val relMap = getRelationMap(definitionMap, filterRelations, "out")
    println("relMap: "+ relMap)
//    finalMetadata.putAll(relMap)

    finalMetadata.put("identifier", node.getIdentifier)
    finalMetadata.put("languageCode", NodeUtil.getLanguageCodes(node))
    println("finalMetadata: "+ finalMetadata)
    finalMetadata
  }

  private def getNodeDefinition(node: Node, filterRelations: List[Relation])(implicit oec: OntologyEngineContext, ec: ExecutionContext) = {
    val metadataMap = node.getMetadata
    val objectCategoryDefinition: ObjectCategoryDefinition = DefinitionNode.getObjectCategoryDefinition(node.getMetadata.getOrDefault("primaryCategory", "").asInstanceOf[String], node.getObjectType.toLowerCase().replace("image", ""), node.getMetadata.getOrDefault("channel", "all").asInstanceOf[String])
    val definitionMap = DefinitionNode.getRelationDefinitionMap(node.getGraphId, schemaVersion, node.getObjectType.toLowerCase().replace("image", ""), objectCategoryDefinition).asJava
    definitionMap
  }


  private def getRelationMap(definitionMap: Map[String, AnyRef], relationMap: util.List[Relation], direction: String): util.Map[String, util.List[Map[String, AnyRef]]] = {
    val relMap = new util.HashMap[String, util.List[Map[String, AnyRef]]]

    var relationArr = new util.ArrayList[Map[String, AnyRef]]()

//    relationMap.foreach(rel =>{
//      val relKey: String = rel.getRelationType + "_" + direction + "_" + rel.getEndNodeObjectType
//      println("relKey  " + relKey)
//      val objectType = rel.getEndNodeObjectType.replace("Image", "")
//      if (null != definitionMap.get(relKey)) {
//        val relationData: java.util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {
//          {
//            put("identifier", rel.getEndNodeId.replace(".img", ""))
//            put("name", rel.getEndNodeName)
//            put("objectType", objectType)
//            put("relation", rel.getRelationType)
//          }
//        }
//        println("relationData " + relationData)
//        relationArr.add(relationData)
//        println("relationArr " + relationArr)
//        relMap.put(definitionMap.get(relKey).asInstanceOf[String], relationArr)
//      } else println(" in else ")
//    })

    for(rel <- relationMap.asScala) {
      val relKey: String = rel.getRelationType + "_" + direction + "_" + rel.getEndNodeObjectType
      println("relKey  "+ relKey)
      val objectType = rel.getEndNodeObjectType.replace("Image", "")
      if (null != definitionMap.get(relKey)) {
          val relationData : java.util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {{
            put("identifier", rel.getEndNodeId.replace(".img", ""))
            put("name", rel.getEndNodeName)
            put("objectType", objectType)
            put("relation", rel.getRelationType)
          }}
        println("relationData "+ relationData)
//        relationArr.add(relationData)
//        println("relationArr "+ relationArr)
//        relMap.put(definitionMap.get(relKey).asInstanceOf[String], new util.ArrayList[util.Map[String, AnyRef]]() {add(relationData)})
      } else println(" in else ")
    }
//    println("relMap === "+ relMap)

//    relationMap.map(rel => {
//      var relationData = Map[String, AnyRef]()
//      val relKey: String = rel.getRelationType + "_" + direction + "_" + rel.getEndNodeObjectType
//      println("relKey  " + relKey)
//      val objectType = rel.getEndNodeObjectType.replace("Image", "")
//      if (null != definitionMap.get(relKey)) {
//        val relationMapD: java.util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {
//          {
//            put("identifier", rel.getEndNodeId.replace(".img", ""))
//            put("name", rel.getEndNodeName)
//            put("objectType", objectType)
//            put("relation", rel.getRelationType)
//          }
//        }
//        println("relationMapD " + relationMapD)
//        relationArr.add(relationMapD)
//        println("relationArr " + relationArr)
//        relationData = Map("identifier" -> rel.getEndNodeId.replace(".img", ""),
//          "name" -> rel.getEndNodeName,
//          "objectType" -> objectType,
//          "relation" -> rel.getRelationType)
//      }
//      relMap.put(definitionMap.get(relKey).asInstanceOf[String], relationArr)
//      relMap
//    })



//    var relationList: List[Map[String, AnyRef]] = relationMap.map(rel => {
//      var relationData = Map[String, AnyRef]()
//      val relKey: String = rel.getRelationType + "_" + direction + "_" + rel.getEndNodeObjectType
//      println("relKey  " + relKey)
//      val objectType = rel.getEndNodeObjectType.replace("Image", "")
//      if (null != definitionMap.get(relKey)) {
//        relationData = Map("identifier" -> rel.getEndNodeId.replace(".img", ""),
//          "name" -> rel.getEndNodeName,
//          "objectType" -> objectType,
//          "relation" -> rel.getRelationType)
//      }
//      relationArr.add(relationData)
////      relMap.put(definitionMap.get(relKey).asInstanceOf[String], relationArr)
////      relationData
//      relationArr
//    })
//    println("relationList " + relationList)
    println("relMap " + relMap)
    relMap
  }

}

