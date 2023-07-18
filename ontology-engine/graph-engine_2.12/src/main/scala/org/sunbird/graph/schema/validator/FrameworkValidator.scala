package org.sunbird.graph.schema.validator

import org.apache.commons.lang3.StringUtils
import org.slf4j.{Logger, LoggerFactory}
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.Platform
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model._
import org.sunbird.graph.schema.{FrameworkMasterCategoryMap, IDefinition}

import java.util
import java.util.concurrent.CompletionException
import scala.collection.JavaConverters._
import scala.collection.Map
import scala.collection.convert.ImplicitConversions._
import scala.concurrent.{ExecutionContext, Future}

trait FrameworkValidator extends IDefinition {

  val logger: Logger = LoggerFactory.getLogger("org.sunbird.graph.schema.validator.FrameworkValidator")

  @throws[Exception]
  abstract override def validate(node: Node, operation: String, setDefaultValue: Boolean)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val graphId: String = if(StringUtils.isNotBlank(node.getGraphId)) node.getGraphId else "domain"
    val validateFramework = Platform.getBoolean("master.category.validation.enabled", true)

    if(!validateFramework) super.validate(node, operation)

    val masterCategories: Map[String, AnyRef] = if (FrameworkMasterCategoryMap.containsKey("masterCategories") && null != FrameworkMasterCategoryMap.get("masterCategories")) {
      FrameworkMasterCategoryMap.get("masterCategories")
    } else {
      val nodes = getMasterCategoryNodes(graphId, "Category")
      nodes.map(dataNodes => {
        if (dataNodes.isEmpty) {
          logger.warn(s"ALERT!... There are no master framework category objects defined. This will not enable framework category properties validation.")
          List[Map[String, AnyRef]]()
        } else {
          val masterCategories: scala.collection.immutable.Map[String, AnyRef] = dataNodes.map(
            node => node.getMetadata.getOrDefault("code", "").asInstanceOf[String] -> Map[String, AnyRef](
              "code" -> node.getMetadata.getOrDefault("code", "").asInstanceOf[String],
              "searchIdFieldName" -> node.getMetadata.getOrDefault("searchIdFieldName", "").asInstanceOf[String],
              "searchLabelFieldName" -> node.getMetadata.getOrDefault("searchLabelFieldName", "").asInstanceOf[String])).toMap
          FrameworkMasterCategoryMap.put("masterCategories", masterCategories)
          masterCategories.map(obj => obj._2.asInstanceOf[Map[String, AnyRef]]).toList
        }
      }) recoverWith {
        case e: CompletionException => throw e.getCause
      }
      FrameworkMasterCategoryMap.get("masterCategories")
    }

    val framework: String = node.getMetadata.getOrDefault("framework", "").asInstanceOf[String]
    if (null != masterCategories && masterCategories.nonEmpty && framework.nonEmpty) {
      //prepare data for validation
      val fwMetadata: Map[String, AnyRef] = node.getMetadata.asScala.filterKeys(key => masterCategories.contains(key))
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


}
