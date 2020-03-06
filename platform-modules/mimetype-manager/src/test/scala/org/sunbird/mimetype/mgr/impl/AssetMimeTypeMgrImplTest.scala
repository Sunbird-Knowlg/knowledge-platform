package org.sunbird.mimetype.mgr.impl

import java.io.File
import java.util
import com.google.common.io.Resources
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node

class AssetMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers {

  implicit val ss: StorageService = new StorageService
  "upload with valid file" should "return artifactUrl with successful response" in {
    val node = getNode()
    val inputUrl = "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_123/artifact/human_vs_robot-.jpg"
    val resFuture = new AssetMimeTypeMgrImpl().upload("do_123", node, new File(Resources.getResource("filesToZip/human_vs_robot-.jpg").toURI))
    resFuture.map(result => {
      assert(null != result)
      assert(!result.isEmpty)
      assert("do_123" == result.getOrElse("identifier", ""))
      assert(inputUrl == result.getOrElse("artifactUrl", ""))
    })
  }

  "upload with file" should "throw client exception" in {
    val exception = intercept[ClientException] {
      new AssetMimeTypeMgrImpl().upload("do_123", new Node(), new File("/tmp/test.pdf"))
    }
    exception.getMessage shouldEqual "Please Provide Valid File!"
  }

  def getNode(): Node = {
    val node = new Node()
    node.setIdentifier("org.ekstep.video")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "org.ekstep.video")
        put("mimeType", "image/jpg")
        put("status", "Draft")
        put("contentType", "Plugin")
      }
    })
    node
  }

  "upload with valid fileUrl" should "return artifactUrl with successful response" in {
    val node = getNode()
    val inputUrl = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
    val resFuture = new AssetMimeTypeMgrImpl().upload("do_123", node, inputUrl)
    resFuture.map(result => {
      assert(null != result)
      assert(!result.isEmpty)
      assert("do_123" == result.getOrElse("identifier", ""))
      assert(inputUrl == result.getOrElse("artifactUrl", ""))
    })
  }
}