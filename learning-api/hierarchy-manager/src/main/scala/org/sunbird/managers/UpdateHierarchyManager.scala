package org.sunbird.managers

import java.util
import java.util.{ArrayList, HashMap, HashSet, List, Map, Set}

import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.{BooleanUtils, StringUtils}
import org.sunbird.common.{DateUtils, JsonUtils}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ErrorCodes, ResourceNotFoundException}
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.schema.DefinitionNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.utils.{HierarchyConstants, HierarchyErrorCodes}
import org.w3c.dom.NodeList

import scala.collection.JavaConversions._
import scala.collection.{JavaConverters, mutable}
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

object UpdateHierarchyManager {
    def updateHierarchy(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        validateRequest(request)
        var idMap: mutable.Map[String, String] = mutable.Map()
        val nodesModified: util.HashMap[String, AnyRef] = request.getRequest.get(HierarchyConstants.NODES_MODIFIED).asInstanceOf[util.HashMap[String, AnyRef]]
        val hierarchy: util.HashMap[String, AnyRef] = request.getRequest.get(HierarchyConstants.HIERARCHY).asInstanceOf[util.HashMap[String, AnyRef]]
        val rootId: String = getRootId(nodesModified, hierarchy)
        var nodeList: ListBuffer[Node] = ListBuffer[Node]()
        getValidatedRootNode(rootId, request).map(node => nodeList += node)
        getExistingHierarchyChildren(request, rootId).map(children => {
            if (CollectionUtils.isNotEmpty(children))
                addChildNodesInNodeList(children, request, nodeList)
        })
        Future(ResponseHandler.OK())
    }

    private def validateRequest(request: Request)(implicit ec: ExecutionContext): Unit = {
        if (!request.getRequest.contains(HierarchyConstants.NODES_MODIFIED) && !request.getRequest.contains(HierarchyConstants.HIERARCHY))
            throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "Hierarchy data is empty")
    }

    private def getRootId(nodesModified: util.HashMap[String, AnyRef], hierarchy: util.HashMap[String, AnyRef])(implicit ec: ExecutionContext): String = {
        val rootId: String = nodesModified.keySet()
            .find(key => nodesModified.get(key).asInstanceOf[util.HashMap[String, AnyRef]].get(HierarchyConstants.ROOT).asInstanceOf[Boolean])
            .getOrElse(nodesModified.keySet().find(key => nodesModified.get(key).asInstanceOf[util.HashMap[String, AnyRef]].get(HierarchyConstants.ROOT).asInstanceOf[Boolean]).orNull)
        if (StringUtils.isEmpty(rootId) && StringUtils.isAllBlank(rootId))
            throw new ClientException(HierarchyErrorCodes.ERR_INVALID_ROOT_ID, "Please Provide Valid Root Node Identifier")
        rootId
    }

    private def getValidatedRootNode(identifier: String, request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        val req = new Request(request)
        req.put(HierarchyConstants.IDENTIFIER, identifier)
        req.put("mode", "edit")
        DefinitionNode.getNode(req).map(rootNode => {
            if (!StringUtils.equals(rootNode.getMetadata.get("mimeType").asInstanceOf[String], HierarchyConstants.COLLECTION_MIME_TYPE)) {
                throw new ClientException(HierarchyErrorCodes.ERR_INVALID_ROOT_ID, "Invalid MimeType for Root Node Identifier  : " + identifier)
                TelemetryManager.error("UpdateHierarchyManager.getValidatedRootNode :: Invalid MimeType for Root node id: " + identifier)
            }
            //Todo: Remove if not required
            if (null == rootNode.getMetadata.get("version") || (rootNode.getMetadata.get("version").asInstanceOf[Number]).intValue < 2) {
                TelemetryManager.error("UpdateHierarchyManager.getValidatedRootNode :: Invalid Content Version for Root node id: " + identifier)
                throw new ClientException(HierarchyErrorCodes.ERR_INVALID_ROOT_ID, "The collection version is not up to date " + identifier)
            }
            rootNode.getMetadata.put(HierarchyConstants.VERSION, HierarchyConstants.LATEST_CONTENT_VERSION)
            rootNode
        })
    }

    private def getExistingHierarchyChildren(request: Request, identifier: String)(implicit ec: ExecutionContext): Future[util.ArrayList[Map[String, AnyRef]]] = {
        val req = new Request(request)
        req.put(HierarchyConstants.IDENTIFIER, identifier)
        ExternalPropsManager.fetchProps(req, List("hierarchy"))
            .map(response => {
                if (!ResponseHandler.checkError(response)) {
                    val hierarchyString = response.getResult.toMap.getOrElse("hierarchy", "").asInstanceOf[String]
                    if (!hierarchyString.isEmpty) {
                        val hierarchyMap = JsonUtils.deserialize(hierarchyString, classOf[util.Map[String, AnyRef]]).toMap
                        hierarchyMap.getOrElse("children", new util.ArrayList[Map[String, AnyRef]]()).asInstanceOf[util.ArrayList[Map[String, AnyRef]]]
                    } else new util.ArrayList[Map[String, AnyRef]]()
                } else new util.ArrayList[Map[String, AnyRef]]()
            })
    }

    private def addChildNodesInNodeList(childrenMaps: util.ArrayList[Map[String, AnyRef]], request: Request, nodeList: ListBuffer[Node])(implicit ec: ExecutionContext): Unit = {
        if (CollectionUtils.isNotEmpty(childrenMaps))
            childrenMaps.map(child => {
                addNodeToList(child, request, nodeList)
                addChildNodesInNodeList(child.get(HierarchyConstants.CHILDREN).asInstanceOf[util.ArrayList[Map[String, AnyRef]]], request, nodeList)
            })
    }

    private def addNodeToList(child: Map[String, AnyRef], request: Request, nodeList: ListBuffer[Node])(implicit ec: ExecutionContext) = {
        if (StringUtils.equalsIgnoreCase("Default", child.get(HierarchyConstants.VISIBILITY).asInstanceOf[String])) {
            DefinitionNode.getNode(request).map(node => {
                node.getMetadata.put(HierarchyConstants.DEPTH, child.get(HierarchyConstants.DEPTH))
                node.getMetadata.put(HierarchyConstants.PARENT, child.get(HierarchyConstants.PARENT))
                node.getMetadata.put(HierarchyConstants.INDEX, child.get(HierarchyConstants.INDEX))
                nodeList += node
            })
        } else {
            val childData: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
            childData.putAll(child)
            childData.remove(HierarchyConstants.CHILDREN)
            nodeList += NodeUtil.deserialize(childData, request.getContext.get("schemaName").asInstanceOf[String], DefinitionNode.getRelationsMap(request))
        }
    }


    private def updateNodesModifiedInNodeList(nodeList: ListBuffer[Node], rootId: String, nodesModified: util.HashMap[String, AnyRef], request: Request): Map[String, AnyRef] = {
        updateRootNode(rootId, nodeList, nodesModified)
        val idMap: mutable.Map[String, String] = mutable.Map()
        nodesModified.foreach(nodeModified => {
            val metadata = nodeModified._2.asInstanceOf[util.HashMap[String, AnyRef]].getOrDefault(HierarchyConstants.METADATA, new util.HashMap()).asInstanceOf[util.HashMap[String, AnyRef]]
            metadata.remove(HierarchyConstants.DIALCODES)
            metadata.put(HierarchyConstants.OBJECT_TYPE, HierarchyConstants.CONTENT_OBJECT_TYPE)
            metadata.put(HierarchyConstants.STATUS, "Draft")
            metadata.put(HierarchyConstants.LAST_UPDATED_ON, DateUtils.formatCurrentDate)
            if (nodeModified._2.asInstanceOf[util.HashMap[String, AnyRef]].containsKey(HierarchyConstants.IS_NEW)
                && nodeModified._2.asInstanceOf[util.HashMap[String, AnyRef]].get(HierarchyConstants.IS_NEW).asInstanceOf[Boolean]) {
                if (nodeModified._2.asInstanceOf[util.HashMap[String, AnyRef]].get(HierarchyConstants.ROOT).asInstanceOf[Boolean])
                    metadata.put(HierarchyConstants.VISIBILITY, "Parent")
                createNewNode(nodeModified._1, idMap, metadata, nodeList, request)
            } else updateTempNode(nodeModified._1, nodeList, idMap, metadata)
        })
        idMap
    }

    private def updateRootNode(rootId: String, nodeList: ListBuffer[Node], nodesModified: util.HashMap[String, AnyRef]): Unit = {
        if (nodesModified.containsKey(rootId)) {
            val metadata = nodesModified.getOrDefault(rootId, new util.HashMap()).asInstanceOf[util.HashMap[String, AnyRef]].getOrDefault(HierarchyConstants.METADATA, new util.HashMap()).asInstanceOf[util.HashMap[String, AnyRef]]
            updateNodeList(nodeList, rootId, metadata)
            nodesModified.remove(rootId)
        }
    }

    private def createNewNode(nodeId: String, idMap: mutable.Map[String, String], metadata: util.HashMap[String, AnyRef], nodeList: ListBuffer[Node], request: Request): Unit = {
        val identifier: String = Identifier.getIdentifier(HierarchyConstants.TAXONOMY_ID, Identifier.getUniqueIdFromTimestamp)
        idMap += (nodeId -> identifier)
        metadata.put(HierarchyConstants.IDENTIFIER, identifier)
        metadata.put(HierarchyConstants.CODE, nodeId)
        metadata.put(HierarchyConstants.VERSION_KEY, System.currentTimeMillis + "")
        metadata.put(HierarchyConstants.CREATED_ON, DateUtils.formatCurrentDate)
        metadata.put(HierarchyConstants.LAST_STATUS_CHANGED_ON, DateUtils.formatCurrentDate)
        //TODO:validate
        val node = NodeUtil.deserialize(metadata, request.getContext.get("schemaName").asInstanceOf[String], DefinitionNode.getRelationsMap(request))
        node.setGraphId(HierarchyConstants.TAXONOMY_ID)
        node.setObjectType(HierarchyConstants.CONTENT_OBJECT_TYPE)
        node.setNodeType(HierarchyConstants.DATA_NODE)
        nodeList.add(node)
    }

    private def updateTempNode(nodeId: String, nodeList: ListBuffer[Node], idMap: mutable.Map[String, String], metadata: util.HashMap[String, AnyRef]): Unit = {
        val tempNode: Node = getTempNode(nodeList, nodeId)
        if (null != tempNode && StringUtils.isNotBlank(tempNode.getIdentifier)) {
            metadata.put(HierarchyConstants.IDENTIFIER, tempNode.getIdentifier)
            idMap += (nodeId -> tempNode.getIdentifier)
            //TODO:validate
            updateNodeList(nodeList, tempNode.getIdentifier, metadata)
        }
        else throw new ResourceNotFoundException(HierarchyErrorCodes.ERR_CONTENT_NOT_FOUND, "Content not found with identifier: " + nodeId)
    }

    private def validate() = {

    }

    private def getChildrenHierarchy(nodeList: ListBuffer[Node], rootId: String, hierarchyData: util.HashMap[String, AnyRef], idMap: mutable.Map[String, String])(implicit ec: ExecutionContext) = {

        getPreparedHierarchyData(nodeList, hierarchyData, idMap, rootId)

    }

    private def getPreparedHierarchyData(nodeList: ListBuffer[Node], hierarchyData: util.HashMap[String, AnyRef], idMap: mutable.Map[String, String], rootId: String)(implicit ec: ExecutionContext): util.List[util.Map[String, AnyRef]] = {

        if (MapUtils.isNotEmpty(hierarchyData)) {
            val childIdMap: Map[String, List[String]] = hierarchyData.map(entry => (if (null != idMap.get(entry._1)) idMap.get(entry._1) else entry._1).asInstanceOf[String] ->
                entry._2.asInstanceOf[util.HashMap[String, AnyRef]]
                    .get(HierarchyConstants.CHILDREN).asInstanceOf[util.ArrayList[String]]
                    .map(id => if (null != idMap.get(id)) idMap.get(id) else id).asInstanceOf[List[String]]).toMap
            val childNodes: mutable.HashSet[Node] = mutable.HashSet[Node]()
            val updatedNodeList: ListBuffer[Node] = ListBuffer()
            updatedNodeList += getTempNode(nodeList, rootId)
            updateNodeList(updatedNodeList, rootId, new util.HashMap() {
                {
                    put(HierarchyConstants.DEPTH, 0)
                }
            })
            updateHierarchyRelatedData(childIdMap.get(rootId), 1, rootId, nodeList, childIdMap, childNodes, updatedNodeList)
            updateNodeList(nodeList, rootId, new util.HashMap[String, AnyRef]() {})
            HierarchyManager.convertNodeToMap(updatedNodeList.toList)
        }
        else {
            updateNodeList(nodeList, rootId, new util.HashMap[String, AnyRef]() {})
            HierarchyManager.convertNodeToMap(nodeList.toList)
        }
    }

    private def updateHierarchyRelatedData(childrenIds: List[String], depth: Int, parent: String, nodeList: ListBuffer[Node], childMap: Map[String, List[String]], childNodes: mutable.HashSet[Node], updatedNodeList: ListBuffer[Node])(implicit ec: ExecutionContext) = {
        val index: Int = 1
        childrenIds.foreach(id => {
            val tempNode = getTempNode(nodeList, id)
            if (null != tempNode && StringUtils.equalsIgnoreCase("Parent", tempNode.getMetadata.get(HierarchyConstants.VISIBILITY).asInstanceOf[String]))
                populateHierarchyRelatedData(tempNode, depth, index, parent)
            else {

            }
        })

    }

    private def populateHierarchyRelatedData(tempNode: Node, depth: Int, index: Int, parent: String) = {
        tempNode.getMetadata.put(HierarchyConstants.DEPTH, depth)
        tempNode.getMetadata.put(HierarchyConstants.PARENT, parent)
        tempNode.getMetadata.put(HierarchyConstants.INDEX, index)
    }

    /**
      * Get the Node with ID provided from List else return Null.
      *
      * @param nodeList
      * @param id
      * @return
      */
    private def getTempNode(nodeList: ListBuffer[Node], id: String): Node = {
        nodeList.toList.find(node => StringUtils.startsWith(node.getIdentifier, id)).orNull
    }

    private def updateNodeList(nodeList: ListBuffer[Node], id: String, metadata: util.HashMap[String, AnyRef]): Unit = {
        if (MapUtils.isNotEmpty(metadata))
            nodeList.toList.filter(node => node.getIdentifier.startsWith(id)).map(node => node.getMetadata.putAll(metadata))
    }

}




















