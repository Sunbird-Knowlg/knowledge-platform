package org.sunbird.graph.schema.validator

import java.util

import com.mashape.unirest.http.{HttpResponse, Unirest}
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.{ClientException, ErrorCodes, ResourceNotFoundException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model._
import org.sunbird.graph.schema.IDefinition

import scala.collection.JavaConverters._
import scala.collection.Map
import scala.concurrent.{ExecutionContext, Future}

trait FrameworkValidator extends IDefinition {

  val ORGANISATIONAL_FRAMEWORK_TERMS = List("organisationFrameworkId", "organisationBoardIds", "organisationGradeLevelIds", "organisationSubjectIds", "organisationMediumIds", "organisationTopicsIds")
  val TARGET_FRAMEWORK_TERMS = List("targetFrameworkIds", "targetBoardIds", "targetGradeLevelIds", "targetSubjectIds", "targetMediumIds", "targetTopicIds")

  @throws[Exception]
  abstract override def validate(node: Node, operation: String, setDefaultValue: Boolean)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val fwCategories: List[String] = schemaValidator.getConfig.getStringList("frameworkCategories").asScala.toList
    validateAndSetMultiFrameworks(node).map(_ => {
      val framework: String = node.getMetadata.getOrDefault("framework", "").asInstanceOf[String]
      if (null != fwCategories && fwCategories.nonEmpty && framework.nonEmpty) {
        //prepare data for validation
        val fwMetadata: Map[String, AnyRef] = node.getMetadata.asScala.filterKeys(key => fwCategories.contains(key))
        //validate data from cache
        if (fwMetadata.nonEmpty) {
          val errors: util.List[String] = new util.ArrayList[String]
          for (cat: String <- fwMetadata.keys) {
            val value: AnyRef = fwMetadata.get(cat).get
            //TODO: Replace Cache Call With FrameworkCache Implementation
            val cacheKey = "cat_" + framework + cat
            val list: List[String] = RedisCache.getList(cacheKey)
            val result: Boolean = value match {
              case value: String => list.contains(value)
              case value: util.List[String] => list.asJava.containsAll(value)
              case value: Array[String] => value.forall(term => list.contains(term))
              case _ => throw new ClientException("CLIENT_ERROR", "Validation Errors.", util.Arrays.asList("Please provide correct value for [" + cat + "]"))
            }

            if (!result) {
              if (list.isEmpty) {
                errors.add(cat + " range data is empty from the given framework.")
              } else {
                errors.add("Metadata " + cat + " should belong from:" + list.asJava)
              }
            }
          }
          if (!errors.isEmpty)
            throw new ClientException("CLIENT_ERROR", "Validation Errors.", errors)
        }
      }
      super.validate(node, operation)
    }).flatMap(f => f)
  }

  private def validateAndSetMultiFrameworks(node: Node)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Map[String, AnyRef]] = {
    getValidatedTerms(node, "Organisation").map(orgTermMap => {
      if (StringUtils.isNotBlank(node.getMetadata.get("organisationFrameworkId").asInstanceOf[String]))
        node.getMetadata.putIfAbsent("framework", node.getMetadata.get("organisationFrameworkId").asInstanceOf[String])
      val boardIds = node.getMetadata.getOrDefault("organisationBoardIds", new util.ArrayList[String]()).asInstanceOf[util.List[String]]
      if (CollectionUtils.isNotEmpty(boardIds))
        node.getMetadata.putIfAbsent("board", orgTermMap(boardIds.get(0)))
      val mediumIds = node.getMetadata.getOrDefault("organisationMediumIds", new util.ArrayList[String]()).asInstanceOf[util.List[String]]
      if (CollectionUtils.isNotEmpty(mediumIds))
        node.getMetadata.putIfAbsent("medium", mediumIds.asScala.map(id => orgTermMap(id)).toList.asJava)
      val subjectIds = node.getMetadata.getOrDefault("organisationSubjectIds", new util.ArrayList[String]()).asInstanceOf[util.List[String]]
      if (CollectionUtils.isNotEmpty(subjectIds))
        node.getMetadata.putIfAbsent("subject", subjectIds.asScala.map(id => orgTermMap(id)).toList.asJava)
      val gradeIds = node.getMetadata.getOrDefault("organisationGradeLevelIds", new util.ArrayList[String]()).asInstanceOf[util.List[String]]
      if (CollectionUtils.isNotEmpty(gradeIds))
        node.getMetadata.putIfAbsent("gradeLevel", gradeIds.asScala.map(id => orgTermMap(id)).toList.asJava)
      val topicIds = node.getMetadata.getOrDefault("organisationTopicsIds", new util.ArrayList[String]()).asInstanceOf[util.List[String]]
      if (CollectionUtils.isNotEmpty(topicIds))
        node.getMetadata.putIfAbsent("topics", topicIds.asScala.map(id => orgTermMap(id)).toList.asJava)
      getValidatedTerms(node, "Target")
    }).flatMap(f => f)
  }


  private def getValidatedTerms(node: Node, `type`: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Map[String, AnyRef]] = {
    val ids: List[String] = `type` match {
      case "Organisation" => node.getMetadata.asScala
          .filter(entry => ORGANISATIONAL_FRAMEWORK_TERMS.contains(entry._1))
          .flatMap(entry => entry._2 match {
            case e: String => List(e)
            case e: util.List[String] => e.asScala
          }).toList
      case "Target" => node.getMetadata.asScala
          .filter(entry => TARGET_FRAMEWORK_TERMS.contains(entry._1))
          .flatMap(_._2.asInstanceOf[util.List[String]].asScala).toList
      case _ => throw new ServerException("ERR_VALIDATING_CONTENT_FRAMEWORK", "Invalid Framework type")
    }
    if (ids.nonEmpty) {
      val mc: MetadataCriterion = MetadataCriterion.create(new util.ArrayList[Filter]() {
        {
          if (ids.size == 1) add(new Filter(SystemProperties.IL_UNIQUE_ID.name(), SearchConditions.OP_EQUAL, ids.asJava.get(0)))
          if (ids.size > 1) add(new Filter(SystemProperties.IL_UNIQUE_ID.name(), SearchConditions.OP_IN, ids.asJava))
          new Filter("status", SearchConditions.OP_NOT_EQUAL, "Retired")
        }
      })

      val searchCriteria = new SearchCriteria {
        {
          addMetadata(mc)
          setCountQuery(false)
        }
      }
      oec.graphService.getNodeByUniqueIds(node.getGraphId, searchCriteria).map(nodeList => {
        if (CollectionUtils.isEmpty(nodeList))
          throw new ResourceNotFoundException("ERR_VALIDATING_CONTENT_FRAMEWORK", s"Nodes not found for Id's $ids ")
        val termMap = nodeList.asScala.map(node => node.getIdentifier -> node.getMetadata.getOrDefault("name", "")).toMap
        validateFrameworkRelatedData(node, termMap,
          if (StringUtils.equals(`type`, "Organisation")) ORGANISATIONAL_FRAMEWORK_TERMS else TARGET_FRAMEWORK_TERMS)
        termMap
      })
    } else Future {
      Map()
    }
  }

  def validateFrameworkRelatedData(node: Node, termMap: Map[String, AnyRef], validationList: List[String]) = {
    validationList.foreach(termName => node.getMetadata.get(termName) match {
      case termId: String => if (!termMap.contains(termId))
        throw new ResourceNotFoundException("ERR_VALIDATING_CONTENT_FRAMEWORK", s"No nodes found for $termName with ids: ${node.getMetadata.get(termName)}")
      case termIds: util.List[String] => if (termIds.asScala.filterNot(id => termMap.contains(id)).nonEmpty)
        throw new ResourceNotFoundException("ERR_VALIDATING_CONTENT_FRAMEWORK", s"No nodes found for $termName with ids: ${node.getMetadata.get(termName)}")
      case _ =>
    })
  }

}
