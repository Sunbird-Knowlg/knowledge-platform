package org.sunbird.managers

import java.util
import java.util.concurrent.CompletionException
import java.util.HashMap

import org.apache.commons.collections4.{CollectionUtils, ListUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.{DateUtils, JsonUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ErrorCodes, ResourceNotFoundException, ServerException}
import org.sunbird.graph.OntologyEngineContext
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
    def updateHierarchy(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
        validateRequest(request)
        val nodesModified: util.HashMap[String, AnyRef] = request.getRequest.get(HierarchyConstants.NODES_MODIFIED).asInstanceOf[util.HashMap[String, AnyRef]]
        val hierarchy: util.HashMap[String, AnyRef] = request.getRequest.get(HierarchyConstants.HIERARCHY).asInstanceOf[util.HashMap[String, AnyRef]]
        val rootId: String = getRootId(nodesModified, hierarchy)
        request.getContext.put(HierarchyConstants.ROOT_ID, rootId)
        var nodeList: ListBuffer[Node] = ListBuffer[Node]()
        getValidatedRootNode(rootId, request).map(node => {
            nodeList += node
            getExistingHierarchyChildren(request, node).map(existingChildren => {
                addChildNodesInNodeList(existingChildren, request, nodeList).map(data => {
                    val idMap: mutable.Map[String, String] = mutable.Map()
                    idMap += (rootId -> rootId)
                    updateNodesModifiedInNodeList(nodeList, nodesModified, request, idMap).map(resp => {
                        getChildrenHierarchy(nodeList, rootId, hierarchy, idMap).map(children => {
                            updateHierarchyData(rootId, children, nodeList, request).map(node => {
                                val response = ResponseHandler.OK()
                                response.put(HierarchyConstants.CONTENT_ID, rootId)
                                idMap.remove(rootId)
                                response.put(HierarchyConstants.IDENTIFIERS, mapAsJavaMap(idMap))
                                if (request.getContext.getOrDefault("shouldImageDelete", false.asInstanceOf[AnyRef]).asInstanceOf[Boolean])
                                    deleteHierarchy(request)
                                Future(response)
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
    private def getValidatedRootNode(identifier: String, request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        val req = new Request(request)
        req.put(HierarchyConstants.IDENTIFIER, identifier)
        req.put(HierarchyConstants.MODE, HierarchyConstants.EDIT_MODE)
        DataNode.read(req).map(rootNode => {
            val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(rootNode, new util.ArrayList[String](), request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
            if (!StringUtils.equals(metadata.get(HierarchyConstants.MIME_TYPE).asInstanceOf[String], HierarchyConstants.COLLECTION_MIME_TYPE)) {
                throw new ClientException(HierarchyErrorCodes.ERR_INVALID_ROOT_ID, "Invalid MimeType for Root Node Identifier  : " + identifier)
                TelemetryManager.error("UpdateHierarchyManager.getValidatedRootNode :: Invalid MimeType for Root node id: " + identifier)
            }
            //Todo: Remove if not required
            if (null == metadata.get(HierarchyConstants.VERSION) || metadata.get(HierarchyConstants.VERSION).asInstanceOf[Number].intValue < 2) {
                TelemetryManager.error("UpdateHierarchyManager.getValidatedRootNode :: Invalid Content Version for Root node id: " + identifier)
                throw new ClientException(HierarchyErrorCodes.ERR_INVALID_ROOT_ID, "The collection version is not up to date " + identifier)
            }
            val originData = metadata.getOrDefault("originData", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
            if (StringUtils.equalsIgnoreCase(originData.getOrElse("copyType", "").asInstanceOf[String], HierarchyConstants.COPY_TYPE_SHALLOW))
                throw new ClientException(HierarchyErrorCodes.ERR_HIERARCHY_UPDATE_DENIED, "Hierarchy update is not allowed for partially (shallow) copied content : " + identifier)
            rootNode.getMetadata.put(HierarchyConstants.VERSION, HierarchyConstants.LATEST_CONTENT_VERSION)
            rootNode
        })
    }

    private def getExistingHierarchyChildren(request: Request, rootNode: Node)(implicit ec: ExecutionContext): Future[util.ArrayList[util.HashMap[String, AnyRef]]] = {
        fetchHierarchy(request, rootNode).map(hierarchyString => {
            if (!hierarchyString.asInstanceOf[String].isEmpty) {
                val hierarchyMap = JsonUtils.deserialize(hierarchyString.asInstanceOf[String], classOf[util.HashMap[String, AnyRef]])
                hierarchyMap.getOrElse(HierarchyConstants.CHILDREN, new util.ArrayList[util.HashMap[String, AnyRef]]()).asInstanceOf[util.ArrayList[util.HashMap[String, AnyRef]]]
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

    private def fetchHierarchy(request: Request, rootNode:Node)(implicit ec: ExecutionContext): Future[Any] = {
        val req = new Request(request)
        req.put(HierarchyConstants.IDENTIFIER, rootNode.getIdentifier)
        ExternalPropsManager.fetchProps(req, List(HierarchyConstants.HIERARCHY)).map(response => {
            if (ResponseHandler.checkError(response) && ResponseHandler.isResponseNotFoundError(response)) {
                if(CollectionUtils.containsAny(HierarchyConstants.HIERARCHY_LIVE_STATUS, rootNode.getMetadata.get("status").asInstanceOf[String]))
                    throw new ServerException(HierarchyErrorCodes.ERR_HIERARCHY_NOT_FOUND, "No hierarchy is present in cassandra for identifier:" + rootNode.getIdentifier)
                else {
                    if(rootNode.getMetadata.containsKey("pkgVersion"))
                        req.put(HierarchyConstants.IDENTIFIER, rootNode.getIdentifier.replace(HierarchyConstants.IMAGE_SUFFIX, ""))
                    else {
                        //TODO: Remove should Image be deleted after migration
                        request.getContext.put("shouldImageDelete", shouldImageBeDeleted(rootNode).asInstanceOf[AnyRef])
                        req.put(HierarchyConstants.IDENTIFIER, if (!rootNode.getIdentifier.endsWith(HierarchyConstants.IMAGE_SUFFIX)) rootNode.getIdentifier + HierarchyConstants.IMAGE_SUFFIX else rootNode.getIdentifier)
                    }
                    ExternalPropsManager.fetchProps(req, List(HierarchyConstants.HIERARCHY)).map(resp => {
                        resp.getResult.toMap.getOrElse(HierarchyConstants.HIERARCHY, "").asInstanceOf[String]
                    }) recover { case e: ResourceNotFoundException => TelemetryManager.log("No hierarchy is present in cassandra for identifier:" + rootNode.getIdentifier) }
                }
            } else Future(response.getResult.toMap.getOrElse(HierarchyConstants.HIERARCHY, "").asInstanceOf[String])
        }).flatMap(f => f)
    }

    private def addChildNodesInNodeList(childrenMaps: util.ArrayList[util.HashMap[String, AnyRef]], request: Request, nodeList: ListBuffer[Node])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[AnyRef] = {
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

    private def addNodeToList(child: util.HashMap[String, AnyRef], request: Request, nodeList: ListBuffer[Node])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[ListBuffer[Node]] = {
        if (StringUtils.isNotEmpty(child.get(HierarchyConstants.VISIBILITY).asInstanceOf[String]))
            if (StringUtils.equalsIgnoreCase(HierarchyConstants.DEFAULT, child.get(HierarchyConstants.VISIBILITY).asInstanceOf[String])) {
                getContentNode(child.getOrDefault(HierarchyConstants.IDENTIFIER, "").asInstanceOf[String], HierarchyConstants.TAXONOMY_ID).map(node => {
                    node.getMetadata.put(HierarchyConstants.DEPTH, child.get(HierarchyConstants.DEPTH))
                    node.getMetadata.put(HierarchyConstants.PARENT, child.get(HierarchyConstants.PARENT))
                    node.getMetadata.put(HierarchyConstants.INDEX, child.get(HierarchyConstants.INDEX))
                    nodeList += node
                    Future(nodeList)
                }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
            } else {
                val childData: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
                childData.putAll(child)
                childData.remove(HierarchyConstants.CHILDREN)
                childData.put(HierarchyConstants.STATUS, "Draft")
                childData.put(HierarchyConstants.CHANNEL, getTempNode(nodeList, request.getContext.get(HierarchyConstants.ROOT_ID).asInstanceOf[String] ).getMetadata.get(HierarchyConstants.CHANNEL))
                nodeList += NodeUtil.deserialize(childData, request.getContext.get(HierarchyConstants.SCHEMA_NAME).asInstanceOf[String], DefinitionNode.getRelationsMap(request))
                Future(nodeList)
            }
        else Future(nodeList)
    }


    private def updateNodesModifiedInNodeList(nodeList: ListBuffer[Node], nodesModified: util.HashMap[String, AnyRef], request: Request, idMap: mutable.Map[String, String])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[AnyRef] = {
        updateRootNode(request.getContext.get(HierarchyConstants.ROOT_ID).asInstanceOf[String], nodeList, nodesModified)
            val futures = nodesModified.filter(nodeModified => !StringUtils.startsWith(request.getContext.get(HierarchyConstants.ROOT_ID).asInstanceOf[String], nodeModified._1))
                .map(nodeModified => { val metadata = nodeModified._2.asInstanceOf[util.HashMap[String, AnyRef]].getOrDefault(HierarchyConstants.METADATA, new util.HashMap()).asInstanceOf[util.HashMap[String, AnyRef]]
                metadata.remove(HierarchyConstants.DIALCODES)
                metadata.put(HierarchyConstants.OBJECT_TYPE, HierarchyConstants.CONTENT_OBJECT_TYPE)
                metadata.put(HierarchyConstants.STATUS, "Draft")
                metadata.put(HierarchyConstants.LAST_UPDATED_ON, DateUtils.formatCurrentDate)
                if (nodeModified._2.asInstanceOf[util.HashMap[String, AnyRef]].containsKey(HierarchyConstants.IS_NEW)
                    && nodeModified._2.asInstanceOf[util.HashMap[String, AnyRef]].get(HierarchyConstants.IS_NEW).asInstanceOf[Boolean]) {
                    if (!nodeModified._2.asInstanceOf[util.HashMap[String, AnyRef]].get(HierarchyConstants.ROOT).asInstanceOf[Boolean])
                        metadata.put(HierarchyConstants.VISIBILITY, HierarchyConstants.PARENT)
                        if(nodeModified._2.asInstanceOf[util.HashMap[String, AnyRef]].contains(HierarchyConstants.SET_DEFAULT_VALUE))
                            createNewNode(nodeModified._1, idMap, metadata, nodeList, request, nodeModified._2.asInstanceOf[util.HashMap[String, AnyRef]].get(HierarchyConstants.SET_DEFAULT_VALUE).asInstanceOf[Boolean])
                        else
                            createNewNode(nodeModified._1, idMap, metadata, nodeList, request)
                } else {
                    updateTempNode(nodeModified._1, nodeList, idMap, metadata)
                    Future(ResponseHandler.OK())
                }
            })
        if(CollectionUtils.isNotEmpty(futures)) Future.sequence(futures.toList) else Future(ResponseHandler.OK())
    }

    private def updateRootNode(rootId: String, nodeList: ListBuffer[Node], nodesModified: util.HashMap[String, AnyRef])(implicit ec: ExecutionContext): Unit = {
        if (nodesModified.containsKey(rootId)) {
            val metadata = nodesModified.getOrDefault(rootId, new util.HashMap()).asInstanceOf[util.HashMap[String, AnyRef]].getOrDefault(HierarchyConstants.METADATA, new util.HashMap()).asInstanceOf[util.HashMap[String, AnyRef]]
            updateNodeList(nodeList, rootId, metadata)
            nodesModified.remove(rootId)
        }
    }

    private def createNewNode(nodeId: String, idMap: mutable.Map[String, String], metadata: util.HashMap[String, AnyRef], nodeList: ListBuffer[Node], request: Request, setDefaultValue: Boolean = true)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[AnyRef] = {
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
        DefinitionNode.validate(createRequest, setDefaultValue).map(node => {
            node.setGraphId(HierarchyConstants.TAXONOMY_ID)
            node.setObjectType(HierarchyConstants.CONTENT_OBJECT_TYPE)
            node.setNodeType(HierarchyConstants.DATA_NODE)
            nodeList.add(node)
            nodeList
        })
    }

    private def updateTempNode(nodeId: String, nodeList: ListBuffer[Node], idMap: mutable.Map[String, String], metadata: util.HashMap[String, AnyRef])(implicit ec: ExecutionContext): Unit = {
        val tempNode: Node = getTempNode(nodeList, nodeId)
        if (null != tempNode && StringUtils.isNotBlank(tempNode.getIdentifier)) {
            metadata.put(HierarchyConstants.IDENTIFIER, tempNode.getIdentifier)
            idMap += (nodeId -> tempNode.getIdentifier)
            updateNodeList(nodeList, tempNode.getIdentifier, metadata)
        } else throw new ResourceNotFoundException(HierarchyErrorCodes.ERR_CONTENT_NOT_FOUND, "Content not found with identifier: " + nodeId)
    }

    private def validateNodes(nodeList: ListBuffer[Node], rootId: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[List[Node]] = {
        val nodesToValidate = nodeList.filter(node => StringUtils.equals(HierarchyConstants.PARENT, node.getMetadata.get(HierarchyConstants.VISIBILITY).asInstanceOf[String]) || StringUtils.equalsAnyIgnoreCase(rootId, node.getIdentifier)).toList
        DefinitionNode.updateJsonPropsInNodes(nodeList.toList, HierarchyConstants.TAXONOMY_ID, HierarchyConstants.COLLECTION_SCHEMA_NAME, HierarchyConstants.SCHEMA_VERSION)
        DefinitionNode.validateContentNodes(nodesToValidate, HierarchyConstants.TAXONOMY_ID, HierarchyConstants.COLLECTION_SCHEMA_NAME, HierarchyConstants.SCHEMA_VERSION)
    }

    @throws[Exception]
    private def getChildrenHierarchy(nodeList: ListBuffer[Node], rootId: String, hierarchyData: util.HashMap[String, AnyRef], idMap: mutable.Map[String, String])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[util.List[util.Map[String, AnyRef]]] = {
        val childrenIdentifiersMap: Map[String, List[String]] = if (MapUtils.isNotEmpty(hierarchyData)) {
            val tempChildMap = hierarchyData.filter(entry => entry._2.asInstanceOf[util.HashMap[String, AnyRef]].containsKey(HierarchyConstants.CHILDREN)).map(entry => idMap.getOrDefault(entry._1, entry._1) -> entry._2.asInstanceOf[util.HashMap[String, AnyRef]]
                .get(HierarchyConstants.CHILDREN).asInstanceOf[util.ArrayList[String]]
                .map(id => idMap.getOrDefault(id, id)).toList).toMap
            val tempResourceMap = hierarchyData.filter(entry => entry._2.asInstanceOf[util.HashMap[String, AnyRef]].containsKey(HierarchyConstants.CHILDREN)).flatMap(entry => entry._2.asInstanceOf[util.HashMap[String, AnyRef]]
                .getOrDefault(HierarchyConstants.CHILDREN, new util.ArrayList[String]())
                .asInstanceOf[util.ArrayList[String]])
                .filter(child => !tempChildMap.contains(idMap.getOrDefault(child, child))).map(child => idMap.getOrDefault(child, child) -> List()).toMap
            tempChildMap.++(tempResourceMap)
        } else Map()
        getPreparedHierarchyData(nodeList, hierarchyData, rootId, childrenIdentifiersMap).map(nodeMaps => {
            val filteredNodeMaps = nodeMaps.filter(nodeMap => null != nodeMap.get(HierarchyConstants.DEPTH)).toList
            val identifierNodeMap: Map[String, AnyRef] = filteredNodeMaps.map(nodeMap => nodeMap.get(HierarchyConstants.IDENTIFIER).asInstanceOf[String] -> nodeMap).toMap
            val hierarchyMap = constructHierarchy(childrenIdentifiersMap, identifierNodeMap)
            //        util.hierarchyCleanUp(collectionHierarchy)
            if (MapUtils.isNotEmpty(hierarchyMap))
                hierarchyMap.getOrDefault(rootId, new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
                    .getOrDefault(HierarchyConstants.CHILDREN, new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]]
                    .filter(child => MapUtils.isNotEmpty(child))
            else
                new util.ArrayList[util.Map[String, AnyRef]]()

        })
    }

    @throws[Exception]
    private def getPreparedHierarchyData(nodeList: ListBuffer[Node], hierarchyData: util.HashMap[String, AnyRef], rootId: String, childrenIdentifiersMap: Map[String, List[String]])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[util.List[util.Map[String, AnyRef]]] = {
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
                validateNodes(updatedNodeList, rootId).map(result => HierarchyManager.convertNodeToMap(updatedNodeList.toList))
            }).flatMap(f => f)
        } else {
            updateNodeList(nodeList, rootId, new util.HashMap[String, AnyRef]() {})
            validateNodes(nodeList, rootId).map(result => HierarchyManager.convertNodeToMap(nodeList.toList))
        }
    }

    @throws[Exception]
    private def updateHierarchyRelatedData(childrenIds: List[String], depth: Int, parent: String, nodeList: ListBuffer[Node], hierarchyStructure: Map[String, List[String]], childNodeIds: mutable.HashSet[String], updatedNodeList: ListBuffer[Node])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[AnyRef] = {
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
                else
                    Future(ResponseHandler.OK())
            } else
                getContentNode(id, HierarchyConstants.TAXONOMY_ID).map(node => {
                    populateHierarchyRelatedData(node, depth, index, parent)
                    updatedNodeList.add(node)
                    childNodeIds.add(id)
                    index += 1
                    if (CollectionUtils.isNotEmpty(hierarchyStructure.getOrDefault(id, List())))
                        updateHierarchyRelatedData(hierarchyStructure.getOrDefault(id, List()), node.getMetadata.get(HierarchyConstants.DEPTH).asInstanceOf[Int] + 1, id, nodeList, hierarchyStructure, childNodeIds, updatedNodeList)
                    else
                        ResponseHandler.OK()
                }) recoverWith { case e: CompletionException => throw e.getCause }
        })
        if (CollectionUtils.isNotEmpty(futures)) Future.sequence(futures) else Future(ResponseHandler.OK())
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
                        val tempChildren: util.List[HashMap[String, AnyRef]] = sortByIndex(childrenIdentifiersMap.getOrDefault(entry._1, List()).map(id => populatedChildMap.getOrDefault(id, new util.HashMap()).asInstanceOf[HashMap[String, AnyRef]]).toList)
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

    def updateHierarchyData(rootId: String, children: util.List[util.Map[String, AnyRef]],nodeList: ListBuffer[Node], request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        val node = getTempNode(nodeList, rootId)
        val updatedHierarchy = new java.util.HashMap[String, AnyRef]()
        updatedHierarchy.put(HierarchyConstants.IDENTIFIER, rootId)
        updatedHierarchy.put(HierarchyConstants.CHILDREN, children)
        val req = new Request(request)
        req.getContext.put(HierarchyConstants.IDENTIFIER, rootId)
        val metadata = cleanUpRootData(node)
        req.getRequest.putAll(metadata)
        req.put(HierarchyConstants.HIERARCHY, ScalaJsonUtils.serialize(updatedHierarchy))
        req.put(HierarchyConstants.IDENTIFIER, rootId)
        req.put(HierarchyConstants.CHILDREN, new util.ArrayList())
        req.put(HierarchyConstants.CONCEPTS, new util.ArrayList())
        DataNode.update(req)
    }

    private def cleanUpRootData(node: Node): util.Map[String, AnyRef] = {
        DefinitionNode.fetchJsonProps(HierarchyConstants.TAXONOMY_ID, HierarchyConstants.SCHEMA_VERSION, HierarchyConstants.CONTENT_SCHEMA_NAME)
            .foreach(key => node.getMetadata.remove(key))
        node.getMetadata.remove(HierarchyConstants.STATUS)
        node.getMetadata
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

    def getContentNode(identifier: String, graphId: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
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
        val flag = if (Platform.config.hasPath("collection.image.migration.enabled")) Platform.config.getBoolean("collection.image.migration.enabled") else false
//        flag && !CollectionUtils.containsAny(HierarchyConstants.HIERARCHY_LIVE_STATUS, rootNode.getMetadata.get(HierarchyConstants.STATUS).asInstanceOf[String]) &&
//            !rootNode.getMetadata.containsKey("pkgVersion")
        flag
    }

    def sortByIndex(childrenMaps: util.List[HashMap[String, AnyRef]]): util.List[util.HashMap[String, AnyRef]] = {
        seqAsJavaList(childrenMaps.sortBy(_.get("index").asInstanceOf[Int]))
    }


    def deleteHierarchy(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        val req = new Request(request)
        val rootId = request.getContext.get(HierarchyConstants.ROOT_ID).asInstanceOf[String]
        req.put(HierarchyConstants.IDENTIFIERS, if (rootId.contains(HierarchyConstants.IMAGE_SUFFIX)) List(rootId) else List(rootId + HierarchyConstants.IMAGE_SUFFIX))
        ExternalPropsManager.deleteProps(req)
    }

}