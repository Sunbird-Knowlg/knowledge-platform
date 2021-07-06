package org.sunbird.content.upload.mgr

import java.io.File
import java.util

import org.apache.commons.lang3.StringUtils
import org.sunbird.models.UploadParams
import org.sunbird.common.Platform
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.content.util.ContentConstants
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.mimetype.factory.MimeTypeManagerFactory
import org.sunbird.telemetry.util.LogTelemetryEventUtil

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.{ExecutionContext, Future}
import org.sunbird.kafka.client.KafkaClient

import scala.collection.Map

object UploadManager {

	private val MEDIA_TYPE_LIST = List("image", "video")
	private val kfClient = new KafkaClient
	private val CONTENT_ARTIFACT_ONLINE_SIZE: Double = Platform.getDouble("content.artifact.size.for_online", 209715200.asInstanceOf[Double])


	def upload(request: Request, node: Node)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		val identifier: String = node.getIdentifier
		val fileUrl: String = request.getRequest.getOrDefault("fileUrl", "").asInstanceOf[String]
		val file = request.getRequest.get("file").asInstanceOf[File]
		val reqFilePath: String = request.getRequest.getOrDefault("filePath", "").asInstanceOf[String].replaceAll("^/+|/+$", "")
		val filePath = if(StringUtils.isBlank(reqFilePath)) None else Option(reqFilePath)
		val mimeType = node.getMetadata().getOrDefault("mimeType", "").asInstanceOf[String]
		val mediaType = node.getMetadata.getOrDefault("mediaType", "").asInstanceOf[String]
		val mgr = MimeTypeManagerFactory.getManager(node.getObjectType, mimeType)
		val params: UploadParams = request.getContext.get("params").asInstanceOf[UploadParams]
		val uploadFuture: Future[Map[String, AnyRef]] = if (StringUtils.isNotBlank(fileUrl)) mgr.upload(identifier, node, fileUrl, filePath, params) else mgr.upload(identifier, node, file, filePath, params)
		uploadFuture.map(result => {
			if(filePath.isDefined)
				updateNode(request, node.getIdentifier, mediaType, node.getObjectType, result + (ContentConstants.ARTIFACT_BASE_PATH -> filePath.get))
			else
				updateNode(request, node.getIdentifier, mediaType, node.getObjectType, result)
		}).flatMap(f => f)
	}

	def updateNode(request: Request, identifier: String, mediaType: String, objectType: String, result: Map[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		val updatedResult = result - "identifier"
		val artifactUrl = updatedResult.getOrElse("artifactUrl", "").asInstanceOf[String]
		val size: Double = updatedResult.getOrElse("size", 0.asInstanceOf[Double]).asInstanceOf[Double]
		if (StringUtils.isNotBlank(artifactUrl)) {
			val updateReq = new Request(request)
			updateReq.getContext().put("identifier", identifier)
			updateReq.getRequest.putAll(mapAsJavaMap(updatedResult))
			if( size > CONTENT_ARTIFACT_ONLINE_SIZE)
				updateReq.put("contentDisposition", "online-only")
			if (StringUtils.equalsIgnoreCase("Asset", objectType) && MEDIA_TYPE_LIST.contains(mediaType))
				updateReq.put("status", "Processing")

			DataNode.update(updateReq).map(node => {
				if (StringUtils.equalsIgnoreCase("Asset", objectType) && MEDIA_TYPE_LIST.contains(mediaType) && null != node)
					pushInstructionEvent(identifier, node)
				getUploadResponse(node)
			})
		} else {
			Future {
				ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, "ERR_UPLOAD_FILE", "Something Went Wrong While Processing Your Request.")
			}
		}
	}

	def getUploadResponse(node: Node)(implicit ec: ExecutionContext): Response = {
		val id = node.getIdentifier.replace(".img", "")
		val url = node.getMetadata.get("artifactUrl").asInstanceOf[String]
		ResponseHandler.OK.put("node_id", id).put("identifier", id).put("artifactUrl", url)
			.put("content_url", url).put("versionKey", node.getMetadata.get("versionKey"))
	}

	@throws[Exception]
	private def pushInstructionEvent(identifier: String, node: Node): Unit = {
		val actor: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
		val context: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
		val objectData: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
		val edata: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
		generateInstructionEventMetadata(actor, context, objectData, edata, node, identifier)
		val beJobRequestEvent: String = LogTelemetryEventUtil.logInstructionEvent(actor, context, objectData, edata)
		val topic: String = Platform.getString("kafka.topics.instruction","sunbirddev.learning.job.request")
		if (StringUtils.isBlank(beJobRequestEvent)) throw new ClientException("BE_JOB_REQUEST_EXCEPTION", "Event is not generated properly.")
		kfClient.send(beJobRequestEvent, topic)
	}

	private def generateInstructionEventMetadata(actor: util.Map[String, AnyRef], context: util.Map[String, AnyRef], objectData: util.Map[String, AnyRef], edata: util.Map[String, AnyRef], node: Node, identifier: String): Unit = {
		val metadata: util.Map[String, AnyRef] = node.getMetadata
		actor.put("id", "Asset Enrichment Samza Job")
		actor.put("type", "System")
		context.put("channel", metadata.get("channel"))
		context.put("pdata", new util.HashMap[String, AnyRef]() {{
				put("id", "org.sunbird.platform")
				put("ver", "1.0")
			}})
		if (Platform.config.hasPath("cloud_storage.env")) {
			val env: String = Platform.getString("cloud_storage.env", "dev")
			context.put("env", env)
		}
		objectData.put("id", identifier)
		objectData.put("ver", metadata.get("versionKey"))
		edata.put("action", "assetenrichment")
		edata.put("status", metadata.get("status"))
		edata.put("mediaType", metadata.get("mediaType"))
		edata.put("objectType", node.getObjectType)
	}
}
