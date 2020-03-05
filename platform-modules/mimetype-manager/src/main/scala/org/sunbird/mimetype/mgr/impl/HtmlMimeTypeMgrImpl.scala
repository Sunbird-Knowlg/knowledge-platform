package org.sunbird.mimetype.mgr.impl

import java.io.File

import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}
import org.sunbird.telemetry.logger.TelemetryManager

import scala.concurrent.{ExecutionContext, Future}

class HtmlMimeTypeMgrImpl(implicit ss: StorageService) extends BaseMimeTypeManager with MimeTypeManager {
    val mgr = new BaseMimeTypeManager()

    override def upload(objectId: String, node: Node, uploadFile: File)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
        validateUploadRequest(objectId, node, uploadFile)
        if (isValidPackageStructure(uploadFile, List[String]("index.html"))) {
            val urls = mgr.uploadArtifactToCloud(uploadFile, objectId)
            node.getMetadata.put("s3Key", urls(IDX_S3_KEY))
            node.getMetadata.put("artifactUrl", urls(IDX_S3_URL))
            mgr.extractPackageInCloud(objectId, uploadFile, node, "snapshot", false)
            Future(Map[String, AnyRef]("identifier" -> objectId, "artifactUrl" -> urls(IDX_S3_URL), "s3Key" -> urls(IDX_S3_KEY), "size" -> getFileSize(uploadFile).asInstanceOf[AnyRef]))

        } else {
            TelemetryManager.error("ERR_INVALID_FILE" + "Please Provide Valid File! with file name: " + uploadFile.getName)
            throw new ClientException("ERR_INVALID_FILE", "Please Provide Valid File!")
        }

    }

    override def upload(objectId: String, node: Node, fileUrl: String)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
        validateUploadRequest(objectId, node, fileUrl)
        val file = copyURLToFile(objectId, fileUrl)
        upload(objectId, node, file)
    }

}
