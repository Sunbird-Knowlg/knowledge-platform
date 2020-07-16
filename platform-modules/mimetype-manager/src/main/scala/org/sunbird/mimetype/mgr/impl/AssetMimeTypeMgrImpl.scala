package org.sunbird.mimetype.mgr.impl

import java.io.File

import org.apache.commons.lang3.StringUtils
import org.sunbird.models.UploadParams
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}
import org.sunbird.telemetry.logger.TelemetryManager

import scala.concurrent.{ExecutionContext, Future}

class AssetMimeTypeMgrImpl(implicit ss: StorageService) extends BaseMimeTypeManager with MimeTypeManager {

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
		Future {
			Map("identifier" -> objectId, "artifactUrl" -> result(1), "downloadUrl" -> result(1), "cloudStorageKey" -> result(0), "s3Key" -> result(0), "size" -> getCloudStoredFileSize(result(0)).asInstanceOf[AnyRef])
		}
	}

	override def upload(objectId: String, node: Node, fileUrl: String, filePath: Option[String], params: UploadParams)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		validateUploadRequest(objectId, node, fileUrl)
		Future {Map[String, AnyRef]("identifier" -> objectId, "artifactUrl" -> fileUrl, "downloadUrl" -> fileUrl,"size" -> getMetadata(fileUrl).get("Content-Length"))}
	}

}
