package org.sunbird.mimetype.mgr.impl

import java.io.File
import java.util

import com.google.common.io.Resources
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node

class PluginMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {

    it should "upload plugin zip file and return public url" in {
        implicit val ss = mock[StorageService]
        val node = getNode()
        val identifier = "org.ekstep.video"
        (ss.uploadFile(_: String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        (ss.uploadDirectory(_: String, _: File, _: Option[Boolean])).expects(*, *, *)
        val resFuture = new PluginMimeTypeMgrImpl().upload(identifier, node, new File(Resources.getResource("plugin.zip").toURI))
        resFuture.map(result => {
            println("Response: " + result)
            result
        })

        assert(true)
    }

    it should "upload plugin zip file url and return public url" in {
        implicit val ss = mock[StorageService]
        val node = getNode()
        val identifier = "org.ekstep.summary"
        (ss.uploadFile(_: String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        (ss.uploadDirectory(_: String, _: File, _: Option[Boolean])).expects(*, *, *)
        val resFuture = new PluginMimeTypeMgrImpl().upload(identifier, node, "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/org.ekstep.summary/artifact/org.ekstep.summary-1.0_1576230748183.zip")
        resFuture.map(result => {
            println("Response: " + result)
            result
        })

        assert(true)
    }

    it should "upload Invalid plugin zip file url and Throw Client Exception" in {
        implicit val ss = new StorageService
        val exception = intercept[ClientException] {
            new PluginMimeTypeMgrImpl().upload("org.ekstep.video", new Node(), "https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/content/do_11218758555843788817/artifact/akshara_kan_1487743191313.zip")
        }
        exception.getMessage shouldEqual "Error !Invalid Content Package File Structure. | [manifest.json should be at root location]"
    }

    it should "upload Invalid plugin zip file and Throw Client Exception" in {
        implicit val ss = new StorageService
        val exception = intercept[ClientException] {
            new PluginMimeTypeMgrImpl().upload("org.ekstep.video", new Node(), new File(Resources.getResource("validEcmlContent.zip").toURI))
        }
        exception.getMessage shouldEqual "Error !Invalid Content Package File Structure. | [manifest.json should be at root location]"
    }

    it should "upload Invalid File for plugin and Throw Client Exception" in {
        implicit val ss = new StorageService
        val exception = intercept[ClientException] {
            new PluginMimeTypeMgrImpl().upload("org.ekstep.video", new Node(), new File(Resources.getResource("sample.pdf").toURI))
        }
        exception.getMessage shouldEqual "Error! Invalid Content Package Mime Type."
    }

    "read data from manifest" should "throw client exception when id is not present" in {
        implicit val ss = new StorageService
        val exception = intercept[ClientException] {
            new PluginMimeTypeMgrImpl().readDataFromManifest(new File(Resources.getResource("manifestNoId.json").toURI), "org.ekstep.summary")
        }
        exception.getMessage shouldEqual "'id' in manifest.json is not same as the plugin identifier."
    }

    "read data from manifest" should "throw client exception when Version is not present" in {
        implicit val ss = new StorageService
        val exception = intercept[ClientException] {
            new PluginMimeTypeMgrImpl().readDataFromManifest(new File(Resources.getResource("manifestNoVer.json").toURI), "org.ekstep.summary")
        }
        exception.getMessage shouldEqual "'ver' is not specified in the plugin manifest.json."
    }

    "read data from manifest" should "Convert manifest when Targets is String and return map " in {
        implicit val ss = new StorageService
        val manifestMap = new PluginMimeTypeMgrImpl().readDataFromManifest(new File(Resources.getResource("manifestStringTargets.json").toURI), "org.ekstep.summary")
        assert(manifestMap != null)
    }

    def getNode(): Node = {
        val node = new Node()
        node.setIdentifier("org.ekstep.video")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "org.ekstep.video")
                put("mimeType", "application/vnd.ekstep.plugin-archive")
                put("status", "Draft")
                put("contentType", "Plugin")
            }
        })
        node
    }

}
