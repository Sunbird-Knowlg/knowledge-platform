package org.sunbird.mimetype.mgr.impl

import java.io.File

import org.sunbird.common.Slug
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}
import org.sunbird.telemetry.logger.TelemetryManager

import scala.concurrent.{ExecutionContext, Future}

object H5PMimeTypeMgrImpl extends BaseMimeTypeManager with MimeTypeManager {
    val mgr = new BaseMimeTypeManager()

    val IDX_S3_KEY = 0

    val IDX_S3_URL = 1

    override def upload(objectId: String, node: Node, uploadFile: File)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
        validateUploadRequest(objectId, node, uploadFile)
        if (isValidPackageStructure(uploadFile, List[String]("h5p.json"))) {
            val extractionBasePath = getBasePath(objectId)
            val zippedFileName = createH5PZipFile(extractionBasePath, uploadFile, objectId)
            val zipFile = new File(zippedFileName)
            uploadAndUpdateNode(zipFile, node, objectId)
            if (zipFile.exists) zipFile.delete
            mgr.extractPackageInCloud(objectId, uploadFile, node, "snapshot", false)
            Future(Map[String, AnyRef]("identifier" -> objectId, "artifactUrl" -> node.getMetadata.get("artifactUrl").asInstanceOf[String], "size" -> getFileSize(uploadFile).asInstanceOf[AnyRef]))
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
    private def createH5PZipFile(extractionBasePath: String, uploadFiled: File, objectId: String): String = {
        // Download the H5P Libraries and Un-Zip the H5P Library Files
        mgr.extractH5pPackage(objectId, extractionBasePath)
        // UnZip the Content Package
        mgr.extractPackage(uploadFiled, extractionBasePath)
        /*					List<Path> paths = Files.walk(Paths.get(extractionBasePath)).filter(Files::isRegularFile)
                                    .collect(Collectors.toList());
                            List<File> files = new ArrayList<File>();
                            for (Path path : paths)
                                files.add(path.toFile());*/
        // Create 'ZIP' Package
        val zipFileName = extractionBasePath + File.separator + System.currentTimeMillis + "_" + Slug.makeSlug(objectId) + "FILENAME_EXTENSION_SEPARATOR" + "DEFAULT_ZIP_EXTENSION"
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
    private def uploadAndUpdateNode(zipFile: File, node: Node, identifier: String) = {
        val urls: Array[String] = mgr.uploadArtifactToCloud(zipFile, identifier)
        node.getMetadata.put("s3Key", urls(IDX_S3_KEY))
        node.getMetadata.put("artifactUrl", urls(IDX_S3_URL))
    }
}
