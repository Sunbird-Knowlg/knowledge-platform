package org.sunbird.mimetype.mgr.impl

import java.io.File
import java.util

import com.google.common.io.Resources
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node

import scala.concurrent.ExecutionContext

class EcmlMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers with AsyncMockFactory{

    implicit val ss = mock[StorageService]

    it should "Throw Client Exception for null file url" in {
        val exception = intercept[ClientException] {
            new EcmlMimeTypeMgrImpl().upload("do_1234", getNode(), "")
        }
        exception.getMessage shouldEqual "Please Provide Valid File Url!"
    }

    it should "Throw Client Except for non zip file " in {
        val exception = intercept[ClientException] {
            new EcmlMimeTypeMgrImpl().upload("do_1234", getNode(), new File(Resources.getResource("sample.pdf").toURI))
        }
        exception.getMessage shouldEqual "INVALID_CONTENT_PACKAGE_FILE_MIME_TYPE_ERROR | [The uploaded package is invalid]"
    }


//    it should "upload ECML zip file and return public url" in {
//        val node = getNode()
//        val identifier = "do_1234"
//        implicit val ss = mock[StorageService]
//        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
//        (ss.uploadDirectoryAsync(_:String, _:File, _: Option[Boolean])(_: ExecutionContext)).expects(*, *, *, *)
//        val resFuture = new EcmlMimeTypeMgrImpl().upload(identifier, node, new File(Resources.getResource("validEcmlContent.zip").toURI))
//        resFuture.map(result => {
//            println("Response: " + result)
//            result
//            assert(null != result)
//            assert(result.nonEmpty)
//            assert("do_123" == result.getOrElse("identifier",""))
////            assert(inputUrl == result.getOrElse("artifactUrl",""))
//        })
//
//        assert(true)
//    }

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
