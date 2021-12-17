package org.sunbird.graph.schema.validator

import java.util
import java.util.concurrent.CompletionException
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.Platform
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model._
import org.sunbird.graph.schema.{FrameworkMasterCategoryMap, IDefinition}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.Map
import scala.concurrent.{ExecutionContext, Future}

trait FrameworkValidator extends IDefinition {

  val logger = LoggerFactory.getLogger("org.sunbird.graph.schema.validator.FrameworkValidator")

  @throws[Exception]
  abstract override def validate(node: Node, operation: String, setDefaultValue: Boolean)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val fwCategories: List[String] = schemaValidator.getConfig.getStringList("frameworkCategories").asScala.toList
    val graphId: String = if(StringUtils.isNotBlank(node.getGraphId)) node.getGraphId else "domain"
    val orgAndTargetFWData: Future[(List[String], List[String])] = if(StringUtils.equalsIgnoreCase(Platform.getString("master.category.validation.enabled", "Yes"), "Yes")) getOrgAndTargetFWData(graphId, "Category") else Future(List(), List())

    orgAndTargetFWData.map(orgAndTargetTouple => {
      val orgFwTerms = orgAndTargetTouple._1
      val targetFwTerms = orgAndTargetTouple._2

      validateAndSetMultiFrameworks(node, orgFwTerms, targetFwTerms).map(_ => {
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
    }).flatMap(f => f)
  }

  private def validateAndSetMultiFrameworks(node: Node, orgFwTerms: List[String], targetFwTerms: List[String])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Map[String, AnyRef]] = {
    getValidatedTerms(node, orgFwTerms).map(orgTermMap => {
      val boards = fetchValidatedList(getList("boardIds", node), orgTermMap)
      if (CollectionUtils.isNotEmpty(boards)) node.getMetadata.put("board", boards.get(0))
      val mediums = fetchValidatedList(getList("mediumIds", node), orgTermMap)
      if (CollectionUtils.isNotEmpty(mediums)) node.getMetadata.put("medium", mediums)
      val subjects = fetchValidatedList(getList("subjectIds", node), orgTermMap)
      if (CollectionUtils.isNotEmpty(subjects)) node.getMetadata.put("subject", subjects)
      val grades = fetchValidatedList(getList("gradeLevelIds", node), orgTermMap)
      if (CollectionUtils.isNotEmpty(grades)) node.getMetadata.put("gradeLevel", grades)
      val topics = fetchValidatedList(getList("topicsIds", node), orgTermMap)
      if (CollectionUtils.isNotEmpty(topics)) node.getMetadata.put("topic", topics)
      getValidatedTerms(node, targetFwTerms)
    }).flatMap(f => f)
  }


  private def getList(termName: String, node: Node): util.List[String] = {
    node.getMetadata.get(termName) match {
      case e: String => List(e).asJava
      case e: util.List[String] => e
      case e: Array[String] => e.toList.asJava
      case _ => List().asJava
    }
  }

  private def getOrgAndTargetFWData(graphId: String, objectType: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext):Future[(List[String], List[String])] = {
    val masterCategories: Future[List[Map[String, AnyRef]]] = getMasterCategory(graphId, objectType)
    masterCategories.map(result => {
      (result.map(cat => cat.getOrDefault("orgIdFieldName", "").asInstanceOf[String]),
        result.map(cat => cat.getOrDefault("targetIdFieldName", "").asInstanceOf[String]))
    })
  }

  private def getMasterCategory(graphId: String, objectType: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[List[Map[String, AnyRef]]] = {

    if (FrameworkMasterCategoryMap.containsKey("masterCategories") && null != FrameworkMasterCategoryMap.get("masterCategories")) {
      val masterCategories: Map[String, AnyRef] = FrameworkMasterCategoryMap.get("masterCategories")
      Future(masterCategories.map(obj => obj._2.asInstanceOf[Map[String, AnyRef]]).toList)
    } else {
      val nodes = getMasterCategoryNodes(graphId, objectType)
      nodes.map(dataNodes => {
        if (dataNodes.isEmpty) {
          logger.warn(s"ALERT!... There are no master framework category objects[$objectType] defined. This will not enable framework category properties validation.")
          List[Map[String, AnyRef]]()
        } else {
          val masterCategories: scala.collection.immutable.Map[String, AnyRef] = dataNodes.map(
            node => node.getMetadata.getOrDefault("code", "").asInstanceOf[String] -> Map[String, AnyRef](
              "code" -> node.getMetadata.getOrDefault("code", "").asInstanceOf[String],
              "orgIdFieldName" -> node.getMetadata.getOrDefault("orgIdFieldName", "").asInstanceOf[String],
              "targetIdFieldName" -> node.getMetadata.getOrDefault("targetIdFieldName", "").asInstanceOf[String],
              "searchIdFieldName" -> node.getMetadata.getOrDefault("searchIdFieldName", "").asInstanceOf[String],
              "searchLabelFieldName" -> node.getMetadata.getOrDefault("searchLabelFieldName", "").asInstanceOf[String])).toMap
          FrameworkMasterCategoryMap.put("masterCategories", masterCategories)
          masterCategories.map(obj => obj._2.asInstanceOf[Map[String, AnyRef]]).toList
        }
      }) recoverWith {
        case e: CompletionException => throw e.getCause
      }
    }
  }

  private def getMasterCategoryNodes(graphId: String, objectType: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext):Future[util.List[Node]]={
    val mc: MetadataCriterion = MetadataCriterion.create(new util.ArrayList[Filter]() {
      {
        add(new Filter(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), SearchConditions.OP_EQUAL, objectType))
        add(new Filter(SystemProperties.IL_SYS_NODE_TYPE.name(), SearchConditions.OP_EQUAL, "DATA_NODE"))
        add(new Filter("status", SearchConditions.OP_NOT_EQUAL, "Retired"))
      }
    })
    val searchCriteria = new SearchCriteria {
      {
        addMetadata(mc)
        setCountQuery(false)
      }
    }
    try {
      oec.graphService.getNodeByUniqueIds(graphId, searchCriteria)
    } catch {
      case e: Exception =>
        throw new ServerException("ERR_GRAPH_PROCESSING_ERROR", "Unable To Fetch Nodes From Graph. Exception is: " + e.getMessage)
    }
  }

  private def getValidatedTerms(node: Node, validationList: List[String])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Map[String, AnyRef]] = {
    val ids: List[String] = node.getMetadata.asScala
          .filter(entry => validationList.contains(entry._1))
          .flatMap(entry => entry._2 match {
            case e: String => List(e)
            case e: util.List[String] => e.asScala
            case e: Array[String] => e.toList
            case _ => List()
          }).toList
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
        validateFrameworkRelatedData(node, termMap, validationList)
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

  def fetchValidatedList(itemList: util.List[String], orgTermMap: Map[String, AnyRef]): util.List[String] = {
    if (CollectionUtils.isNotEmpty(itemList)) {
      itemList.asScala.map(id => orgTermMap.getOrElse(id, "").asInstanceOf[String])
        .filter(medium => medium.nonEmpty)
        .toList.asJava
    } else List().asJava
  }
}
