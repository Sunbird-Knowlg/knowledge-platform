package org.sunbird.content.dial

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.Platform
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception._
import org.sunbird.content.util.ContentConstants
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.ScalaJsonUtils
import org.sunbird.managers.HierarchyManager

import java.util
import scala.collection.JavaConverters._
import scala.collection.immutable.{HashMap, Map}
import scala.concurrent.{ExecutionContext, Future}


object DIALManager {

	val DIAL_SEARCH_API_URL: String = Platform.config.getString("dial_service.api.base_url") + Platform.config.getString("dial_service.api.search")
	val DIAL_API_AUTH_KEY: String = "Bearer " + Platform.config.getString("dial_service.api.auth_key")
	val PASSPORT_KEY: String = Platform.config.getString("graph.passport.key.base")

	def link(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		val linkType: String = request.getContext.getOrDefault(DIALConstants.LINK_TYPE, DIALConstants.CONTENT).asInstanceOf[String]
		val channelId: String = request.getContext.getOrDefault(DIALConstants.CHANNEL, "").asInstanceOf[String]
		val objectId: String = request.getContext.getOrDefault(DIALConstants.IDENTIFIER, "").asInstanceOf[String]
		val reqList: List[Map[String, List[String]]] = getRequestData(request)
		val requestMap: Map[String, List[String]] = validateAndGetRequestMap(channelId, reqList)
		linkType match {
			case DIALConstants.CONTENT => linkContent(requestMap, request.getContext)
			case DIALConstants.COLLECTION => linkCollection(objectId, requestMap, request.getContext)
			case _ => throw new ClientException(DIALErrors.ERR_DIALCODE_LINK_REQUEST, DIALErrors.ERR_INVALID_REQ_MSG)
		}
	}

	def getRequestData(request: Request): List[Map[String, List[String]]] = {
		val req = request.getRequest.get(DIALConstants.CONTENT)
		req match {
			case req: util.List[util.Map[String, AnyRef]] => req.asScala.toList.map(obj => obj.asScala.toMap.map(x => (x._1, getList(x._2))))
			case req: util.Map[String, AnyRef] => List(req.asScala.toMap.map(x => (x._1, getList(x._2))))
			case _ => throw new ClientException(DIALErrors.ERR_DIALCODE_LINK_REQUEST, DIALErrors.ERR_INVALID_REQ_MSG)
		}
	}

	def getList(obj: AnyRef): List[String] = {
		(obj match {
			case obj: util.List[String] => obj.asScala.toList.distinct
			case obj: String => List(obj).distinct
			case _ => List.empty
		}).filter((x: String) => StringUtils.isNotBlank(x) && !StringUtils.equals(" ", x))
	}

	def validateAndGetRequestMap(channelId: String, requestList: List[Map[String, List[String]]])(implicit oec:OntologyEngineContext): Map[String, List[String]] = {
		var reqMap = HashMap[String, List[String]]()
		requestList.foreach(req => {
			val contents: List[String] = req(DIALConstants.IDENTIFIER)
			val dialcodes: List[String] = req(DIALConstants.DIALCODE)
			validateReqStructure(dialcodes, contents)
			contents.foreach(id => reqMap += (id -> dialcodes))
		})
		if (Platform.getBoolean("content.link_dialcode.validation", true)) {
			val dials = requestList.collect { case m if m.contains(DIALConstants.DIALCODE) => m(DIALConstants.DIALCODE) }.flatten
			validateDialCodes(channelId, dials)
		}
		reqMap
	}

	def validateReqStructure(dialcodes: List[String], contents: List[String]): Unit = {
		if (null == dialcodes || null == contents || contents.isEmpty)
			throw new ClientException(DIALErrors.ERR_DIALCODE_LINK_REQUEST, DIALErrors.ERR_REQUIRED_PROPS_MSG)
		val maxLimit: Int = Platform.getInteger("content.link_dialcode.max_limit", 10)
		if (dialcodes.size >= maxLimit || contents.size >= maxLimit)
			throw new ClientException(DIALErrors.ERR_DIALCODE_LINK_REQUEST, DIALErrors.ERR_MAX_LIMIT_MSG + maxLimit)
	}

	def validateDialCodes(channelId: String, dialcodes: List[String])(implicit oec: OntologyEngineContext): Boolean = {
		if (dialcodes.nonEmpty) {
			val reqMap = new util.HashMap[String, AnyRef]() {{
				put(DIALConstants.REQUEST, new util.HashMap[String, AnyRef]() {{
					put(DIALConstants.SEARCH, new util.HashMap[String, AnyRef]() {{
						put(DIALConstants.IDENTIFIER, dialcodes.distinct.asJava)
					}})
				}})
			}}
			val headerParam = new util.HashMap[String, String]{put(DIALConstants.X_CHANNEL_ID, channelId); put(DIALConstants.AUTHORIZATION, DIAL_API_AUTH_KEY);}

			val searchResponse = oec.httpUtil.post(DIAL_SEARCH_API_URL, reqMap, headerParam)
			if (searchResponse.getResponseCode.toString == "OK") {
				val result = searchResponse.getResult
				if (dialcodes.distinct.size == result.get(DIALConstants.COUNT).asInstanceOf[Integer]) {
					return true
				} else {
					val dials = result.get(DIALConstants.DIALCODES).asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala.toList.map(obj => obj.asScala.toMap).map(_.getOrElse(DIALConstants.IDENTIFIER, "")).asInstanceOf[List[String]]
					throw new ResourceNotFoundException(DIALErrors.ERR_DIALCODE_LINK, DIALErrors.ERR_DIAL_NOT_FOUND_MSG + dialcodes.distinct.diff(dials).asJava)
				}
			}
			else throw new ServerException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name, DIALErrors.ERR_SERVER_ERROR_MSG)
		}
		true
	}

	def linkContent(requestMap: Map[String, List[String]], reqContext: util.Map[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		validateContents(requestMap, reqContext).map(result => {
			val futureList: List[Future[Node]] = requestMap.filter(x => !result.contains(x._1)).map(map => {
				val updateReqMap = new util.HashMap[String, AnyRef]() {{
					val dials: util.List[String] = if (map._2.nonEmpty) map._2.asJava else new util.ArrayList[String]()
					put(DIALConstants.DIALCODES, dials)
					put(DIALConstants.VERSION_KEY, PASSPORT_KEY)
				}}
				val updateRequest = new Request()
				reqContext.put(DIALConstants.IDENTIFIER, map._1)
				updateRequest.setContext(reqContext)
				updateRequest.putAll(updateReqMap)
				DataNode.update(updateRequest)
			}).toList
			val updatedNodes: Future[List[Node]] = Future.sequence(futureList)
			getResponse(requestMap, updatedNodes, result)
		}).flatMap(f => f)
	}

	def linkCollection(objectId: String, requestMap: Map[String, List[String]], reqContext: util.Map[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		val request = new Request()
		request.setContext(reqContext)
		request.getContext.put(ContentConstants.SCHEMA_NAME, ContentConstants.COLLECTION_SCHEMA_NAME)
		request.getContext.put(ContentConstants.VERSION, ContentConstants.SCHEMA_VERSION)
		request.put(ContentConstants.ROOT_ID, objectId)
		request.put(ContentConstants.MODE, "edit")

		val req = new Request(request)
		req.put("identifier", request.get("rootId").asInstanceOf[String])
		req.put("mode", request.get("mode").asInstanceOf[String])
		DataNode.read(req).flatMap(rootNode => {
			HierarchyManager.getHierarchy(request).flatMap(getHierarchyResponse => {
				val collectionHierarchy = getHierarchyResponse.getResult.getOrDefault(ContentConstants.CONTENT, new java.util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
				val childrenHierarchy = collectionHierarchy.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]]
				val updatedChildrenHierarchy = updateChildrenHierarchy(childrenHierarchy, requestMap)
				val childrenDIALMap = getChildrenDIALMap(updatedChildrenHierarchy, requestMap)
				val consolidatedUnitDIALMap = if (!requestMap.contains(objectId)) childrenDIALMap else childrenDIALMap ++ Map(objectId -> requestMap(objectId))

				validateDuplicateDIALCodes(consolidatedUnitDIALMap.filter(rec => rec._2.asInstanceOf[List[String]].nonEmpty))

				val updatedHierarchy = new java.util.HashMap[String, AnyRef]()
				updatedHierarchy.put("identifier", objectId)
				updatedHierarchy.put("children", updatedChildrenHierarchy.asJava)

				val hierarchyReq = new Request(request)
				hierarchyReq.put("hierarchy", ScalaJsonUtils.serialize(updatedHierarchy))
				hierarchyReq.put("identifier", rootNode.getIdentifier)
				oec.graphService.saveExternalProps(hierarchyReq).flatMap(rec => if(requestMap.contains(objectId)) {
					val updateReq = new Request(request)
					updateReq.put("identifier", rootNode.getIdentifier)
					val rootNodeMetadata = rootNode.getMetadata
					rootNodeMetadata.remove("discussionForum")
					rootNodeMetadata.remove("credentials")
					rootNodeMetadata.remove("trackable")

					if(rootNodeMetadata.containsKey("dialcodes"))
						rootNodeMetadata.remove("dialcodes")

					if(requestMap(objectId).isEmpty)
						updateReq.put(DIALConstants.DIALCODES, null)
					else
						updateReq.put(DIALConstants.DIALCODES, requestMap(objectId).toArray[String])

					updateReq.getRequest.putAll(rootNodeMetadata)

					DataNode.update(updateReq).flatMap(response => {
						getResponseCollectionLink(requestMap, consolidatedUnitDIALMap.keySet.toList, requestMap.keySet.diff(consolidatedUnitDIALMap.keySet).toList)
					})
				} else {
					getResponseCollectionLink(requestMap, consolidatedUnitDIALMap.keySet.toList, requestMap.keySet.diff(consolidatedUnitDIALMap.keySet).toList)
				})
			})
		})
	}

	def validateContents(requestMap: Map[String, List[String]], reqContext: util.Map[String, AnyRef])(implicit ec: ExecutionContext, oec:OntologyEngineContext): Future[List[String]] = {
		val request = new Request()
		request.setContext(reqContext)
		request.put(DIALConstants.IDENTIFIERS, requestMap.keys.toList.asJava)
		DataNode.list(request).map(obj => {
			if (null != obj && !obj.isEmpty) {
				val identifiers = obj.asScala.collect { case node if null != node => node.getIdentifier }.toList
				Future {
					requestMap.keys.toList.diff(identifiers)
				}
			} else throw new ResourceNotFoundException(DIALErrors.ERR_DIALCODE_LINK, DIALErrors.ERR_CONTENT_NOT_FOUND_MSG + requestMap.keySet.asJava)
		}).flatMap(f => f)
	}

	def getResponse(requestMap: Map[String, List[String]], updatedNodes: Future[List[Node]], invalidIds: List[String])(implicit ec: ExecutionContext): Future[Response] = {
		updatedNodes.map(obj => {
			val successIds = obj.collect { case node if null != node => node.getIdentifier }
			if (requestMap.keySet.size == successIds.size)
				ResponseHandler.OK
			else if (invalidIds.nonEmpty && successIds.isEmpty)
				ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, DIALErrors.ERR_DIALCODE_LINK, DIALErrors.ERR_CONTENT_NOT_FOUND_MSG + invalidIds.asJava)
			else
				ResponseHandler.ERROR(ResponseCode.PARTIAL_SUCCESS, DIALErrors.ERR_DIALCODE_LINK, DIALErrors.ERR_CONTENT_NOT_FOUND_MSG + invalidIds.asJava)
		})
	}

	def getResponseCollectionLink(requestMap: Map[String, List[String]], updatedUnits: List[String], invalidIds: List[String])(implicit ec: ExecutionContext): Future[Response] = {
		val response = if (requestMap.keySet.size == updatedUnits.size)
				ResponseHandler.OK
			else if (invalidIds.nonEmpty && updatedUnits.isEmpty)
				ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, DIALErrors.ERR_DIALCODE_LINK, DIALErrors.ERR_CONTENT_NOT_FOUND_MSG + invalidIds.asJava)
			else
				ResponseHandler.ERROR(ResponseCode.PARTIAL_SUCCESS, DIALErrors.ERR_DIALCODE_LINK, DIALErrors.ERR_CONTENT_NOT_FOUND_MSG + invalidIds.asJava)

		Future(response)
	}

	def updateChildrenHierarchy(childrenHierarchy: util.List[util.Map[String, AnyRef]], requestMap: Map[String, List[String]]): List[util.Map[String, AnyRef]] = {
		childrenHierarchy.asScala.toList.map(child => {
			if (requestMap.contains(child.get("identifier").toString) && StringUtils.equalsIgnoreCase("Parent", child.get("visibility").toString)) {
				if (requestMap.getOrElse(child.get("identifier").toString, List.empty).nonEmpty && requestMap(child.get("identifier").toString).exists(rec => rec.trim.nonEmpty))
					child.put("dialcodes", requestMap(child.get("identifier").toString))
				else
					child.remove("dialcodes")
			}
			if(child.get("children")!=null)
					updateChildrenHierarchy(child.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]], requestMap)
			child
		})
	}

	def getChildrenDIALMap(childrenHierarchy: List[util.Map[String, AnyRef]], requestMap: Map[String, List[String]]): Map[String, AnyRef] = {
		childrenHierarchy.map(child => {
			val subChildrenDIALMap = if(child.get("children")!=null)
				getChildrenDIALMap(child.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala.toList, requestMap)
			else Map.empty[String, String]

			val childDIALMap = if(requestMap.contains(child.get("identifier").toString) && child.get("dialcodes")!=null)
				Map(child.get("identifier").toString -> child.get("dialcodes"))
			else if(requestMap.contains(child.get("identifier").toString))
				Map(child.get("identifier").toString -> List.empty)
			else Map.empty

			subChildrenDIALMap ++ childDIALMap
		}).filter(msg => msg.nonEmpty).flatten.toMap[String, AnyRef]
	}

	def validateDuplicateDIALCodes(unitDIALCodesMap: Map[String, AnyRef]): Unit = {
		val duplicateDIALCodes = unitDIALCodesMap.flatMap(mapRec => mapRec._2.asInstanceOf[List[String]].flatMap(listRec => {
			val dupUnitsList: List[String] = unitDIALCodesMap.flatMap(loopMapRec => if(loopMapRec._1 != mapRec._1 && loopMapRec._2.asInstanceOf[List[String]].contains(listRec)) {
				List(loopMapRec._1, mapRec._1)
			} else List.empty[String]).filter(unitRec => unitRec.nonEmpty).toList
			Map(listRec -> dupUnitsList)
		})).filter(unitRec => unitRec._2.nonEmpty)

		if (duplicateDIALCodes.nonEmpty)
			throw new ClientException(DIALErrors.ERR_DUPLICATE_DIAL_CODES, DIALErrors.ERR_DUPLICATE_DIAL_CODES_MSG + duplicateDIALCodes)
	}
}
