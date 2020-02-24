package org.sunbird.mimetype.mgr.impl

import java.io.{File, IOException}

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}

import scala.concurrent.{ExecutionContext, Future}

object DocumentMimeTypeMgrImpl extends BaseMimeTypeManager with MimeTypeManager {

	override def upload(objectId: String, node: Node, uploadFile: File)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		validateUploadRequest(objectId, node, uploadFile)
		val file: File =
			if (StringUtils.equalsAnyIgnoreCase("application/epub", node.getMetadata().getOrDefault("mimeType", "").asInstanceOf[String])) {
				val basePath = getBasePath(objectId) + "/index.epub"
				val tempFile = new File(basePath)
				try FileUtils.moveFile(uploadFile, tempFile)
				catch {
					case e: IOException => e.printStackTrace()
				}
				tempFile
			} else uploadFile
		val result: Array[String] = uploadArtifactToCloud(file, objectId)
		Future {
			Map("identifier" -> objectId, "artifactUrl" -> result(1), "cloudStorageKey" -> result(0), "size" -> getCloudStoredFileSize(result(0)))
		}
	}

	override def upload(objectId: String, node: Node, fileUrl: String)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		validateUploadRequest(objectId, node, fileUrl)
		Future {
			Map[String, AnyRef]("identifier" -> objectId, "artifactUrl" -> fileUrl)
		}
	}

}
