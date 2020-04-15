package org.sunbird.mimetype.mgr.impl

import java.io.File
import java.util.concurrent.CompletionException

import org.sunbird.cloudstore.StorageService
import org.sunbird.common.Slug
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}
import org.sunbird.telemetry.logger.TelemetryManager

import scala.concurrent.{ExecutionContext, Future}

class H5PMimeTypeMgrImpl(implicit ss: StorageService) extends BaseMimeTypeManager()(ss) with MimeTypeManager {

    override def upload(objectId: String, node: Node, uploadFile: File, filePath: Option[String])(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
        validateUploadRequest(objectId, node, uploadFile)
        if (isValidPackageStructure(uploadFile, List[String]("h5p.json"))) {
            val extractionBasePath = getBasePath(objectId)
            val zippedFileName = createH5PZipFile(extractionBasePath, uploadFile, objectId)
            val zipFile = new File(zippedFileName)
            val urls: Array[String] = uploadArtifactToCloud(zipFile, objectId, filePath)
            if (zipFile.exists) zipFile.delete
            extractH5PPackageInCloud(objectId, extractionBasePath, node, "snapshot", false).map(resp =>
                Map[String, AnyRef]("identifier" -> objectId, "artifactUrl" -> urls(IDX_S3_URL), "size" -> getFileSize(uploadFile).asInstanceOf[AnyRef], "s3Key" -> urls(IDX_S3_KEY))
            ) recoverWith { case e: CompletionException => throw e.getCause }
        } else {
            TelemetryManager.error("ERR_INVALID_FILE" + "Please Provide Valid File! with file name: " + uploadFile.getName)
            throw new ClientException("ERR_INVALID_FILE", "Please Provide Valid File!")
        }
    }

    override def upload(objectId: String, node: Node, fileUrl: String, filePath: Option[String])(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
        validateUploadRequest(objectId, node, fileUrl)
        val file = copyURLToFile(objectId, fileUrl)
        upload(objectId, node, file, filePath)
    }

    /**
      *
      * @param extractionBasePath
      * @param uploadFiled
      * @param objectId
      * @return
      */
    def createH5PZipFile(extractionBasePath: String, uploadFiled: File, objectId: String): String = {
        // Download the H5P Libraries and Un-Zip the H5P Library Files
        extractH5pPackage(objectId, extractionBasePath)
        // UnZip the Content Package
        extractPackage(uploadFiled, extractionBasePath + File.separator + "content")
        // Create 'ZIP' Package
        val zipFileName = extractionBasePath + File.separator + System.currentTimeMillis + "_" + Slug.makeSlug(objectId) + FILENAME_EXTENSION_SEPARATOR + DEFAULT_ZIP_EXTENSION
        createZipPackage(extractionBasePath, zipFileName)
        zipFileName
    }

    def copyH5P(uploadFile: File, node: Node)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
        val objectId: String = node.getIdentifier
        val extractionBasePath = getBasePath(objectId)
        extractPackage(uploadFile, extractionBasePath)
        val urls: Array[String] = uploadArtifactToCloud(uploadFile, objectId, None)
        node.getMetadata.put("s3Key", urls(IDX_S3_KEY))
        node.getMetadata.put("artifactUrl", urls(IDX_S3_URL))
        extractH5PPackageInCloud(objectId, extractionBasePath, node, "snapshot", false).map(resp =>
            Map[String, AnyRef]("identifier" -> objectId, "artifactUrl" -> urls(IDX_S3_URL), "size" -> getFileSize(uploadFile).asInstanceOf[AnyRef], "s3Key" -> urls(IDX_S3_KEY))
        ) recoverWith { case e: CompletionException => throw e.getCause }
    }

}
