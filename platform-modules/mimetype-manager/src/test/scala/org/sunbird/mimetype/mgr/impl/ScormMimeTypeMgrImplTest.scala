package org.sunbird.mimetype.mgr.impl

import com.google.common.io.Resources
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node
import org.sunbird.models.UploadParams

import java.io.File
import java.util
import scala.concurrent.Future

class ScormMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {

    implicit val ss = mock[StorageService]

    it should "Throw Client Exception for null file" in {
        val exception = intercept[ClientException] {
            new ScormMimeTypeMgrImpl().upload("do_1234", getNode(), new File(""), None, UploadParams())
        }
        exception.getMessage shouldEqual "Please Provide Valid File!"
    }

    def getNode(): Node = {
        val node = new Node()
        node.setIdentifier("do_1234")
        node.setMetadata(new util.HashMap[String, AnyRef](){{
            put("identifier", "do_1234")
            put("mimeType", "application/vnd.ekstep.scorm-archive")
            put("status","Draft")
            put("contentType", "Resource")
        }})
        node
    }
}
