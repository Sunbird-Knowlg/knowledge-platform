package org.sunbird.content.dial

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.Platform
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception._
import org.sunbird.content.util.ContentConstants
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode

import java.util
import scala.collection.JavaConverters._
import scala.collection.immutable.HashMap
import scala.concurrent.{ExecutionContext, Future}


object DIALManager {

	val DIAL_SEARCH_API_URL = Platform.config.getString("dial_service.api.base_url") + "/dialcode/v3/search"
	val DIALCODE_GENERATE_URI = Platform.config.getString("dial_service.api.base_url") + Platform.config.getString("dial_service.api.generate_url")
	val DIAL_API_AUTH_KEY = "Bearer " + Platform.config.getString("dial_service.api.auth_key")
	val PASSPORT_KEY = Platform.config.getString("graph.passport.key.base")

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
			val contents: List[String] = req.get(DIALConstants.IDENTIFIER).get
			val dialcodes: List[String] = req.get(DIALConstants.DIALCODE).get
			validateReqStructure(dialcodes, contents)
			contents.foreach(id => reqMap += (id -> dialcodes))
		})
		if (Platform.getBoolean("content.link_dialcode.validation", true)) {
			val dials = requestList.collect { case m if m.get(DIALConstants.DIALCODE).nonEmpty => m.get(DIALConstants.DIALCODE).get }.flatten
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
		if (!dialcodes.isEmpty) {
			val reqMap = new util.HashMap[String, AnyRef]() {{
				put(DIALConstants.REQUEST, new util.HashMap[String, AnyRef]() {{
					put(DIALConstants.SEARCH, new util.HashMap[String, AnyRef]() {{
						put(DIALConstants.IDENTIFIER, dialcodes.distinct.asJava)
					}})
				}})
			}}
			val headerParam = HashMap[String, String](DIALConstants.X_CHANNEL_ID -> channelId, DIALConstants.AUTHORIZATION -> DIAL_API_AUTH_KEY).asJava
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
					val dials: util.List[String] = if (!map._2.isEmpty) map._2.asJava else new util.ArrayList[String]()
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

	//TODO: Complete the implementation
	def linkCollection(objectId: String, requestMap: Map[String, List[String]], getContext: util.Map[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		Future {
			ResponseHandler.OK()
		}
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

	def reserve(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		val channelId: String = request.getContext.getOrDefault(DIALConstants.CHANNEL, "").asInstanceOf[String]
		val contentId: String = request.get(ContentConstants.IDENTIFIER).asInstanceOf[String]

		println("DialManager:: reserve:: channelId: " + channelId)
		println("DialManager:: reserve:: contentId: " + contentId)

		if (contentId == null || contentId.isEmpty) throw new ClientException(DIALErrors.ERR_CONTENT_BLANK_OBJECT_ID, DIALErrors.ERR_CONTENT_BLANK_OBJECT_ID_MSG)

		val req = new Request()
		req.setContext(request.getContext)
		req.put(DIALConstants.IDENTIFIER, contentId)
		req.put(ContentConstants.ROOT_ID, contentId)
		req.put(ContentConstants.MODE, "edit")
		DataNode.read(req).flatMap(rootNode => {
			val contentMetadata = rootNode.getMetadata
			println("DialManager:: reserve:: contentMetadata: " + contentMetadata)
			val contentChannel = contentMetadata.get("channel").asInstanceOf[String]
			validateChannel(contentChannel, channelId)
			validateContentForReservedDialcodes(contentMetadata)
			validateCountForReservingDialCode(request.getRequest.get("dialcodes").asInstanceOf[util.Map[String, AnyRef]])

			if (contentMetadata.get("status").asInstanceOf[String].equalsIgnoreCase("Live") || contentMetadata.get("status").asInstanceOf[String].equalsIgnoreCase("Unlisted"))
				throw new ClientException(DIALErrors.ERR_CONTENT_INVALID_OBJECT, DIALErrors.ERR_CONTENT_INVALID_OBJECT_MSG)

			val reservedDialCodes = contentMetadata.getOrDefault("dialcodes", Map.empty[String, Integer]).asInstanceOf[Map[String, Integer]]
			println("DialManager:: reserve:: reservedDialCodes: " + reservedDialCodes)
			val maxIndex: Integer = if (reservedDialCodes.nonEmpty) reservedDialCodes.max._2	else -1
			val dialCodes = reservedDialCodes.keySet
			val reqDialcodesCount = request.getRequest.get("dialcodes").asInstanceOf[util.Map[String, AnyRef]].get("count").asInstanceOf[Integer]
			println("DialManager:: reserve:: reqDialcodesCount: " + reqDialcodesCount)
			val updateDialCodes  = if (dialCodes.size < reqDialcodesCount) {
				val newDialcodes = generateDialCodes(channelId, contentId, reqDialcodesCount - dialCodes.size, request.get("publisher").asInstanceOf[String])
				val newDialCodesMap: Map[String, Integer] = newDialcodes.zipWithIndex.map { case (newDialCode, idx) =>
					(newDialCode -> (maxIndex + idx + 1).asInstanceOf[Integer])
				}.toMap
				reservedDialCodes ++ newDialCodesMap
			} else reservedDialCodes
			println("DialManager:: reserve:: updateDialCodes: " + updateDialCodes)
			if(updateDialCodes.size > reservedDialCodes.size) {
				val updateReq = new Request(request)
				updateReq.put("identifier", rootNode.getIdentifier)
				val rootNodeMetadata = rootNode.getMetadata
				rootNodeMetadata.remove("discussionForum")
				rootNodeMetadata.remove("credentials")
				rootNodeMetadata.remove("trackable")

				updateReq.put(DIALConstants.RESERVED_DIALCODES, updateDialCodes)
				updateReq.getRequest.putAll(rootNodeMetadata)
				DataNode.update(updateReq).map(updatedNode => {
					val response = ResponseHandler.OK()
					response.getResult.put("count", updateDialCodes.size.asInstanceOf[Integer])
					response.getResult.put("node_id", contentId)
					response.getResult.put("processId", updatedNode.getMetadata.get("processId"))
					response.getResult.put("reservedDialcodes", updatedNode.getMetadata.get("reservedDialcodes"))
					response.getResult.put("versionKey", updatedNode.getMetadata.get("versionKey"))
					println("DialManager:: reserve:: response: " + response)
					response
				})
			} else {
				val errorResponse = ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, DIALErrors.ERR_INVALID_COUNT, DIALErrors.ERR_DIAL_INVALID_COUNT_RESPONSE)
				errorResponse.getResult.put("count", reservedDialCodes.size.asInstanceOf[Integer])
				errorResponse.getResult.put("node_id", contentId)
				errorResponse.getResult.put("processId", rootNode.getMetadata.get("processId"))
				errorResponse.getResult.put("reservedDialcodes", rootNode.getMetadata.get("reservedDialcodes"))
				println("DialManager:: reserve:: errorResponse: " + errorResponse)
				Future(errorResponse)
			}
		})
	}

	def validateChannel(contentChannel: String, channelId: String): Unit = {
		if(contentChannel == null || channelId == null || !contentChannel.equalsIgnoreCase(channelId))
			throw new ClientException(DIALErrors.ERR_INVALID_CHANNEL, DIALErrors.ERR_INVALID_CHANNEL_MSG)
	}

	def validateContentForReservedDialcodes(metaData: util.Map[String, AnyRef]): Unit = {
		val validMimeType = if (Platform.config.hasPath("reserve_dialcode.mimeType")) Platform.config.getStringList("reserve_dialcode.mimeType") else util.Arrays.asList("application/vnd.ekstep.content-collection")
		if (!validMimeType.contains(metaData.get("mimeType"))) throw new ClientException(DIALErrors.ERR_CONTENT_MIMETYPE, DIALErrors.ERR_CONTENT_MIMETYPE_MSG)
	}

	def validateCountForReservingDialCode(request: util.Map[String, AnyRef]): Unit = {
		println("DIALManager::  validateCountForReservingDialCode:: request:" + request)
		if (null == request.get("count") || !request.get("count").isInstanceOf[Integer]) throw new ClientException(DIALErrors.ERR_INVALID_COUNT, DIALErrors.ERR_INVALID_COUNT_MSG)
		val count = request.get("count").asInstanceOf[Integer]
		val maxCount = if (Platform.config.hasPath("reserve_dialcode.max_count")) Platform.config.getInt("reserve_dialcode.max_count") else 250
		if (count < 1 || count > maxCount) throw new ClientException(DIALErrors.ERR_INVALID_COUNT_RANGE, DIALErrors.ERR_INVALID_COUNT_RANGE_MSG + maxCount + ".")
	}

	@throws[Exception]
	private def generateDialCodes(channelId: String, contentId: String, dialcodeCount: Integer, publisher: String)(implicit oec: OntologyEngineContext): List[String] = {
		val dialcodeMap = new util.HashMap[String, AnyRef]
		dialcodeMap.put("count", dialcodeCount)
		dialcodeMap.put("publisher", publisher)
		dialcodeMap.put("batchCode", contentId)
		val request = new util.HashMap[String, AnyRef]
		request.put("dialcodes", dialcodeMap)
		val requestMap = new util.HashMap[String, AnyRef]
		requestMap.put("request", request)
		val headerParam = new util.HashMap[String, String]{put(DIALConstants.X_CHANNEL_ID, channelId); put(DIALConstants.AUTHORIZATION, DIAL_API_AUTH_KEY);}

		println("DIALManager:: generateDialCodes:: request URL:: " + DIALCODE_GENERATE_URI)
		println("DIALManager:: generateDialCodes:: requestMap:: " + requestMap)

		val generateResponse = oec.httpUtil.post(DIALCODE_GENERATE_URI, requestMap, headerParam)
		println("DIALManager:: generateDialCodes:: getParams:: " + generateResponse.getParams)
		if ((generateResponse.getResponseCode == ResponseCode.OK) || (generateResponse.getResponseCode == ResponseCode.PARTIAL_SUCCESS)) {
			val result = generateResponse.getResult
			println("DIALManager:: generateDialCodes:: result:: " + generateResponse.getResult)

			val generatedDialCodes = result.get("dialcodes").asInstanceOf[util.ArrayList[String]].asScala.toList
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
}
