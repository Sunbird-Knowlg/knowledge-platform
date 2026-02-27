package org.sunbird.managers.content

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.Platform
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ErrorCodes, ResponseCode}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.external.store.ExternalStoreFactory
import org.sunbird.telemetry.logger.TelemetryManager

import java.util
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object RelationManager {

    private val relationCacheKeyspace: String =
        if (Platform.config.hasPath("hierarchy.relation.keyspace"))
            Platform.config.getString("hierarchy.relation.keyspace")
        else "dev_hierarchy_store"

    private val relationCacheTable: String =
        if (Platform.config.hasPath("hierarchy.relation.table"))
            Platform.config.getString("hierarchy.relation.table")
        else "hierarchy_relations"

    private val primaryKey: java.util.List[String] = java.util.Arrays.asList("relationship_key")
    private val propsMapping: Map[String, String] = Map("node_ids" -> "")


    def updateHierarchyRelationships(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
        val rootId = request.getOrDefault("rootId", "").asInstanceOf[String]
        if (StringUtils.isBlank(rootId))
            throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "rootId is mandatory")

        HierarchyManager.fetchHierarchy(request, rootId).flatMap { hierarchy =>
            if (hierarchy.nonEmpty) {
                val hierarchyJava = hierarchy.asJava

                val leafNodesMap     = getLeafNodes(rootId, hierarchyJava)
                val optionalNodesMap = getOptionalNodes(rootId, hierarchyJava)
                val ancestorsMap     = getAncestors(rootId, hierarchyJava)

                TelemetryManager.log(s"RelationCache | rootId=$rootId | leafNodes=${leafNodesMap.size} | optionalNodes=${optionalNodesMap.size} | ancestors=${ancestorsMap.size}")

                val storeFutures = List(
                    storeRelationshipData(rootId, "leafnodes",     leafNodesMap),
                    storeRelationshipData(rootId, "optionalnodes", optionalNodesMap),
                    storeRelationshipData(rootId, "ancestors",     ancestorsMap)
                )

                Future.sequence(storeFutures).map { _ =>
                    TelemetryManager.log(s"Hierarchy relationships updated successfully for collection: $rootId")
                    ResponseHandler.OK.put("rootId", rootId)
                }
            } else {
                Future(ResponseHandler.ERROR(
                    ResponseCode.RESOURCE_NOT_FOUND,
                    ResponseCode.RESOURCE_NOT_FOUND.name(),
                    s"Hierarchy is empty for rootId: $rootId"
                ))
            }
        }
    }


    private def getLeafNodes(identifier: String, hierarchy: util.Map[String, AnyRef]): Map[String, List[String]] = {
        val mimeType = hierarchy.getOrDefault("mimeType", "").asInstanceOf[String]
        if (StringUtils.equalsIgnoreCase(mimeType, "application/vnd.ekstep.content-collection")) {
            val leafNodes = getOrComposeLeafNodes(hierarchy, compose = false)
            val selfMap: Map[String, List[String]] = if (leafNodes.nonEmpty) Map(identifier -> leafNodes) else Map()
            val children = getChildren(hierarchy)
            val childLeafNodesMap: Map[String, List[String]] =
                if (CollectionUtils.isNotEmpty(children))
                    children.asScala.flatMap { child =>
                        val childId = child.get("identifier").asInstanceOf[String]
                        getLeafNodes(childId, child)
                    }.toMap
                else Map()
            (selfMap ++ childLeafNodesMap).filter(_._2.nonEmpty)
        } else Map()
    }

    private def getOrComposeLeafNodes(hierarchy: util.Map[String, AnyRef], compose: Boolean = true): List[String] = {
        if (hierarchy.containsKey("leafNodes") && !compose) {
            hierarchy.getOrDefault("leafNodes", java.util.Arrays.asList())
                .asInstanceOf[java.util.List[String]].asScala.toList
        } else {
            val children = getChildren(hierarchy)
            val childCollections = children.asScala.filter(isCollection)
            val leafList = childCollections.flatMap(coll => getOrComposeLeafNodes(coll, compose = true)).toList
            val ids = children.asScala
                .filterNot(isCollection)
                .map(_.getOrDefault("identifier", "").asInstanceOf[String])
                .filter(StringUtils.isNotBlank)
                .toList
            leafList ++ ids
        }
    }


    private def getOptionalNodes(identifier: String, hierarchy: util.Map[String, AnyRef]): Map[String, List[String]] = {
        val mimeType = hierarchy.getOrDefault("mimeType", "").asInstanceOf[String]
        if (StringUtils.equalsIgnoreCase(mimeType, "application/vnd.ekstep.content-collection")) {
            val optionalNodes = getOrComposeOptionalNodes(hierarchy)
            val selfMap: Map[String, List[String]] = if (optionalNodes.nonEmpty) Map(identifier -> optionalNodes) else Map()
            val children = getChildren(hierarchy)
            val childOptionalNodesMap: Map[String, List[String]] =
                if (CollectionUtils.isNotEmpty(children))
                    children.asScala.flatMap { child =>
                        val childId = child.get("identifier").asInstanceOf[String]
                        getOptionalNodes(childId, child)
                    }.toMap
                else Map()
            (selfMap ++ childOptionalNodesMap).filter(_._2.nonEmpty)
        } else Map()
    }

    private def getOrComposeOptionalNodes(hierarchy: util.Map[String, AnyRef]): List[String] = {
        val children = getChildren(hierarchy)
        val ids = children.asScala
            .filter(isOptional)
            .map(_.getOrDefault("identifier", "").asInstanceOf[String])
            .filter(StringUtils.isNotBlank)
            .toList
        val childCollections = children.asScala.filterNot(isOptional)
        val optionalList = childCollections.flatMap(coll => getOrComposeOptionalNodes(coll)).toList
        optionalList ++ ids
    }

    private def getAncestors(
        identifier : String,
        hierarchy  : util.Map[String, AnyRef],
        parents    : List[String] = List()
    ): Map[String, List[String]] = {
        val isColl    = isCollection(hierarchy)
        val ancestors = if (isColl) identifier :: parents else parents

        if (isColl) {
            val childMaps = getChildren(hierarchy).asScala.map { child =>
                val childId = child.get("identifier").asInstanceOf[String]
                getAncestors(childId, child, ancestors)
            }.filter(_.nonEmpty).toList

            val merged =
                if (childMaps.isEmpty) Map[String, List[String]]()
                else childMaps.reduce { (a, b) =>
                    (a.toSeq ++ b.toSeq).groupBy(_._1)
                        .view.mapValues(_.flatMap(_._2).distinct.toList).toMap
                }
            merged.filter(_._2.nonEmpty)
        } else {
            Map(identifier -> parents).filter(_._2.nonEmpty)
        }
    }

    private def isCollection(content: util.Map[String, AnyRef]): Boolean =
        StringUtils.equalsIgnoreCase(
            content.getOrDefault("mimeType", "").asInstanceOf[String],
            "application/vnd.ekstep.content-collection"
        )

    private def isOptional(content: util.Map[String, AnyRef]): Boolean = {
        val relMeta = content.getOrDefault("relationalMetadata", new java.util.HashMap[String, AnyRef]())
            .asInstanceOf[util.Map[String, AnyRef]]
        StringUtils.equalsIgnoreCase(relMeta.getOrDefault("optional", "").toString, "true")
    }

    private def getChildren(hierarchy: util.Map[String, AnyRef]): java.util.List[util.Map[String, AnyRef]] = {
        val children = hierarchy.getOrDefault("children", java.util.Arrays.asList())
            .asInstanceOf[java.util.List[util.Map[String, AnyRef]]]
        if (CollectionUtils.isEmpty(children)) new java.util.ArrayList[util.Map[String, AnyRef]]() else children
    }

    private def storeRelationshipData(
        rootId           : String,
        relationshipType : String,
        dataMap          : Map[String, List[String]]
    )(implicit ec: ExecutionContext): Future[List[Response]] = {
        val store = ExternalStoreFactory.getExternalStore(
            s"$relationCacheKeyspace.$relationCacheTable", primaryKey)

        val futures = dataMap.map { case (identifier, nodeIds) =>
            val relationshipKey =
                if (StringUtils.isNotBlank(rootId)) s"$rootId:$identifier:$relationshipType"
                else s"$identifier:$relationshipType"

            store.update(
                relationshipKey,
                List("node_ids"),
                List(nodeIds.asJava.asInstanceOf[AnyRef]),
                propsMapping
            )
        }
        Future.sequence(futures.toList)
    }
}
