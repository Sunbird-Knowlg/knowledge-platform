package org.sunbird.mimetype.mgr.impl

import java.io.File

import org.sunbird.cloudstore.StorageService
import org.sunbird.common.Slug
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}
import org.sunbird.telemetry.logger.TelemetryManager

import scala.concurrent.{ExecutionContext, Future}

class H5PMimeTypeMgrImpl(implicit ss: StorageService) extends BaseMimeTypeManager()(ss) with MimeTypeManager {

    override def upload(objectId: String, node: Node, uploadFile: File)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
        validateUploadRequest(objectId, node, uploadFile)
        if (isValidPackageStructure(uploadFile, List[String]("h5p.json"))) {
            val extractionBasePath = getBasePath(objectId)
            val zippedFileName = createH5PZipFile(extractionBasePath, uploadFile, objectId)
            val zipFile = new File(zippedFileName)
            uploadAndUpdateNode(zipFile, node, objectId)
            if (zipFile.exists) zipFile.delete
            extractPackageInCloud(objectId, uploadFile, node, "snapshot", false)
            Future(Map[String, AnyRef]("identifier" -> objectId, "artifactUrl" -> node.getMetadata.get("artifactUrl").asInstanceOf[String], "size" -> getFileSize(uploadFile).asInstanceOf[AnyRef], "s3Key" -> node.getMetadata.get("s3Key")))
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
        extractPackage(uploadFiled, extractionBasePath)
        // Create 'ZIP' Package
        val zipFileName = extractionBasePath + File.separator + System.currentTimeMillis + "_" + Slug.makeSlug(objectId) + FILENAME_EXTENSION_SEPARATOR + DEFAULT_ZIP_EXTENSION
        createZipPackage(extractionBasePath, zipFileName)
        zipFileName
    }

    /**
      *
      * @param zipFile
      * @param node
      * @param identifier
      * @return
      */
    private def uploadAndUpdateNode(zipFile: File, node: Node, identifier: String)(implicit ss: StorageService) = {
        val urls: Array[String] = uploadArtifactToCloud(zipFile, identifier)
        node.getMetadata.put("s3Key", urls(IDX_S3_KEY))
        node.getMetadata.put("artifactUrl", urls(IDX_S3_URL))
    }
}
