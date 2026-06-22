package org.sunbird.mimetype.mgr.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import java.io.File
import java.nio.file.Paths
import javax.xml.parsers.SAXParserFactory
import play.api.libs.json.Json
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}
import org.sunbird.models.UploadParams
import org.sunbird.telemetry.logger.TelemetryManager
import java.util
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.xml.{Elem, NodeSeq}
import scala.xml.factory.XMLLoader

object ScormMimeTypeMgrImpl {
    private val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
}

class ScormMimeTypeMgrImpl(implicit ss: StorageService) extends BaseMimeTypeManager()(ss) with MimeTypeManager {

    override def upload(objectId: String, node: Node, uploadFile: File, filePath: Option[String], params: UploadParams)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
                validateUploadRequest(objectId, node, uploadFile)
                TelemetryManager.info("SCORM content upload for objectId:: " + objectId)
                val extractionBasePath = getBasePath(objectId)
                try {
                    if (isValidPackageStructure(uploadFile, List("imsmanifest.xml"))) {
                        extractPackage(uploadFile, extractionBasePath)
                        val manifestFile = new File(extractionBasePath + File.separator + "imsmanifest.xml")
                        
                        val scoList = getScoList(getSecureXml(manifestFile))

                        if (scoList.isEmpty) {
                            throw new ClientException("ERR_INVALID_FILE", "No SCOs found in imsmanifest.xml!")
                        }

                        // Validate all SCO hrefs up-front
                        scoList.foreach(sco => getValidatedLaunchFile(extractionBasePath, sco.getOrElse("href", "")))
                        
                        val launchFile = scoList.head.getOrElse("href", "")
        
                        val javaScoList = new util.ArrayList[util.Map[String, String]]()

                        scoList.foreach { sco =>
                                            val javaMap = new util.HashMap[String, String]()
                                            sco.foreach { case (k, v) => javaMap.put(k, v) }
                                            javaScoList.add(javaMap)
                                        }

                        val urls: Array[String] = uploadArtifactToCloud(uploadFile, objectId, filePath)
                        
                        extractPackageInCloud(objectId, uploadFile, node, "snapshot", false)
                        
                        Future(
                            Map[String, AnyRef](
                                "identifier"  -> objectId,
                                "artifactUrl" -> urls(IDX_S3_URL),
                                "s3Key"       -> urls(IDX_S3_KEY),
                                "size"        -> getFileSize(uploadFile).asInstanceOf[AnyRef],
                                "launchFile"  -> launchFile,
                                "scoList"     -> javaScoList 
                            )
                        )

                    } else {
                        TelemetryManager.error("ERR_INVALID_FILE:: " + "Invalid SCORM package structure: imsmanifest.xml not found! with file name: " + uploadFile.getName)
                        throw new ClientException("ERR_INVALID_FILE", "Invalid SCORM package: imsmanifest.xml is missing!")
                    }
                } finally {
                    delete(new File(extractionBasePath))
                }

    }

private def getValidatedLaunchFile(extractionBasePath: String, launchFile: String): String = {
    if (launchFile.isEmpty) {
        throw new ClientException("ERR_INVALID_FILE", "Invalid launch file path!")
    }

    val delimiterIndex = launchFile.indexWhere(c => c == '?' || c == '#')
    val cleanLaunchFile = if (delimiterIndex != -1) launchFile.substring(0, delimiterIndex) else launchFile

    val basePath = Paths.get(extractionBasePath)
    val launchPath = basePath.resolve(cleanLaunchFile).normalize()
    
    TelemetryManager.info(s"Validating launch file: basePath=$basePath, launchFile=$cleanLaunchFile, combinedPath=${launchPath.toAbsolutePath}")

    if (!launchPath.startsWith(basePath)) {
        TelemetryManager.error("ERR_INVALID_FILE:: Potential path traversal detected: " + cleanLaunchFile)
        throw new ClientException("ERR_INVALID_FILE", "Invalid launch file path!")
    }

    if (!launchPath.toFile.exists() || launchPath.toFile.isDirectory) {
        TelemetryManager.error("ERR_INVALID_FILE:: Launch file defined in imsmanifest.xml does not exist or is a directory: " + cleanLaunchFile)
        throw new ClientException("ERR_INVALID_FILE", "The launch file '" + cleanLaunchFile + "' specified in imsmanifest.xml is missing or invalid!")
    }

    launchFile
}

 private def getScoList(xml: Elem): List[Map[String, String]] = {
    (xml \\ "item").filter(item => (item \@ "identifierref").nonEmpty).map { item =>
        val ref = item \@ "identifierref"
        val title = (item \ "title").text
        val baseHref = (xml \\ "resource")
          .find(res => (res \@ "identifier") == ref)
          .map(_ \@ "href")
          .getOrElse("")
        
        val parameters = (item \@ "parameters")
                        .replace("&#61;", "=") 
                        .replace("&#64;", "@")  
                        .replace("&#63;", "?")   
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&apos;", "'")
        val finalHref = if (parameters.nonEmpty) baseHref + parameters else baseHref

        Map(
            "identifier" -> (item \@ "identifier"), 
            "title"      -> title, 
            "href"       -> finalHref,
            "parameters" -> parameters 
        )
    }.toList
}
    private def getSecureXml(manifestFile: File): Elem = {
        val spf = SAXParserFactory.newInstance()
        spf.setNamespaceAware(true)
        spf.setFeature("http://xml.org/sax/features/external-general-entities", false)
        spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        
        val saxParser = spf.newSAXParser()
        scala.xml.XML.withSAXParser(saxParser).loadFile(manifestFile)
    }

    

    override def upload(objectId: String, node: Node, fileUrl: String, filePath: Option[String], params: UploadParams)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
        validateUploadRequest(objectId, node, fileUrl)
        val file = copyURLToFile(objectId, fileUrl)
        upload(objectId, node, file, filePath, params)
    }

    override def review(objectId: String, node: Node)(implicit ec: ExecutionContext, ontologyEngineContext: OntologyEngineContext): Future[Map[String, AnyRef]] = {
        validate(node, "[SCORM file should be uploaded for further processing!]")
        Future(getEnrichedMetadata(node.getMetadata.getOrDefault("status", "").asInstanceOf[String]))
    }

    override def publish(objectId: String, node: Node)(implicit ec: ExecutionContext, ontologyEngineContext: OntologyEngineContext): Future[Map[String, AnyRef]] = {
        validate(node, "[SCORM file should be uploaded for further processing!]")
        Future(getEnrichedPublishMetadata(node.getMetadata.getOrDefault("status", "").asInstanceOf[String]))
    }
}
