package org.sunbird.mimetype.mgr.impl

import java.io.File

import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node

class CollectionMimeTypeMgrImplTest  extends AsyncFlatSpec with Matchers {

	implicit val ss: StorageService = new StorageService

	"upload with file" should "throw client exception" in {
		val exception = intercept[ClientException] {
			new CollectionMimeTypeMgrImpl().upload("do_123", new Node(), new File("/tmp/test.pdf"))
		}
		exception.getMessage shouldEqual "FILE_UPLOAD_ERROR | Upload operation not supported for given mimeType"
	}

	"upload with fileUrl" should "throw client exception" in {
		val exception = intercept[ClientException] {
			new CollectionMimeTypeMgrImpl().upload("do_123", new Node(), "https://abc.com/content/sample.pdf")
		}
		exception.getMessage shouldEqual "FILE_UPLOAD_ERROR | Upload operation not supported for given mimeType"
	}
}
