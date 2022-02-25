package org.sunbird.managers

import java.util
import java.util.concurrent.CompletionException

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ErrorCodes, ResourceNotFoundException, ResponseCode, ServerException}
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.{NodeUtil, ScalaJsonUtils}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.JavaConverters
import scala.concurrent.{ExecutionContext, Future}
import com.mashape.unirest.http.HttpResponse
import com.mashape.unirest.http.Unirest
import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.utils.HierarchyConstants

object HierarchyManager {

    val schemaName: String = "questionset"
    val schemaVersion: String = "1.0"
    val imgSuffix: String = ".img"
    val hierarchyPrefix: String = "qs_hierarchy_"
    val statusList = List("Live", "Unlisted", "Flagged")
    val ASSESSMENT_OBJECT_TYPES = List("Question", "QuestionSet")

    val keyTobeRemoved = {
        if(Platform.config.hasPath("content.hierarchy.removed_props_for_leafNodes"))
            Platform.config.getStringList("content.hierarchy.removed_props_for_leafNodes")
        else
            java.util.Arrays.asList("collections","children","usedByContent","item_sets","methods","libraries","editorState")
    }

    val externalKeys: java.util.List[String] = if(Platform.config.hasPath("questionset.hierarchy.remove_external_props")) Platform.config.getStringList("questionset.hierarchy.remove_external_props")
    else List("hierarchy","outcomeDeclaration").asJava
    
    @throws[Exception]
    def addLeafNodesToHierarchy(request:Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
        validateRequest(request, "add")
        val rootNodeFuture = getRootNode(request)
        rootNodeFuture.map(rootNode => {
            val unitId = request.getRequest.getOrDefault("collectionId", "").asInstanceOf[String]
            if (StringUtils.isBlank(unitId)) attachLeafToRootNode(request, rootNode, "add") else {
                val rootNodeMap =  NodeUtil.serialize(rootNode, java.util.Arrays.asList("childNodes", "originData"), schemaName, schemaVersion)
                if(!rootNodeMap.get("childNodes").asInstanceOf[Array[String]].toList.contains(unitId)) {
                    Future{ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "collectionId " + unitId + " does not exist")}
                }else {
                    val hierarchyFuture = fetchHierarchy(request, rootNode.getIdentifier)
                    hierarchyFuture.map(hierarchy => {
                        if(hierarchy.isEmpty){
                            Future{ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), "hierarchy is empty")}
                        } else {
                            val leafNodesFuture = fetchLeafNodes(request)
                            leafNodesFuture.map(leafNodes => updateHierarchyData(unitId, hierarchy, leafNodes, rootNode, request, "add").map(node => ResponseHandler.OK.put("rootId", node.getIdentifier.replaceAll(imgSuffix, "")))).flatMap(f => f)
                        }
                    }).flatMap(f => f)
                }
            }
        }).flatMap(f => f) recoverWith {case e: CompletionException => throw e.getCause}
    }

    @throws[Exception]
    def removeLeafNodesFromHierarchy(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
        validateRequest(request, "remove")
        val rootNodeFuture = getRootNode(request)
        rootNodeFuture.map(rootNode => {
            val unitId = request.getRequest.getOrDefault("collectionId", "").asInstanceOf[String]
            if (StringUtils.isBlank(unitId)) attachLeafToRootNode(request, rootNode, "remove") else {
                val rootNodeMap =  NodeUtil.serialize(rootNode, java.util.Arrays.asList("childNodes", "originData"), schemaName, schemaVersion)
                if(!rootNodeMap.get("childNodes").asInstanceOf[Array[String]].toList.contains(unitId)) {
                    Future{ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "collectionId " + unitId + " does not exist")}
                }else {
                    val hierarchyFuture = fetchHierarchy(request, rootNode.getIdentifier)
                    hierarchyFuture.map(hierarchy => {
                        if(hierarchy.isEmpty){
                            Future{ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), "hierarchy is empty")}
                        } else updateHierarchyData(unitId, hierarchy, null, rootNode, request, "remove").map(node => ResponseHandler.OK.put("rootId", node.getIdentifier.replaceAll(imgSuffix, "")))
                    }).flatMap(f => f)
                }
            }
        }).flatMap(f => f) recoverWith {case e: CompletionException => throw e.getCause}
    }

    def attachLeafToRootNode(request: Request, rootNode: Node, operation: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
        fetchHierarchy(request, rootNode.getIdentifier).map(hierarchy => {
            if (hierarchy.isEmpty) {
                Future(ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), "hierarchy is empty"))
            } else {
                fetchLeafNodes(request).map(leafNodes => {
                    val rootNodeMap =  NodeUtil.serialize(rootNode, java.util.Arrays.asList(HierarchyConstants.BRANCHING_LOGIC, HierarchyConstants.ALLOW_BRANCHING, HierarchyConstants.CHILD_NODES), schemaName, schemaVersion)
                    if (isBranchingEnabled(rootNodeMap, request, operation)) {
                        val branchingLogic = rootNodeMap.getOrDefault(HierarchyConstants.BRANCHING_LOGIC, new util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
                        TelemetryManager.info(s"Branching Found For ${rootNode.getIdentifier}. Branching Rules are : ${branchingLogic}")
                        leafNodes.foreach(leafNode => {
                            val updatedBranching = operation match {
                                case "remove" => removeBranching(leafNode.getIdentifier, branchingLogic)
                                case "add" => addBranching(leafNode.getIdentifier, branchingLogic, request, rootNode.getMetadata.getOrDefault("childNodes", Array[String]()).asInstanceOf[Array[String]].toList)
                            }
                            if (MapUtils.isNotEmpty(updatedBranching)) {
                                rootNode.getMetadata.put(HierarchyConstants.BRANCHING_LOGIC, updatedBranching)
                                request.put(HierarchyConstants.BRANCHING_LOGIC, updatedBranching)
                            }
                        })
                        TelemetryManager.info("updated branchingLogic for node " + rootNode.getIdentifier + " is : " + rootNode.getMetadata.get(HierarchyConstants.BRANCHING_LOGIC))
                    } else if (StringUtils.equalsIgnoreCase("add", operation) && request.getRequest.getOrDefault(HierarchyConstants.BRANCHING_LOGIC, new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]].nonEmpty)
                        throw new ClientException("ERR_BRANCHING_LOGIC", s"Branching Is Not Enabled For ${rootNode.getIdentifier}. Please Enable Branching Or Remove branchingLogic from Request.")
                    updateRootNode(rootNode, request, operation).map(node => {
                        updateRootHierarchy(hierarchy, leafNodes, node, request, operation).map(response => {
                            if (!ResponseHandler.checkError(response)) {
                                ResponseHandler.OK
                                    .put("rootId", node.getIdentifier.replaceAll(imgSuffix, ""))
                                    .put("children", request.get("children"))
                            } else response
                        })
                    }).flatMap(f => f)
                }).flatMap(f => f)
            }
        }).flatMap(f => f)
    }

    def updateRootHierarchy(hierarchy: java.util.Map[String, AnyRef], leafNodes: List[Node], rootNode: Node, request: Request, operation: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext) = {
        val leafNodeIds = request.get("children").asInstanceOf[util.List[String]]
        val req = new Request(request)
        if ("add".equalsIgnoreCase(operation)) {
            val updatedChildren = restructureUnit(hierarchy.get("children").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]],
                convertNodeToMap(leafNodes), leafNodeIds, 1, rootNode.getIdentifier.replace(imgSuffix, ""))
            val updatedHierarchy = Map("children" -> updatedChildren, "identifier" -> rootNode.getIdentifier.replace(imgSuffix, "")).asJava
            req.put("hierarchy", ScalaJsonUtils.serialize(updatedHierarchy))
        }
        if ("remove".equalsIgnoreCase(operation)) {
            val filteredChildren = hierarchy.get("children")
                .asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala
                .filter(child => !leafNodeIds.contains(child.get("identifier")))
            filteredChildren.sortBy(_.get("index").asInstanceOf[Integer])
                .zipWithIndex.foreach(zippedChild => zippedChild._1.put("index", (zippedChild._2.asInstanceOf[Integer] + 1).asInstanceOf[Object]))
            val updatedHierarchy = Map("children" -> filteredChildren, "identifier" -> rootNode.getIdentifier.replace(imgSuffix, "")).asJava
            req.put("hierarchy", ScalaJsonUtils.serialize(updatedHierarchy))
        }
        req.put("identifier", rootNode.getIdentifier)
        oec.graphService.saveExternalProps(req)
    }

    @throws[Exception]
    def getHierarchy(request : Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
        val mode = request.get("mode").asInstanceOf[String]
        if(StringUtils.isNotEmpty(mode) && mode.equals("edit"))
            getUnPublishedHierarchy(request)
        else
            getPublishedHierarchy(request)
    }

    @throws[Exception]
    def getUnPublishedHierarchy(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
        val rootNodeFuture = getRootNode(request)
        rootNodeFuture.map(rootNode => {
            if (StringUtils.equalsIgnoreCase("Retired", rootNode.getMetadata.getOrDefault("status", "").asInstanceOf[String])) {
                Future(ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "rootId " + request.get("rootId") + " does not exist"))
            }
            val bookmarkId = request.get("bookmarkId").asInstanceOf[String]
            var metadata: util.Map[String, AnyRef] = NodeUtil.serialize(rootNode, new util.ArrayList[String](), request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
            val hierarchy = fetchHierarchy(request, rootNode.getIdentifier)
            hierarchy.map(hierarchy => {
                val children = hierarchy.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.ArrayList[java.util.Map[String, AnyRef]]]
                val leafNodeIds = new util.ArrayList[String]()
                fetchAllLeafNodes(children, leafNodeIds)
                getLatestLeafNodes(leafNodeIds).map(leafNodesMap => {
                    updateLatestLeafNodes(children, leafNodesMap)
                    metadata.put("children", children)
                    metadata.put("identifier", request.get("rootId"))
                    if(StringUtils.isNotEmpty(bookmarkId))
                        metadata = filterBookmarkHierarchy(metadata.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]], bookmarkId)
                    if (MapUtils.isEmpty(metadata)) {
                        ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "bookmarkId " + bookmarkId + " does not exist")
                    } else {
                        ResponseHandler.OK.put("questionSet", metadata)
                    }
                })
            }).flatMap(f => f)
        }).flatMap(f => f) recoverWith { case e: ResourceNotFoundException => {
            val searchResponse = searchRootIdInElasticSearch(request.get("rootId").asInstanceOf[String])
            searchResponse.map(rootHierarchy => {
                if(!rootHierarchy.isEmpty && StringUtils.isNotEmpty(rootHierarchy.asInstanceOf[util.HashMap[String, AnyRef]].get("identifier").asInstanceOf[String])){
                    val unPublishedBookmarkHierarchy = getUnpublishedBookmarkHierarchy(request, rootHierarchy.asInstanceOf[util.HashMap[String, AnyRef]].get("identifier").asInstanceOf[String])
                    unPublishedBookmarkHierarchy.map(hierarchy => {
                        if (!hierarchy.isEmpty) {
                            val children = hierarchy.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.ArrayList[java.util.Map[String, AnyRef]]]
                            val leafNodeIds = new util.ArrayList[String]()
                            fetchAllLeafNodes(children, leafNodeIds)
                            getLatestLeafNodes(leafNodeIds).map(leafNodesMap => {
                                updateLatestLeafNodes(children, leafNodesMap)
                                hierarchy.put("children", children)
                            })
                            ResponseHandler.OK.put("questionSet", hierarchy)
                        } else
                            ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "rootId " + request.get("rootId") + " does not exist")
                    })
                } else {
                    Future(ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "rootId " + request.get("rootId") + " does not exist"))
                }
            }).flatMap(f => f)
        }
        }
    }

    @throws[Exception]
    def getPublishedHierarchy(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
        val redisHierarchy = if(Platform.getBoolean("questionset.cache.enable", false)) RedisCache.get(hierarchyPrefix + request.get("rootId")) else ""

        val hierarchyFuture = if (StringUtils.isNotEmpty(redisHierarchy)) {
            Future(mapAsJavaMap(Map("questionSet" -> JsonUtils.deserialize(redisHierarchy, classOf[java.util.Map[String, AnyRef]]))))
        } else getCassandraHierarchy(request)
        hierarchyFuture.map(result => {
            if (!result.isEmpty) {
                val bookmarkId = request.get("bookmarkId").asInstanceOf[String]
                val rootHierarchy  = result.get("questionSet").asInstanceOf[util.Map[String, AnyRef]]
                if (StringUtils.isEmpty(bookmarkId)) {
                    ResponseHandler.OK.put("questionSet", rootHierarchy)
                } else {
                    val children = rootHierarchy.getOrElse("children", new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]]
                    val bookmarkHierarchy = filterBookmarkHierarchy(children, bookmarkId)
                    if (MapUtils.isEmpty(bookmarkHierarchy)) {
                        ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "bookmarkId " + bookmarkId + " does not exist")
                    } else {
                        ResponseHandler.OK.put("questionSet", bookmarkHierarchy)
                    }
                }
            } else
                ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "rootId " + request.get("rootId") + " does not exist")
        })
    }

    def validateRequest(request: Request, operation: String)(implicit ec: ExecutionContext) = {
        val rootId = request.get("rootId").asInstanceOf[String]
        val children = request.get("children").asInstanceOf[java.util.List[String]]
        val branchingLogic = request.getRequest.getOrDefault(HierarchyConstants.BRANCHING_LOGIC, new util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
        if (StringUtils.isBlank(rootId)) {
            throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "rootId is mandatory")
        }
        if (null == children || children.isEmpty) {
            throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "children are mandatory")
        }
        if(StringUtils.equalsAnyIgnoreCase(operation, "add") && MapUtils.isNotEmpty(branchingLogic)) {
            if(!children.containsAll(branchingLogic.keySet()))
                throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "Branch Rule Found For The Node Which Is Not A Children Having Identifier : "+branchingLogic.keySet().toList.diff(children.toList).asJava)
        }
    }

    private def getRootNode(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        val req = new Request(request)
        req.put("identifier", request.get("rootId").asInstanceOf[String])
        req.put("mode", request.get("mode").asInstanceOf[String])
        req.put("fields",request.get("fields").asInstanceOf[java.util.List[String]])
        DataNode.read(req)
    }

    def fetchLeafNodes(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[List[Node]] =  {
        val leafNodes = request.get("children").asInstanceOf[java.util.List[String]]
        val req = new Request(request)
        req.put("identifiers", leafNodes)
        DataNode.list(req).map(nodes => {
            if(nodes.size() != leafNodes.size()) {
                val filteredList = leafNodes.toList.filter(id => !nodes.contains(id))
                throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "Children which are not available are: " + filteredList)
            } else {
                val invalidNodes = nodes.filterNot(node => ASSESSMENT_OBJECT_TYPES.contains(node.getObjectType))
                if (CollectionUtils.isNotEmpty(invalidNodes))
                    throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), s"Children must be of types $ASSESSMENT_OBJECT_TYPES for ids:  ${invalidNodes.map(_.getIdentifier)}")
                else nodes.toList
            }
        })
    }

    def convertNodeToMap(leafNodes: List[Node])(implicit oec: OntologyEngineContext, ec: ExecutionContext): java.util.List[java.util.Map[String, AnyRef]] = {
        leafNodes.map(node => {
            val updatedNode: Node = if (node.getObjectType.equalsIgnoreCase("QuestionSet")
              && node.getMetadata.getOrDefault("visibility", "Parent").asInstanceOf[String].equalsIgnoreCase("Parent")) {
                val extData = if (null != node.getExternalData) node.getExternalData.filter(entry => !externalKeys.contains(entry._1)).asJava else Map().asJava
                node.getMetadata.putAll(extData)
                node
            } else node
            val nodeMap:java.util.Map[String,AnyRef] = NodeUtil.serialize(updatedNode, null, updatedNode.getObjectType.toLowerCase().replace("image", ""), schemaVersion)
            nodeMap.keySet().removeAll(keyTobeRemoved)
            nodeMap
        })
    }

    def addChildrenToUnit(children: java.util.List[java.util.Map[String,AnyRef]], unitId:String, leafNodes: java.util.List[java.util.Map[String, AnyRef]], leafNodeIds: java.util.List[String], request: Request): Unit = {
        val childNodes = children.filter(child => ("Parent".equalsIgnoreCase(child.get("visibility").asInstanceOf[String]) && unitId.equalsIgnoreCase(child.get("identifier").asInstanceOf[String]))).toList
        if(null != childNodes && !childNodes.isEmpty){
            val child = childNodes.get(0)
            if (isBranchingEnabled(child, request, "add")) {
                TelemetryManager.info(s"Branching Found for ${child.get("identifier")}. Branching Rules Are : ${child.get(HierarchyConstants.BRANCHING_LOGIC)}")
                val childrenIds: List[String] = child.getOrDefault(HierarchyConstants.CHILDREN, new util.ArrayList[java.util.Map[String, AnyRef]]()).asInstanceOf[util.ArrayList[java.util.Map[String, AnyRef]]].toList.map(child => child.get("identifier").asInstanceOf[String])
                leafNodeIds.foreach(nodeId => {
                    val updatedBranching = addBranching(nodeId, child.getOrDefault(HierarchyConstants.BRANCHING_LOGIC, new util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]], request, childrenIds)
                    if (MapUtils.isNotEmpty(updatedBranching)) {
                        child.put(HierarchyConstants.BRANCHING_LOGIC, updatedBranching)
                    }
                })
                TelemetryManager.info(s"Branching Updated for ${child.get("identifier")}. Updated Branching Rules Are : ${child.get(HierarchyConstants.BRANCHING_LOGIC)}")
            } else if(request.getRequest.getOrDefault(HierarchyConstants.BRANCHING_LOGIC, new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]].nonEmpty)
                throw new ClientException("ERR_BRANCHING_LOGIC", s"Branching Is Not Enabled For ${unitId}. Please Enable Branching Or Remove branchingLogic from Request.")
            val childList = child.get("children").asInstanceOf[java.util.List[java.util.Map[String,AnyRef]]]
            val restructuredChildren: java.util.List[java.util.Map[String,AnyRef]] = restructureUnit(childList, leafNodes, leafNodeIds, (child.get("depth").asInstanceOf[Integer] + 1), unitId)
            child.put("children", restructuredChildren)
        } else {
            for(child <- children) {
                if(null !=child.get("children") && !child.get("children").asInstanceOf[java.util.List[java.util.Map[String,AnyRef]]].isEmpty)
                    addChildrenToUnit(child.get("children").asInstanceOf[java.util.List[java.util.Map[String,AnyRef]]], unitId, leafNodes, leafNodeIds, request)
            }
        }
    }

    def removeChildrenFromUnit(children: java.util.List[java.util.Map[String, AnyRef]], unitId: String, leafNodeIds: java.util.List[String]):Unit = {
        val childNodes = children.filter(child => ("Parent".equalsIgnoreCase(child.get("visibility").asInstanceOf[String]) && unitId.equalsIgnoreCase(child.get("identifier").asInstanceOf[String]))).toList
        if(null != childNodes && !childNodes.isEmpty){
            val child = childNodes.get(0)
            if (isBranchingEnabled(child, new Request(), "remove")) {
                TelemetryManager.info(s"Branching Found for ${child.get("identifier")}. Branching Rules Are : ${child.get(HierarchyConstants.BRANCHING_LOGIC)}")
                leafNodeIds.foreach(nodeId => {
                    val updatedBranching = removeBranching(nodeId, child.getOrDefault(HierarchyConstants.BRANCHING_LOGIC, new util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]])
                    if (MapUtils.isNotEmpty(updatedBranching)) {
                        child.put(HierarchyConstants.BRANCHING_LOGIC, updatedBranching)
                    }
                })
                TelemetryManager.info(s"Branching Updated for ${child.get("identifier")}. Updated Branching Rules Are : ${child.get(HierarchyConstants.BRANCHING_LOGIC)}")
            }
            if(null != child.get("children") && !child.get("children").asInstanceOf[java.util.List[java.util.Map[String,AnyRef]]].isEmpty) {
                var filteredLeafNodes = child.get("children").asInstanceOf[java.util.List[java.util.Map[String,AnyRef]]].filter(existingLeafNode => {
                    !leafNodeIds.contains(existingLeafNode.get("identifier").asInstanceOf[String])
                })
                var index: Integer = 1
                filteredLeafNodes.toList.sortBy(x => x.get("index").asInstanceOf[Integer]).foreach(node => {
                    node.put("index", index)
                    index += 1
                })
                child.put("children", filteredLeafNodes)
            }
        } else {
            for(child <- children) {
                if(null !=child.get("children") && !child.get("children").asInstanceOf[java.util.List[java.util.Map[String,AnyRef]]].isEmpty)
                    removeChildrenFromUnit(child.get("children").asInstanceOf[java.util.List[java.util.Map[String,AnyRef]]], unitId, leafNodeIds)
            }
        }
    }

    def updateRootNode(rootNode: Node, request: Request, operation: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext) = {
        val req = new Request(request)
        val leafNodes = request.get("children").asInstanceOf[java.util.List[String]]
        val childNodes = new java.util.ArrayList[String]()
        childNodes.addAll(rootNode.getMetadata.getOrDefault("childNodes", Array[String]()).asInstanceOf[Array[String]].toList)
        if(operation.equalsIgnoreCase("add"))
            childNodes.addAll(leafNodes)
        if(operation.equalsIgnoreCase("remove"))
            childNodes.removeAll(leafNodes)
        if(request.getRequest.containsKey(HierarchyConstants.BRANCHING_LOGIC))
            req.put(HierarchyConstants.BRANCHING_LOGIC, request.get(HierarchyConstants.BRANCHING_LOGIC).asInstanceOf[java.util.Map[String, AnyRef]])
        req.put("childNodes", childNodes.distinct.toArray)
        req.getContext.put("identifier", rootNode.getIdentifier.replaceAll(imgSuffix, ""))
        req.getContext.put("skipValidation", java.lang.Boolean.TRUE)
        DataNode.update(req)
    }

    def updateHierarchy(unitId: String, hierarchy: java.util.Map[String, AnyRef], leafNodes: List[Node], rootNode: Node, request: Request, operation: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext) = {
        val children =  hierarchy.get("children").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        val leafNodeIds = request.get("children").asInstanceOf[java.util.List[String]]
        if("add".equalsIgnoreCase(operation)){
            val leafNodesMap:java.util.List[java.util.Map[String, AnyRef]] = convertNodeToMap(leafNodes)
            addChildrenToUnit(children, unitId, leafNodesMap, leafNodeIds, request)
        }
        if("remove".equalsIgnoreCase(operation)) {
            removeChildrenFromUnit(children,unitId, leafNodeIds)
        }
        val rootId = rootNode.getIdentifier.replaceAll(imgSuffix, "")
        val updatedHierarchy = new java.util.HashMap[String, AnyRef]()
        updatedHierarchy.put("identifier", rootId)
        updatedHierarchy.put("children", children)
        val req = new Request(request)
        req.put("hierarchy", ScalaJsonUtils.serialize(updatedHierarchy))
        req.put("identifier", rootNode.getIdentifier)
        oec.graphService.saveExternalProps(req)
    }

    def updateHierarchyData(unitId: String, hierarchy: java.util.Map[String, AnyRef], leafNodes: List[Node], rootNode: Node, request: Request, operation: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        val children =  hierarchy.get("children").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        val leafNodeIds = request.get("children").asInstanceOf[java.util.List[String]]
        val childNodes = new java.util.ArrayList[String]()
        childNodes.addAll(rootNode.getMetadata.getOrDefault("childNodes", Array[String]()).asInstanceOf[Array[String]].toList)
        if("add".equalsIgnoreCase(operation)){
            val leafNodesMap:java.util.List[java.util.Map[String, AnyRef]] = convertNodeToMap(leafNodes)
            addChildrenToUnit(children, unitId, leafNodesMap, leafNodeIds, request)
            childNodes.addAll(leafNodeIds)
        }
        if("remove".equalsIgnoreCase(operation)) {
            removeChildrenFromUnit(children, unitId, leafNodeIds)
            childNodes.removeAll(leafNodeIds)
        }
        val rootId = rootNode.getIdentifier.replaceAll(imgSuffix, "")
        val updatedHierarchy = new java.util.HashMap[String, AnyRef]()
        updatedHierarchy.put(HierarchyConstants.IDENTIFIER, rootId)
        updatedHierarchy.put(HierarchyConstants.CHILDREN, children)
        val req = new Request()
        req.setContext(request.getContext)
        req.getContext.put(HierarchyConstants.IDENTIFIER, rootNode.getIdentifier)
        req.put(HierarchyConstants.CHILD_NODES, childNodes.distinct.toArray)
        req.put(HierarchyConstants.HIERARCHY, ScalaJsonUtils.serialize(updatedHierarchy))
        DataNode.update(req)
    }

    def restructureUnit(childList: java.util.List[java.util.Map[String, AnyRef]], leafNodes: java.util.List[java.util.Map[String, AnyRef]], leafNodeIds: java.util.List[String], depth: Integer, parent: String): java.util.List[java.util.Map[String, AnyRef]] = {
        var maxIndex:Integer = 0
        var leafNodeMap: java.util.Map[String, java.util.Map[String, AnyRef]] =  new util.HashMap[String, java.util.Map[String, AnyRef]]()
        for(leafNode <- leafNodes){
            leafNodeMap.put(leafNode.get("identifier").asInstanceOf[String], JavaConverters.mapAsJavaMapConverter(leafNode).asJava)
        }
        var filteredLeafNodes: java.util.List[java.util.Map[String, AnyRef]] = new util.ArrayList[java.util.Map[String, AnyRef]]()
        if(null != childList && !childList.isEmpty) {
            val childMap:Map[String, java.util.Map[String, AnyRef]] = childList.toList.map(f => f.get("identifier").asInstanceOf[String] -> f).toMap
            val existingLeafNodes = childMap.filter(p => leafNodeIds.contains(p._1))
            existingLeafNodes.map(en => {
                leafNodeMap.get(en._1).put("index", en._2.get("index").asInstanceOf[Integer])
            })
            filteredLeafNodes = bufferAsJavaList(childList.filter(existingLeafNode => {
                !leafNodeIds.contains(existingLeafNode.get("identifier").asInstanceOf[String])
            }))
            maxIndex = childMap.values.toList.map(child => child.get("index").asInstanceOf[Integer]).toList.max.asInstanceOf[Integer]
        }
        leafNodeIds.foreach(id => {
            var node = leafNodeMap.getOrDefault(id, new util.HashMap[String, AnyRef]())
            node.put("parent", parent)
            node.put("depth", depth)
            if( null == node.get("index")) {
                val index:Integer = maxIndex + 1
                node.put("index", index)
                maxIndex += 1
            }
            filteredLeafNodes.add(node)
        })
        filteredLeafNodes
    }

    def fetchHierarchy(request: Request, identifier: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Map[String, AnyRef]] = {
        val req = new Request(request)
        req.put("identifier", identifier)
        val responseFuture = oec.graphService.readExternalProps(req, List("hierarchy"))
        responseFuture.map(response => {
            if (!ResponseHandler.checkError(response)) {
                val hierarchyString = response.getResult.toMap.getOrDefault("hierarchy", "").asInstanceOf[String]
                if (StringUtils.isNotEmpty(hierarchyString)) {
                    Future(JsonUtils.deserialize(hierarchyString, classOf[java.util.Map[String, AnyRef]]).toMap)
                } else
                    Future(Map[String, AnyRef]())
            } else if (ResponseHandler.checkError(response) && response.getResponseCode.code() == 404 && Platform.config.hasPath("collection.image.migration.enabled") && Platform.config.getBoolean("collection.image.migration.enabled")) {
                req.put("identifier", identifier.replaceAll(imgSuffix, "") + imgSuffix)
                val responseFuture = oec.graphService.readExternalProps(req, List("hierarchy"))
                responseFuture.map(response => {
                    if (!ResponseHandler.checkError(response)) {
                        val hierarchyString = response.getResult.toMap.getOrDefault("hierarchy", "").asInstanceOf[String]
                        if (StringUtils.isNotEmpty(hierarchyString)) {
                            JsonUtils.deserialize(hierarchyString, classOf[java.util.Map[String, AnyRef]]).toMap
                        } else
                            Map[String, AnyRef]()
                    } else if (ResponseHandler.checkError(response) && response.getResponseCode.code() == 404)
                        Map[String, AnyRef]()
                    else
                        throw new ServerException("ERR_WHILE_FETCHING_HIERARCHY_FROM_CASSANDRA", "Error while fetching hierarchy from cassandra")
                })
            } else if (ResponseHandler.checkError(response) && response.getResponseCode.code() == 404)
                Future(Map[String, AnyRef]())
            else
                throw new ServerException("ERR_WHILE_FETCHING_HIERARCHY_FROM_CASSANDRA", "Error while fetching hierarchy from cassandra")
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
    }

    def getCassandraHierarchy(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[util.Map[String, AnyRef]] = {
        val rootHierarchy: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]()
        val hierarchy = fetchHierarchy(request, request.getRequest.get("rootId").asInstanceOf[String])
        hierarchy.map(hierarchy => {
            if (!hierarchy.isEmpty) {
                if (StringUtils.isNotEmpty(hierarchy.getOrDefault("status", "").asInstanceOf[String]) && statusList.contains(hierarchy.getOrDefault("status", "").asInstanceOf[String])) {
                    val hierarchyMap = mapAsJavaMap(hierarchy)
                    rootHierarchy.put("questionSet", hierarchyMap)
                    RedisCache.set(hierarchyPrefix + request.get("rootId"), JsonUtils.serialize(hierarchyMap))
                    Future(rootHierarchy)
                } else {
                    Future(new util.HashMap[String, AnyRef]())
                }
            } else {
                val searchResponse = searchRootIdInElasticSearch(request.get("rootId").asInstanceOf[String])
                searchResponse.map(response => {
                    if (!response.isEmpty) {
                        if (StringUtils.isNotEmpty(response.getOrDefault("identifier", "").asInstanceOf[String])) {
                            val parentHierarchy = fetchHierarchy(request, response.get("identifier").asInstanceOf[String])
                            parentHierarchy.map(hierarchy => {
                                if (!hierarchy.isEmpty) {
                                    if (StringUtils.isNoneEmpty(hierarchy.getOrDefault("status", "").asInstanceOf[String]) && statusList.contains(hierarchy.getOrDefault("status", "").asInstanceOf[String]) && CollectionUtils.isNotEmpty(mapAsJavaMap(hierarchy).get("children").asInstanceOf[util.ArrayList[util.HashMap[String, AnyRef]]])) {
                                        val bookmarkHierarchy = filterBookmarkHierarchy(mapAsJavaMap(hierarchy).get("children").asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]], request.get("rootId").asInstanceOf[String])
                                        if (!bookmarkHierarchy.isEmpty) {
                                            rootHierarchy.put("questionSet", hierarchy)
                                            RedisCache.set(hierarchyPrefix + request.get("rootId"), JsonUtils.serialize(hierarchy))
                                            rootHierarchy
                                        } else {
                                            new util.HashMap[String, AnyRef]()
                                        }
                                    } else {
                                        new util.HashMap[String, AnyRef]()
                                    }
                                } else {
                                    new util.HashMap[String, AnyRef]()
                                }
                            })
                        } else {
                            Future(new util.HashMap[String, AnyRef]())
                        }
                    } else {
                        Future(new util.HashMap[String, AnyRef]())
                    }
                }).flatMap(f => f)
            }
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
    }

    def searchRootIdInElasticSearch(rootId: String)(implicit ec: ExecutionContext): Future[util.Map[String, AnyRef]] = {
        val mapper: ObjectMapper = new ObjectMapper()
        val searchRequest: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {
            put("request", new util.HashMap[String, AnyRef]() {
                put("filters", new util.HashMap[String, AnyRef]() {
                    put("status", new util.ArrayList[String]() {
                        add("Live");
                        add("Unlisted")
                    })
                    put("mimeType", "application/vnd.ekstep.content-collection")
                    put("childNodes", new util.ArrayList[String]() {
                        add(rootId)
                    })
                    put("visibility", "Default")
                })
                put("fields", new util.ArrayList[String]() {
                    add("identifier")
                })
            })
        }
        val url: String = if (Platform.config.hasPath("composite.search.url")) Platform.config.getString("composite.search.url") else "https://dev.sunbirded.org/action/composite/v3/search"
        val httpResponse: HttpResponse[String] = Unirest.post(url).header("Content-Type", "application/json").body(mapper.writeValueAsString(searchRequest)).asString
        if (httpResponse.getStatus == 200) {
            val response: Response = JsonUtils.deserialize(httpResponse.getBody, classOf[Response])
            if (response.get("count").asInstanceOf[Integer] > 0 && CollectionUtils.isNotEmpty(response.get("content").asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]])) {
                Future(response.get("content").asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]].get(0))
            } else {
                Future(new util.HashMap[String, AnyRef]())
            }
        } else {
            throw new ServerException("SERVER_ERROR", "Invalid response from search")
        }
    }

    def filterBookmarkHierarchy(children: util.List[util.Map[String, AnyRef]], bookmarkId: String)(implicit ec: ExecutionContext): util.Map[String, AnyRef] = {
        if (CollectionUtils.isNotEmpty(children)) {
            val response = children.filter(_.get("identifier") == bookmarkId).toList
            if (CollectionUtils.isNotEmpty(response)) {
                response.get(0)
            } else {
                val nextChildren = bufferAsJavaList(children.flatMap(child => {
                    if (!child.isEmpty && CollectionUtils.isNotEmpty(child.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]]))
                        child.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]]
                    else new util.ArrayList[util.Map[String, AnyRef]]
                }))
                filterBookmarkHierarchy(nextChildren, bookmarkId)
            }
        } else {
            new util.HashMap[String, AnyRef]()
        }
    }

    def getUnpublishedBookmarkHierarchy(request: Request, identifier: String)(implicit ec: ExecutionContext, oec:OntologyEngineContext): Future[util.Map[String, AnyRef]] = {
        if (StringUtils.isNotEmpty(identifier)) {
            val parentHierarchy = fetchHierarchy(request, identifier + imgSuffix)
            parentHierarchy.map(hierarchy => {
                if (!hierarchy.isEmpty && CollectionUtils.isNotEmpty(mapAsJavaMap(hierarchy).get("children").asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]])) {
                    val bookmarkHierarchy = filterBookmarkHierarchy(mapAsJavaMap(hierarchy).get("children").asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]], request.get("rootId").asInstanceOf[String])
                    if (!bookmarkHierarchy.isEmpty) {
                        bookmarkHierarchy
                    } else {
                        new util.HashMap[String, AnyRef]()
                    }
                } else {
                    new util.HashMap[String, AnyRef]()
                }
            })
        } else {
            Future(new util.HashMap[String, AnyRef]())
        }
    }

    def updateLatestLeafNodes(children: util.List[util.Map[String, AnyRef]], leafNodeMap: util.Map[String, AnyRef]): List[Any] = {
        children.toList.map(content => {
            if(StringUtils.equalsIgnoreCase("Default", content.getOrDefault("visibility", "").asInstanceOf[String])) {
                val metadata: util.Map[String, AnyRef] = leafNodeMap.getOrDefault(content.get("identifier").asInstanceOf[String], new java.util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
                if(HierarchyConstants.RETIRED_STATUS.equalsIgnoreCase(metadata.getOrDefault("status", HierarchyConstants.RETIRED_STATUS).asInstanceOf[String])){
                    children.remove(content)
                } else {
                    content.putAll(metadata)
                }
            } else {
                updateLatestLeafNodes(content.getOrDefault("children", new util.ArrayList[Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]], leafNodeMap)
            }
        })
    }

    def fetchAllLeafNodes(children: util.List[util.Map[String, AnyRef]], leafNodeIds: util.List[String]): List[Any] = {
        children.toList.map(content => {
            if(StringUtils.equalsIgnoreCase("Default", content.getOrDefault("visibility", "").asInstanceOf[String])) {
                leafNodeIds.add(content.get("identifier").asInstanceOf[String])
                leafNodeIds
            } else {
                fetchAllLeafNodes(content.getOrDefault("children", new util.ArrayList[Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]], leafNodeIds)
            }
        })
    }

    def getLatestLeafNodes(leafNodeIds : util.List[String])(implicit oec: OntologyEngineContext, ec: ExecutionContext) = {
        if(CollectionUtils.isNotEmpty(leafNodeIds)) {
            val request = new Request()
            request.setContext(new util.HashMap[String, AnyRef]() {
                {
                    put(HierarchyConstants.GRAPH_ID, HierarchyConstants.TAXONOMY_ID)
                }
            })
            request.put("identifiers", leafNodeIds)
            DataNode.list(request).map(nodes => {
                val leafNodeMap: Map[String, AnyRef] = nodes.toList.map(node => (node.getIdentifier, NodeUtil.serialize(node, null, node.getObjectType.toLowerCase.replace("image", ""), HierarchyConstants.SCHEMA_VERSION, true).asInstanceOf[AnyRef])).toMap
                val imageNodeIds: util.List[String] = JavaConverters.seqAsJavaListConverter(leafNodeIds.toList.map(id => id + HierarchyConstants.IMAGE_SUFFIX)).asJava
                request.put("identifiers", imageNodeIds)
                DataNode.list(request).map(imageNodes => {
                    val imageLeafNodeMap: Map[String, AnyRef] = imageNodes.toList.map(imageNode => {
                        val identifier = imageNode.getIdentifier.replaceAll(HierarchyConstants.IMAGE_SUFFIX, "")
                        val metadata = NodeUtil.serialize(imageNode, null, imageNode.getObjectType.toLowerCase.replace("image", ""), HierarchyConstants.SCHEMA_VERSION, true)
                        metadata.replace("identifier", identifier)
                        (identifier, metadata.asInstanceOf[AnyRef])
                    }).toMap
                    val updatedMap = leafNodeMap ++ imageLeafNodeMap
                    JavaConverters.mapAsJavaMapConverter(updatedMap).asJava
                })
            }).flatMap(f => f)
        } else {
            Future{new util.HashMap[String, AnyRef]()}
        }
    }

    def isBranchingEnabled(metadata: java.util.Map[String, AnyRef], request: Request, operation: String): Boolean = {
        val isBranchingAvailable = operation match {
            case "add" => MapUtils.isNotEmpty(request.getRequest.getOrDefault(HierarchyConstants.BRANCHING_LOGIC, new util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]])
            case "remove" => MapUtils.isNotEmpty(metadata.getOrDefault(HierarchyConstants.BRANCHING_LOGIC, new java.util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]])
            case _  => false
        }
        StringUtils.equalsIgnoreCase("Yes", metadata.getOrDefault(HierarchyConstants.ALLOW_BRANCHING, "No").asInstanceOf[String]) && isBranchingAvailable
    }

    def removeBranching(identifier: String, branchingLogic: java.util.Map[String, AnyRef]): java.util.Map[String, AnyRef] = {
        if (branchingLogic.keySet().contains(identifier)) {
            val obj: java.util.Map[String, AnyRef] = branchingLogic.getOrDefault(identifier, new util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
            val source: java.util.List[String] = obj.getOrDefault(HierarchyConstants.SOURCE, new java.util.ArrayList[String]()).asInstanceOf[java.util.List[String]]
            val target: java.util.List[String] = obj.getOrDefault(HierarchyConstants.TARGET, new java.util.ArrayList[String]()).asInstanceOf[java.util.List[String]]
            val preCondition: java.util.Map[String, AnyRef] = obj.getOrDefault(HierarchyConstants.PRE_CONDITION, new util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
            if ((source.nonEmpty && preCondition.nonEmpty) && target.isEmpty) {
                val parentObj: java.util.Map[String, AnyRef] = branchingLogic.getOrDefault(source.get(0), new util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
                val pTarget = parentObj.getOrDefault(HierarchyConstants.TARGET, new java.util.ArrayList[String]()).asInstanceOf[java.util.List[String]]
                pTarget.remove(identifier)
                parentObj.put(HierarchyConstants.TARGET, pTarget)
                branchingLogic.put(source.get(0), parentObj)
                branchingLogic.remove(identifier)
            } else if (source.isEmpty && preCondition.isEmpty) {
                if (target.nonEmpty)
                    throw new ClientException("ERR_BRANCHING_LOGIC", s"Dependent Children Found! Please Remove Children With Identifiers ${target} For Node : ${identifier}")
                else branchingLogic.remove(identifier)
            }
        }
        branchingLogic
    }

    def addBranching(identifier: String, branchingLogic: java.util.Map[String, AnyRef], request: Request, childrenIds: List[String]): java.util.Map[String, AnyRef] = {
        val reqBranching: util.Map[String, AnyRef] = request.getRequest.getOrDefault(HierarchyConstants.BRANCHING_LOGIC, new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
        if (reqBranching.nonEmpty) {
            val sourceIds: List[String] = reqBranching.flatMap(entry => entry._2.asInstanceOf[util.Map[String, AnyRef]].get(HierarchyConstants.SOURCE).asInstanceOf[util.ArrayList[String]]).toList
            if (!childrenIds.containsAll(sourceIds))
                throw new ClientException("ERR_BRANCHING_LOGIC", s"Source With Identifiers ${sourceIds.diff(childrenIds).asJava} Not Found! Please Provide Valid Source Identifier.")
        }
        val updatedBranchingLogic = new util.HashMap[String, AnyRef]()
        updatedBranchingLogic.putAll(branchingLogic)
        reqBranching.map(entry => {
            val obj = entry._2.asInstanceOf[java.util.Map[String, AnyRef]]
            val source: java.util.List[String] = obj.getOrDefault(HierarchyConstants.SOURCE, new java.util.ArrayList[String]()).asInstanceOf[java.util.List[String]]
            if (source.nonEmpty && source.size > 1)
                throw new ClientException("ERR_BRANCHING_LOGIC", "An Object Can't Depend On More Than 1 Object")
            if (branchingLogic.contains(source.get(0))) {
                val parentObj = branchingLogic.getOrDefault(source.get(0), new util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
                val pSource: java.util.List[String] = parentObj.getOrDefault(HierarchyConstants.SOURCE, new java.util.ArrayList[String]()).asInstanceOf[java.util.List[String]]
                if (pSource.nonEmpty)
                    throw new ClientException("ERR_BRANCHING_LOGIC", s"${source.get(0)} Is Already Children Of ${pSource.get(0)}. So It Can't Be Parent For ${entry._1}")
                val pTarget: java.util.List[String] = parentObj.getOrDefault(HierarchyConstants.TARGET, new java.util.ArrayList[String]()).asInstanceOf[java.util.List[String]]
                pTarget.add(entry._1)
                parentObj.put(HierarchyConstants.TARGET, pTarget)
                updatedBranchingLogic.put(source.get(0), parentObj)

            } else {
                val parentObj = Map("target" -> List(entry._1).asJava, "source" -> List().asJava, "preCondition" -> Map().asJava).asJava
                updatedBranchingLogic.put(source.get(0), parentObj)
            }
            updatedBranchingLogic.put(entry._1, entry._2.asInstanceOf[java.util.Map[String, AnyRef]])

        })
        TelemetryManager.info(s"updated BranchingLogic for ${identifier} : ${updatedBranchingLogic}")
        updatedBranchingLogic
    }


}
