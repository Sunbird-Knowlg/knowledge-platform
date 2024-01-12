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
import org.sunbird.telemetry.logger.TelemetryManager

import java.util
import scala.collection.JavaConverters._
import scala.collection.immutable.{HashMap, Map}
import scala.concurrent.{ExecutionContext, Future}


object DIALManager {

	val DIAL_SEARCH_API_URL: String = Platform.config.getString("dial_service.api.base_url") + Platform.config.getString("dial_service.api.search")
	val DIALCODE_GENERATE_URI: String = Platform.config.getString("dial_service.api.base_url") + Platform.config.getString("dial_service.api.generate")
	val DIAL_API_AUTH_KEY: String = ContentConstants.BEARER + Platform.config.getString("dial_service.api.auth_key")
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
			val contents: List[String] = if(req.contains(DIALConstants.IDENTIFIER)) req(DIALConstants.IDENTIFIER) else throw new ClientException(DIALErrors.ERR_DIALCODE_CONTENT_LINK_FIELDS_MISSING, DIALErrors.ERR_DIALCODE_CONTENT_LINK_FIELDS_MISSING_MSG + DIALConstants.IDENTIFIER)
			val dialcodes: List[String] = if(req.contains(DIALConstants.DIALCODE)) req(DIALConstants.DIALCODE) else throw new ClientException(DIALErrors.ERR_DIALCODE_CONTENT_LINK_FIELDS_MISSING, DIALErrors.ERR_DIALCODE_CONTENT_LINK_FIELDS_MISSING_MSG + DIALConstants.DIALCODE)
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
			if (searchResponse.getResponseCode.toString == ContentConstants.OK_RESPONSE_CODE) {
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
		val req = new Request()
		req.setContext(reqContext)
		req.put(ContentConstants.IDENTIFIER, objectId)
		req.put(ContentConstants.MODE, ContentConstants.EDIT_MODE)
		DataNode.read(req).flatMap(rootNode => {
			val updateReq = getLinkUpdateRequest(req, rootNode, requestMap, objectId)
			DataNode.update(updateReq).flatMap(response => {
				req.getContext.put(ContentConstants.SCHEMA_NAME, ContentConstants.COLLECTION_SCHEMA_NAME)
				req.getContext.put(ContentConstants.VERSION, ContentConstants.SCHEMA_VERSION)
				req.put(ContentConstants.ROOT_ID, objectId)
				HierarchyManager.getHierarchy(req).flatMap(getHierarchyResponse => {
					val updatedChildrenHierarchy = getUpdatedChildrenHierarchy(getHierarchyResponse, requestMap)
					val consolidatedUnitDIALMap = getConsolidatedUnitDIALMap(updatedChildrenHierarchy, requestMap, objectId)
					validateDuplicateDIALCodes(consolidatedUnitDIALMap.filter(rec => rec._2.asInstanceOf[List[String]].nonEmpty))

					val hierarchyReq = getHierarchyRequest(req, objectId, updatedChildrenHierarchy, rootNode)
					oec.graphService.saveExternalProps(hierarchyReq).flatMap(rec =>
						getResponseCollectionLink(requestMap, consolidatedUnitDIALMap.keySet.toList, requestMap.keySet.diff(consolidatedUnitDIALMap.keySet).toList)
					)
				})
			})
		})
	}

	def getUpdatedChildrenHierarchy(getHierarchyResponse: Response, requestMap: Map[String, List[String]]): List[util.Map[String, AnyRef]] = {
		val collectionHierarchy = getHierarchyResponse.getResult.getOrDefault(ContentConstants.CONTENT, new java.util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
		val childrenHierarchy = collectionHierarchy.get(ContentConstants.CHILDREN).asInstanceOf[util.List[util.Map[String, AnyRef]]]
		updateChildrenHierarchy(childrenHierarchy, requestMap)
	}

	def getConsolidatedUnitDIALMap(updatedChildrenHierarchy: List[util.Map[String, AnyRef]], requestMap: Map[String, List[String]], objectId: String): Map[String, AnyRef] = {
		val childrenDIALMap = getChildrenDIALMap(updatedChildrenHierarchy, requestMap)
		if (!requestMap.contains(objectId)) childrenDIALMap else childrenDIALMap ++ Map(objectId -> requestMap(objectId))
	}

	def getHierarchyRequest(req: Request, objectId: String, updatedChildrenHierarchy: List[util.Map[String, AnyRef]], rootNode: Node): Request = {
		val updatedHierarchy = new java.util.HashMap[String, AnyRef]()
		updatedHierarchy.put(ContentConstants.IDENTIFIER, objectId)
		updatedHierarchy.put(ContentConstants.CHILDREN, updatedChildrenHierarchy.asJava)

		val hierarchyReq = new Request(req)
		hierarchyReq.put(ContentConstants.HIERARCHY, ScalaJsonUtils.serialize(updatedHierarchy))
		hierarchyReq.put(ContentConstants.IDENTIFIER, rootNode.getIdentifier)

		hierarchyReq
	}

	def getLinkUpdateRequest(req: Request, rootNode: Node, requestMap: Map[String, List[String]], objectId: String): Request = {
		val updateReq = new Request(req)
		updateReq.put(ContentConstants.IDENTIFIER, rootNode.getIdentifier)
		updateReq.put(DIALConstants.VERSION_KEY, rootNode.getMetadata.get("versionKey"))

		if(!requestMap.contains(objectId))
			updateReq.put(DIALConstants.DIALCODES, null)
		else
			updateReq.put(DIALConstants.DIALCODES, requestMap(objectId).toArray[String])

		updateReq
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
			if (requestMap.contains(child.get(ContentConstants.IDENTIFIER).toString) && StringUtils.equalsIgnoreCase(ContentConstants.PARENT, child.get(ContentConstants.VISIBILITY).toString)) {
				if (requestMap.getOrElse(child.get(ContentConstants.IDENTIFIER).toString, List.empty).nonEmpty && requestMap(child.get(ContentConstants.IDENTIFIER).toString).exists(rec => rec.trim.nonEmpty))
					child.put(DIALConstants.DIALCODES, requestMap(child.get(ContentConstants.IDENTIFIER).toString))
				else
					child.remove(DIALConstants.DIALCODES)
			}
			if(child.get(ContentConstants.CHILDREN)!=null)
					updateChildrenHierarchy(child.get(ContentConstants.CHILDREN).asInstanceOf[util.List[util.Map[String, AnyRef]]], requestMap)
			child
		})
	}

	def getChildrenDIALMap(childrenHierarchy: List[util.Map[String, AnyRef]], requestMap: Map[String, List[String]]): Map[String, AnyRef] = {
		childrenHierarchy.map(child => {
			val subChildrenDIALMap = if(child.get(ContentConstants.CHILDREN)!=null)
				getChildrenDIALMap(child.get(ContentConstants.CHILDREN).asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala.toList, requestMap)
			else Map.empty[String, String]

			val childDIALMap = if(requestMap.contains(child.get(ContentConstants.IDENTIFIER).toString) && child.get(DIALConstants.DIALCODES)!=null)
				Map(child.get(ContentConstants.IDENTIFIER).toString -> child.get(DIALConstants.DIALCODES))
			else if(requestMap.contains(child.get(ContentConstants.IDENTIFIER).toString))
				Map(child.get(ContentConstants.IDENTIFIER).toString -> List.empty)
			else Map.empty

			subChildrenDIALMap ++ childDIALMap
		}).filter(msg => msg.nonEmpty).flatten.toMap[String, AnyRef]
	}

	def validateDuplicateDIALCodes(unitDIALCodesMap: Map[String, AnyRef]): Unit = {
		val duplicateDIALCodes = unitDIALCodesMap.flatMap(mapRec => mapRec._2.asInstanceOf[List[String]].flatMap(listRec => {
			val dupUnitsList: List[String] = unitDIALCodesMap.flatMap(loopMapRec => if(loopMapRec._1 != mapRec._1 && loopMapRec._2.asInstanceOf[List[String]].contains(listRec)) {
				List(loopMapRec._1, mapRec._1)
			} else List.empty[String]).filter(unitRec => unitRec.nonEmpty).toList
			Map(listRec -> dupUnitsList.toSet)
		})).filter(unitRec => unitRec._2.nonEmpty)

		if (duplicateDIALCodes.nonEmpty)
			throw new ClientException(DIALErrors.ERR_DUPLICATE_DIAL_CODES, DIALErrors.ERR_DUPLICATE_DIAL_CODES_MSG + duplicateDIALCodes)
	}

	def reserveOrRelease(request: Request, operation: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		val channelId: String = request.getContext.getOrDefault(DIALConstants.CHANNEL, "").asInstanceOf[String]
		val contentId: String = request.get(ContentConstants.IDENTIFIER).asInstanceOf[String]

		if (contentId == null || contentId.isEmpty) throw new ClientException(DIALErrors.ERR_CONTENT_BLANK_OBJECT_ID, DIALErrors.ERR_CONTENT_BLANK_OBJECT_ID_MSG)

		val req = new Request()
		req.setContext(request.getContext)
		req.put(DIALConstants.IDENTIFIER, contentId)
		req.put(ContentConstants.ROOT_ID, contentId)
		req.put(ContentConstants.MODE, ContentConstants.EDIT_MODE)
		DataNode.read(req).flatMap(rootNode => {
			val contentMetadata = rootNode.getMetadata

			validateChannel(contentMetadata.get(DIALConstants.CHANNEL).asInstanceOf[String], channelId)
			validateContentForReserveAndReleaseDialcodes(contentMetadata)
			validateCountForReservingAndReleasingDialCode(request.getRequest.get(DIALConstants.DIALCODES).asInstanceOf[util.Map[String, AnyRef]])

			operation match {
				case ContentConstants.RESERVE => reserve(request, channelId, contentId, rootNode, contentMetadata)
				case ContentConstants.RELEASE => release(request, contentId, rootNode, contentMetadata)
				case _ => throw new ClientException(DIALErrors.ERR_INVALID_OPERATION, DIALErrors.ERR_INVALID_OPERATION_MSG)
			}
		})
	}

	def reserve(request: Request, channelId: String, contentId: String, rootNode: Node, contentMetadata: util.Map[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		validateContentStatus(contentMetadata)

		val reservedDialCodes = if(contentMetadata.containsKey(DIALConstants.RESERVED_DIALCODES)) ScalaJsonUtils.deserialize[Map[String, Integer]](contentMetadata.get(DIALConstants.RESERVED_DIALCODES).asInstanceOf[String]) else Map.empty[String, Integer]
		val updateDialCodes  = getUpdateDIALCodes(reservedDialCodes, request, channelId, contentId)

		if(updateDialCodes.size > reservedDialCodes.size) {
			val updateReq = getDIALReserveUpdateRequest(request, rootNode, updateDialCodes)
			DataNode.update(updateReq).map(updatedNode => {
				val response = ResponseHandler.OK()
				val updatedSuccessResponse = getDIALReserveUpdateResponse(response, updateDialCodes.size.asInstanceOf[Integer], contentId, updatedNode)
				updatedSuccessResponse.getResult.put(DIALConstants.VERSION_KEY, updatedNode.getMetadata.get(DIALConstants.VERSION_KEY))
				updatedSuccessResponse
			})
		} else {
			val errorResponse = ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, DIALErrors.ERR_INVALID_COUNT, DIALErrors.ERR_DIAL_INVALID_COUNT_RESPONSE)
			val updatedErrorResponse = getDIALReserveUpdateResponse(errorResponse, reservedDialCodes.size.asInstanceOf[Integer], contentId, rootNode)
			Future(updatedErrorResponse)
		}
	}

	def release(request: Request, contentId: String, rootNode: Node, contentMetadata: util.Map[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		val reservedDialCodes = if(contentMetadata.containsKey(DIALConstants.RESERVED_DIALCODES)) ScalaJsonUtils.deserialize[Map[String, Integer]](contentMetadata.get(DIALConstants.RESERVED_DIALCODES).asInstanceOf[String])
		else throw new ClientException(DIALErrors.ERR_CONTENT_MISSING_RESERVED_DIAL_CODES, DIALErrors.ERR_CONTENT_MISSING_RESERVED_DIAL_CODES_MSG)

		val countInRequest = request.getRequest.get(DIALConstants.DIALCODES).asInstanceOf[util.Map[String, AnyRef]].get(DIALConstants.COUNT).asInstanceOf[Integer]
		if(reservedDialCodes.keys.size < countInRequest)
			throw new ClientException(DIALErrors.ERR_COUNT_GREATER_THAN_RESERVED_DIAL_CODES, DIALErrors.ERR_COUNT_GREATER_THAN_RESERVED_DIAL_CODES_MSG)

		populateAssignedDialCodes(contentId, contentMetadata, request).map(assignedDialCodes => {
			val toReleaseDIALCodes = reservedDialCodes.keySet -- assignedDialCodes.toSet

			if(toReleaseDIALCodes.isEmpty) throw new ClientException(DIALErrors.ERR_ALL_DIALCODES_UTILIZED, DIALErrors.ERR_ALL_DIALCODES_UTILIZED_MSG)

			val reqDialcodesCount = request.getRequest.get(DIALConstants.DIALCODES).asInstanceOf[util.Map[String, AnyRef]].get(DIALConstants.COUNT).asInstanceOf[Integer]

			val updatedReleaseDialcodes = if (toReleaseDIALCodes.size > reqDialcodesCount) {
				toReleaseDIALCodes.take(reqDialcodesCount)
			} else toReleaseDIALCodes

			val updatedReserveDialCodes = reservedDialCodes.filter(rec => !updatedReleaseDialcodes.contains(rec._1)).keySet.zipWithIndex.map { case (dialCode, idx) =>
				(dialCode -> idx.asInstanceOf[Integer])
			}.toMap

			val updateReq = new Request()
			updateReq.setContext(request.getContext)
			updateReq.getContext.put(ContentConstants.IDENTIFIER, rootNode.getIdentifier)
			updateReq.put(ContentConstants.IDENTIFIER, rootNode.getIdentifier)
			updateReq.put(DIALConstants.VERSION_KEY,rootNode.getMetadata.get(ContentConstants.VERSION_KEY))
			updateReq.put(DIALConstants.RESERVED_DIALCODES, if(updatedReserveDialCodes.nonEmpty) updatedReserveDialCodes.asJava else null)
			DataNode.update(updateReq).map(node => {
				ResponseHandler.OK.putAll(Map(ContentConstants.IDENTIFIER -> node.getIdentifier.replace(ContentConstants.IMAGE_SUFFIX, ""),
					ContentConstants.VERSION_KEY -> node.getMetadata.get(ContentConstants.VERSION_KEY),
					DIALConstants.RESERVED_DIALCODES -> node.getMetadata.get(DIALConstants.RESERVED_DIALCODES)).asJava)
			})
		}).flatMap(f=>f)
	}

	def populateAssignedDialCodes(contentId: String, contentMetadata: util.Map[String, AnyRef], req: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[List[String]] = {
		val request = new Request()
		request.setContext(req.getContext)
		request.getContext.put(ContentConstants.SCHEMA_NAME, ContentConstants.COLLECTION_SCHEMA_NAME)
		request.getContext.put(ContentConstants.VERSION, ContentConstants.SCHEMA_VERSION)
		request.put(ContentConstants.ROOT_ID, contentMetadata.getOrDefault(ContentConstants.IDENTIFIER,"").asInstanceOf[String])
		request.put(ContentConstants.MODE, ContentConstants.EDIT_MODE)

		HierarchyManager.getHierarchy(request).flatMap(getImageHierarchyResponse => {
			val imageCollectionHierarchy = getImageHierarchyResponse.getResult.getOrDefault(ContentConstants.CONTENT, new java.util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
			val imageChildrenHierarchy = imageCollectionHierarchy.get(ContentConstants.CHILDREN).asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala.toList
			val imageChildrenAssignedDIALList = getAssignedDIALcodes(imageChildrenHierarchy)
			val contentImageAssignedDIALList = if(imageCollectionHierarchy.containsKey(DIALConstants.DIALCODES) && imageCollectionHierarchy.get(DIALConstants.DIALCODES) != null) {
				val collectionDialCodeStr = ScalaJsonUtils.serialize(imageCollectionHierarchy.get(DIALConstants.DIALCODES))
				val collectionDialCode = ScalaJsonUtils.deserialize[List[String]](collectionDialCodeStr)
				imageChildrenAssignedDIALList ++ collectionDialCode
			}
			else imageChildrenAssignedDIALList

			request.put(ContentConstants.ROOT_ID, contentId)
			request.put(ContentConstants.MODE, "")
			if(contentMetadata.getOrDefault(ContentConstants.IDENTIFIER,"").asInstanceOf[String].endsWith(ContentConstants.IMAGE_SUFFIX)) {
				HierarchyManager.getHierarchy(request).flatMap(getHierarchyResponse => {
					val collectionHierarchy = getHierarchyResponse.getResult.getOrDefault(ContentConstants.CONTENT, new java.util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
					val childrenHierarchy = collectionHierarchy.get(ContentConstants.CHILDREN).asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala.toList
					val childrenAssignedDIALList = getAssignedDIALcodes(childrenHierarchy)
					val contentAssignedDIALList = if(collectionHierarchy.containsKey(DIALConstants.DIALCODES) && collectionHierarchy.get(DIALConstants.DIALCODES) != null)
						childrenAssignedDIALList ++ collectionHierarchy.getOrDefault(DIALConstants.DIALCODES, List.empty[String]).asInstanceOf[List[String]]
					else childrenAssignedDIALList

					Future(contentImageAssignedDIALList ++ contentAssignedDIALList)
				})
			} else Future(contentImageAssignedDIALList)
		})
	}

	def getAssignedDIALcodes(childrenHierarchy: List[util.Map[String, AnyRef]]): List[String] = {
		childrenHierarchy.map(child => {
			val subChildrenDIALMap = if(child.get(ContentConstants.CHILDREN)!=null)
				getAssignedDIALcodes(child.get(ContentConstants.CHILDREN).asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala.toList)
			else List.empty[String]

			val childDIALMap = if(child.get(DIALConstants.DIALCODES)!=null)
				child.get(DIALConstants.DIALCODES).asInstanceOf[util.List[String]].asScala.toList
			else List.empty[String]

			subChildrenDIALMap ++ childDIALMap
		}).filter(msg => msg.nonEmpty).flatten
	}


	def validateChannel(contentChannel: String, channelId: String): Unit = {
		if(contentChannel == null || channelId == null || !contentChannel.equalsIgnoreCase(channelId))
			throw new ClientException(DIALErrors.ERR_INVALID_CHANNEL, DIALErrors.ERR_INVALID_CHANNEL_MSG)
	}

	def validateContentForReserveAndReleaseDialcodes(metaData: util.Map[String, AnyRef]): Unit = {
		val validMimeType = if (Platform.config.hasPath("reserve_dialcode.mimeType")) Platform.config.getStringList("reserve_dialcode.mimeType") else util.Arrays.asList(ContentConstants.COLLECTION_MIME_TYPE)
		if (!validMimeType.contains(metaData.get(ContentConstants.MIME_TYPE))) throw new ClientException(DIALErrors.ERR_CONTENT_MIMETYPE, DIALErrors.ERR_CONTENT_MIMETYPE_MSG)

		val validContentStatus = if (Platform.config.hasPath("reserve_dialcode.valid_content_status")) Platform.config.getStringList("reserve_dialcode.valid_content_status") else util.Arrays.asList(ContentConstants.DRAFT, ContentConstants.LIVE)
		if(!validContentStatus.contains(metaData.get(ContentConstants.STATUS).asInstanceOf[String])) throw new ClientException(DIALErrors.ERR_CONTENT_RETIRED_OBJECT_ID, DIALErrors.ERR_CONTENT_RETIRED_OBJECT_ID_MSG)
	}

	def validateCountForReservingAndReleasingDialCode(requestDIALCodesMap: util.Map[String, AnyRef]): Unit = {
		if (null == requestDIALCodesMap.get(DIALConstants.COUNT) || !requestDIALCodesMap.get(DIALConstants.COUNT).isInstanceOf[Integer]) throw new ClientException(DIALErrors.ERR_INVALID_COUNT, DIALErrors.ERR_INVALID_COUNT_MSG)
		val count = requestDIALCodesMap.get(DIALConstants.COUNT).asInstanceOf[Integer]
		val maxCount = if (Platform.config.hasPath("reserve_dialcode.max_count")) Platform.config.getInt("reserve_dialcode.max_count") else 250
		if (count < 1 || count > maxCount) throw new ClientException(DIALErrors.ERR_INVALID_COUNT_RANGE, DIALErrors.ERR_INVALID_COUNT_RANGE_MSG + maxCount + ".")
	}

	def validateContentStatus(contentMetadata: util.Map[String, AnyRef]): Unit = {
		if (contentMetadata.get(ContentConstants.STATUS).asInstanceOf[String].equalsIgnoreCase(DIALConstants.LIVE_STATUS) || contentMetadata.get(ContentConstants.STATUS).asInstanceOf[String].equalsIgnoreCase(DIALConstants.UNLISTED_STATUS))
			throw new ClientException(DIALErrors.ERR_CONTENT_INVALID_OBJECT, DIALErrors.ERR_CONTENT_INVALID_OBJECT_MSG)
	}

	def getUpdateDIALCodes(reservedDialCodes: Map[String, Integer], request: Request, channelId: String, contentId: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Map[String, Integer] = {
		val maxIndex: Integer = if (reservedDialCodes.nonEmpty) reservedDialCodes.max._2	else -1
		val dialCodes = reservedDialCodes.keySet
		val reqDialcodesCount = request.getRequest.get(DIALConstants.DIALCODES).asInstanceOf[util.Map[String, AnyRef]].get(DIALConstants.COUNT).asInstanceOf[Integer]

		if (dialCodes.size < reqDialcodesCount) {
			val newDialcodes = generateDialCodes(channelId, contentId, reqDialcodesCount - dialCodes.size, request.get(DIALConstants.PUBLISHER).asInstanceOf[String])
			val newDialCodesMap: Map[String, Integer] = newDialcodes.zipWithIndex.map { case (newDialCode, idx) =>
				(newDialCode -> (maxIndex + idx + 1).asInstanceOf[Integer])
			}.toMap
			reservedDialCodes ++ newDialCodesMap
		} else reservedDialCodes
	}

	@throws[Exception]
	private def generateDialCodes(channelId: String, contentId: String, dialcodeCount: Integer, publisher: String)(implicit oec: OntologyEngineContext): List[String] = {
		val requestMap = new util.HashMap[String, AnyRef]() {{
			put(DIALConstants.REQUEST, new util.HashMap[String, AnyRef]() {{
				put(DIALConstants.DIALCODES, new util.HashMap[String, AnyRef]() {{
					put(DIALConstants.COUNT, dialcodeCount)
					put(DIALConstants.PUBLISHER, publisher)
					put(DIALConstants.BATCH_CODE, contentId)
				}})
			}})
		}}

		val headerParam = new util.HashMap[String, String]{put(DIALConstants.X_CHANNEL_ID, channelId); put(DIALConstants.AUTHORIZATION, DIAL_API_AUTH_KEY);}
		val generateResponse = oec.httpUtil.post(DIALCODE_GENERATE_URI, requestMap, headerParam)
		if (generateResponse.getResponseCode == ResponseCode.OK || generateResponse.getResponseCode == ResponseCode.PARTIAL_SUCCESS) {
			val generatedDialCodes =  generateResponse.getResult.get(DIALConstants.DIALCODES).asInstanceOf[util.ArrayList[String]].asScala.toList
			if (generatedDialCodes.nonEmpty) generatedDialCodes
			else throw new ServerException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name, DIALErrors.ERR_DIAL_GEN_LIST_EMPTY_MSG)
		}
		else if (generateResponse.getResponseCode eq ResponseCode.CLIENT_ERROR) {
			throw new ClientException(generateResponse.getParams.getErr, generateResponse.getParams.getErrmsg)
		}
		else {
			throw new ServerException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name, DIALErrors.ERR_DIAL_GENERATION_MSG)
		}
	}

	def getDIALReserveUpdateRequest(req: Request, rootNode: Node, updateDialCodes: Map[String, Integer]): Request = {
		val updateReq = new Request()
		updateReq.setContext(req.getContext)
		updateReq.getContext.put(ContentConstants.IDENTIFIER, rootNode.getIdentifier)
		updateReq.put(ContentConstants.IDENTIFIER, rootNode.getIdentifier)
		updateReq.put(DIALConstants.VERSION_KEY,rootNode.getMetadata.get("versionKey"))
		updateReq.put(DIALConstants.RESERVED_DIALCODES, updateDialCodes.asJava)
		updateReq
	}

	def getDIALReserveUpdateResponse(response: Response, count: Integer, contentId: String, node: Node): Response = {
		response.getResult.put(DIALConstants.COUNT, count)
		response.getResult.put(ContentConstants.NODE_ID, contentId)
		response.getResult.put(DIALConstants.RESERVED_DIALCODES, node.getMetadata.get(DIALConstants.RESERVED_DIALCODES))

		response
	}
}
