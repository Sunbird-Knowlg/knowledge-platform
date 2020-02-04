package org.sunbird.mimetype.mgr

import java.io.File

import org.sunbird.graph.dac.model.Node
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.common.exception.ClientException

class BaseMimeTypeManagerTest extends AsyncFlatSpec with Matchers {

	val mgr = new BaseMimeTypeManager

	"validateUploadRequest with empty data" should "throw ClientException" in {
		val exception = intercept[ClientException] {
			mgr.validateUploadRequest("do_123", new Node(), null)
		}
		exception.getMessage shouldEqual "Please Provide Valid File Or File Url!"
	}

	"getBasePath with valid objectId" should "return a valid path" in {
		val result = mgr.getBasePath("do_123")
		assert(result.contains("/tmp/content/"))
		assert(result.contains("_temp/do_123"))
	}

	"getFieNameFromURL" should "return file name from url" in {
		val result = mgr.getFieNameFromURL("http://abc.com/content/sample.pdf")
		assert(result.contains("sample_"))
		assert(result.endsWith(".pdf"))
	}

	"getFileSize with invalid file" should "return 0 size" in {
		assert(0==mgr.getFileSize(new File("/tmp/sample.pdf")))
	}

	"copyURLToFile with valid url" should "return a valid file" in {
		val result = mgr.copyURLToFile("do_123","https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf")
		assert(result.exists())
		assert(true)
	}

}
