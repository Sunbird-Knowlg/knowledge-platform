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
import scala.collection.JavaConverters
import scala.collection.JavaConverters.asJavaIterableConverter
import scala.concurrent.{ExecutionContext, Future}
import com.mashape.unirest.http.HttpResponse
import com.mashape.unirest.http.Unirest
import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.schema.{DefinitionNode, ObjectCategoryDefinition}
import org.sunbird.utils.{HierarchyBackwardCompatibilityUtil, HierarchyConstants, HierarchyErrorCodes}

object HierarchyManager {

    val schemaName: String = "collection"
    val schemaVersion: String = "1.0"
    val imgSuffix: String = ".img"
    val hierarchyPrefix: String = "hierarchy_"
    val statusList = List("Live", "Unlisted", "Flagged")

    val keyTobeRemoved = {
        if(Platform.config.hasPath("content.hierarchy.removed_props_for_leafNodes"))
            Platform.config.getStringList("content.hierarchy.removed_props_for_leafNodes")
        else
            java.util.Arrays.asList("collections","children","usedByContent","item_sets","methods","libraries","editorState")
    }

    val mapPrimaryCategoriesEnabled: Boolean = if (Platform.config.hasPath("collection.primarycategories.mapping.enabled")) Platform.config.getBoolean("collection.primarycategories.mapping.enabled") else true
    val objectTypeAsContentEnabled: Boolean = if (Platform.config.hasPath("objecttype.as.content.enabled")) Platform.config.getBoolean("objecttype.as.content.enabled") else true

    @throws[Exception]
    def addLeafNodesToHierarchy(request:Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
        validateRequest(request)
        val rootNodeFuture = getRootNode(request)
        rootNodeFuture.map(rootNode => {
            val unitId = request.get("unitId").asInstanceOf[String]
            val rootNodeMap =  NodeUtil.serialize(rootNode, java.util.Arrays.asList("childNodes", "originData"), schemaName, schemaVersion)
            validateShallowCopied(rootNodeMap, "add", rootNode.getIdentifier.replaceAll(imgSuffix, ""))
            if(!rootNodeMap.get("childNodes").asInstanceOf[Array[String]].toList.contains(unitId)) {
                Future{ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "unitId " + unitId + " does not exist")}
            }else {
                val hierarchyFuture = fetchHierarchy(request, rootNode.getIdentifier)
                hierarchyFuture.map(hierarchy => {
                    if(hierarchy.isEmpty){
                        Future{ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), "hierarchy is empty")}
                    } else {
                        val leafNodesFuture = fetchLeafNodes(request)
                        leafNodesFuture.map(leafNodes => {
                            updateRootNode(rootNode, request, "add").map(node => {
                                val updateResponse = updateHierarchy(unitId, hierarchy, leafNodes, node, request, "add")
                                updateResponse.map(response => {
                                    if(!ResponseHandler.checkError(response)) {
                                            ResponseHandler.OK
                                                .put("rootId", node.getIdentifier.replaceAll(imgSuffix, ""))
                                                .put(unitId, request.get("children"))
                                    }else {
                                        response 
                                    }
                                })
                            }).flatMap(f => f)
                        }).flatMap(f => f)
                    }
                }).flatMap(f => f)
            }
        }).flatMap(f => f) recoverWith {case e: CompletionException => throw e.getCause}
    }

    @throws[Exception]
    def removeLeafNodesFromHierarchy(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
        validateRequest(request)
        val rootNodeFuture = getRootNode(request)
        rootNodeFuture.map(rootNode => {
            val unitId = request.get("unitId").asInstanceOf[String]
            val rootNodeMap =  NodeUtil.serialize(rootNode, java.util.Arrays.asList("childNodes", "originData"), schemaName, schemaVersion)
            validateShallowCopied(rootNodeMap, "remove", rootNode.getIdentifier.replaceAll(imgSuffix, ""))
            if(!rootNodeMap.get("childNodes").asInstanceOf[Array[String]].toList.contains(unitId)) {
                Future{ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "unitId " + unitId + " does not exist")}
            }else {
                val hierarchyFuture = fetchHierarchy(request, rootNode.getIdentifier)
                hierarchyFuture.map(hierarchy => {
                    if(hierarchy.isEmpty){
                        Future{ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), "hierarchy is empty")}
                    } else {
                        updateRootNode(rootNode, request, "remove").map(node =>{
                            val updateResponse = updateHierarchy(unitId, hierarchy, null, node, request, "remove")
                            updateResponse.map(response => {
                                if(!ResponseHandler.checkError(response)) {
                                    ResponseHandler.OK.put("rootId", node.getIdentifier.replaceAll(imgSuffix, ""))
                                } else {
                                    response
                                }
                            })
                        }).flatMap(f => f)
                    }
                }).flatMap(f => f)
            }
        }).flatMap(f => f) recoverWith {case e: CompletionException => throw e.getCause}
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
            //TODO: Remove content Mapping for backward compatibility
            HierarchyBackwardCompatibilityUtil.setContentAndCategoryTypes(metadata)
            hierarchy.map(hierarchy => {
                val children = hierarchy.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.ArrayList[java.util.Map[String, AnyRef]]]
                //TODO: Remove content Mapping for backward compatibility
                updateContentMappingInChildren(children)
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
                        ResponseHandler.OK.put("content", metadata)
                    }
                })
            }).flatMap(f => f)
        }).flatMap(f => f) recoverWith { case e: ResourceNotFoundException => {
                val searchResponse = searchRootIdInElasticSearch(request.get("rootId").asInstanceOf[String])
                searchResponse.map(rootHierarchy => {
                    if(!rootHierarchy.isEmpty && StringUtils.isNotEmpty(rootHierarchy.asInstanceOf[util.HashMap[String, AnyRef]].get("identifier").asInstanceOf[String])){
                        //TODO: Remove content Mapping for backward compatibility
                        HierarchyBackwardCompatibilityUtil.setContentAndCategoryTypes(rootHierarchy.asInstanceOf[util.HashMap[String, AnyRef]])
                        val unPublishedBookmarkHierarchy = getUnpublishedBookmarkHierarchy(request, rootHierarchy.asInstanceOf[util.HashMap[String, AnyRef]].get("identifier").asInstanceOf[String])
                        unPublishedBookmarkHierarchy.map(hierarchy => {
                            if (!hierarchy.isEmpty) {
                                val children = hierarchy.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.ArrayList[java.util.Map[String, AnyRef]]]
                                //TODO: Remove content Mapping for backward compatibility
                                updateContentMappingInChildren(children)
                                val leafNodeIds = new util.ArrayList[String]()
                                fetchAllLeafNodes(children, leafNodeIds)
                                getLatestLeafNodes(leafNodeIds).map(leafNodesMap => {
                                    updateLatestLeafNodes(children, leafNodesMap)
                                    hierarchy.put("children", children)
                                })
                                ResponseHandler.OK.put("content", hierarchy)
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
        val redisHierarchy = RedisCache.get(hierarchyPrefix + request.get("rootId"))
        val hierarchyFuture = if (StringUtils.isNotEmpty(redisHierarchy)) {
            Future(mapAsJavaMap(Map("content" -> JsonUtils.deserialize(redisHierarchy, classOf[java.util.Map[String, AnyRef]]))))
        } else getCassandraHierarchy(request)
        hierarchyFuture.map(result => {
            if (!result.isEmpty) {
                val bookmarkId = request.get("bookmarkId").asInstanceOf[String]
                val rootHierarchy  = result.get("content").asInstanceOf[util.Map[String, AnyRef]]
                if (StringUtils.isEmpty(bookmarkId)) {
                    ResponseHandler.OK.put("content", rootHierarchy)
                } else {
                    val children = rootHierarchy.getOrElse("children", new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]]
                    val bookmarkHierarchy = filterBookmarkHierarchy(children, bookmarkId)
                    if (MapUtils.isEmpty(bookmarkHierarchy)) {
                        ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "bookmarkId " + bookmarkId + " does not exist")
                    } else {
                        ResponseHandler.OK.put("content", bookmarkHierarchy)
                    }
                }
            } else
                ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "rootId " + request.get("rootId") + " does not exist")
        })
    }

    def validateRequest(request: Request)(implicit ec: ExecutionContext) = {
        val rootId = request.get("rootId").asInstanceOf[String]
        val unitId = request.get("unitId").asInstanceOf[String]
        val children = request.get("children").asInstanceOf[java.util.List[String]]

        if (StringUtils.isBlank(rootId)) {
            throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "rootId is mandatory")
        }
        if (StringUtils.isBlank(unitId)) {
            throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "unitId is mandatory")
        }
        if (null == children || children.isEmpty) {
            throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "children are mandatory")
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
        val nodes = DataNode.list(req).map(nodes => {
            if(nodes.size() != leafNodes.size()) {
                val filteredList = leafNodes.toList.filter(id => !nodes.contains(id))
                throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "Children which are not available are: " + leafNodes)
            }
            else nodes.toList
        })
        nodes
    }

    def convertNodeToMap(leafNodes: List[Node])(implicit oec: OntologyEngineContext, ec: ExecutionContext): java.util.List[java.util.Map[String, AnyRef]] = {
        leafNodes.map(node => {
            val nodeMap:java.util.Map[String,AnyRef] = NodeUtil.serialize(node, null, node.getObjectType.toLowerCase().replace("image", ""), schemaVersion)
            nodeMap.keySet().removeAll(keyTobeRemoved)
            nodeMap
        })
    }

    def addChildrenToUnit(children: java.util.List[java.util.Map[String,AnyRef]], unitId:String, leafNodes: java.util.List[java.util.Map[String, AnyRef]], leafNodeIds: java.util.List[String], request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Unit = {
        val childNodes = children.filter(child => ("Parent".equalsIgnoreCase(child.get("visibility").asInstanceOf[String]) && unitId.equalsIgnoreCase(child.get("identifier").asInstanceOf[String]))).toList
        if(null != childNodes && !childNodes.isEmpty){
            val child = childNodes.get(0)
            leafNodes.toList.map(leafNode => validateLeafNodes(child, leafNode, request))
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
                req.put("identifier", identifier.replaceAll(".img", "") + ".img")
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
                    //TODO: Remove mapping
                    val hierarchyMap = mapPrimaryCategories(hierarchy)
                    rootHierarchy.put("content", hierarchyMap)
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
                                            //TODO: Remove mapping
                                            val hierarchyMap = mapPrimaryCategories(bookmarkHierarchy)
                                            rootHierarchy.put("content", hierarchyMap)
                                            RedisCache.set(hierarchyPrefix + request.get("rootId"), JsonUtils.serialize(hierarchyMap))
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

    def getUnpublishedBookmarkHierarchy(request: Request, identifier: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[util.Map[String, AnyRef]] = {
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

    def validateShallowCopied(rootNodeMap: util.Map[String, AnyRef], operation: String, identifier: String) = {
        val originData = rootNodeMap.getOrDefault("originData", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
        if (StringUtils.equalsIgnoreCase(originData.getOrElse("copyType", "").asInstanceOf[String], HierarchyConstants.COPY_TYPE_SHALLOW)) {
            operation match {
                case "add"=> throw new ClientException(HierarchyErrorCodes.ERR_ADD_HIERARCHY_DENIED, "Add Hierarchy is not allowed for partially (shallow) copied content : " + identifier)
                case "remove"=> throw new ClientException(HierarchyErrorCodes.ERR_REMOVE_HIERARCHY_DENIED, "Remove Hierarchy is not allowed for partially (shallow) copied content : " + identifier)
            }

        }
    }

    def updateLatestLeafNodes(children: util.List[util.Map[String, AnyRef]], leafNodeMap: util.Map[String, AnyRef]): List[Any] = {
        children.toList.map(content => {
            if(StringUtils.equalsIgnoreCase("Default", content.getOrDefault("visibility", "").asInstanceOf[String])) {
                val metadata: util.Map[String, AnyRef] = leafNodeMap.getOrDefault(content.get("identifier").asInstanceOf[String], new java.util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
                if(HierarchyConstants.RETIRED_STATUS.equalsIgnoreCase(metadata.getOrDefault("status", HierarchyConstants.RETIRED_STATUS).asInstanceOf[String])){
                    children.remove(content)
                } else {
                    if (objectTypeAsContentEnabled)
                        HierarchyBackwardCompatibilityUtil.setObjectTypeForRead(metadata, metadata.get("objectType").asInstanceOf[String])
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
                    //val imageLeafNodeMap: Map[String, AnyRef] = imageNodes.toList.map(imageNode => (imageNode.getIdentifier.replaceAll(HierarchyConstants.IMAGE_SUFFIX, ""), NodeUtil.serialize(imageNode, null, HierarchyConstants.CONTENT_SCHEMA_NAME, HierarchyConstants.SCHEMA_VERSION, true).asInstanceOf[AnyRef])).toMap
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

    def updateContentMappingInChildren(children: util.List[util.Map[String, AnyRef]]): List[Any] = {
        children.toList.map(content => {
            if (mapPrimaryCategoriesEnabled)
                HierarchyBackwardCompatibilityUtil.setContentAndCategoryTypes(content, content.get("objectType").asInstanceOf[String])
            if (objectTypeAsContentEnabled)
                HierarchyBackwardCompatibilityUtil.setObjectTypeForRead(content, content.get("objectType").asInstanceOf[String])
            updateContentMappingInChildren(content.getOrDefault("children", new util.ArrayList[Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]])
        })
    }

    private def mapPrimaryCategories(hierarchy: java.util.Map[String, AnyRef]):util.Map[String, AnyRef] = {
        val updatedHierarchy = new util.HashMap[String, AnyRef](hierarchy)
        if (mapPrimaryCategoriesEnabled)
            HierarchyBackwardCompatibilityUtil.setContentAndCategoryTypes(updatedHierarchy)
        if (objectTypeAsContentEnabled)
            HierarchyBackwardCompatibilityUtil.setObjectTypeForRead(updatedHierarchy, updatedHierarchy.get("objectType").asInstanceOf[String])
        val children = new util.HashMap[String, AnyRef](hierarchy).getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.ArrayList[java.util.Map[String, AnyRef]]]
        updateContentMappingInChildren(children)
        updatedHierarchy
    }

    def validateLeafNodes(parentNode: java.util.Map[String, AnyRef], childNode: java.util.Map[String, AnyRef], request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext) = {
        val primaryCategory = parentNode.getOrDefault("primaryCategory", "").asInstanceOf[String]
        val channel: String = parentNode.getOrDefault("channel", "all").asInstanceOf[String]
        //val objectCategoryDefinition: ObjectCategoryDefinition = DefinitionNode.getObjectCategoryDefinition(primaryCategory, parentNode.getOrDefault("objectType", "").asInstanceOf[String].toLowerCase(), channel)
        //val outRelations = DefinitionNode.getOutRelations(HierarchyConstants.GRAPH_ID, "1.0", parentNode.getOrDefault("objectType", "").asInstanceOf[String].toLowerCase().replace("image", ""), objectCategoryDefinition)

        val objectCategoryDefinition: ObjectCategoryDefinition = DefinitionNode.getObjectCategoryDefinition(primaryCategory, request.getContext.get(HierarchyConstants.SCHEMA_NAME).asInstanceOf[String].toLowerCase(), channel)
        val outRelations = DefinitionNode.getOutRelations(HierarchyConstants.GRAPH_ID, "1.0", request.getContext.get(HierarchyConstants.SCHEMA_NAME).asInstanceOf[String].toLowerCase(), objectCategoryDefinition)

        val configObjTypes: List[String] = outRelations.find(_.keySet.contains("children")).orNull.getOrElse("children", Map()).asInstanceOf[java.util.Map[String, AnyRef]].getOrElse("objects", new util.ArrayList[String]()).asInstanceOf[java.util.List[String]].toList
        if(configObjTypes.nonEmpty && !configObjTypes.contains(childNode.getOrDefault("objectType", "").asInstanceOf[String]))
            throw new ClientException("ERR_INVALID_CHILDREN", "Invalid Children objectType "+childNode.get("objectType")+" found for : "+childNode.get("identifier") + "| Please provide children having one of the objectType from "+ configObjTypes.asJava)
    }
}
