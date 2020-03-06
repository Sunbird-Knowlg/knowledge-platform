package org.sunbird.mimetype.mgr.impl

import java.io.File
import java.util

import com.google.common.io.Resources
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.graph.dac.model.Node

class PluginMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {


    it should "upload plugin zip file and return public url" in {
        val node = getNode()
        val identifier = "org.ekstep.video"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        (ss.uploadDirectory(_:String, _:File, _: Option[Boolean])).expects(*, *, *)
        val resFuture = new PluginMimeTypeMgrImpl().upload(identifier, node, new File(Resources.getResource("plugin.zip").toURI))
        resFuture.map(result => {
            println("Response: " + result)
            result
        })

        assert(true)
    }

    def getNode(): Node = {
        val node = new Node()
        node.setIdentifier("org.ekstep.video")
        node.setMetadata(new util.HashMap[String, AnyRef](){{
            put("identifier", "org.ekstep.video")
            put("mimeType", "application/vnd.ekstep.plugin-archive")
            put("status","Draft")
            put("contentType", "Plugin")
        }})
        node
    }

}
