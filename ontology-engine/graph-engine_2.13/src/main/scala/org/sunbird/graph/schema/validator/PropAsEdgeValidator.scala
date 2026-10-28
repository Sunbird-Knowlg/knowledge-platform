package org.sunbird.graph.schema.validator

import org.apache.commons.collections4.CollectionUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.Platform
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.{Filter, MetadataCriterion, Node, SearchConditions, SearchCriteria}
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.schema.IDefinition

import scala.collection.convert.ImplicitConversions._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

trait PropAsEdgeValidator extends IDefinition {

    val edgePropsKey = "edge.properties"
    val prefix = "edge_"

    @throws[Exception]
    abstract override def validate(node: Node, operation: String, setDefaultValue: Boolean)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        if (schemaValidator.getConfig.hasPath(edgePropsKey)) {
            val keys = CollectionUtils.intersection(node.getMetadata.keySet(), schemaValidator.getConfig.getObject(edgePropsKey).keySet())
            if (!keys.isEmpty) {
                Future.sequence(keys.toArray().toList.map { key =>
                    val objectType = schemaValidator.getConfig.getString(edgePropsKey + "." + key.toString)
                    val cacheKey = prefix + objectType.toLowerCase
                    val cachedList: List[String] = if (Platform.getBoolean("redis.enable", false))
                        RedisCache.getList(cacheKey) else List()
                    val resolvedGraphId = if (org.apache.commons.lang3.StringUtils.isNotBlank(node.getGraphId)) node.getGraphId else "domain"
                    val listFuture: Future[List[String]] =
                        if (cachedList.nonEmpty) Future.successful(cachedList)
                        else getEdgeListFromDB(resolvedGraphId, objectType).map { list =>
                            if (list.nonEmpty && Platform.getBoolean("redis.enable", false))
                                RedisCache.saveList(cacheKey, list)
                            list
                        }
                    listFuture.map { list =>
                        if (list.isEmpty)
                            throw new ClientException("ERR_EMPTY_EDGE_PROPERTY_LIST", "The list to validate input is empty.")
                        val value = node.getMetadata.get(key)
                        if (value.isInstanceOf[String]) {
                            if (!list.contains(value.asInstanceOf[String]))
                                throw new ClientException("ERR_INVALID_EDGE_PROPERTY", key + " value should be one of " + list)
                        } else if (value.isInstanceOf[java.util.List[AnyRef]]) {
                            val filteredSize = value.asInstanceOf[java.util.List[AnyRef]].toList.count(e => list.contains(e))
                            if (filteredSize != value.asInstanceOf[java.util.List[AnyRef]].size)
                                throw new ClientException("ERR_INVALID_EDGE_PROPERTY", key + " value should be any of " + list)
                        } else {
                            throw new ClientException("ERR_INVALID_EDGE_PROPERTY", key + " given datatype is invalid.")
                        }
                    }
                }).flatMap(_ => super.validate(node, operation, setDefaultValue))
            } else super.validate(node, operation, setDefaultValue)
        } else super.validate(node, operation, setDefaultValue)
    }

    private def getEdgeListFromDB(
        graphId: String, objectType: String
    )(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[List[String]] = {
        val mc = MetadataCriterion.create(new java.util.ArrayList[Filter]() {{
            add(new Filter(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), SearchConditions.OP_EQUAL, objectType))
            add(new Filter(SystemProperties.IL_SYS_NODE_TYPE.name(), SearchConditions.OP_EQUAL, "DATA_NODE"))
            add(new Filter("status", SearchConditions.OP_EQUAL, "Live"))
        }})
        // objectType/nodeType must be set on the criteria itself: the Janus query is narrowed from these
        // (index lookup), while metadata filters are applied afterwards, one database read per vertex.
        val criteria = new SearchCriteria {{
            addMetadata(mc)
            setCountQuery(false)
            setObjectType(objectType)
            setNodeType("DATA_NODE")
        }}
        oec.graphService.getNodeByUniqueIds(graphId, criteria).map { nodes =>
            nodes.asScala.flatMap { n =>
                Option(n.getMetadata.get("name")).map(_.asInstanceOf[String])
            }.toList
        }
    }
}
