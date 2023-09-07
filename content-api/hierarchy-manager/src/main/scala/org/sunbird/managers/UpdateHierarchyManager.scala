package org.sunbird.managers

import java.util.concurrent.CompletionException
import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ErrorCodes, ResourceNotFoundException, ResponseCode, ServerException}
import org.sunbird.common.{DateUtils, JsonUtils, Platform}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.DefinitionNode
import org.sunbird.graph.utils.{NodeUtil, ScalaJsonUtils}
import org.sunbird.schema.dto.ValidationResult
import org.sunbird.schema.{ISchemaValidator, SchemaValidatorFactory}
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.utils.{HierarchyBackwardCompatibilityUtil, HierarchyConstants, HierarchyErrorCodes}

import scala.collection.JavaConverters._
import scala.collection.convert.ImplicitConversions._
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

object UpdateHierarchyManager {

    @throws[Exception]
    def updateHierarchy(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
        validateRequest(request)
        val nodesModified: java.util.HashMap[String, AnyRef] = request.getRequest.get(HierarchyConstants.NODES_MODIFIED).asInstanceOf[java.util.HashMap[String, AnyRef]]
        TelemetryManager.info("UpdateHierarchyManager:: updateHierarchy:: nodesModified: " + nodesModified)
        val hierarchy: java.util.HashMap[String, AnyRef] = request.getRequest.get(HierarchyConstants.HIERARCHY).asInstanceOf[java.util.HashMap[String, AnyRef]]
        TelemetryManager.info("UpdateHierarchyManager:: updateHierarchy:: hierarchy: " + hierarchy)
        val rootId: String = getRootId(nodesModified, hierarchy)
        TelemetryManager.info("UpdateHierarchyManager:: updateHierarchy:: rootId: " + rootId)
        request.getContext.put(HierarchyConstants.ROOT_ID, rootId)
        getValidatedRootNode(rootId, request).map(node => {
            getExistingHierarchy(request, node).map(existingHierarchy => {
                val existingChildren = existingHierarchy.getOrElse(HierarchyConstants.CHILDREN, new java.util.ArrayList[java.util.HashMap[String, AnyRef]]()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
                val nodes = List(node)
                addChildNodesInNodeList(existingChildren, request, nodes).map(list => (existingHierarchy, list))
            }).flatMap(f => f)
              .map(result => {
                  val nodes = result._2
                  TelemetryManager.info("NodeList final size: " + nodes.size)
                  val duplicates = nodes.groupBy(node => node.getIdentifier).map(t => t._1 -> t._2.size).toMap
                  //TelemetryManager.info("NodeList for root with duplicates :" + rootId +" :: " + ScalaJsonUtils.serialize(duplicates))
                  val nodeMap: Map[String, AnyRef] = nodes.map(node => node.getIdentifier -> node.getMetadata.get("visibility")).toMap
                  //TelemetryManager.info("NodeList for root id :" + rootId +" :: " + ScalaJsonUtils.serialize(nodeMap))
                  val idMap: mutable.Map[String, String] = mutable.Map()
                  idMap += (rootId -> rootId)
                  updateNodesModifiedInNodeList(nodes, nodesModified, request, idMap).map(modifiedNodeList => {
                      getChildrenHierarchy(modifiedNodeList, rootId, hierarchy, idMap, result._1, request).map(children => {
                          TelemetryManager.log("Children for root id :" + rootId +" :: " + JsonUtils.serialize(children))
                          updateHierarchyData(rootId, children, modifiedNodeList, request).map(node => {
                              val response = ResponseHandler.OK()
                              response.put(HierarchyConstants.CONTENT_ID, rootId)
                              idMap.remove(rootId)
                              response.put(HierarchyConstants.IDENTIFIERS, mapAsJavaMap(idMap))
                              if (request.getContext.getOrDefault("shouldImageDelete", false.asInstanceOf[AnyRef]).asInstanceOf[Boolean])
                                  deleteHierarchy(request)
                              Future(response)
                          }).flatMap(f => f)
                      }).flatMap(f => f).recoverWith {
                          case clientException: ClientException => if(clientException.getMessage.equalsIgnoreCase("Validation Errors")) {
                              Future(ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, ResponseCode.CLIENT_ERROR.name(), clientException.getMessages.mkString(",")))
                          } else throw clientException
                          case e: Exception =>  throw e
                      }
                  }).flatMap(f => f)
              })
        }).flatMap(f => f).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
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
    private def getRootId(nodesModified: java.util.HashMap[String, AnyRef], hierarchy: java.util.HashMap[String, AnyRef])(implicit ec: ExecutionContext): String = {
        val rootId: String = nodesModified.keySet()
                .find(key => nodesModified.get(key).asInstanceOf[java.util.HashMap[String, AnyRef]].get(HierarchyConstants.ROOT).asInstanceOf[Boolean])
                .getOrElse(hierarchy.keySet().find(key => hierarchy.get(key).asInstanceOf[java.util.HashMap[String, AnyRef]].get(HierarchyConstants.ROOT).asInstanceOf[Boolean]).orNull)
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
            val metadata: java.util.Map[String, AnyRef] = NodeUtil.serialize(rootNode, new java.util.ArrayList[String](), request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
            if (!StringUtils.equals(metadata.get(HierarchyConstants.MIME_TYPE).asInstanceOf[String], HierarchyConstants.COLLECTION_MIME_TYPE)) {
                TelemetryManager.error("UpdateHierarchyManager.getValidatedRootNode :: Invalid MimeType for Root node id: " + identifier)
                throw new ClientException(HierarchyErrorCodes.ERR_INVALID_ROOT_ID, "Invalid MimeType for Root Node Identifier  : " + identifier)
            }
            //Todo: Remove if not required
            if (null == metadata.get(HierarchyConstants.VERSION) || metadata.get(HierarchyConstants.VERSION).asInstanceOf[Number].intValue < 2) {
                TelemetryManager.error("UpdateHierarchyManager.getValidatedRootNode :: Invalid Content Version for Root node id: " + identifier)
                throw new ClientException(HierarchyErrorCodes.ERR_INVALID_ROOT_ID, "The collection version is not up to date " + identifier)
            }
            val originData = metadata.getOrDefault("originData", new java.util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
            if (StringUtils.equalsIgnoreCase(originData.getOrElse("copyType", "").asInstanceOf[String], HierarchyConstants.COPY_TYPE_SHALLOW))
                throw new ClientException(HierarchyErrorCodes.ERR_HIERARCHY_UPDATE_DENIED, "Hierarchy update is not allowed for partially (shallow) copied content : " + identifier)
            rootNode.getMetadata.put(HierarchyConstants.VERSION, HierarchyConstants.LATEST_CONTENT_VERSION)
            //TODO: Remove the Populate category mapping before updating for backward
            HierarchyBackwardCompatibilityUtil.setContentAndCategoryTypes(rootNode.getMetadata)
            HierarchyBackwardCompatibilityUtil.setNewObjectType(rootNode)
            rootNode
        })
    }

    private def getExistingHierarchy(request: Request, rootNode: Node)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[java.util.HashMap[String, AnyRef]] = {
        fetchHierarchy(request, rootNode).map(hierarchyString => {
            if (hierarchyString.asInstanceOf[String].nonEmpty) {
                JsonUtils.deserialize(hierarchyString.asInstanceOf[String], classOf[java.util.HashMap[String, AnyRef]])
            } else new java.util.HashMap[String, AnyRef]()
        })
    }

    private def fetchHierarchy(request: Request, rootNode: Node)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Any] = {
        val req = new Request(request)
        req.put(HierarchyConstants.IDENTIFIER, rootNode.getIdentifier)
        oec.graphService.readExternalProps(req, List(HierarchyConstants.HIERARCHY)).map(response => {
            if (ResponseHandler.checkError(response) && ResponseHandler.isResponseNotFoundError(response)) {
                if (CollectionUtils.containsAny(HierarchyConstants.HIERARCHY_LIVE_STATUS, rootNode.getMetadata.get("status").asInstanceOf[String]))
                    throw new ServerException(HierarchyErrorCodes.ERR_HIERARCHY_NOT_FOUND, "No hierarchy is present in cassandra for identifier:" + rootNode.getIdentifier)
                else {
                    if (rootNode.getMetadata.containsKey("pkgVersion"))
                        req.put(HierarchyConstants.IDENTIFIER, rootNode.getIdentifier.replace(HierarchyConstants.IMAGE_SUFFIX, ""))
                    else {
                        //TODO: Remove should Image be deleted after migration
                        request.getContext.put("shouldImageDelete", shouldImageBeDeleted(rootNode).asInstanceOf[AnyRef])
                        req.put(HierarchyConstants.IDENTIFIER, if (!rootNode.getIdentifier.endsWith(HierarchyConstants.IMAGE_SUFFIX)) rootNode.getIdentifier + HierarchyConstants.IMAGE_SUFFIX else rootNode.getIdentifier)
                    }
                    oec.graphService.readExternalProps(req, List(HierarchyConstants.HIERARCHY)).map(resp => {
                        resp.getResult.toMap.getOrElse(HierarchyConstants.HIERARCHY, "").asInstanceOf[String]
                    }) recover { case e: ResourceNotFoundException => TelemetryManager.log("No hierarchy is present in cassandra for identifier:" + rootNode.getIdentifier) }
                }
            } else Future(response.getResult.toMap.getOrElse(HierarchyConstants.HIERARCHY, "").asInstanceOf[String])
        }).flatMap(f => f)
    }

    private def addChildNodesInNodeList(childrenMaps: java.util.List[java.util.Map[String, AnyRef]], request: Request, nodes: scala.collection.immutable.List[Node])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[scala.collection.immutable.List[Node]] = {
        if (CollectionUtils.isNotEmpty(childrenMaps)) {
            val futures = childrenMaps.map(child => {
                addNodeToList(child, request, nodes).map(modifiedList => {
                    if (!StringUtils.equalsIgnoreCase(HierarchyConstants.DEFAULT, child.get(HierarchyConstants.VISIBILITY).asInstanceOf[String])) {
                        addChildNodesInNodeList(child.get(HierarchyConstants.CHILDREN).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]], request, modifiedList)
                    } else
                        Future(modifiedList)
                }).flatMap(f => f)
            }).toList
            Future.sequence(futures).map(f => f.flatten.distinct)
        } else {
            Future(nodes)
        }
    }

    private def addNodeToList(child: java.util.Map[String, AnyRef], request: Request, nodes: scala.collection.immutable.List[Node])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[scala.collection.immutable.List[Node]] = {
        if (StringUtils.isNotEmpty(child.get(HierarchyConstants.VISIBILITY).asInstanceOf[String]))
            if (StringUtils.equalsIgnoreCase(HierarchyConstants.DEFAULT, child.get(HierarchyConstants.VISIBILITY).asInstanceOf[String])) {
                getContentNode(child.getOrDefault(HierarchyConstants.IDENTIFIER, "").asInstanceOf[String], HierarchyConstants.TAXONOMY_ID).map(node => {
                    node.getMetadata.put(HierarchyConstants.DEPTH, child.get(HierarchyConstants.DEPTH))
                    node.getMetadata.put(HierarchyConstants.PARENT_KEY, child.get(HierarchyConstants.PARENT_KEY))
                    node.getMetadata.put(HierarchyConstants.INDEX, child.get(HierarchyConstants.INDEX))
                    //TODO: Remove the Populate category mapping before updating for backward
                    HierarchyBackwardCompatibilityUtil.setContentAndCategoryTypes(node.getMetadata, node.getObjectType)
                    HierarchyBackwardCompatibilityUtil.setNewObjectType(node)
                    val updatedNodes = node :: nodes
                    updatedNodes
                }) recoverWith { case e: CompletionException => throw e.getCause }
            } else {
                val childData: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]
                childData.putAll(child)
                childData.remove(HierarchyConstants.CHILDREN)
                childData.put(HierarchyConstants.STATUS, "Draft")
                //TODO: Remove the Populate category mapping before updating for backward
                val rootNode = getTempNode(nodes, request.getContext.get(HierarchyConstants.ROOT_ID).asInstanceOf[String])
                childData.put(HierarchyConstants.CHANNEL, rootNode.getMetadata.get(HierarchyConstants.CHANNEL))
                childData.put(HierarchyConstants.AUDIENCE, rootNode.getMetadata.get(HierarchyConstants.AUDIENCE) )
                HierarchyBackwardCompatibilityUtil.setContentAndCategoryTypes(childData)
                val node = NodeUtil.deserialize(childData, request.getContext.get(HierarchyConstants.SCHEMA_NAME).asInstanceOf[String], DefinitionNode.getRelationsMap(request))
                HierarchyBackwardCompatibilityUtil.setNewObjectType(node)
                val updatedNodes = node :: nodes
                Future(updatedNodes)
            }
        else {
            Future(nodes)
        }
    }


    private def updateNodesModifiedInNodeList(nodeList: List[Node], nodesModified: java.util.HashMap[String, AnyRef], request: Request, idMap: mutable.Map[String, String])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[List[Node]] = {
        updateRootNode(request.getContext.get(HierarchyConstants.ROOT_ID).asInstanceOf[String], nodeList, nodesModified)
        val futures = nodesModified.filter(nodeModified => !StringUtils.startsWith(request.getContext.get(HierarchyConstants.ROOT_ID).asInstanceOf[String], nodeModified._1))
                .map(nodeModified => {
                    val metadata = nodeModified._2.asInstanceOf[java.util.HashMap[String, AnyRef]].getOrDefault(HierarchyConstants.METADATA, new java.util.HashMap()).asInstanceOf[java.util.HashMap[String, AnyRef]]
                    metadata.remove(HierarchyConstants.DIALCODES)
                    metadata.put(HierarchyConstants.STATUS, "Draft")
                    metadata.put(HierarchyConstants.LAST_UPDATED_ON, DateUtils.formatCurrentDate)
                    if (nodeModified._2.asInstanceOf[java.util.HashMap[String, AnyRef]].containsKey(HierarchyConstants.IS_NEW)
                            && nodeModified._2.asInstanceOf[java.util.HashMap[String, AnyRef]].get(HierarchyConstants.IS_NEW).asInstanceOf[Boolean]) {
                        if (!nodeModified._2.asInstanceOf[java.util.HashMap[String, AnyRef]].get(HierarchyConstants.ROOT).asInstanceOf[Boolean])
                            metadata.put(HierarchyConstants.VISIBILITY, HierarchyConstants.PARENT)
                        if (nodeModified._2.asInstanceOf[java.util.HashMap[String, AnyRef]].contains(HierarchyConstants.SET_DEFAULT_VALUE))
                            createNewNode(nodeModified._1, idMap, metadata, nodeList, request, nodeModified._2.asInstanceOf[java.util.HashMap[String, AnyRef]].get(HierarchyConstants.SET_DEFAULT_VALUE).asInstanceOf[Boolean])
                        else
                            createNewNode(nodeModified._1, idMap, metadata, nodeList, request)
                    } else {
                        updateTempNode(nodeModified._1, nodeList, idMap, metadata)
                        Future(nodeList.distinct)
                    }
                })
        if (CollectionUtils.isNotEmpty(futures))
            Future.sequence(futures.toList).map(f => f.flatten)
        else Future(nodeList)
    }

    private def updateRootNode(rootId: String, nodeList: List[Node], nodesModified: java.util.HashMap[String, AnyRef])(implicit ec: ExecutionContext): Unit = {
        if (nodesModified.containsKey(rootId)) {
            val metadata = nodesModified.getOrDefault(rootId, new java.util.HashMap()).asInstanceOf[java.util.HashMap[String, AnyRef]].getOrDefault(HierarchyConstants.METADATA, new java.util.HashMap()).asInstanceOf[java.util.HashMap[String, AnyRef]]
            updateNodeList(nodeList, rootId, metadata)
            nodesModified.remove(rootId)
        }
    }

    private def createNewNode(nodeId: String, idMap: mutable.Map[String, String], metadata: java.util.HashMap[String, AnyRef], nodeList: List[Node], request: Request, setDefaultValue: Boolean = true)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[List[Node]] = {
        val identifier: String = Identifier.getIdentifier(HierarchyConstants.TAXONOMY_ID, Identifier.getUniqueIdFromTimestamp)
        idMap += (nodeId -> identifier)
        metadata.put(HierarchyConstants.IDENTIFIER, identifier)
        metadata.put(HierarchyConstants.CODE, nodeId)
        metadata.put(HierarchyConstants.VERSION_KEY, System.currentTimeMillis + "")
        metadata.put(HierarchyConstants.CREATED_ON, DateUtils.formatCurrentDate)
        metadata.put(HierarchyConstants.LAST_STATUS_CHANGED_ON, DateUtils.formatCurrentDate)
        val rootNode = getTempNode(nodeList, request.getContext.get(HierarchyConstants.ROOT_ID).asInstanceOf[String])
        metadata.put(HierarchyConstants.CHANNEL, rootNode.getMetadata.get(HierarchyConstants.CHANNEL))
        metadata.put(HierarchyConstants.AUDIENCE, rootNode.getMetadata.get(HierarchyConstants.AUDIENCE) )
        val createRequest: Request = new Request(request)
        //TODO: Remove the Populate category mapping before updating for backward
        HierarchyBackwardCompatibilityUtil.setContentAndCategoryTypes(metadata)
        createRequest.setRequest(metadata)
        DefinitionNode.validate(createRequest, setDefaultValue).map(node => {
            node.setGraphId(HierarchyConstants.TAXONOMY_ID)
            node.setNodeType(HierarchyConstants.DATA_NODE)
            //Object type mapping
            HierarchyBackwardCompatibilityUtil.setNewObjectType(node)
            val updatedList = node :: nodeList
            updatedList.distinct
        })
    }

    private def updateTempNode(nodeId: String, nodeList: List[Node], idMap: mutable.Map[String, String], metadata: java.util.HashMap[String, AnyRef])(implicit ec: ExecutionContext): Unit = {
        val tempNode: Node = getTempNode(nodeList, nodeId)
        if (null != tempNode && StringUtils.isNotBlank(tempNode.getIdentifier)) {
            metadata.put(HierarchyConstants.IDENTIFIER, tempNode.getIdentifier)
            idMap += (nodeId -> tempNode.getIdentifier)
            updateNodeList(nodeList, tempNode.getIdentifier, metadata)
        } else throw new ResourceNotFoundException(HierarchyErrorCodes.ERR_CONTENT_NOT_FOUND, "Content not found with identifier: " + nodeId)
    }

    private def validateNodes(nodeList: java.util.List[Node], rootId: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[List[Node]] = {
        val nodesToValidate = nodeList.filter(node => StringUtils.equals(HierarchyConstants.PARENT, node.getMetadata.get(HierarchyConstants.VISIBILITY).asInstanceOf[String]) || StringUtils.equalsAnyIgnoreCase(rootId, node.getIdentifier)).toList
        DefinitionNode.updateJsonPropsInNodes(nodeList.toList, HierarchyConstants.TAXONOMY_ID, HierarchyConstants.COLLECTION_SCHEMA_NAME, HierarchyConstants.SCHEMA_VERSION)
        //TODO: Use actual object schema instead of collection, when another object with visibility parent introduced.
        DefinitionNode.validateContentNodes(nodesToValidate, HierarchyConstants.TAXONOMY_ID, HierarchyConstants.COLLECTION_SCHEMA_NAME, HierarchyConstants.SCHEMA_VERSION)
    }

    def constructHierarchy(list: List[java.util.Map[String, AnyRef]]): java.util.Map[String, AnyRef] = {
        val hierarchy: java.util.Map[String, AnyRef] = list.filter(root => root.get(HierarchyConstants.DEPTH).asInstanceOf[Number].intValue() == 0).head
        if (MapUtils.isNotEmpty(hierarchy)) {
            val maxDepth = list.map(node => node.get(HierarchyConstants.DEPTH).asInstanceOf[Number].intValue()).max
            for (i <- 0 to maxDepth) {
                val depth = i
                val currentLevelNodes: Map[String, List[java.util.Map[String, Object]]] = list.filter(node => node.get(HierarchyConstants.DEPTH).asInstanceOf[Number].intValue() == depth).groupBy(_.get("identifier").asInstanceOf[String].replaceAll(".img", ""))
                val nextLevel: List[java.util.Map[String, AnyRef]] = list.filter(node => node.get(HierarchyConstants.DEPTH).asInstanceOf[Number].intValue() == (depth + 1))
                if (CollectionUtils.isNotEmpty(nextLevel) && MapUtils.isNotEmpty(currentLevelNodes)) {
                    nextLevel.foreach(e => {
                        val parentId = e.get("parent").asInstanceOf[String]
                        currentLevelNodes.getOrDefault(parentId, List[java.util.Map[String, AnyRef]]()).foreach(parent => {
                            val children = parent.getOrDefault(HierarchyConstants.CHILDREN, new java.util.ArrayList[java.util.Map[String, AnyRef]]()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
                            children.add(e)
                            parent.put(HierarchyConstants.CHILDREN, sortByIndex(children))
                        })
                    })
                }
            }
        }
        hierarchy
    }

    @throws[Exception]
    private def getChildrenHierarchy(nodeList: List[Node], rootId: String, hierarchyData: java.util.HashMap[String, AnyRef], idMap: mutable.Map[String, String], existingHierarchy: java.util.Map[String, AnyRef], request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[java.util.List[java.util.Map[String, AnyRef]]] = {
        val childrenIdentifiersMap: Map[String, Map[String, Int]] = getChildrenIdentifiersMap(hierarchyData, idMap, existingHierarchy)
//        TelemetryManager.log("Children Id map for root id :" + rootId + " :: " + ScalaJsonUtils.serialize(childrenIdentifiersMap))
        getPreparedHierarchyData(nodeList, rootId, childrenIdentifiersMap, request).map(nodeMaps => {
            TelemetryManager.info("prepared hierarchy list without filtering: " + nodeMaps.size())
            val filteredNodeMaps = nodeMaps.filter(nodeMap => null != nodeMap.get(HierarchyConstants.DEPTH)).toList
            TelemetryManager.info("prepared hierarchy list with filtering: " + filteredNodeMaps.size())
//            TelemetryManager.log("filteredNodeMaps for root id :" + rootId + " :: " + ScalaJsonUtils.serialize(filteredNodeMaps))
            val hierarchyMap = constructHierarchy(filteredNodeMaps)
            if (MapUtils.isNotEmpty(hierarchyMap)) {
                hierarchyMap.getOrDefault(HierarchyConstants.CHILDREN, new java.util.ArrayList[java.util.Map[String, AnyRef]]()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
                        .filter(child => MapUtils.isNotEmpty(child))
            }
            else
                new java.util.ArrayList[java.util.Map[String, AnyRef]]()

        })
    }

    private def getChildrenIdentifiersMap(hierarchyData: java.util.Map[String, AnyRef], idMap: mutable.Map[String, String], existingHierarchy: java.util.Map[String, AnyRef]): Map[String, Map[String, Int]] = {
        if (MapUtils.isNotEmpty(hierarchyData)) {
            hierarchyData.map(entry => idMap.getOrDefault(entry._1, entry._1) -> entry._2.asInstanceOf[java.util.HashMap[String, AnyRef]]
                    .get(HierarchyConstants.CHILDREN).asInstanceOf[java.util.ArrayList[String]]
                    .map(id => idMap.getOrDefault(id, id)).zipWithIndex.toMap).toMap
        } else {
            val tempChildMap: java.util.Map[String, Map[String, Int]] = new java.util.HashMap[String, Map[String, Int]]()
            val tempResourceMap: java.util.Map[String, Map[String, Int]] = new java.util.HashMap[String, Map[String, Int]]()
            getChildrenIdMapFromExistingHierarchy(existingHierarchy, tempChildMap, tempResourceMap)
            tempChildMap.putAll(tempResourceMap)
            tempChildMap.toMap
        }
    }

    private def getChildrenIdMapFromExistingHierarchy(existingHierarchy: java.util.Map[String, AnyRef], tempChildMap: java.util.Map[String, Map[String, Int]], tempResourceMap: java.util.Map[String, Map[String, Int]]): Unit = {
        if (existingHierarchy.containsKey(HierarchyConstants.CHILDREN) && CollectionUtils.isNotEmpty(existingHierarchy.get(HierarchyConstants.CHILDREN).asInstanceOf[java.util.ArrayList[java.util.HashMap[String, AnyRef]]])) {
            tempChildMap.put(existingHierarchy.get(HierarchyConstants.IDENTIFIER).asInstanceOf[String], existingHierarchy.get(HierarchyConstants.CHILDREN).asInstanceOf[java.util.ArrayList[java.util.HashMap[String, AnyRef]]]
                    .map(child => child.get(HierarchyConstants.IDENTIFIER).asInstanceOf[String] -> child.get(HierarchyConstants.INDEX).asInstanceOf[Int]).toMap)
            existingHierarchy.get(HierarchyConstants.CHILDREN).asInstanceOf[java.util.ArrayList[java.util.HashMap[String, AnyRef]]]
                    .foreach(child => getChildrenIdMapFromExistingHierarchy(child, tempChildMap, tempResourceMap))
        } else
            tempResourceMap.put(existingHierarchy.get(HierarchyConstants.IDENTIFIER).asInstanceOf[String], Map[String, Int]())
    }

    @throws[Exception]
    private def getPreparedHierarchyData(nodeList: List[Node], rootId: String, childrenIdentifiersMap: Map[String, Map[String, Int]], request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[java.util.List[java.util.Map[String, AnyRef]]] = {
        if (MapUtils.isNotEmpty(childrenIdentifiersMap)) {
            val updatedNodeList = getTempNode(nodeList, rootId) :: List()
            updateHierarchyRelatedData(childrenIdentifiersMap.getOrElse(rootId, Map[String, Int]()), 1,
                rootId, nodeList, childrenIdentifiersMap, updatedNodeList, request, rootId).map(finalEnrichedNodeList => {
                TelemetryManager.info("Final enriched list size: " + finalEnrichedNodeList.size)
                val childNodeIds = finalEnrichedNodeList.map(node => node.getIdentifier.replaceAll(".img", "")).filterNot(id => StringUtils.containsIgnoreCase(rootId, id)).distinct
                TelemetryManager.info("Final enriched ids (childNodes): " + childNodeIds + " :: size: " + childNodeIds.size)
                // UNDERSTANDING: below we used nodeList to update DEPTH and CHILD_NODES. It automatically updated to finalEnrichedNodeList.
                // Because, the Node object is a Java POJO with metadata using java.util.Map.
                updateNodeList(nodeList, rootId, new java.util.HashMap[String, AnyRef]() {
                    put(HierarchyConstants.DEPTH, 0.asInstanceOf[AnyRef])
                    put(HierarchyConstants.CHILD_NODES, new java.util.ArrayList[String](childNodeIds))
                })
                validateNodes(finalEnrichedNodeList, rootId).map(result => HierarchyManager.convertNodeToMap(finalEnrichedNodeList))
            }).flatMap(f => f)
        } else {
            updateNodeList(nodeList, rootId, new java.util.HashMap[String, AnyRef]() {
                {
                    put(HierarchyConstants.DEPTH, 0.asInstanceOf[AnyRef])
                }
            })
            validateNodes(nodeList, rootId).map(result => HierarchyManager.convertNodeToMap(nodeList))
        }
    }

    @throws[Exception]
    private def updateHierarchyRelatedData(childrenIds: Map[String, Int], depth: Int, parent: String, nodeList: List[Node], hierarchyStructure: Map[String, Map[String, Int]], enrichedNodeList: scala.collection.immutable.List[Node], request: Request, rootId: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[List[Node]] = {
        val rootResourceChange: Boolean = if (Platform.config.hasPath("hierarchyUpdate.allow.resource.at.root.level")) Platform.config.getBoolean("hierarchyUpdate.allow.resource.at.root.level") else false
        val futures = childrenIds.map(child => {
            val id = child._1
            val index = child._2 + 1
            val tempNode = getTempNode(nodeList, id)
            if (null != tempNode && StringUtils.equalsIgnoreCase(HierarchyConstants.PARENT, tempNode.getMetadata.get(HierarchyConstants.VISIBILITY).asInstanceOf[String])) {
                populateHierarchyRelatedData(tempNode, depth, index, parent)
                val nxtEnrichedNodeList = tempNode :: enrichedNodeList
                if (MapUtils.isNotEmpty(hierarchyStructure.getOrDefault(child._1, Map[String, Int]())))
                    updateHierarchyRelatedData(hierarchyStructure.getOrDefault(child._1, Map[String, Int]()),
                        tempNode.getMetadata.get(HierarchyConstants.DEPTH).asInstanceOf[Int] + 1, id, nodeList, hierarchyStructure, nxtEnrichedNodeList, request, rootId)
                else
                    Future(nxtEnrichedNodeList)
            } else {
//                TelemetryManager.info("Get ContentNode as TempNode is null for ID: " + id)
                getContentNode(id, HierarchyConstants.TAXONOMY_ID).map(node => {
                    val parentNode: Node = if (rootResourceChange && nodeList.find(p => p.getIdentifier.equals(parent)).orNull == null) {
                        if (nodeList.find(p => p.getIdentifier.equals(rootId)).orNull == null)
                            nodeList.find(p => p.getIdentifier.equals(rootId + ".img")).orNull
                        else
                            nodeList.find(p => p.getIdentifier.equals(rootId)).orNull
                    } else
                        nodeList.find(p => p.getIdentifier.equals(parent)).orNull
                    val nxtEnrichedNodeList = if (null != parentNode) {
                        TelemetryManager.info(s"ObjectType for $parent is ${parentNode.getObjectType}...")
                        val parentMetadata: java.util.Map[String, AnyRef] = NodeUtil.serialize(parentNode, new java.util.ArrayList[String](), parentNode.getObjectType.toLowerCase, "1.0")
                        val childMetadata: java.util.Map[String, AnyRef] = NodeUtil.serialize(node, new java.util.ArrayList[String](), node.getObjectType.toLowerCase, "1.0")
                        HierarchyManager.validateLeafNodes(parentMetadata, childMetadata, request)
                        populateHierarchyRelatedData(node, depth, index, parent)
                        node.getMetadata.put(HierarchyConstants.VISIBILITY, HierarchyConstants.DEFAULT)
                        //TODO: Populate category mapping before updating for backward
                        HierarchyBackwardCompatibilityUtil.setContentAndCategoryTypes(node.getMetadata, node.getObjectType)
                        HierarchyBackwardCompatibilityUtil.setNewObjectType(node)
                        node :: enrichedNodeList
                    } else {
                        TelemetryManager.info("There is no parent node for identifier:" + parent)
                        enrichedNodeList
                    }
                    if (MapUtils.isNotEmpty(hierarchyStructure.getOrDefault(id, Map[String, Int]()))) {
                        updateHierarchyRelatedData(hierarchyStructure.getOrDefault(id, Map[String, Int]()), node.getMetadata.get(HierarchyConstants.DEPTH).asInstanceOf[Int] + 1, id, nodeList, hierarchyStructure, nxtEnrichedNodeList, request, rootId)
                    } else
                        Future(nxtEnrichedNodeList)
                }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
            }
        })
        if (CollectionUtils.isNotEmpty(futures)) {
            val listOfFutures = Future.sequence(futures.toList)
            listOfFutures.map(f => f.flatten.distinct)
        } else
            Future(enrichedNodeList)
    }

    private def populateHierarchyRelatedData(tempNode: Node, depth: Int, index: Int, parent: String) = {
        tempNode.getMetadata.put(HierarchyConstants.DEPTH, depth.asInstanceOf[AnyRef])
        tempNode.getMetadata.put(HierarchyConstants.PARENT_KEY, parent.replaceAll(".img", ""))
        tempNode.getMetadata.put(HierarchyConstants.INDEX, index.asInstanceOf[AnyRef])
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

    def updateHierarchyData(rootId: String, children: java.util.List[java.util.Map[String, AnyRef]], nodeList: List[Node], request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        val reqHierarchy: java.util.HashMap[String, AnyRef] = request.getRequest.get(HierarchyConstants.HIERARCHY).asInstanceOf[java.util.HashMap[String, AnyRef]]
        val rmSchemaValidator = SchemaValidatorFactory.getInstance(HierarchyConstants.RELATIONAL_METADATA.toLowerCase(), "1.0")

        reqHierarchy.foreach(rec=> {
           if(rec._2.asInstanceOf[java.util.Map[String,AnyRef]].containsKey(HierarchyConstants.RELATIONAL_METADATA)) {
               val rmObj = rec._2.asInstanceOf[java.util.Map[String,AnyRef]](HierarchyConstants.RELATIONAL_METADATA)
               rmObj.asInstanceOf[java.util.Map[String,AnyRef]].foreach(rmChild=>{
                   rmSchemaValidator.validate(rmChild._2.asInstanceOf[java.util.Map[String, AnyRef]])
               })
            }
        })

        val node = getTempNode(nodeList, rootId)
        val updatedHierarchy = new java.util.HashMap[String, AnyRef]()
        updatedHierarchy.put(HierarchyConstants.IDENTIFIER, rootId)
        updatedHierarchy.put(HierarchyConstants.CHILDREN, children)
        val req = new Request(request)
        req.getContext.put(HierarchyConstants.IDENTIFIER, rootId)
        val metadata = cleanUpRootData(node)
        req.getRequest.putAll(metadata)
        req.put(HierarchyConstants.HIERARCHY, ScalaJsonUtils.serialize(updatedHierarchy))
        req.put(HierarchyConstants.RELATIONAL_METADATA_COL, ScalaJsonUtils.serialize(reqHierarchy))
        req.put(HierarchyConstants.IDENTIFIER, rootId)
        req.put(HierarchyConstants.CHILDREN, new java.util.ArrayList())
        req.put(HierarchyConstants.CONCEPTS, new java.util.ArrayList())
        DataNode.update(req)
    }

    private def cleanUpRootData(node: Node)(implicit oec: OntologyEngineContext, ec: ExecutionContext): java.util.Map[String, AnyRef] = {
        DefinitionNode.getRestrictedProperties(HierarchyConstants.TAXONOMY_ID, HierarchyConstants.SCHEMA_VERSION, HierarchyConstants.OPERATION_UPDATE_HIERARCHY, HierarchyConstants.COLLECTION_SCHEMA_NAME)
          .foreach(key => node.getMetadata.remove(key))
        node.getMetadata.remove(HierarchyConstants.STATUS)
        node.getMetadata.remove(HierarchyConstants.LAST_UPDATED_ON)
        node.getMetadata.remove(HierarchyConstants.LAST_STATUS_CHANGED_ON)
        node.getMetadata
    }

    /**
     * Get the Node with ID provided from List else return Null.
     *
     * @param nodeList
     * @param id
     * @return
     */
    private def getTempNode(nodeList: List[Node], id: String) = {
        nodeList.find(node => StringUtils.startsWith(node.getIdentifier, id)).orNull
    }

    private def updateNodeList(nodeList: List[Node], id: String, metadata: java.util.HashMap[String, AnyRef]): Unit = {
        nodeList.foreach(node => {
            if(node.getIdentifier.startsWith(id)){
                node.getMetadata.putAll(metadata)
            }
        })
    }

    def getContentNode(identifier: String, graphId: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        val request: Request = new Request()
        request.setContext(new java.util.HashMap[String, AnyRef]() {
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
        request.put(HierarchyConstants.FIELDS, new java.util.ArrayList[String]())
        DataNode.read(request)
    }

    private def shouldImageBeDeleted(rootNode: Node): Boolean = {
        val flag = if (Platform.config.hasPath("collection.image.migration.enabled")) Platform.config.getBoolean("collection.image.migration.enabled") else false
        //        flag && !CollectionUtils.containsAny(HierarchyConstants.HIERARCHY_LIVE_STATUS, rootNode.getMetadata.get(HierarchyConstants.STATUS).asInstanceOf[String]) &&
        //            !rootNode.getMetadata.containsKey("pkgVersion")
        flag
    }

    def sortByIndex(childrenMaps: java.util.List[java.util.Map[String, AnyRef]]): java.util.List[java.util.Map[String, AnyRef]] = {
        bufferAsJavaList(childrenMaps.sortBy(_.get("index").asInstanceOf[Int]))
    }


    def deleteHierarchy(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
        val req = new Request(request)
        val rootId = request.getContext.get(HierarchyConstants.ROOT_ID).asInstanceOf[String]
        req.put(HierarchyConstants.IDENTIFIERS, if (rootId.contains(HierarchyConstants.IMAGE_SUFFIX)) List(rootId) else List(rootId + HierarchyConstants.IMAGE_SUFFIX))
        oec.graphService.deleteExternalProps(req)
    }

}
