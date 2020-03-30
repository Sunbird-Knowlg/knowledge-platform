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

class HtmlMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {
    implicit val ss: StorageService = new StorageService

    "upload invalid html file" should "return client exception" in {
        val exception = intercept[ClientException] {
            new HtmlMimeTypeMgrImpl().upload("do_123", new Node(), new File(Resources.getResource("invalidHtmlContent.zip").toURI))
        }
        exception.getMessage shouldEqual "Please Provide Valid File!"
    }

    "upload invalid html file url" should "return client exception" in {
        val exception = intercept[ClientException] {
            new HtmlMimeTypeMgrImpl().upload("do_123", new Node(), "invalid.html")
        }
        exception.getMessage shouldEqual "Please Provide Valid File Url!"
    }

    it should "upload HTML zip file and return public url" in {
        val node = getNode()
        val identifier = "do_1234"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_: String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        (ss.uploadDirectory(_:String, _:File, _: Option[Boolean])).expects(*, *, *)
        val resFuture = new HtmlMimeTypeMgrImpl().upload(identifier, node, new File(Resources.getResource("validHtml.zip").toURI))
        resFuture.map(result => {
            assert(null != result)
            assert(result.nonEmpty)
            assert("do_1234" == result.getOrElse("identifier", ""))
            assert(result.get("artifactUrl") != null)
            assert(result.get("s3Key") != null)
            assert(result.get("size") != null)

        })
    }

    it should "upload HTML zip Url and return public url" in {
        val node = getNode()
        val identifier = "do_1234"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_: String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        (ss.uploadDirectory(_:String, _:File, _: Option[Boolean])).expects(*, *, *)
        val resFuture = new HtmlMimeTypeMgrImpl().upload(identifier, node, "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1126981080516608001180/artifact/1.-_1550062024041.zip")
        resFuture.map(result => {
            assert(null != result)
            assert(result.nonEmpty)
            assert("do_1234" == result.getOrElse("identifier", ""))
            assert(result.get("artifactUrl") != null)
            assert(result.get("s3Key") != null)
            assert(result.get("size") != null)

        })
    }

    def getNode(): Node = {
        val node = new Node()
        node.setIdentifier("do_1234")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_1234")
                put("mimeType", "application/vnd.ekstep.html-archive")
                put("status", "Draft")
                put("contentType", "Resource")
            }
        })
        node
    }

}
