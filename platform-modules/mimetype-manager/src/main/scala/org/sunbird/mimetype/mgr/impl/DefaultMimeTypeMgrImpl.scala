package org.sunbird.mimetype.mgr.impl

import java.io.File

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.exception.ClientException
import org.sunbird.cloudstore.StorageService
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}
import org.sunbird.telemetry.logger.TelemetryManager

import scala.concurrent.{ExecutionContext, Future}

class DefaultMimeTypeMgrImpl(implicit ss: StorageService) extends BaseMimeTypeManager with MimeTypeManager {

	override def upload(objectId: String, node: Node, uploadFile: File)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		validateUploadRequest(objectId, node, uploadFile)
		val nodeMimeType = node.getMetadata.getOrDefault("mimeType", "").asInstanceOf[String]
		//TODO: Throw Client Exception Here
		if (!isValidMimeType(uploadFile, nodeMimeType))
			throw new ClientException("VALIDATION_ERROR", "Uploaded File MimeType is not same as Node (Object) MimeType.");
		val result: Array[String] = uploadArtifactToCloud(uploadFile, objectId)
		//TODO: depreciate s3Key. use cloudStorageKey instead
		Future {
			Map("identifier" -> objectId, "artifactUrl" -> result(1), "downloadUrl" -> result(1), "cloudStorageKey" -> result(0), "s3Key" -> result(0), "size" -> getCloudStoredFileSize(result(0)).asInstanceOf[AnyRef])
		}
	}

	override def upload(objectId: String, node: Node, fileUrl: String)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		validateUploadRequest(objectId, node, fileUrl)
		val file = copyURLToFile(objectId, fileUrl)
		Future {
			Map[String, AnyRef]("identifier" -> objectId, "artifactUrl" -> fileUrl, "downloadUrl" -> fileUrl, "size" -> getFileSize(file).asInstanceOf[AnyRef])
		}
	}

}
