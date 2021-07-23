package org.sunbird.graph.schema.validator

import org.apache.commons.collections4.CollectionUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.schema.IDefinition

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

trait PropAsEdgeValidator extends IDefinition {

    val edgePropsKey = "edge.properties"
    val prefix = "edge_"

    @throws[Exception]
    abstract override def validate(node: Node, operation: String, setDefaultValue: Boolean)(implicit ec: ExecutionContext, oec:OntologyEngineContext): Future[Node] = {
        if (schemaValidator.getConfig.hasPath(edgePropsKey)) {
            val keys = CollectionUtils.intersection(node.getMetadata.keySet(), schemaValidator.getConfig.getObject(edgePropsKey).keySet())
            if (!keys.isEmpty) {
                keys.toArray().toStream.map(key => {
                    val cacheKey = prefix + schemaValidator.getConfig.getString(edgePropsKey + "." + key).toLowerCase
                    val list = RedisCache.getList(cacheKey)
                    if (CollectionUtils.isNotEmpty(list)) {
                        val value = node.getMetadata.get(key)
                        if (value.isInstanceOf[String]) {
                            if(!list.contains(value.asInstanceOf[String]))
                                throw new ClientException("ERR_INVALID_EDGE_PROPERTY", key + " value should be one of " + list)
                        } else if (value.isInstanceOf[java.util.List[AnyRef]]) {
                            val filteredSize = value.asInstanceOf[java.util.List[AnyRef]].toList.filter(e => list.contains(e)).size
                            if(filteredSize != value.asInstanceOf[java.util.List[AnyRef]].size)
                                throw new ClientException("ERR_INVALID_EDGE_PROPERTY", key + " value should be any of " + list)
                        } else {
                            throw new ClientException("ERR_INVALID_EDGE_PROPERTY", key + " given datatype is invalid.")
                        }
                    } else {
                        throw new ClientException("ERR_EMPTY_EDGE_PROPERTY_LIST", "The list to validate input is empty.")
                    }
                })
            }
        }
        super.validate(node, operation)
    }
}
