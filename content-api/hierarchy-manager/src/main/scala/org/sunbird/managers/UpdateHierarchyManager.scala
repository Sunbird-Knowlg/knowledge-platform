package org.sunbird.managers

import java.util
import java.util.concurrent.CompletionException
import java.util.HashMap

import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.{DateUtils, JsonUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ErrorCodes, ResourceNotFoundException}
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.schema.DefinitionNode
import org.sunbird.graph.utils.{NodeUtil, ScalaJsonUtils}
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.utils.{HierarchyConstants, HierarchyErrorCodes}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

object UpdateHierarchyManager {
    @throws[Exception]
    def updateHierarchy(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        validateRequest(request)
        val nodesModified: util.HashMap[String, AnyRef] = request.getRequest.get(HierarchyConstants.NODES_MODIFIED).asInstanceOf[util.HashMap[String, AnyRef]]
        val hierarchy: util.HashMap[String, AnyRef] = request.getRequest.get(HierarchyConstants.HIERARCHY).asInstanceOf[util.HashMap[String, AnyRef]]
        val rootId: String = getRootId(nodesModified, hierarchy)
        request.getContext.put(HierarchyConstants.ROOT_ID, rootId)
        var nodeList: ListBuffer[Node] = ListBuffer[Node]()
        //TODO: Try to combine get node and hierarchy in one call as external prop?
        getValidatedRootNode(rootId, request).map(node => {
            nodeList += node
            //TODO: Remove should Image be deleted after migration
            request.getContext.put("shouldImageDelete", shouldImageBeDeleted(node).asInstanceOf[AnyRef])
            getExistingHierarchyChildren(request, rootId).map(existingChildren => {
                addChildNodesInNodeList(existingChildren, request, nodeList).map(data => {
                    updateNodesModifiedInNodeList(nodeList, rootId, nodesModified, request).map(idMap => {
                        validateNodes(nodeList).map(result => {
                            getChildrenHierarchy(nodeList, rootId, hierarchy, idMap).map(children => {
                                updateHierarchyData(rootId, children, nodeList, request).map(node => {
                                    val response = ResponseHandler.OK()
                                    response.put(HierarchyConstants.CONTENT_ID, rootId)
                                    idMap.remove(rootId)
                                    response.put(HierarchyConstants.IDENTIFIERS, mapAsJavaMap(idMap))
                                    if(request.getContext.get("shouldImageDelete").asInstanceOf[Boolean])
                                        deleteHierarchy(request)
                                    Future(response)
                                }).flatMap(f => f)
                            }).flatMap(f => f)
                        }).flatMap(f => f)
                    }).flatMap(f => f)
                }).flatMap(f => f)
            }).flatMap(f => f)
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
    }

    private def validateRequest(request: Request)(implicit ec: ExecutionContext): Unit = {
        if (!request.getRequest.contains(HierarchyConstants.NODES_MODIFIED) && !request.getRequest.contains(HierarchyConstants.HIERARCHY))
            throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "Hierarchy data is empty")
    }

    /**
      * Checks if root id is empty, all black or image id
      *
      * @param nodesModified
      * @param hierarchy
      * @param ec
      * @return
      */
    private def getRootId(nodesModified: util.HashMap[String, AnyRef], hierarchy: util.HashMap[String, AnyRef])(implicit ec: ExecutionContext): String = {
        val rootId: String = nodesModified.keySet()
            .find(key => nodesModified.get(key).asInstanceOf[util.HashMap[String, AnyRef]].get(HierarchyConstants.ROOT).asInstanceOf[Boolean])
            .getOrElse(hierarchy.keySet().find(key => hierarchy.get(key).asInstanceOf[util.HashMap[String, AnyRef]].get(HierarchyConstants.ROOT).asInstanceOf[Boolean]).orNull)
        if (StringUtils.isEmpty(rootId) && StringUtils.isAllBlank(rootId) || StringUtils.contains(rootId, HierarchyConstants.IMAGE_SUFFIX))
            throw new ClientException(HierarchyErrorCodes.ERR_INVALID_ROOT_ID, "Please Provide Valid Root Node Identifier")
        rootId
    }

    //Check if you can combine the below methods
    private def getValidatedRootNode(identifier: String, request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        val req = new Request(request)
        req.put(HierarchyConstants.IDENTIFIER, identifier)
        req.put(HierarchyConstants.MODE, HierarchyConstants.EDIT_MODE)
        DataNode.read(req).map(rootNode => {
            if (!StringUtils.equals(rootNode.getMetadata.get(HierarchyConstants.MIME_TYPE).asInstanceOf[String], HierarchyConstants.COLLECTION_MIME_TYPE)) {
                throw new ClientException(HierarchyErrorCodes.ERR_INVALID_ROOT_ID, "Invalid MimeType for Root Node Identifier  : " + identifier)
                TelemetryManager.error("UpdateHierarchyManager.getValidatedRootNode :: Invalid MimeType for Root node id: " + identifier)
            }
            //Todo: Remove if not required
            if (null == rootNode.getMetadata.get(HierarchyConstants.VERSION) || rootNode.getMetadata.get(HierarchyConstants.VERSION).asInstanceOf[Number].intValue < 2) {
                TelemetryManager.error("UpdateHierarchyManager.getValidatedRootNode :: Invalid Content Version for Root node id: " + identifier)
                throw new ClientException(HierarchyErrorCodes.ERR_INVALID_ROOT_ID, "The collection version is not up to date " + identifier)
            }
            rootNode.getMetadata.put(HierarchyConstants.VERSION, HierarchyConstants.LATEST_CONTENT_VERSION)
            rootNode
        })
    }

    private def getExistingHierarchyChildren(request: Request, identifier: String)(implicit ec: ExecutionContext): Future[util.ArrayList[util.HashMap[String, AnyRef]]] = {
        val hierarchyFuture = if (request.getContext.get("shouldImageDelete").asInstanceOf[Boolean]) fetchImageHierarchy(request, identifier) else fetchHierarchy(request, identifier)
        hierarchyFuture.map(response => {
            if (!ResponseHandler.checkError(response)) {
                val hierarchyString = response.getResult.toMap.getOrElse(HierarchyConstants.HIERARCHY, "").asInstanceOf[String]
                if (!hierarchyString.isEmpty) {
                    val hierarchyMap = JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]])
                    hierarchyMap.getOrElse(HierarchyConstants.CHILDREN, new util.ArrayList[util.HashMap[String, AnyRef]]()).asInstanceOf[util.ArrayList[util.HashMap[String, AnyRef]]]
                } else new util.ArrayList[util.HashMap[String, AnyRef]]()
            } else new util.ArrayList[util.HashMap[String, AnyRef]]()
        })
    }

//    private def getExistingHierarchyChildren(request: Request, identifier: String)(implicit ec: ExecutionContext): Future[util.ArrayList[util.HashMap[String, AnyRef]]] = {
//        fetchHierarchy(request, identifier).map(rootNode => {
//            val hierarchyString = rootNode.getMetadata.getOrDefault(HierarchyConstants.HIERARCHY, "").asInstanceOf[String]
//            if (!hierarchyString.isEmpty) {
//                val hierarchyMap = JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]])
//                hierarchyMap.getOrElse(HierarchyConstants.CHILDREN, new util.ArrayList[util.HashMap[String, AnyRef]]()).asInstanceOf[util.ArrayList[util.HashMap[String, AnyRef]]]
//            } else new util.ArrayList[util.HashMap[String, AnyRef]]()
//        }) recoverWith { case e: CompletionException => throw e.getCause}
//    }

    private def fetchHierarchy(request: Request, identifier: String)(implicit ec: ExecutionContext): Future[Response] = {
        val req = new Request(request)
        req.put(HierarchyConstants.IDENTIFIER, identifier)
        ExternalPropsManager.fetchProps(req, List(HierarchyConstants.HIERARCHY))
    }

    private def fetchImageHierarchy(request: Request, identifier: String)(implicit ec: ExecutionContext): Future[Response] = {
        val req = new Request(request)
        req.put(HierarchyConstants.IDENTIFIER, identifier + HierarchyConstants.IMAGE_SUFFIX)
        ExternalPropsManager.fetchProps(req, List(HierarchyConstants.HIERARCHY)) recoverWith { case e: CompletionException =>
            fetchHierarchy(request, identifier)
        }
    }

    private def addChildNodesInNodeList(childrenMaps: util.ArrayList[util.HashMap[String, AnyRef]], request: Request, nodeList: ListBuffer[Node])(implicit ec: ExecutionContext): Future[AnyRef] = {
        if (CollectionUtils.isNotEmpty(childrenMaps)) {
            val futures = childrenMaps.map(child => {
                addNodeToList(child, request, nodeList).map(modifiedList => {
                    if (!StringUtils.equalsIgnoreCase(HierarchyConstants.DEFAULT, child.get(HierarchyConstants.VISIBILITY).asInstanceOf[String]))
                        addChildNodesInNodeList(child.get(HierarchyConstants.CHILDREN).asInstanceOf[util.ArrayList[util.HashMap[String, AnyRef]]], request, nodeList)
                    else
                        Future(modifiedList)
                }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
            })
            Future.sequence(futures.toList) recoverWith { case e: CompletionException => throw e.getCause }
        } else
            Future(nodeList)
    }

    private def addNodeToList(child: util.HashMap[String, AnyRef], request: Request, nodeList: ListBuffer[Node])(implicit ec: ExecutionContext): Future[ListBuffer[Node]] = {
        if (StringUtils.isNotEmpty(child.get(HierarchyConstants.VISIBILITY).asInstanceOf[String]))
            if (StringUtils.equalsIgnoreCase(HierarchyConstants.DEFAULT, child.get(HierarchyConstants.VISIBILITY).asInstanceOf[String])) {
                getContentNode(child.getOrDefault(HierarchyConstants.IDENTIFIER, "").asInstanceOf[String], HierarchyConstants.TAXONOMY_ID).map(node => {
                    node.getMetadata.put(HierarchyConstants.DEPTH, child.get(HierarchyConstants.DEPTH))
                    node.getMetadata.put(HierarchyConstants.PARENT, child.get(HierarchyConstants.PARENT))
                    node.getMetadata.put(HierarchyConstants.INDEX, child.get(HierarchyConstants.INDEX))
                    node.getMetadata.put(HierarchyConstants.STATUS, "Draft")
                    nodeList += node
                    Future(nodeList)
                }).flatMap(f => f)
            } else {
                val childData: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
                childData.putAll(child)
                childData.remove(HierarchyConstants.CHILDREN)
                childData.put(HierarchyConstants.STATUS, "Draft")
                nodeList += NodeUtil.deserialize(childData, request.getContext.get(HierarchyConstants.SCHEMA_NAME).asInstanceOf[String], DefinitionNode.getRelationsMap(request))
                Future(nodeList)
            }
        else Future(nodeList)
    }


    private def updateNodesModifiedInNodeList(nodeList: ListBuffer[Node], rootId: String, nodesModified: util.HashMap[String, AnyRef], request: Request)(implicit ec: ExecutionContext): Future[mutable.Map[String, String]] = {
        val idMap: mutable.Map[String, String] = mutable.Map()
        idMap += (rootId -> rootId)
        updateRootNode(rootId, nodeList, nodesModified)
        nodesModified.foreach(nodeModified => {
            val metadata = nodeModified._2.asInstanceOf[util.HashMap[String, AnyRef]].getOrDefault(HierarchyConstants.METADATA, new util.HashMap()).asInstanceOf[util.HashMap[String, AnyRef]]
            metadata.remove(HierarchyConstants.DIALCODES)
            metadata.put(HierarchyConstants.OBJECT_TYPE, HierarchyConstants.CONTENT_OBJECT_TYPE)
            metadata.put(HierarchyConstants.STATUS, "Draft")
            metadata.put(HierarchyConstants.LAST_UPDATED_ON, DateUtils.formatCurrentDate)
            if (nodeModified._2.asInstanceOf[util.HashMap[String, AnyRef]].containsKey(HierarchyConstants.IS_NEW)
                && nodeModified._2.asInstanceOf[util.HashMap[String, AnyRef]].get(HierarchyConstants.IS_NEW).asInstanceOf[Boolean]) {
                if (!nodeModified._2.asInstanceOf[util.HashMap[String, AnyRef]].get(HierarchyConstants.ROOT).asInstanceOf[Boolean])
                    metadata.put(HierarchyConstants.VISIBILITY, HierarchyConstants.PARENT)
                createNewNode(nodeModified._1, idMap, metadata, nodeList, request)
            } else updateTempNode(nodeModified._1, nodeList, idMap, metadata)
        })
        Future(idMap)
    }

    private def updateRootNode(rootId: String, nodeList: ListBuffer[Node], nodesModified: util.HashMap[String, AnyRef])(implicit ec: ExecutionContext): Unit = {
        if (nodesModified.containsKey(rootId)) {
            val metadata = nodesModified.getOrDefault(rootId, new util.HashMap()).asInstanceOf[util.HashMap[String, AnyRef]].getOrDefault(HierarchyConstants.METADATA, new util.HashMap()).asInstanceOf[util.HashMap[String, AnyRef]]
            updateNodeList(nodeList, rootId, metadata)
            nodesModified.remove(rootId)
        }
    }

    private def createNewNode(nodeId: String, idMap: mutable.Map[String, String], metadata: util.HashMap[String, AnyRef], nodeList: ListBuffer[Node], request: Request)(implicit ec: ExecutionContext): Unit = {
        val identifier: String = Identifier.getIdentifier(HierarchyConstants.TAXONOMY_ID, Identifier.getUniqueIdFromTimestamp)
        idMap += (nodeId -> identifier)
        metadata.put(HierarchyConstants.IDENTIFIER, identifier)
        metadata.put(HierarchyConstants.CODE, nodeId)
        metadata.put(HierarchyConstants.VERSION_KEY, System.currentTimeMillis + "")
        metadata.put(HierarchyConstants.CREATED_ON, DateUtils.formatCurrentDate)
        metadata.put(HierarchyConstants.LAST_STATUS_CHANGED_ON, DateUtils.formatCurrentDate)
        metadata.put(HierarchyConstants.CHANNEL, getTempNode(nodeList, request.getContext.get(HierarchyConstants.ROOT_ID).asInstanceOf[String]).getMetadata.get(HierarchyConstants.CHANNEL))
        val createRequest: Request = new Request(request)
        createRequest.setRequest(metadata)
        DefinitionNode.validate(createRequest).map(node => {
            node.setGraphId(HierarchyConstants.TAXONOMY_ID)
            node.setObjectType(HierarchyConstants.CONTENT_OBJECT_TYPE)
            node.setNodeType(HierarchyConstants.DATA_NODE)
            nodeList.add(node)
        }) recoverWith { case e: CompletionException => throw e.getCause }
    }

    private def updateTempNode(nodeId: String, nodeList: ListBuffer[Node], idMap: mutable.Map[String, String], metadata: util.HashMap[String, AnyRef])(implicit ec: ExecutionContext): Unit = {
        val tempNode: Node = getTempNode(nodeList, nodeId)
        if (null != tempNode && StringUtils.isNotBlank(tempNode.getIdentifier)) {
            metadata.put(HierarchyConstants.IDENTIFIER, tempNode.getIdentifier)
            idMap += (nodeId -> tempNode.getIdentifier)
            //TODO:validate
            updateNodeList(nodeList, tempNode.getIdentifier, metadata)
        }
        else throw new ResourceNotFoundException(HierarchyErrorCodes.ERR_CONTENT_NOT_FOUND, "Content not found with identifier: " + nodeId)
    }

    private def validateNodes(nodeList: ListBuffer[Node])(implicit ec: ExecutionContext): Future[List[Node]] = {
        val nonUnitNodes = nodeList.filter(node => StringUtils.equals(HierarchyConstants.DEFAULT, node.getMetadata.get(HierarchyConstants.VISIBILITY).asInstanceOf[String])).toList
        DefinitionNode.validateContentNodes(nonUnitNodes, HierarchyConstants.TAXONOMY_ID, HierarchyConstants.CONTENT_SCHEMA_NAME, HierarchyConstants.SCHEMA_VERSION)
    }

    @throws[Exception]
    private def getChildrenHierarchy(nodeList: ListBuffer[Node], rootId: String, hierarchyData: util.HashMap[String, AnyRef], idMap: mutable.Map[String, String])(implicit ec: ExecutionContext): Future[util.List[util.Map[String, AnyRef]]] = {
        val childrenIdentifiersMap: Map[String, List[String]] = if (MapUtils.isNotEmpty(hierarchyData)) {
            val tempChildMap = hierarchyData.filter(entry => entry._2.asInstanceOf[util.HashMap[String, AnyRef]].containsKey(HierarchyConstants.CHILDREN)).map(entry => idMap.getOrDefault(entry._1, entry._1) -> entry._2.asInstanceOf[util.HashMap[String, AnyRef]]
                .get(HierarchyConstants.CHILDREN).asInstanceOf[util.ArrayList[String]]
                .map(id => idMap.getOrDefault(id, id)).toList).toMap
            val tempResourceMap = hierarchyData.filter(entry => entry._2.asInstanceOf[util.HashMap[String, AnyRef]].containsKey(HierarchyConstants.CHILDREN)).flatMap(entry => entry._2.asInstanceOf[util.HashMap[String, AnyRef]]
                .getOrDefault(HierarchyConstants.CHILDREN, new util.ArrayList[String]())
                .asInstanceOf[util.ArrayList[String]])
                .filter(child => !tempChildMap.contains(idMap.getOrDefault(child, child))).map(child => child -> List()).toMap
            tempChildMap.++(tempResourceMap)
        } else Map()
        getPreparedHierarchyData(nodeList, hierarchyData, rootId, childrenIdentifiersMap).map(nodeMaps => {
            val filteredNodeMaps = nodeMaps.filter(nodeMap => null != nodeMap.get(HierarchyConstants.DEPTH)).toList
            val identifierNodeMap: Map[String, AnyRef] = filteredNodeMaps.map(nodeMap => nodeMap.get(HierarchyConstants.IDENTIFIER).asInstanceOf[String] -> nodeMap).toMap
            val hierarchyMap = constructHierarchy(childrenIdentifiersMap, identifierNodeMap)
            //        util.hierarchyCleanUp(collectionHierarchy)
            if (MapUtils.isNotEmpty(hierarchyMap))
                hierarchyMap.getOrDefault(rootId, new util.HashMap[String,AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
                    .get(HierarchyConstants.CHILDREN).asInstanceOf[util.List[util.Map[String, AnyRef]]]
                    .filter(child => MapUtils.isNotEmpty(child))
            else
                new util.ArrayList[util.Map[String, AnyRef]]()

        })
    }

    @throws[Exception]
    private def getPreparedHierarchyData(nodeList: ListBuffer[Node], hierarchyData: util.HashMap[String, AnyRef], rootId: String, childrenIdentifiersMap: Map[String, List[String]])(implicit ec: ExecutionContext): Future[util.List[util.Map[String, AnyRef]]] = {
        if (MapUtils.isNotEmpty(hierarchyData) && MapUtils.isNotEmpty(childrenIdentifiersMap)) {
            val childNodeIds: mutable.HashSet[String] = mutable.HashSet[String]()
            val updatedNodeList: ListBuffer[Node] = ListBuffer()
            updatedNodeList += getTempNode(nodeList, rootId)
            updateHierarchyRelatedData(childrenIdentifiersMap.getOrElse(rootId, List()), 1,
                rootId, nodeList, childrenIdentifiersMap, childNodeIds, updatedNodeList).map(response => {
                updateNodeList(nodeList, rootId, new util.HashMap[String, AnyRef]() {
                    put(HierarchyConstants.DEPTH, 0.asInstanceOf[AnyRef])
                    put(HierarchyConstants.CHILD_NODES, new util.ArrayList[String](childNodeIds))
                })
                HierarchyManager.convertNodeToMap(updatedNodeList.toList)
            })
        } else {
            updateNodeList(nodeList, rootId, new util.HashMap[String, AnyRef]() {})
            Future(HierarchyManager.convertNodeToMap(nodeList.toList))
        }
    }

    @throws[Exception]
    private def updateHierarchyRelatedData(childrenIds: List[String], depth: Int, parent: String, nodeList: ListBuffer[Node], hierarchyStructure: Map[String, List[String]], childNodeIds: mutable.HashSet[String], updatedNodeList: ListBuffer[Node])(implicit ec: ExecutionContext): Future[List[Response]] = {
        var index: Int = 1
        val futures = childrenIds.map(id => {
            val tempNode = getTempNode(nodeList, id)
            if (null != tempNode && StringUtils.equalsIgnoreCase(HierarchyConstants.PARENT, tempNode.getMetadata.get(HierarchyConstants.VISIBILITY).asInstanceOf[String])) {
                populateHierarchyRelatedData(tempNode, depth, index, parent)
                updatedNodeList.add(tempNode)
                childNodeIds.add(id)
                index += 1
                if (CollectionUtils.isNotEmpty(hierarchyStructure.getOrDefault(id, List())))
                    updateHierarchyRelatedData(hierarchyStructure.getOrDefault(id, List()), tempNode.getMetadata.get(HierarchyConstants.DEPTH).asInstanceOf[Int] + 1, id, nodeList, hierarchyStructure, childNodeIds, updatedNodeList)
                Future(ResponseHandler.OK())
            } else {
                getContentNode(id, HierarchyConstants.TAXONOMY_ID).map(node => {
                    populateHierarchyRelatedData(node, depth, index, parent)
                    updatedNodeList.add(node)
                    childNodeIds.add(id)
                    index += 1
                    if (CollectionUtils.isNotEmpty(hierarchyStructure.getOrDefault(id, List())))
                        updateHierarchyRelatedData(hierarchyStructure.getOrDefault(id, List()), tempNode.getMetadata.get(HierarchyConstants.DEPTH).asInstanceOf[Int] + 1, id, nodeList, hierarchyStructure, childNodeIds, updatedNodeList)
                    Future(ResponseHandler.OK())
                }).flatMap(f => f)
            }
        })
        Future.sequence(futures) recoverWith { case e: CompletionException => throw e.getCause }
    }

    private def populateHierarchyRelatedData(tempNode: Node, depth: Int, index: Int, parent: String) = {
        tempNode.getMetadata.put(HierarchyConstants.DEPTH, depth.asInstanceOf[AnyRef])
        tempNode.getMetadata.put(HierarchyConstants.PARENT, parent)
        tempNode.getMetadata.put(HierarchyConstants.INDEX, index.asInstanceOf[AnyRef])
    }

    private def constructHierarchy(childrenIdentifiersMap: Map[String, List[String]], identifierNodeMap: Map[String, AnyRef]): mutable.Map[String, AnyRef] = {
        val clonedIdentifiersMap = mutable.Map[String, List[String]]() ++= childrenIdentifiersMap
        var populatedChildMap = mutable.Map[String, AnyRef]()
        do {
            childrenIdentifiersMap.map(entry => {
                if (CollectionUtils.isEmpty(childrenIdentifiersMap.getOrDefault(entry._1, List()))) {
                    populatedChildMap += (entry._1 -> identifierNodeMap.getOrDefault(entry._1, new HashMap[String, AnyRef]()).asInstanceOf[HashMap[String, AnyRef]])
                    clonedIdentifiersMap.remove(entry._1)
                } else {
                    if (isFullyPopulated(childrenIdentifiersMap.getOrDefault(entry._1, List()), populatedChildMap)) {
                        val tempMap = identifierNodeMap.getOrDefault(entry._1, new util.HashMap()).asInstanceOf[HashMap[String, AnyRef]]
                        val tempChildren: util.List[HashMap[String, AnyRef]] = childrenIdentifiersMap.getOrDefault(entry._1, List()).map(id =>  populatedChildMap.getOrDefault(id, new util.HashMap()).asInstanceOf[HashMap[String, AnyRef]]).toList
                        tempMap.put(HierarchyConstants.CHILDREN, tempChildren)
                        populatedChildMap.put(entry._1, tempMap)
                        clonedIdentifiersMap.remove(entry._1)
                    }
                }
            })
        } while (MapUtils.isNotEmpty(clonedIdentifiersMap))
        populatedChildMap
    }

    /**
      * This method is to check if all the children of the parent entity are present in the populated map
      *
      * @param children
      * @param populatedChildMap
      * @return
      */
    def isFullyPopulated(children: List[String], populatedChildMap: mutable.Map[_, _]): Boolean = {
        children.forall(child => populatedChildMap.containsKey(child))
    }

    def updateHierarchyData(rootId: String, children: util.List[util.Map[String, AnyRef]], nodeList: ListBuffer[Node], request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        val node = getTempNode(nodeList, rootId)
        val updatedHierarchy = new java.util.HashMap[String, AnyRef]()
        updatedHierarchy.put(HierarchyConstants.IDENTIFIER, rootId)
        updatedHierarchy.put(HierarchyConstants.CHILDREN, children)
        val req = new Request(request)
        req.getContext.put(HierarchyConstants.IDENTIFIER, rootId)
        req.getRequest.putAll(node.getMetadata)
        req.put(HierarchyConstants.HIERARCHY, ScalaJsonUtils.serialize(updatedHierarchy))
        req.put(HierarchyConstants.IDENTIFIER, rootId)
        req.put(HierarchyConstants.CHILDREN, new util.ArrayList())
        req.put(HierarchyConstants.CONCEPTS, new util.ArrayList())
        DataNode.update(req)
    }

    /**
      * Get the Node with ID provided from List else return Null.
      *
      * @param nodeList
      * @param id
      * @return
      */
    private def getTempNode(nodeList: ListBuffer[Node], id: String) = {
        nodeList.toList.find(node => StringUtils.startsWith(node.getIdentifier, id)).orNull
    }

    private def updateNodeList(nodeList: ListBuffer[Node], id: String, metadata: util.HashMap[String, AnyRef]): Unit = {
        if (MapUtils.isNotEmpty(metadata))
            nodeList.toList.filter(node => node.getIdentifier.startsWith(id)).foreach(node => node.getMetadata.putAll(metadata))
    }

    private def getContentNode(identifier: String, graphId: String)(implicit ec: ExecutionContext): Future[Node] = {
        val request: Request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put(HierarchyConstants.GRAPH_ID, graphId)
                put(HierarchyConstants.VERSION, HierarchyConstants.SCHEMA_VERSION)
                put(HierarchyConstants.OBJECT_TYPE, HierarchyConstants.CONTENT_OBJECT_TYPE)
                put(HierarchyConstants.SCHEMA_NAME, HierarchyConstants.CONTENT_SCHEMA_NAME)
            }
        })
        request.setObjectType(HierarchyConstants.CONTENT_OBJECT_TYPE)
        request.put(HierarchyConstants.IDENTIFIER, identifier)
        request.put(HierarchyConstants.MODE, HierarchyConstants.READ_MODE)
        request.put(HierarchyConstants.FIELDS, new util.ArrayList[String]())
        DataNode.read(request)
    }

    private def shouldImageBeDeleted(rootNode: Node): Boolean = {
        if (Platform.config.getBoolean("collection.image.migration.enabled") &&
            !CollectionUtils.containsAny(HierarchyConstants.HIERARCHY_LIVE_STATUS, rootNode.getMetadata.get(HierarchyConstants.STATUS).asInstanceOf[String]) &&
            rootNode.getMetadata.containsKey("pkgVersion"))
            true
        else
            false
    }

    def deleteHierarchy(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        val req = new Request(request)
        val rootId: String = request.getContext.get(HierarchyConstants.ROOT_ID).asInstanceOf[String]
        req.put(HierarchyConstants.IDENTIFIER, if (rootId.contains(HierarchyConstants.IMAGE_SUFFIX)) rootId else rootId + HierarchyConstants.IMAGE_SUFFIX)
        ExternalPropsManager.deleteProps(req, List())
    }

}




















