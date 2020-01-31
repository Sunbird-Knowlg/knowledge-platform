package org.sunbird.mimetype.mgr.impl
import org.sunbird.graph.dac.model.Node
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.common.exception.{ClientException}

class DocumentMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers {

	"upload with valid file url" should "return artifactUrl with successful response" in {
		val inputUrl = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
		val resFuture = DocumentMimeTypeMgrImpl.upload("do_123", new Node(), inputUrl)
		resFuture.map(result => {
			assert(null != result)
			assert(!result.isEmpty)
			assert("do_123" == result.getOrElse("identifier",""))
			assert(inputUrl == result.getOrElse("artifactUrl",""))
		})
	}

	"upload with invalid file url" should "return client exception" in {
		val exception = intercept[ClientException] {
			DocumentMimeTypeMgrImpl.upload("do_123", new Node(), "abcd")
		}
		exception.getMessage shouldEqual "Please Provide Valid File Url!"
	}
}
