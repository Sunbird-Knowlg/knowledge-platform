package org.sunbird.mimetype.mgr.impl

import java.io.File
import java.util

import com.google.common.io.Resources
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.graph.dac.model.Node

class ApkMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers with AsyncMockFactory  {

    it should "upload apk file and return public url" in {
        val node = getNode()
        val identifier = "do_123"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        val resFuture = new ApkMimeTypeMgrImpl().upload(identifier, node, new File(Resources.getResource("uploadAPK.apk").toURI))
        resFuture.map(result => {
            println("Response: " + result)
            result
        })

        assert(true)
    }

    it should "upload apk fileurl and return public url" in {
        val node = getNode()
        val identifier = "do_123"
        implicit val ss = mock[StorageService]
        val resFuture = new ApkMimeTypeMgrImpl().upload(identifier, node, "https://ekstep-public-prod.s3-ap-south-1.amazonaws.com/content/do_30083930/artifact/aser-6.0.0.17_215_1505458979_1505459188679.apk")
        resFuture.map(result => {
            println("Response: " + result)
            result
        })

        assert(true)
    }

    def getNode(): Node = {
        val node = new Node()
        node.setIdentifier("do_123")
        node.setMetadata(new util.HashMap[String, AnyRef](){{
            put("identifier", "do_123")
            put("mimeType", "application/vnd.android.package-archive")
            put("status","Draft")
            put("contentType", "Plugin")
        }})
        node
    }

}
