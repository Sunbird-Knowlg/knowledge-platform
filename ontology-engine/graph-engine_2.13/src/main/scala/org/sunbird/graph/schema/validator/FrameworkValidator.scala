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
import scala.jdk.CollectionConverters._
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model._
import org.sunbird.graph.schema.{FrameworkMasterCategoryMap, IDefinition}

import scala.collection.Map
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

trait FrameworkValidator extends IDefinition {

  val logger = LoggerFactory.getLogger("org.sunbird.graph.schema.validator.FrameworkValidator")

  @throws[Exception]
  abstract override def validate(node: Node, operation: String, setDefaultValue: Boolean)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val fwCategories: List[String] = schemaValidator.getConfig.getStringList("frameworkCategories").asScala.toList
    val graphId: String = if(StringUtils.isNotBlank(node.getGraphId)) node.getGraphId else "domain"
    val orgAndTargetFWData: Future[(List[String], List[String], List[Map[String, AnyRef]])] = if(StringUtils.equalsIgnoreCase(Platform.getString("master.category.validation.enabled", "Yes"), "Yes")) getOrgAndTargetFWData(graphId, "Category") else Future(List(), List(), List())
    orgAndTargetFWData.map(orgAndTargetTouple => {
      val orgFwTerms = orgAndTargetTouple._1
      val targetFwTerms = orgAndTargetTouple._2
      val masterCategories = orgAndTargetTouple._3

      validateAndSetMultiFrameworks(node, orgFwTerms, targetFwTerms, masterCategories).flatMap(_ => {
        val framework: String = node.getMetadata.getOrDefault("framework", "").asInstanceOf[String]
        if (null != fwCategories && fwCategories.nonEmpty && framework.nonEmpty) {
          val fwMetadata: Map[String, AnyRef] = node.getMetadata.asScala.filter(entry => fwCategories.contains(entry._1)).toMap
          if (fwMetadata.nonEmpty) {
            Future.sequence(fwMetadata.keys.map { cat =>
              val value: AnyRef = fwMetadata(cat)
              val cacheKey = "cat_" + framework + cat
              val redisEnabled = Platform.getBoolean("redis.enable", false)
              val cachedList: List[String] = if (redisEnabled) RedisCache.getList(cacheKey) else List()
              val termsFuture: Future[List[String]] =
                if (cachedList.nonEmpty) Future.successful(cachedList)
                else getCategoryTermsFromDB(graphId, framework, cat).map { terms =>
                  if (terms.nonEmpty && redisEnabled)
                    RedisCache.saveList(cacheKey, terms)
                  terms
                }
              termsFuture.map { list =>
                val result: Boolean = value match {
                  case v: String            => list.contains(v)
                  case v: util.List[String] => list.asJava.containsAll(v)
                  case v: Array[String]     => v.forall(list.contains)
                  case _ => throw new ClientException("CLIENT_ERROR", "Validation Errors.",
                      util.Arrays.asList("Please provide correct value for [" + cat + "]"))
                }
                if (!result) {
                  if (list.isEmpty) cat + " range data is empty from the given framework."
                  else "Metadata " + cat + " should belong from:" + list.asJava
                } else ""
              }
            }.toList).flatMap { errorList =>
              val errors = errorList.filter(_.nonEmpty)
              if (errors.nonEmpty)
                throw new ClientException("CLIENT_ERROR", "Validation Errors.", errors.asJava)
              super.validate(node, operation, setDefaultValue)
            }
          } else super.validate(node, operation, setDefaultValue)
        } else super.validate(node, operation, setDefaultValue)
      })
    }).flatten
  }

  private def getCategoryTermsFromDB(
      graphId: String, framework: String, category: String
  )(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[List[String]] = {
    val mc = MetadataCriterion.create(new util.ArrayList[Filter]() {{
      add(new Filter("category", SearchConditions.OP_EQUAL, category))
      add(new Filter("status", SearchConditions.OP_NOT_EQUAL, "Retired"))
    }})
    // objectType/nodeType must be set on the criteria itself: the Janus query is narrowed from these
    // (index lookup), while metadata filters are applied afterwards, one database read per vertex.
    val criteria = new SearchCriteria {{
      addMetadata(mc)
      setCountQuery(false)
      setObjectType("Term")
      setNodeType("DATA_NODE")
    }}
    oec.graphService.getNodeByUniqueIds(graphId, criteria).map { nodes =>
      nodes.asScala.filter { n =>
        val id = n.getIdentifier
        id != null && id.toLowerCase.startsWith(framework.toLowerCase + "_")
      }.flatMap { n =>
        Option(n.getMetadata.get("name")).map(_.asInstanceOf[String])
      }.toList
    }
  }

  private def validateAndSetMultiFrameworks(node: Node, orgFwTerms: List[String], targetFwTerms: List[String], masterCategories: List[Map[String, AnyRef]])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Map[String, AnyRef]] = {
    getValidatedTerms(node, orgFwTerms).map(orgTermMap => {
     val jsonPropsType = schemaValidator.getAllPropsType.asScala
     masterCategories.map(masterCategory => {
      val orgIdFieldName = masterCategory.getOrElse("orgIdFieldName", "").asInstanceOf[String]
      val code = masterCategory.getOrElse("code", "").asInstanceOf[String]
      if(StringUtils.isNotBlank(orgIdFieldName)){
        val categoryData = fetchValidatedList(getList(orgIdFieldName, node), orgTermMap)
        if (CollectionUtils.isNotEmpty(categoryData) && StringUtils.isNotBlank(code)) {
          val typeInfo = jsonPropsType.getOrElse(code, "").asInstanceOf[String]
          if(StringUtils.isNotBlank(typeInfo) && typeInfo == "array"){
            node.getMetadata.put(code, categoryData)
          } else {
            node.getMetadata.put(code, categoryData.get(0))
          }
        }
      }
     })
     getValidatedTerms(node, targetFwTerms)
    }).flatten
  }


  private def getList(termName: String, node: Node): util.List[String] = {
    node.getMetadata.get(termName) match {
      case e: String => List(e).asJava
      case e: util.List[String] => e
      case e: Array[String] => e.toList.asJava
      case _ => List().asJava
    }
  }

  private def getOrgAndTargetFWData(graphId: String, objectType: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext):Future[(List[String], List[String],List[Map[String, AnyRef]])] = {
    val masterCategories: Future[List[Map[String, AnyRef]]] = getMasterCategory(graphId, objectType)
    masterCategories.map(result => {
      (result.map(cat => cat.getOrElse("orgIdFieldName", "").asInstanceOf[String]),
        result.map(cat => cat.getOrElse("targetIdFieldName", "").asInstanceOf[String]), result.map(cat => cat.asInstanceOf[Map[String, AnyRef]]))
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
          val masterCategories: scala.collection.immutable.Map[String, AnyRef] = dataNodes.asScala.map(
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
        add(new Filter("status", SearchConditions.OP_NOT_EQUAL, "Retired"))
      }
    })
    // objectType/nodeType must be set on the criteria itself: the Janus query is narrowed from these
    // (index lookup), while metadata filters are applied afterwards, one database read per vertex.
    val searchCriteria = new SearchCriteria {
      {
        addMetadata(mc)
        setCountQuery(false)
        setObjectType(objectType)
        setNodeType("DATA_NODE")
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
      val graphId = if (StringUtils.isNotBlank(node.getGraphId)) node.getGraphId else "domain"
      oec.graphService.getNodeByUniqueIds(graphId, searchCriteria).map(nodeList => {
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
