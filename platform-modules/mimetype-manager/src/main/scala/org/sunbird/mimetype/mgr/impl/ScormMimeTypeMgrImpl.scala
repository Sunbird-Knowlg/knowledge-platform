package org.sunbird.mimetype.mgr.impl

import java.io.File
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}
import org.sunbird.models.UploadParams
import org.sunbird.telemetry.logger.TelemetryManager

import scala.concurrent.{ExecutionContext, Future}

class ScormMimeTypeMgrImpl(implicit ss: StorageService) extends BaseMimeTypeManager()(ss) with MimeTypeManager {

    override def upload(objectId: String, node: Node, uploadFile: File, filePath: Option[String], params: UploadParams)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
        validateUploadRequest(objectId, node, uploadFile)
        TelemetryManager.info("SCORM content upload for objectId:: " + objectId)

        if (isValidPackageStructure(uploadFile, List("imsmanifest.xml"))) {
            val extractionBasePath = getBasePath(objectId)
            extractPackage(uploadFile, extractionBasePath)
            val manifestFile = new File(extractionBasePath + File.separator + "imsmanifest.xml")
            val xml = scala.xml.XML.loadFile(manifestFile)
            // Use local name to ignore namespaces
            val launchFile = (xml \\ "_").find(node => node.label == "resource" && (node \@ "href").nonEmpty)
              .map(_ \@ "href")
              .getOrElse("index.html")

            if (!new File(extractionBasePath + File.separator + launchFile).exists()) {
                TelemetryManager.error("ERR_INVALID_FILE:: " + "Launch file defined in imsmanifest.xml does not exist: " + launchFile)
                throw new ClientException("ERR_INVALID_FILE", "The launch file '" + launchFile + "' specified in imsmanifest.xml is missing from the package!")
            }

            val urls: Array[String] = uploadArtifactToCloud(uploadFile, objectId, filePath)
            Future { Map[String, AnyRef]("identifier" -> objectId, "artifactUrl" -> urls(IDX_S3_URL), "size" -> getFileSize(uploadFile).asInstanceOf[AnyRef], "s3Key" -> urls(IDX_S3_KEY), "launchFile" -> launchFile) }
        } else {
            TelemetryManager.error("ERR_INVALID_FILE:: " + "Invalid SCORM package structure: imsmanifest.xml not found! with file name: " + uploadFile.getName)
            throw new ClientException("ERR_INVALID_FILE", "Invalid SCORM package: imsmanifest.xml is missing!")
        }
    }

    override def upload(objectId: String, node: Node, fileUrl: String, filePath: Option[String], params: UploadParams)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
        validateUploadRequest(objectId, node, fileUrl)
        val file = copyURLToFile(objectId, fileUrl)
        upload(objectId, node, file, filePath, params)
    }

    override def review(objectId: String, node: Node)(implicit ec: ExecutionContext, ontologyEngineContext: OntologyEngineContext): Future[Map[String, AnyRef]] = {
        validate(node, "| [SCORM file should be uploaded for further processing!]")
        Future(getEnrichedMetadata(node.getMetadata.getOrDefault("status", "").asInstanceOf[String]))
    }

    override def publish(objectId: String, node: Node)(implicit ec: ExecutionContext, ontologyEngineContext: OntologyEngineContext): Future[Map[String, AnyRef]] = {
        validate(node, "| [SCORM file should be uploaded for further processing!]")
        Future(getEnrichedPublishMetadata(node.getMetadata.getOrDefault("status", "").asInstanceOf[String]))
    }
}
