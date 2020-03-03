package org.sunbird.mimetype.mgr.impl

import java.io.File
import java.util

import com.google.common.io.Resources
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node

class H5PMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers with AsyncMockFactory /*with MockitoSugar */{

    "upload invalid H5P file" should "return client exception" in {
        val exception = intercept[ClientException] {
            H5PMimeTypeMgrImpl.upload("do_123", new Node(), new File(Resources.getResource("validEcmlContent.zip").toURI))
        }
        exception.getMessage shouldEqual "Please Provide Valid File!"
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
