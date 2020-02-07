package org.sunbird.mimetype.mgr

import java.io.File

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import org.sunbird.cloudstore.CloudStore
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.common.{Platform, Slug}
import org.sunbird.graph.dac.model.Node
import org.sunbird.telemetry.logger.TelemetryManager

import scala.concurrent.ExecutionContext

class BaseMimeTypeManager {

	protected val TEMP_FILE_LOCATION = Platform.getString("content.upload.temp_location", "/tmp/content")
	private val CONTENT_FOLDER = "cloud_storage.content.folder"
	private val ARTIFACT_FOLDER = "cloud_storage.artifact.folder"
	private val validator = new UrlValidator()

	protected val UPLOAD_DENIED_ERR_MSG = "FILE_UPLOAD_ERROR | Upload operation not supported for given mimeType"

	def validateUploadRequest(objectId: String, node: Node, data: AnyRef)(implicit ec: ExecutionContext): Unit = {
		if (StringUtils.isBlank(objectId))
			throw new ClientException("ERR_INVALID_ID", "Please Provide Valid Identifier!")
		if (null == node)
			throw new ClientException("ERR_INVALID_NODE", "Please Provide Valid Node!")
		if (null == data)
			throw new ClientException("ERR_INVALID_DATA", "Please Provide Valid File Or File Url!")
		if (data.isInstanceOf[String])
			validateUrl(data.toString)
		else if (data.isInstanceOf[File])
			validateFile(data.asInstanceOf[File])
	}

	def validateFile(file: File): Unit = {
		println("file ::::: " + file)
		//TODO: Complete Implementation
	}

	def validateUrl(fileUrl: String): Unit = {
		if (!validator.isValid(fileUrl))
			throw new ClientException("ERR_INVALID_FILE_URL", "Please Provide Valid File Url!")
	}

	def uploadArtifactToCloud(uploadedFile: File, identifier: String): Array[String] = {
		var urlArray = new Array[String](2)
		try {
			val folder = Platform.getString(CONTENT_FOLDER, "content") + File.separator + Slug.makeSlug(identifier, true) + File.separator + Platform.getString(ARTIFACT_FOLDER, "artifact")
			urlArray = CloudStore.uploadFile(folder, uploadedFile, true)
		} catch {
			case e: Exception =>
				TelemetryManager.error("Error while uploading the file.", e)
				throw new ServerException("ERR_CONTENT_UPLOAD_FILE", "Error while uploading the File.", e)
		}
		urlArray
	}

	def getBasePath(objectId: String): String = {
		if (!StringUtils.isBlank(objectId)) TEMP_FILE_LOCATION + File.separator + System.currentTimeMillis + "_temp" + File.separator + objectId else ""
	}

	def delete(file: File): Unit = {
		if (null != file && file.isDirectory)
			FileUtils.deleteDirectory(file)
		else file.delete()
	}

	def isInternalUrl(url: String): Boolean = {
		if (url.contains(CloudStore.getContainerName))
			true
		else
			false
	}
}

