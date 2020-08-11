package org.sunbird.mimetype.mgr.impl

import java.io.File
import java.util

import com.google.common.io.Resources
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.models.UploadParams
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node

import scala.concurrent.ExecutionContext

class EcmlMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers with AsyncMockFactory{

    implicit val ss = mock[StorageService]

    it should "Throw Client Exception for null file url" in {
        val exception = intercept[ClientException] {
            new EcmlMimeTypeMgrImpl().upload("do_1234", getNode(), "", None, UploadParams())
        }
        exception.getMessage shouldEqual "Please Provide Valid File Url!"
    }

    it should "Throw Client Except for non zip file " in {
        val exception = intercept[ClientException] {
            new EcmlMimeTypeMgrImpl().upload("do_1234", getNode(), new File(Resources.getResource("sample.pdf").toURI), None, UploadParams())
        }
        exception.getMessage shouldEqual "INVALID_CONTENT_PACKAGE_FILE_MIME_TYPE_ERROR | [The uploaded package is invalid]"
    }


    it should "upload ECML zip file and return public url" in {
        val node = getNode()
        val identifier = "do_1234"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier)).repeated(3)
        (ss.uploadDirectory(_:String, _:File, _: Option[Boolean])).expects(*, *, *)
        val resFuture = new EcmlMimeTypeMgrImpl().upload(identifier, node, new File(Resources.getResource("validecml.zip").toURI), None, UploadParams())
        resFuture.map(result => {
            assert(null != result)
            assert(result.nonEmpty)
            assert("do_123" == result.getOrElse("identifier",""))
        })

        assert(true)
    }

    it should "upload ECML with json zip file and return public url" in {
        val node = getNode()
        val identifier = "do_1234"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        (ss.uploadDirectory(_:String, _:File, _: Option[Boolean])).expects(*, *, *)
        val resFuture = new EcmlMimeTypeMgrImpl().upload(identifier, node, new File(Resources.getResource("validecml_withjson.zip").toURI), None, UploadParams())
        resFuture.map(result => {
            assert(null != result)
            assert(result.nonEmpty)
            assert("do_123" == result.getOrElse("identifier",""))
        })

        assert(true)
    }

    it should "upload ECML with json zip file URL and return public url" in {
        val node = getNode()
        val identifier = "do_1234"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        (ss.uploadDirectory(_:String, _:File, _: Option[Boolean])).expects(*, *, *)
        val resFuture = new EcmlMimeTypeMgrImpl().upload(identifier, node, "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/kp_ft_1594670084387/artifact/ecml_with_json.zip", None, UploadParams())
        resFuture.map(result => {
            assert(null != result)
            assert(result.nonEmpty)
            assert("do_123" == result.getOrElse("identifier",""))
        })

        assert(true)
    }

    def getNode(): Node = {
        val node = new Node()
        node.setIdentifier("do_1234")
        node.setMetadata(new util.HashMap[String, AnyRef](){{
            put("identifier", "do_1234")
            put("mimeType", "application/vnd.ekstep.ecml-archive")
            put("status","Draft")
            put("contentType", "Resource")
        }})
        node
    }
}
