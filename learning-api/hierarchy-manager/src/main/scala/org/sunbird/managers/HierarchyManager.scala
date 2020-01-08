package org.sunbird.managers

import java.util
import java.util.concurrent.CompletionException

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ErrorCodes, ResponseCode}
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.nodes.DataNode
import org.sunbird.utils.{NodeUtil, ScalaJsonUtils}

import scala.collection.JavaConversions._
import scala.collection.JavaConverters
import scala.concurrent.{ExecutionContext, Future}

object HierarchyManager {

    val schemaName: String = "collection"
    val imgSuffix: String = ".img"
    val keyTobeRemoved = {
        if(Platform.config.hasPath("content.hierarchy.removed_props_for_leafNodes"))
            Platform.config.getStringList("content.hierarchy.removed_props_for_leafNodes")
        else
            java.util.Arrays.asList("collections","children","usedByContent","item_sets","methods","libraries","editorState")
    }

    @throws[Exception]
    def addLeafNodesToHierarchy(request:Request)(implicit ec: ExecutionContext): Future[Response] = {
        validateRequest(request)
        val rootNodeFuture = getRootNode(request)
        rootNodeFuture.map(rootNode => {
            val unitId = request.get("unitId").asInstanceOf[String]
            val rootNodeMap =  NodeUtil.serialize(rootNode, java.util.Arrays.asList("childNodes"), schemaName)
            if(!rootNodeMap.get("childNodes").asInstanceOf[Array[String]].toList.contains(unitId)) {
                Future{ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "unitId " + unitId + " does not exist")}
            }else {
                val hierarchyFuture = fetchHierarchy(request)
                hierarchyFuture.map(hierarchy => {
                    if(hierarchy.isEmpty){
                        Future{ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), "hierarchy is empty")}
                    } else {
                        val leafNodesFuture = fetchLeafNodes(request)
                        leafNodesFuture.map(leafNodes => {
                            val updateResponse = updateHierarchy(unitId, hierarchy, leafNodes, rootNode, request, "add")
                            updateResponse.map(response => {
                                if(!ResponseHandler.checkError(response)) {
                                    updateRootNode(rootNode, request, "add").map(node => {
                                        val resp: Response = ResponseHandler.OK
                                        resp.put("rootId", rootNode.getIdentifier)
                                        resp.put(unitId, request.get("children"))
                                        resp
                                    })
                                } else {
                                    Future { response }
                                }
                            }).flatMap(f => f)
                        }).flatMap(f => f)
                    }
                }).flatMap(f => f)
            }
        }).flatMap(f => f) recoverWith {case e: CompletionException => throw e.getCause}
    }

    @throws[Exception]
    def removeLeafNodesFromHierarchy(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        validateRequest(request)
        val rootNodeFuture = getRootNode(request)
        rootNodeFuture.map(rootNode => {
            val unitId = request.get("unitId").asInstanceOf[String]
            val rootNodeMap =  NodeUtil.serialize(rootNode, java.util.Arrays.asList("childNodes"), schemaName)
            if(!rootNodeMap.get("childNodes").asInstanceOf[Array[String]].toList.contains(unitId)) {
                Future{ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "unitId " + unitId + " does not exist")}
            }else {
                val hierarchyFuture = fetchHierarchy(request)
                hierarchyFuture.map(hierarchy => {
                    if(hierarchy.isEmpty){
                        Future{ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), "hierarchy is empty")}
                    } else {
                        val updateResponse = updateHierarchy(unitId, hierarchy, null, rootNode, request, "remove")
                        updateResponse.map(response => {
                            if(!ResponseHandler.checkError(response)) {
                                updateRootNode(rootNode, request, "remove").map(node => {
                                    val resp: Response = ResponseHandler.OK
                                    resp.put("rootId", rootNode.getIdentifier)
                                    resp
                                })
                            } else {
                                Future { response }
                            }
                        }).flatMap(f => f)
                    }
                }).flatMap(f => f)
            }
        }).flatMap(f => f) recoverWith {case e: CompletionException => throw e.getCause}
    }


    def validateRequest(request: Request)(implicit ec: ExecutionContext) = {
        val rootId = request.get("rootId").asInstanceOf[String]
        val unitId = request.get("unitId").asInstanceOf[String]
        val children = request.get("children").asInstanceOf[java.util.List[String]]

        if(StringUtils.isBlank(rootId)){
            throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "rootId is mandatory")
        }
        if(StringUtils.isBlank(unitId)){
            throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "unitId is mandatory")
        }
        if(null == children || children.isEmpty){
            throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "children are mandatory")
        }
    }

    private def getRootNode(request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        val req = new Request(request)
        req.put("identifier", request.get("rootId").asInstanceOf[String])
        req.put("mode", "edit")
        DataNode.read(req)
    }

    def fetchHierarchy(request: Request)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
        val req = new Request(request)
        req.put("identifier", request.get("rootId").asInstanceOf[String] + imgSuffix)
        val responseFuture = ExternalPropsManager.fetchProps(req, List("hierarchy"))
        responseFuture.map(response => {
            if(!ResponseHandler.checkError(response)) {
                val hierarchyString = response.getResult.toMap.getOrElse("hierarchy", "").asInstanceOf[String]
                if(!hierarchyString.isEmpty)
                    JsonUtils.deserialize(hierarchyString, classOf[java.util.Map[String, AnyRef]]).toMap
                else
                    Map[String, AnyRef]()
            } else {
                Map[String, AnyRef]()
            }
        })
    }

    def fetchLeafNodes(request: Request)(implicit ec: ExecutionContext): Future[List[Node]] =  {
        val leafNodes = request.get("children").asInstanceOf[java.util.List[String]]
        val req = new Request(request)
        req.put("identifiers", leafNodes)
        val nodes = DataNode.list(req).map(nodes => {
            if(nodes.size() != leafNodes.size()) {
                val filteredList = leafNodes.toList.filter(id => !nodes.contains(id))
                throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "Children which are not available are: " + leafNodes)
            }
            else nodes.toList
        })
        nodes
    }

    def convertNodeToMap(leafNodes: List[Node]): java.util.List[java.util.Map[String, AnyRef]] = {
        leafNodes.map(node => {
            val nodeMap:java.util.Map[String,AnyRef] = NodeUtil.serialize(node, null, schemaName)
            nodeMap.keySet().removeAll(keyTobeRemoved)
            nodeMap
        })
    }

    def addChildrenToUnit(children: java.util.List[java.util.Map[String,AnyRef]], unitId:String, leafNodes: java.util.List[java.util.Map[String, AnyRef]], leafNodeIds: java.util.List[String]): Unit = {
        val childNodes = children.filter(child => ("Parent".equalsIgnoreCase(child.get("visibility").asInstanceOf[String]) && unitId.equalsIgnoreCase(child.get("identifier").asInstanceOf[String]))).toList
        if(null != childNodes && !childNodes.isEmpty){
            val child = childNodes.get(0)
            val childList = child.get("children").asInstanceOf[java.util.List[java.util.Map[String,AnyRef]]]
            val restructuredChildren: java.util.List[java.util.Map[String,AnyRef]] = restructureUnit(childList, leafNodes, leafNodeIds, (child.get("depth").asInstanceOf[Integer] + 1), unitId)
            child.put("children", restructuredChildren)
        } else {
            for(child <- children) {
                if(null !=child.get("children") && !child.get("children").asInstanceOf[java.util.List[java.util.Map[String,AnyRef]]].isEmpty)
                    addChildrenToUnit(child.get("children").asInstanceOf[java.util.List[java.util.Map[String,AnyRef]]], unitId, leafNodes, leafNodeIds)
            }
        }
    }

    def removeChildrenFromUnit(children: java.util.List[java.util.Map[String, AnyRef]], unitId: String, leafNodeIds: java.util.List[String]):Unit = {
        val childNodes = children.filter(child => ("Parent".equalsIgnoreCase(child.get("visibility").asInstanceOf[String]) && unitId.equalsIgnoreCase(child.get("identifier").asInstanceOf[String]))).toList
        if(null != childNodes && !childNodes.isEmpty){
            val child = childNodes.get(0)
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

    def updateRootNode(rootNode: Node, request: Request, operation: String)(implicit ec: ExecutionContext) = {
        val req = new Request(request)
        val leafNodes = request.get("children").asInstanceOf[java.util.List[String]]
        var childNodes = new java.util.ArrayList[String]()
        childNodes.addAll(rootNode.getMetadata.get("childNodes").asInstanceOf[Array[String]].toList)
        if(operation.equalsIgnoreCase("add"))
            childNodes.addAll(leafNodes)
        if(operation.equalsIgnoreCase("remove"))
            childNodes.removeAll(leafNodes)
        req.put("childNodes", childNodes.distinct.toArray)
        req.getContext.put("identifier", rootNode.getIdentifier.replaceAll(imgSuffix, ""))
        req.getContext.put("skipValidation", java.lang.Boolean.TRUE)
        DataNode.update(req)
    }

    def updateHierarchy(unitId: String, hierarchy: java.util.Map[String, AnyRef], leafNodes: List[Node], rootNode: Node, request: Request, operation: String)(implicit ec: ExecutionContext) = {
        val children =  hierarchy.get("children").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        val leafNodeIds = request.get("children").asInstanceOf[java.util.List[String]]
        if("add".equalsIgnoreCase(operation)){
            val leafNodesMap:java.util.List[java.util.Map[String, AnyRef]] = convertNodeToMap(leafNodes)
            addChildrenToUnit(children, unitId, leafNodesMap, leafNodeIds)
        }
        if("remove".equalsIgnoreCase(operation)) {
            removeChildrenFromUnit(children,unitId, leafNodeIds)
        }
        val updatedHierarchy = new java.util.HashMap[String, AnyRef]()
        updatedHierarchy.putAll(hierarchy)
        updatedHierarchy.put("children", children)
        val req = new Request(request)
        req.put("hierarchy", ScalaJsonUtils.serialize(updatedHierarchy))
        req.put("identifier", rootNode.getIdentifier.replaceAll(imgSuffix, "") + imgSuffix)
        ExternalPropsManager.saveProps(req)
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
            var node = leafNodeMap.get(id)
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

}
