package org.sunbird.`object`.importer

import java.util
import java.util.UUID

import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.`object`.importer.constant.ImportConstants
import org.sunbird.`object`.importer.error.ImportErrors
import org.sunbird.common.Platform
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.utils.ScalaJsonUtils
import org.sunbird.telemetry.util.LogTelemetryEventUtil

import scala.collection.JavaConversions.mapAsJavaMap
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}


case class ImportConfig(topicName: String, requestLimit: Integer, requiredProps: List[String], validContentStage: List[String], propsToRemove: List[String])

class ImportManager(config: ImportConfig) {

	def importObject(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
		val reqList: util.List[util.Map[String, AnyRef]] = getRequest(request)
		if (CollectionUtils.isEmpty(reqList))
			throw new ClientException(ImportErrors.ERR_INVALID_IMPORT_REQUEST, ImportErrors.ERR_INVALID_IMPORT_REQUEST_MSG)
		else if (CollectionUtils.isNotEmpty(reqList) && reqList.size > config.requestLimit)
			throw new ClientException(ImportErrors.ERR_REQUEST_LIMIT_EXCEED, ImportErrors.ERR_REQUEST_LIMIT_EXCEED_MSG + config.requestLimit)
		val processId: String = UUID.randomUUID().toString
		val invalidCodes: util.List[String] = new util.ArrayList[String]()
		val invalidStage: util.List[String] = new util.ArrayList[String]()
		validateAndGetRequest(reqList, processId, invalidCodes, invalidStage, request).map(objects => {
			if (CollectionUtils.isNotEmpty(invalidCodes)) {
				val msg = if (invalidCodes.asScala.filter(c => StringUtils.isNotBlank(c)).toList.size > 0) " | Required Property's Missing For " + invalidCodes else ""
				throw new ClientException(ImportErrors.ERR_REQUIRED_PROPS_VALIDATION, ImportErrors.ERR_REQUIRED_PROPS_VALIDATION_MSG + ScalaJsonUtils.serialize(config.requiredProps) + msg)
			} else if (CollectionUtils.isNotEmpty(invalidStage)) throw new ClientException(ImportErrors.ERR_OBJECT_STAGE_VALIDATION, ImportErrors.ERR_OBJECT_STAGE_VALIDATION_MSG + request.getContext.get("VALID_OBJECT_STAGE").asInstanceOf[java.util.List[String]])
			else {
				objects.asScala.map(obj => pushInstructionEvent(graphId, obj))
				val response = ResponseHandler.OK()
				response.put(ImportConstants.PROCESS_ID, processId)
				response
			}
		})

	}

	def validateAndGetRequest(objects: util.List[util.Map[String, AnyRef]], processId: String, invalidCodes: util.List[String], invalidStages: util.List[String], request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[util.List[util.Map[String, AnyRef]]] = {
		Future {
			objects.asScala.map(obj => {
				val source: String = obj.getOrDefault(ImportConstants.SOURCE, "").toString
				val stage: String = obj.getOrDefault(ImportConstants.STAGE, "").toString
				val reqMetadata: util.Map[String, AnyRef] = obj.getOrDefault(ImportConstants.METADATA, new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
				val sourceMetadata: util.Map[String, AnyRef] = getMetadata(source, request.getContext.get("objectType").asInstanceOf[String].toLowerCase())
				val finalMetadata: util.Map[String, AnyRef] = if (MapUtils.isNotEmpty(sourceMetadata)) {
					sourceMetadata.putAll(reqMetadata)
					sourceMetadata.put(ImportConstants.SOURCE, source)
					sourceMetadata
				} else reqMetadata
				val originData = finalMetadata.getOrDefault(ImportConstants.ORIGIN_DATA, new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
				finalMetadata.keySet().removeAll(config.propsToRemove.asJava)
				finalMetadata.put(ImportConstants.PROCESS_ID, processId)
				if (!validateMetadata(finalMetadata, config.requiredProps.asJava))
					invalidCodes.add(finalMetadata.getOrDefault(ImportConstants.CODE, "").asInstanceOf[String])
				if(!validateStage(stage, config.validContentStage.asJava)) invalidStages.add(finalMetadata.getOrDefault(ImportConstants.CODE, "").asInstanceOf[String])
				obj.put(ImportConstants.METADATA, finalMetadata)
				obj.put(ImportConstants.ORIGIN_DATA, originData)
				obj
			}).asJava
		}
	}

	def getRequest(request: Request): util.List[util.Map[String, AnyRef]] = {
		val req = request.getRequest.get(request.getObjectType.toLowerCase())
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

	def getMetadata(source: String, key: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): util.Map[String, AnyRef] = {
		if (StringUtils.isNotBlank(source)) {
			val response: Response = oec.httpUtil.get(source, "", new util.HashMap[String, String]())
			if (null != response && response.getResponseCode.code() == 200)
				response.getResult.getOrDefault(key, new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
			else throw new ClientException(ImportErrors.ERR_READ_SOURCE, ImportErrors.ERR_READ_SOURCE_MSG + response.getResponseCode)
		} else new util.HashMap[String, AnyRef]()
	}

	def validateMetadata(metadata: util.Map[String, AnyRef], requiredProps: util.List[String]): Boolean = {
		val reqFields = requiredProps.asScala.filter(x => null == metadata.get(x)).toList
		reqFields.isEmpty
	}

	def validateStage(stage: String, validObjectStage: util.List[String]): Boolean = if(StringUtils.isNotBlank(stage)) validObjectStage.contains(stage) else true

	def getInstructionEvent(identifier: String, source: String, metadata: util.Map[String, AnyRef], collection: util.List[util.Map[String, AnyRef]], stage: String, originData: util.Map[String, AnyRef]): String = {
		val actor = mapAsJavaMap[String, AnyRef](Map[String, AnyRef]("id" -> "Auto Creator", "type" -> "System"))
		val context = mapAsJavaMap[String, AnyRef](Map[String, AnyRef]("pdata" -> mapAsJavaMap(Map[String, AnyRef]("id" -> "org.sunbird.platform", "ver" -> "1.0", "env" -> Platform.getString("cloud_storage.env", "dev"))), ImportConstants.CHANNEL -> metadata.getOrDefault(ImportConstants.CHANNEL, "")))
		val objectData = mapAsJavaMap[String, AnyRef](Map[String, AnyRef]("id" -> identifier, "ver" -> metadata.get(ImportConstants.VERSION_KEY)))
		val edata = mutable.Map[String, AnyRef]("action" -> "auto-create", "iteration" -> 1.asInstanceOf[AnyRef], ImportConstants.OBJECT_TYPE -> metadata.getOrDefault(ImportConstants.OBJECT_TYPE, "").asInstanceOf[String],
			if (StringUtils.isNotBlank(source)) ImportConstants.REPOSITORY -> source else ImportConstants.IDENTIFIER -> identifier, ImportConstants.METADATA -> metadata, if (CollectionUtils.isNotEmpty(collection)) ImportConstants.COLLECTION -> collection else ImportConstants.COLLECTION -> List().asJava,
			ImportConstants.STAGE -> stage, if(StringUtils.isNotBlank(source) && MapUtils.isNotEmpty(originData)) ImportConstants.ORIGIN_DATA -> originData else ImportConstants.ORIGIN_DATA -> new util.HashMap[String, AnyRef]()).asJava
		val kafkaEvent: String = LogTelemetryEventUtil.logInstructionEvent(actor, context, objectData, edata)
		if (StringUtils.isBlank(kafkaEvent)) throw new ClientException(ImportErrors.BE_JOB_REQUEST_EXCEPTION, ImportErrors.ERR_INVALID_IMPORT_REQUEST_MSG)
		kafkaEvent
	}

	def pushInstructionEvent(graphId: String, obj: util.Map[String, AnyRef])(implicit oec: OntologyEngineContext): Unit = {
		val stage = obj.getOrDefault(ImportConstants.STAGE, "").toString
		val source: String = obj.getOrDefault(ImportConstants.SOURCE, "").toString
		//TODO: Enhance identifier extraction logic for handling any query param, if present in source
		val identifier = if (StringUtils.isNotBlank(source)) source.substring(source.lastIndexOf('/') + 1) else Identifier.getIdentifier(graphId, Identifier.getUniqueIdFromTimestamp)
		val metadata = obj.getOrDefault(ImportConstants.METADATA, new util.HashMap()).asInstanceOf[util.Map[String, AnyRef]]
		val collection = obj.getOrDefault(ImportConstants.COLLECTION, new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]]
		val originData = obj.getOrDefault(ImportConstants.ORIGIN_DATA, new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
		val event = getInstructionEvent(identifier, source, metadata, collection, stage, originData)
		oec.kafkaClient.send(event, config.topicName)
	}
}