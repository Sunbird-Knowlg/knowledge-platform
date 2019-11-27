package org.sunbird.managers

import java.util
import java.util.concurrent.CompletionException

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.JsonUtils
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ErrorCodes, ResponseCode}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.nodes.DataNode
import org.sunbird.utils.NodeUtil

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

object HierarchyManager {

    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    val schemaName: String = "collection"
    val imgSuffix: String = ".img"

    @throws[Exception]
    def addLeafNodesToHierarchy(request:Request)(implicit ec: ExecutionContext): Future[Response] = {
        validateRequest(request)
        val rootNodeFuture = getRootNode(request)
        rootNodeFuture.map(rootNode => {
            val unitId = request.get("unitId").asInstanceOf[String]
            val rootNodeMap =  NodeUtil.serialize(rootNode, util.Arrays.asList("childNodes"))
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
                            val updateResponse = updateHierarchy(unitId, hierarchy, leafNodes, rootNode, request)
                            updateResponse.map(response => {
                                if(!ResponseHandler.checkError(response)) {
                                    updateChildNodes(rootNode, request, "add").map(node => {
                                        val resp: Response = ResponseHandler.OK
                                        resp.put("rootId", rootNode.getIdentifier)
                                        resp.put(unitId, node.getMetadata.get("childNodes"))
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
            val rootNodeMap =  NodeUtil.serialize(rootNode, util.Arrays.asList("childNodes"))
            if(!rootNodeMap.get("childNodes").asInstanceOf[Array[String]].toList.contains(unitId)) {
                Future{ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "unitId " + unitId + " does not exist")}
            }else {
                val hierarchyFuture = fetchHierarchy(request)
                hierarchyFuture.map(hierarchy => {
                    if(hierarchy.isEmpty){
                        Future{ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), "hierarchy is empty")}
                    } else {
                        val updateResponse = removeLeafNodes(unitId, hierarchy, request)
                        updateResponse.map(response => {
                            if(!ResponseHandler.checkError(response)) {
                                updateChildNodes(rootNode, request, "remove").map(node => {
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
        val leafNodes = request.get("leafNodes").asInstanceOf[java.util.List[String]]

        if(StringUtils.isBlank(rootId)){
            throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "rootId is mandatory")
        }
        if(StringUtils.isBlank(unitId)){
            throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "unitId is mandatory")
        }
        if(null == leafNodes || leafNodes.isEmpty){
            throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "leafNodes are mandatory")
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

    def fetchLeafNodes(request: Request)(implicit ec: ExecutionContext):Future[List[Node]] =  {
        val leafNodes = request.get("leafNodes").asInstanceOf[java.util.List[String]]
        val req = new Request(request)
        req.put("identifiers", leafNodes)
        val nodes = DataNode.list(req).map(nodes => {
            if(nodes.size() != leafNodes.size()) {
                val filteredList = leafNodes.toList.filter(id => !nodes.contains(id))
                throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "LeafNodes which are not available are: " + leafNodes)
            }
            else nodes.toList
        })
        nodes
    }

    def updateHierarchy(unitId: String, hierarchy: util.Map[String, AnyRef], leafNodes: List[Node], rootNode: Node, request: Request)(implicit ec: ExecutionContext) = {
        val leafNodesMap:util.List[util.Map[String, AnyRef]] = convertNodeToMap(leafNodes)
        val children =  hierarchy.get("children").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        val leafNodeIds = request.get("leafNodes").asInstanceOf[java.util.List[String]]
        populateUnit(children, unitId, leafNodesMap, leafNodeIds)
        val updatedHierarchy = new util.HashMap[String, AnyRef]()
        updatedHierarchy.putAll(hierarchy)
        updatedHierarchy.put("children", children)
        val req = new Request(request)
        req.put("hierarchy", mapper.writeValueAsString(updatedHierarchy))
        req.put("identifier", rootNode.getIdentifier.replaceAll(imgSuffix, "") + imgSuffix)
        ExternalPropsManager.saveProps(req)
    }

    def convertNodeToMap(leafNodes: List[Node]): util.List[util.Map[String, AnyRef]] = {
        leafNodes.map(node => {
            val nodeMap = NodeUtil.serialize(node, null)
            nodeMap.remove("collections")
            nodeMap.remove("children")
            nodeMap.remove("usedByContent")
            nodeMap.remove("item_sets")
            nodeMap.remove("methods")
            nodeMap.remove("libraries")
            nodeMap.remove("editorState")
            nodeMap
        })
    }

    def populateUnit(children: util.List[util.Map[String,AnyRef]], unitId:String, leafNodes: util.List[util.Map[String, AnyRef]], leafNodeIds: util.List[String]): Unit = {
        val childNodes = children.filter(child => ("Parent".equalsIgnoreCase(child.get("visibility").asInstanceOf[String]) && unitId.equalsIgnoreCase(child.get("identifier").asInstanceOf[String]))).toList
        if(null != childNodes && !childNodes.isEmpty){
            val child = childNodes.get(0)
            var filteredLeafNodes = child.get("children").asInstanceOf[java.util.List[util.Map[String,AnyRef]]].filter(existingLeafNode => {
                !leafNodeIds.contains(existingLeafNode.get("identifier").asInstanceOf[String])
            })
            if(null == filteredLeafNodes) filteredLeafNodes = new util.ArrayList[util.Map[String,AnyRef]]()
            filteredLeafNodes.addAll(leafNodes)
            child.put("children", filteredLeafNodes)
        } else {
            for(child <- children) {
                if(!child.get("children").asInstanceOf[java.util.List[util.Map[String,AnyRef]]].isEmpty)
                    populateUnit(child.get("children").asInstanceOf[java.util.List[util.Map[String,AnyRef]]], unitId, leafNodes, leafNodeIds)
            }
        }
    }

    def updateChildNodes(rootNode: Node, request: Request, operation: String)(implicit ec: ExecutionContext) = {
        val req = new Request(request)
        val leafNodes = request.get("leafNodes").asInstanceOf[java.util.List[String]]
        var childNodes = new util.ArrayList[String]()
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

    def removeLeafNodes(unitId: String, hierarchy: Map[String, AnyRef], request: Request)(implicit ec: ExecutionContext) = {
        val children =  hierarchy.get("children").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        val leafNodeIds = request.get("leafNodes").asInstanceOf[java.util.List[String]]
        val rootId = request.get("rootId").asInstanceOf[String]
        removeLeafNodesFromUnit(children, unitId, leafNodeIds)
        val updatedHierarchy = new util.HashMap[String, AnyRef]()
        updatedHierarchy.putAll(hierarchy)
        updatedHierarchy.put("children", children)
        val req = new Request(request)
        req.put("hierarchy", mapper.writeValueAsString(updatedHierarchy))
        req.put("identifier", rootId.replaceAll(imgSuffix, "") + imgSuffix)
        ExternalPropsManager.saveProps(req)
    }

    def removeLeafNodesFromUnit(children: util.List[util.Map[String, AnyRef]], unitId: String, leafNodeIds: util.List[String]):Unit = {
        val childNodes = children.filter(child => ("Parent".equalsIgnoreCase(child.get("visibility").asInstanceOf[String]) && unitId.equalsIgnoreCase(child.get("identifier").asInstanceOf[String]))).toList
        if(null != childNodes && !childNodes.isEmpty){
            val child = childNodes.get(0)
            var filteredLeafNodes = child.get("children").asInstanceOf[java.util.List[util.Map[String,AnyRef]]].filter(existingLeafNode => {
                !leafNodeIds.contains(existingLeafNode.get("identifier").asInstanceOf[String])
            })
            child.put("children", filteredLeafNodes)
        } else {
            for(child <- children) {
                if(!child.get("children").asInstanceOf[java.util.List[util.Map[String,AnyRef]]].isEmpty)
                    removeLeafNodesFromUnit(child.get("children").asInstanceOf[java.util.List[util.Map[String,AnyRef]]], unitId, leafNodeIds)
            }
        }
    }
}
