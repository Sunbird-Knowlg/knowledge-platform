package org.sunbird.mimetype.mgr.impl

import java.io.File

import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node

class YouTubeMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers {
	implicit val ss: StorageService = new StorageService

	"upload with valid youtube url" should "return artifactUrl with successful response" in {
		val inputUrl = "https://www.youtube.com/watch?v=8irSFvoyLHQ"
		val resFuture = new YouTubeMimeTypeMgrImpl().upload("do_123", new Node(), inputUrl)
		resFuture.map(result => {
			assert(null != result)
			assert(!result.isEmpty)
			assert("do_123" == result.getOrElse("identifier",""))
			assert(inputUrl == result.getOrElse("artifactUrl",""))
		})
	}

	"upload with file" should "throw client exception" in {
		val exception = intercept[ClientException] {
			new YouTubeMimeTypeMgrImpl().upload("do_123", new Node(), new File("/tmp/test.pdf"))
		}
		exception.getMessage shouldEqual "FILE_UPLOAD_ERROR | Upload operation not supported for given mimeType"
	}

}
