package org.sunbird.mimetype.mgr.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import java.io.{File, FileOutputStream}
import java.util.zip.{ZipEntry, ZipOutputStream}
import org.apache.commons.io.FileUtils
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.models.UploadParams
import scala.concurrent.Future

class ScormMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    implicit val ss: StorageService = mock[StorageService]

    implicit val oec: OntologyEngineContext = stub[OntologyEngineContext]
    val scormMgr = new ScormMimeTypeMgrImpl()(ss) {
        override protected val TEMP_FILE_LOCATION: String = System.getProperty("java.io.tmpdir") + File.separator + "content"
    }

    def getNode(): Node = {
        val node = new Node()
        node.setMetadata(new java.util.HashMap[String, AnyRef]())
        node.getMetadata.put("mimeType", "application/vnd.ekstep.scorm-archive")
        node
    }

    def createZip(files: Map[String, String]): File = {
        val zipFile = File.createTempFile("scorm", ".zip")
        val zos = new ZipOutputStream(new FileOutputStream(zipFile))
        files.foreach { case (name, content) =>
            val entry = new ZipEntry(name)
            zos.putNextEntry(entry)
            zos.write(content.getBytes)
            zos.closeEntry()
        }
        zos.close()
        zipFile
    }

    // Happy path — valid SCORM zip with correct imsmanifest.xml and existing launch file succeeds
    "upload" should "succeed for a valid SCORM package" in {
        val manifest = """<manifest><organizations default="org"><organization identifier="org"><item identifierref="res"/></organization></organizations><resources><resource identifier="res" href="index.html"/></resources></manifest>"""
        val file = createZip(Map("imsmanifest.xml" -> manifest, "index.html" -> "<html></html>"))

        (ss.uploadFile(_: String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array("s3Key", "s3Url"))
        (ss.uploadDirectory(_: String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array("url"))

        scormMgr.upload("do_1", getNode(), file, None, UploadParams()).map { result =>
            result("launchFile") shouldBe "index.html"
            FileUtils.deleteQuietly(file)
            succeed
        }
    }

    "upload" should "succeed and return scoList for a multi-SCO SCORM package" in {
        val manifest = """<manifest><organizations default="org"><organization identifier="org"><item identifier="item1" identifierref="res1"><title>SCO 1</title></item><item identifier="item2" identifierref="res2"><title>SCO 2</title></item></organization></organizations><resources><resource identifier="res1" href="sco1/index.html"/><resource identifier="res2" href="sco2/index.html"/></resources></manifest>"""
        val file = createZip(Map("imsmanifest.xml" -> manifest, "sco1/index.html" -> "<html></html>", "sco2/index.html" -> "<html></html>"))

        (ss.uploadFile(_: String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array("s3Key", "s3Url"))
        (ss.uploadDirectory(_: String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array("url"))

        scormMgr.upload("do_1", getNode(), file, None, UploadParams()).map { result =>
            result("launchFile") shouldBe "sco1/index.html"
            val scoListJson = result("scoList").asInstanceOf[String]
            val scoList = mapper.readValue(scoListJson, classOf[List[Map[String, String]]])
            scoList.size shouldBe 2
            scoList.exists(sco => sco("identifier") == "item1" && sco("title") == "SCO 1" && sco("href") == "sco1/index.html") shouldBe true
            scoList.exists(sco => sco("identifier") == "item2" && sco("title") == "SCO 2" && sco("href") == "sco2/index.html") shouldBe true
            FileUtils.deleteQuietly(file)
            succeed
        }
    }

    // Missing manifest — zip without imsmanifest.xml throws ClientException synchronously;
    // wrap in Future{}.flatten so that .transform can handle it.
    "upload" should "throw ClientException for zip missing imsmanifest.xml" in {
        val file = createZip(Map("index.html" -> "<html></html>"))
        Future { scormMgr.upload("do_1", getNode(), file, None, UploadParams()) }.flatten.transform {
            case scala.util.Failure(e: ClientException) =>
                e.getErrCode shouldBe "ERR_INVALID_FILE"
                FileUtils.deleteQuietly(file)
                scala.util.Success(succeed)
            case scala.util.Failure(e) =>
                FileUtils.deleteQuietly(file)
                scala.util.Failure(new Exception(s"Expected ClientException, got ${e.getClass.getName}: ${e.getMessage}", e))
            case scala.util.Success(_) =>
                FileUtils.deleteQuietly(file)
                scala.util.Failure(new Exception("Expected ClientException, but upload succeeded"))
        }
    }

    // Missing launch file — manifest references a file absent from the zip throws ClientException
    "upload" should "throw ClientException when launch file is absent from package" in {
        val manifest = """<manifest><organizations default="org"><organization identifier="org"><item identifierref="res"/></organization></organizations><resources><resource identifier="res" href="missing.html"/></resources></manifest>"""
        val file = createZip(Map("imsmanifest.xml" -> manifest))
        Future { scormMgr.upload("do_1", getNode(), file, None, UploadParams()) }.flatten.transform {
            case scala.util.Failure(e: ClientException) =>
                e.getErrCode shouldBe "ERR_INVALID_FILE"
                FileUtils.deleteQuietly(file)
                scala.util.Success(succeed)
            case scala.util.Failure(e) =>
                FileUtils.deleteQuietly(file)
                scala.util.Failure(new Exception(s"Expected ClientException, got ${e.getClass.getName}: ${e.getMessage}", e))
            case scala.util.Success(_) =>
                FileUtils.deleteQuietly(file)
                scala.util.Failure(new Exception("Expected ClientException, but upload succeeded"))
        }
    }

    // Path traversal in manifest — manifest href "../../../etc/passwd" throws ClientException
    "upload" should "throw ClientException for href with path traversal" in {
        val manifest = """<manifest><organizations default="org"><organization identifier="org"><item identifierref="res"/></organization></organizations><resources><resource identifier="res" href="../../../etc/passwd"/></resources></manifest>"""
        val file = createZip(Map("imsmanifest.xml" -> manifest))
        Future { scormMgr.upload("do_1", getNode(), file, None, UploadParams()) }.flatten.transform {
            case scala.util.Failure(e: ClientException) =>
                e.getErrCode shouldBe "ERR_INVALID_FILE"
                FileUtils.deleteQuietly(file)
                scala.util.Success(succeed)
            case scala.util.Failure(e) =>
                FileUtils.deleteQuietly(file)
                scala.util.Failure(new Exception(s"Expected ClientException, got ${e.getClass.getName}: ${e.getMessage}", e))
            case scala.util.Success(_) =>
                FileUtils.deleteQuietly(file)
                scala.util.Failure(new Exception("Expected ClientException, but upload succeeded"))
        }
    }

    // review with no artifact — validate() throws ClientException("VALIDATOR_ERROR", ...) synchronously
    "review" should "throw ClientException when no artifactUrl is present" in {
        val node = getNode()
        Future { scormMgr.review("do_1", node) }.flatten.transform {
            case scala.util.Failure(e: ClientException) =>
                e.getErrCode shouldBe "VALIDATOR_ERROR"
                scala.util.Success(succeed)
            case scala.util.Failure(e) =>
                scala.util.Failure(new Exception(s"Expected ClientException, got ${e.getClass.getName}: ${e.getMessage}", e))
            case scala.util.Success(_) =>
                scala.util.Failure(new Exception("Expected ClientException, but review succeeded"))
        }
    }
}