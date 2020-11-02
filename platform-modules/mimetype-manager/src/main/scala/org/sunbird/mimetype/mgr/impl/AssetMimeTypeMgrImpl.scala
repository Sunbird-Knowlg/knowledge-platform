package org.sunbird.mimetype.mgr.impl

import java.io.File
import java.util

import org.apache.commons.lang3.StringUtils
import org.sunbird.models.UploadParams
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.Platform
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node
import org.sunbird.kafka.client.KafkaClient
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.telemetry.util.LogTelemetryEventUtil

import scala.concurrent.{ExecutionContext, Future}

class AssetMimeTypeMgrImpl(implicit ss: StorageService) extends BaseMimeTypeManager with MimeTypeManager {

	private val kfClient = new KafkaClient
	private val MEDIA_TYPE_LIST = List("image", "video")

	override def upload(objectId: String, node: Node, uploadFile: File, filePath: Option[String], params: UploadParams)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		validateUploadRequest(objectId, node, uploadFile)
		val fileMimeType = getFileMimeType(uploadFile)
		val nodeMimeType = node.getMetadata.getOrDefault("mimeType", "").asInstanceOf[String]
		TelemetryManager.log("Uploading Asset MimeType: " + fileMimeType)
		if (!StringUtils.equalsIgnoreCase(fileMimeType, nodeMimeType)) {
			TelemetryManager.log("Uploaded File MimeType is not same as Node (Object) MimeType. [Uploading MimeType: " + fileMimeType + " | Node (Object) MimeType: " + nodeMimeType + "]")
		}
		val result: Array[String] = uploadArtifactToCloud(uploadFile, objectId, filePath)
		//TODO: depreciate s3Key. use cloudStorageKey instead
		val mediaType = node.getMetadata.getOrDefault("mediaType", "").asInstanceOf[String]
		if (!MEDIA_TYPE_LIST.contains(mediaType) && null != node)
			pushInstructionEvent(node.getIdentifier, node)

		Future {
			Map("identifier" -> objectId, "artifactUrl" -> result(1), "downloadUrl" -> result(1), "cloudStorageKey" -> result(0), "s3Key" -> result(0), "size" -> getCloudStoredFileSize(result(0)).asInstanceOf[AnyRef])
		}
	}

	override def upload(objectId: String, node: Node, fileUrl: String, filePath: Option[String], params: UploadParams)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		validateUploadRequest(objectId, node, fileUrl)
		Future {Map[String, AnyRef]("identifier" -> objectId, "artifactUrl" -> fileUrl, "downloadUrl" -> fileUrl,"size" -> getMetadata(fileUrl).get("Content-Length"))}
	}

	@throws[Exception]
	private def pushInstructionEvent(identifier: String, node: Node): Unit = {
		val actor: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
		val context: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
		val objectData: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
		val edata: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
		generateInstructionEventMetadata(actor, context, objectData, edata, node, identifier)
		val beJobRequestEvent: String = LogTelemetryEventUtil.logInstructionEvent(actor, context, objectData, edata)
		val topic: String = Platform.getString("kafka.topics.instruction", "sunbirddev.learning.job.request")
		if (StringUtils.isBlank(beJobRequestEvent)) throw new ClientException("BE_JOB_REQUEST_EXCEPTION", "Event is not generated properly.")
		kfClient.send(beJobRequestEvent, topic)
	}

	private def generateInstructionEventMetadata(actor: util.Map[String, AnyRef], context: util.Map[String, AnyRef], objectData: util.Map[String, AnyRef], edata: util.Map[String, AnyRef], node: Node, identifier: String): Unit = {
		val metadata: util.Map[String, AnyRef] = node.getMetadata
		actor.put("id", "Asset Enrichment Samza Job")
		actor.put("type", "System")
		context.put("channel", metadata.get("channel"))
		context.put("pdata", new util.HashMap[String, AnyRef]() {
			{
				put("id", "org.sunbird.platform")
				put("ver", "1.0")
			}
		})
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
