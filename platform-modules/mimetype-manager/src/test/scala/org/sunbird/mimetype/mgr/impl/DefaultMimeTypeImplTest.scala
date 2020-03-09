package org.sunbird.mimetype.mgr.impl

import java.io.File

import com.google.common.io.Resources
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node

class DefaultMimeTypeImplTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {

    "upload pdf file" should "upload pdf file and return public url" in {
        val node = new Node()
        node.setMetadata(new java.util.HashMap[String, AnyRef]() {{
            put("mimeType", "application/pdf")
        }})
        val identifier ="do_123"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        val resFuture = new DefaultMimeTypeMgrImpl().upload(identifier, node, new File(Resources.getResource("sample.pdf").toURI))
        resFuture.map(result => {
            println("Response: " + result)
            result
        })

        assert(true)
    }

    it should "upload apk fileurl and return public url" in {
        val node = new Node()
        val identifier = "do_123"
        implicit val ss = mock[StorageService]
        val resFuture = new DefaultMimeTypeMgrImpl().upload(identifier, node, "https://ekstep-public-prod.s3-ap-south-1.amazonaws.com/content/do_30083930/artifact/aser-6.0.0.17_215_1505458979_1505459188679.apk")
        resFuture.map(result => {
            println("Response: " + result)
            result
        })

        assert(true)
    }

    "upload pdf file" should "throw client error for mimeType mismatch" in {
        val node = new Node()
        node.setMetadata(new java.util.HashMap[String, AnyRef]() {{
            put("mimeType", "application/msword")
        }})
        implicit val ss = mock[StorageService]
        val exception = intercept[ClientException] {
            new DefaultMimeTypeMgrImpl().upload("do_123", node, new File(Resources.getResource("sample.pdf").toURI))
        }
        exception.getMessage shouldEqual "Uploaded File MimeType is not same as Node (Object) MimeType."
    }

}
