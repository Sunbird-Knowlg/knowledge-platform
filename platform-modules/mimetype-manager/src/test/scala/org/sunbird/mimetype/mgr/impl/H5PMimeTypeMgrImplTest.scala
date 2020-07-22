package org.sunbird.mimetype.mgr.impl

import java.io.File
import java.util

import com.google.common.io.Resources
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringUtils
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.BaseMimeTypeManager
import org.sunbird.models.UploadParams

import scala.concurrent.{ExecutionContext, Future}

class H5PMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {

    "H5PMimeTypeManager" should "upload and download H5P file with validation" in {
        implicit val ss = mock[StorageService]
        val exception = intercept[ClientException] {
            new H5PMimeTypeMgrImpl().upload("do_123", new Node(), new File(Resources.getResource("validEcmlContent.zip").toURI), None, UploadParams())
        }
        exception.getMessage shouldEqual "Please Provide Valid File!"
    }

    it should "create a H5P zip file with required dependencies to play" in {
        implicit val ss = new StorageService
        val mgr = new BaseMimeTypeManager
        var zipFile = ""
        try {
            val extractionBasePath = mgr.getBasePath("do_1234")
            zipFile = new H5PMimeTypeMgrImpl().createH5PZipFile(extractionBasePath, new File(Resources.getResource("valid_h5p_content.h5p").getPath), "do_1234")
            assert(StringUtils.isNotBlank(zipFile))
        } finally {
            FileUtils.deleteQuietly(new File(zipFile))
        }
    }

    it should "upload H5P zip file and return public url" in {
        val node = getNode()
        val identifier = "do_1234"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        (ss.uploadDirectoryAsync(_:String, _:File, _: Option[Boolean])(_: ExecutionContext)).expects(*, *, *, *).returns(Future(List(identifier, identifier)))
        val resFuture = new H5PMimeTypeMgrImpl().upload(identifier, node, new File(Resources.getResource("valid_h5p_content.h5p").toURI), None, UploadParams())
        resFuture.map(result => {
            assert("do_1234" == result.getOrElse("identifier", ""))
            assert(result.get("artifactUrl") != null)
            assert(result.get("s3Key") != null)
            assert(result.get("size") != null)
        })
    }

    it should "upload H5P zip file Url and return public url" in {
        val node = getNode()
        val identifier = "do_1234"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        (ss.uploadDirectoryAsync(_:String, _:File, _: Option[Boolean])(_: ExecutionContext)).expects(*, *, *, *).returns(Future(List(identifier, identifier)))
        val resFuture = new H5PMimeTypeMgrImpl().upload(identifier, node,"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/test-cases/valid_h5p_content.h5p", None, UploadParams())
        resFuture.map(result => {
            assert("do_1234" == result.getOrElse("identifier", "do_1234"))
            assert(result.get("artifactUrl") != null)
            assert(result.get("s3Key") != null)
            assert(result.get("size") != null)
        })
    }

    it should "upload H5P composed zip file url extension and return public url" in {
        val node = getNode()
        val identifier = "do_1234"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        (ss.uploadDirectoryAsync(_:String, _:File, _: Option[Boolean])(_: ExecutionContext)).expects(*, *, *, *).returns(Future(List(identifier, identifier)))
        val resFuture = new H5PMimeTypeMgrImpl().upload(identifier, node,"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11297886517561753611/artifact/1584334130903_do_11297886517561753611.zip", None, UploadParams(Some("composed-h5p-zip")))
        resFuture.map(result => {
            assert("do_1234" == result.getOrElse("identifier", "do_1234"))
            assert(result.get("artifactUrl") != null)
            assert(result.get("s3Key") != null)
            assert(result.get("size") != null)
        })
    }

    it should "upload H5P composed zip file url extension with old zips and return public url" in {
        val node = getNode()
        val identifier = "do_1234"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        (ss.uploadDirectoryAsync(_:String, _:File, _: Option[Boolean])(_: ExecutionContext)).expects(*, *, *, *).returns(Future(List(identifier, identifier)))
        val resFuture = new H5PMimeTypeMgrImpl().upload(identifier, node,"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_112499826618179584111/artifact/1525857774447_do_112499826618179584111.zip", None, UploadParams(Some("composed-h5p-zip")))
        resFuture.map(result => {
            assert("do_1234" == result.getOrElse("identifier", "do_1234"))
            assert(result.get("artifactUrl") != null)
            assert(result.get("s3Key") != null)
            assert(result.get("size") != null)
        })
    }

    it should "upload H5P composed zip file and return public url" in {
        val node = getNode()
        val identifier = "do_1234"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        (ss.uploadDirectoryAsync(_:String, _:File, _: Option[Boolean])(_: ExecutionContext)).expects(*, *, *, *).returns(Future(List(identifier, identifier)))
        val resFuture = new H5PMimeTypeMgrImpl().upload(identifier, node, new File(Resources.getResource("valid_composed_h5p.zip").toURI), None, UploadParams(Some("composed-h5p-zip")))
        resFuture.map(result => {
            assert("do_1234" == result.getOrElse("identifier", ""))
            assert(result.get("artifactUrl") != null)
            assert(result.get("s3Key") != null)
            assert(result.get("size") != null)
        })
    }

    def getNode(): Node = {
        val node = new Node()
        node.setIdentifier("do_1234")
        node.setMetadata(new util.HashMap[String, AnyRef](){{
            put("identifier", "do_1234")
            put("mimeType", "application/vnd.ekstep.h5p-archive")
            put("status","Draft")
            put("contentType", "Resource")
        }})
        node
    }
}
