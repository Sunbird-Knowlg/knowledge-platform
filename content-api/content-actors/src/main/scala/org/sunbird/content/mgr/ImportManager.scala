package org.sunbird.content.mgr

import java.util
import java.util.UUID

import org.apache.commons.collections.CollectionUtils
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.Platform
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.content.constant.ImportConstants
import org.sunbird.content.error.ImportErrors
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.Identifier
import org.sunbird.telemetry.util.LogTelemetryEventUtil

import scala.collection.JavaConversions.mapAsJavaMap
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object ImportManager {

	val REQUEST_LIMIT = Platform.getInteger("content.import.request_size_limit", 200)
	val AUTO_CREATE_TOPIC_NAME = Platform.config.getString("content.import.topic_name")
	val REQUIRED_PROPS = Platform.getStringList("content.import.required_props", java.util.Arrays.asList("name", "code", "mimeType", "contentType", "artifactUrl", "framework"))

	def importContent(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
		val reqList: util.List[util.Map[String, AnyRef]] = getRequest(request)
		if (CollectionUtils.isNotEmpty(reqList) && reqList.size > REQUEST_LIMIT)
			throw new ClientException(ImportErrors.ERR_REQUEST_LIMIT_EXCEED, ImportErrors.ERR_REQUEST_LIMIT_EXCEED_MSG + REQUEST_LIMIT)
		val processId: String = UUID.randomUUID().toString
		val invalidCodes: util.List[String] = new util.ArrayList[String]()
		validateAndGetRequest(reqList, processId, invalidCodes).map(contents => {
			if (CollectionUtils.isNotEmpty(invalidCodes)) {
				val msg = if (invalidCodes.asScala.filter(c => StringUtils.isNotBlank(c)).toList.size > 0) " | Required Property's Missing For " + invalidCodes else ""
				throw new ClientException(ImportErrors.ERR_REQUIRED_PROPS_VALIDATION, ImportErrors.ERR_REQUIRED_PROPS_VALIDATION_MSG + REQUIRED_PROPS + msg)
			} else {
				contents.asScala.map(content => pushInstructionEvent(graphId, content))
				val response = ResponseHandler.OK()
				response.put(ImportConstants.PROCESS_ID, processId)
				response
			}
		})

	}

	def validateAndGetRequest(contents: util.List[util.Map[String, AnyRef]], processId: String, invalidCodes: util.List[String])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[util.List[util.Map[String, AnyRef]]] = {
		Future {
			contents.asScala.map(content => {
				val source: String = content.getOrDefault(ImportConstants.SOURCE, "").toString
				val reqMetadata: util.Map[String, AnyRef] = content.getOrDefault(ImportConstants.METADATA, new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
				val sourceMetadata: util.Map[String, AnyRef] = getMetadata(source)
				val finalMetadata: util.Map[String, AnyRef] = if (MapUtils.isNotEmpty(sourceMetadata)) {
					sourceMetadata.putAll(reqMetadata)
					sourceMetadata.put(ImportConstants.SOURCE, source)
					sourceMetadata
				} else reqMetadata
				finalMetadata.put(ImportConstants.PROCESS_ID, processId)
				if (!validateMetadata(finalMetadata))
					invalidCodes.add(finalMetadata.getOrDefault(ImportConstants.CODE, "").asInstanceOf[String])
				content.put(ImportConstants.METADATA, finalMetadata)
				content
			}).asJava
		}
	}

	def getRequest(request: Request): util.List[util.Map[String, AnyRef]] = {
		val req = request.getRequest.get(ImportConstants.CONTENT)
		req match {
			case req: util.List[util.Map[String, AnyRef]] => req
			case req: util.Map[String, AnyRef] => new util.ArrayList[util.Map[String, AnyRef]]() {
				{
					add(req)
				}
			}
			case _ => throw new ClientException(ImportErrors.ERR_INVALID_IMPORT_REQUEST, ImportErrors.ERR_INVALID_IMPORT_REQUEST_MSG)
		}
	}

	def getMetadata(source: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): util.Map[String, AnyRef] = {
		if (StringUtils.isNotBlank(source)) {
			val response: Response = oec.httpUtil.get(source, "", new util.HashMap[String, String]())
			if (null != response && response.getResponseCode.code() == 200)
				response.getResult.getOrDefault(ImportConstants.CONTENT, new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
			else throw new ClientException(ImportErrors.ERR_READ_SOURCE, ImportErrors.ERR_READ_SOURCE_MSG + response.getResponseCode)
		} else new util.HashMap[String, AnyRef]()
	}

	def validateMetadata(metadata: util.Map[String, AnyRef]): Boolean = {
		val reqFields = REQUIRED_PROPS.asScala.filter(x => null == metadata.get(x)).toList
		reqFields.isEmpty
	}

	def getInstructionEvent(identifier: String, source: String, metadata: util.Map[String, AnyRef], collection: util.List[util.Map[String, AnyRef]]): String = {
		val actor = mapAsJavaMap[String, AnyRef](Map[String, AnyRef]("id" -> "Auto Creator", "type" -> "System"))
		val context = mapAsJavaMap[String, AnyRef](Map[String, AnyRef]("pdata" -> mapAsJavaMap(Map[String, AnyRef]("id" -> "org.sunbird.platform", "ver" -> "1.0", "env" -> Platform.getString("cloud_storage.env", "dev"))), ImportConstants.CHANNEL -> metadata.getOrDefault(ImportConstants.CHANNEL, "")))
		val objectData = mapAsJavaMap[String, AnyRef](Map[String, AnyRef]("id" -> identifier, "ver" -> metadata.get(ImportConstants.VERSION_KEY)))
		val edata = mapAsJavaMap[String, AnyRef](Map[String, AnyRef]("action" -> "auto-create", "iteration" -> 1.asInstanceOf[AnyRef], ImportConstants.OBJECT_TYPE -> metadata.getOrDefault(ImportConstants.OBJECT_TYPE, "Content").asInstanceOf[String],
			if (StringUtils.isNotBlank(source)) ImportConstants.REPOSITORY -> source else ImportConstants.IDENTIFIER -> identifier, ImportConstants.METADATA -> metadata, if (CollectionUtils.isNotEmpty(collection)) ImportConstants.COLLECTION -> collection else ImportConstants.COLLECTION -> List().asJava))
		val kafkaEvent: String = LogTelemetryEventUtil.logInstructionEvent(actor, context, objectData, edata)
		if (StringUtils.isBlank(kafkaEvent)) throw new ClientException(ImportErrors.BE_JOB_REQUEST_EXCEPTION, ImportErrors.ERR_INVALID_IMPORT_REQUEST_MSG)
		kafkaEvent
	}

	def pushInstructionEvent(graphId: String, content: util.Map[String, AnyRef])(implicit oec: OntologyEngineContext): Unit = {
		val source: String = content.getOrDefault(ImportConstants.SOURCE, "").toString
		//TODO: Enhance identifier extraction logic for handling any query param, if present in source
		val identifier = if (StringUtils.isNotBlank(source)) source.substring(source.lastIndexOf('/') + 1) else Identifier.getIdentifier(graphId, Identifier.getUniqueIdFromTimestamp)
		val metadata = content.getOrDefault(ImportConstants.METADATA, new util.HashMap()).asInstanceOf[util.Map[String, AnyRef]]
		val collection = content.getOrDefault(ImportConstants.COLLECTION, new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]]
		val event = getInstructionEvent(identifier, source, metadata, collection)
		oec.kafkaClient.send(event, AUTO_CREATE_TOPIC_NAME)
	}

}
