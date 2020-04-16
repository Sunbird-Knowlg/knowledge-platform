package org.sunbird.content.util

import java.util
import java.util._
import java.util.concurrent.CompletionException

import com.mashape.unirest.http.{HttpResponse, Unirest}
import org.apache.commons.collections.MapUtils
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.http.HttpStatus
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode, ServerException}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.telemetry.logger.TelemetryManager

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConversions._


object ReserveDialcodeUtil {

    def reserveDialcodes(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
        validateRequest(request)
        getNodeToReserveDialcodes(request).map(node => {
            val dialCodeMap: util.Map[String, Integer] = if (node.getMetadata.containsKey(ContentConstants.RESERVED_DIAL_CODES))
                JsonUtils.deserialize(node.getMetadata.get(ContentConstants.RESERVED_DIAL_CODES).asInstanceOf[String], classOf[util.Map[String, Integer]])
            else new util.HashMap[String, Integer]()
            val reqCount = request.get(ContentConstants.COUNT).asInstanceOf[Integer]
            if (dialCodeMap.size() >= reqCount)
                Future(getErrorResponse(request.get(ContentConstants.IDENTIFIER).asInstanceOf[String], dialCodeMap))
            else {
                val maxIndex: Integer = if (MapUtils.isNotEmpty(dialCodeMap)) Collections.max(dialCodeMap.values) else -1
                val newDialcodes = getGeneratedDialcodes(request, reqCount - dialCodeMap.keySet().size())
                for ((dialcode, index) <- newDialcodes.view.zipWithIndex) dialCodeMap.put(dialcode, maxIndex + index + 1)
                updateNodes(request, dialCodeMap).map(resp => {
                    val response = ResponseHandler.OK()
                    response.put(ContentConstants.COUNT, dialCodeMap.keySet().size)
                    response.put(ContentConstants.RESERVED_DIAL_CODES, dialCodeMap)
                    response.put(ContentConstants.NODE_ID, request.get(ContentConstants.IDENTIFIER))
                    response.put(ContentConstants.IDENTIFIER, request.get(ContentConstants.IDENTIFIER))
                    response.put(ContentConstants.VERSION_KEY, node.getMetadata.get(ContentConstants.VERSION_KEY))
                    TelemetryManager.info("DIAL Codes generated and reserved.")
                    response
                })
            }
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
    }

    private def validateRequest(request: Request): Unit = {
        if (StringUtils.isBlank(request.getContext.get(ContentConstants.CHANNEL).asInstanceOf[String]))
            throw new ClientException(ContentConstants.ERR_CHANNEL_BLANK_OBJECT, "Channel can not be blank.")
        if (StringUtils.isBlank(request.getRequest.getOrDefault(ContentConstants.IDENTIFIER, "").asInstanceOf[String])
            || StringUtils.endsWith(request.getRequest.getOrDefault(ContentConstants.IDENTIFIER, "").asInstanceOf[String], ContentConstants.IMAGE_SUFFIX))
            throw new ClientException(ContentConstants.ERR_INVALID_CONTENT_ID, "Please provide valid content identifier")
    }

    private def getNodeToReserveDialcodes(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        request.put(ContentConstants.MODE, ContentConstants.EDIT_MODE)
        DataNode.read(request).map(node => {
            val validContentType: util.List[String] = Platform.getStringList("learning.reserve_dialcode.content_type", util.Arrays.asList("TextBook"))
            if (!validContentType.contains(node.getMetadata.get(ContentConstants.CONTENT_TYPE)))
                throw new ClientException(ContentConstants.ERR_CONTENT_CONTENT_TYPE, "Invalid Content Type.")
            if (!StringUtils.equals(node.getMetadata.get(ContentConstants.CHANNEL).asInstanceOf[String], request.getContext.get(ContentConstants.CHANNEL).asInstanceOf[String]))
                throw new ClientException(ContentConstants.ERR_CONTENT_INVALID_CHANNEL, "Invalid Channel Id.")
            if (null == request.get(ContentConstants.COUNT) || !request.get(ContentConstants.COUNT).isInstanceOf[Integer])
                throw new ClientException(ContentConstants.ERR_INVALID_COUNT, "Invalid dial code count.")
            val count = request.get(ContentConstants.COUNT).asInstanceOf[Integer]
            val maxCount = Platform.getInteger("learning.reserve_dialcode.max_count", 250)
            if (count < 1 || count > maxCount)
                throw new ClientException(ContentConstants.ERR_INVALID_COUNT, "Invalid dial code count range. Its should be between 1 to " + maxCount + ".")
            node
        })
    }


    private def getErrorResponse(contentId: String, dialCodeMap: util.Map[String, Integer]): Response = {
        val error = ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, ResponseCode.CLIENT_ERROR.name(), "No new DIAL Codes have been generated, as requested count is less or equal to existing reserved dialcode count.")
        error.put(ContentConstants.MESSAGES, "No new DIAL Codes have been generated, as requested count is less or equal to existing reserved dialcode count.")
        error.put(ContentConstants.COUNT, dialCodeMap.keySet().size)
        error.put(ContentConstants.RESERVED_DIAL_CODES, dialCodeMap)
        error.put(ContentConstants.IDENTIFIER, contentId)
        error.put(ContentConstants.NODE_ID, contentId)
        error
    }

    private def getGeneratedDialcodes(request: Request, dialcodeCount: Integer)(implicit executionContext: ExecutionContext): util.List[String] = {
        val generateRequest = "{\n" +
            "    \"request\": {\n" +
            "        \"dialcodes\": {\n" +
            "            \"count\": " + dialcodeCount + ",\n" +
            "            \"publisher\": \"" + request.get(ContentConstants.PUBLISHER) + "\",\n" +
            "            \"batchCode\": \" " + request.get(ContentConstants.IDENTIFIER) + "\"\n" +
            "        }\n" +
            "    }\n" +
            "}"
        val headerParam = new util.HashMap[String, String]
        headerParam.put(ContentConstants.CHANNEL_ID, request.getContext.get(ContentConstants.CHANNEL_ID).asInstanceOf[String])
        headerParam.put("Content-Type", "application/json")
        headerParam.put("Authorization", "Bearer" + " " + Platform.config.getString("dialcode.api.generate.api_key"))
        val url: String = Platform.getString("dialcode.api.generate.url", "http://11.2.4.22:8080/learning-service/dialcode/v3/generate")
        val httpResponse = Unirest.post(url).headers(headerParam).body(generateRequest).asString
        if (httpResponse.getStatus == HttpStatus.SC_OK) {
            val response: Response = JsonUtils.deserialize(httpResponse.getBody, classOf[Response])
            if (CollectionUtils.isNotEmpty(response.getResult.getOrDefault(ContentConstants.DIAL_CODES, new util.ArrayList[String]()).asInstanceOf[util.List[String]])) {
                response.get(ContentConstants.DIAL_CODES).asInstanceOf[util.List[String]]
            } else throw new ServerException(ContentConstants.SERVER_ERROR, "Dialcode generated list is empty. Please Try Again After Sometime!")
        } else throw new ServerException(ContentConstants.SERVER_ERROR, "Could not generate dialcodes due to some unknown error. Please check authorization/ request structure")
    }

    private def updateNodes(request: Request, dialCodeMap: util.Map[String, Integer])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[AnyRef] = {
        val updateReq = new Request(request)
        updateReq.put("identifiers", java.util.Arrays.asList(request.get("identifier").asInstanceOf[String], request.get("identifier").asInstanceOf[String] + ContentConstants.IMAGE_SUFFIX))
        updateReq.put("metadata", new util.HashMap[String, AnyRef]() {
            {
                put(ContentConstants.RESERVED_DIAL_CODES, dialCodeMap)
            }
        })
        DataNode.bulkUpdate(updateReq)
    }

}
