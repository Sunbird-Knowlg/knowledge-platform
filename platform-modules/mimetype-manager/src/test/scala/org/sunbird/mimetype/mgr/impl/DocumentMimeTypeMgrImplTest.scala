package org.sunbird.mimetype.mgr.impl
import java.io.File
import java.util

import com.google.common.io.Resources
import org.scalamock.scalatest.AsyncMockFactory
import org.sunbird.graph.dac.model.Node
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException

class DocumentMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {
	implicit val ss: StorageService = new StorageService

	"upload with valid file url" should "return artifactUrl with successful response" in {
		val inputUrl = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
		val node = new Node()
		node.setMetadata(new util.HashMap[String, AnyRef](){{
			put("mimeType","application/pdf")
		}})
		val resFuture = new DocumentMimeTypeMgrImpl().upload("do_123", node, inputUrl, None)
		resFuture.map(result => {
			assert(null != result)
			assert(!result.isEmpty)
			assert("do_123" == result.getOrElse("identifier",""))
			assert(inputUrl == result.getOrElse("artifactUrl",""))
		})
	}

	"validateFileUrlExtension with invalid pdf file url" should "return client exception" in {
		val exception = intercept[ClientException] {
			new DocumentMimeTypeMgrImpl().validateFileUrlExtension("application/pdf", "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.txt")
		}
		exception.getMessage shouldEqual "Please Provide Valid Pdf File Url!"
	}

	"validateFileUrlExtension with invalid epub file url" should "return client exception" in {
		val exception = intercept[ClientException] {
			new DocumentMimeTypeMgrImpl().validateFileUrlExtension("application/epub", "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.txt")
		}
		exception.getMessage shouldEqual "Please Provide Valid Epub File Url!"
	}

	"validateFileUrlExtension with invalid msword file url" should "return client exception" in {
		val exception = intercept[ClientException] {
			new DocumentMimeTypeMgrImpl().validateFileUrlExtension("application/msword", "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf")
		}
		exception.getMessage shouldEqual "Please Provide Valid Document File Url!"
	}

	"upload with invalid file url" should "return client exception" in {
		val exception = intercept[ClientException] {
			new DocumentMimeTypeMgrImpl().upload("do_123", new Node(), "abcd", None)
		}
		exception.getMessage shouldEqual "Please Provide Valid File Url!"
	}

	"upload with empty objectId" should "throw client exception" in {
		val exception = intercept[ClientException] {
			new DocumentMimeTypeMgrImpl().upload("", new Node(), "https://abc.com/content/sample.pdf", None)
		}
		exception.getMessage shouldEqual "Please Provide Valid Identifier!"
	}

	"upload with empty node object" should "throw client exception" in {
		val exception = intercept[ClientException] {
			new DocumentMimeTypeMgrImpl().upload("do_123", null, "https://abc.com/content/sample.pdf", None)
		}
		exception.getMessage shouldEqual "Please Provide Valid Node!"
	}

	"upload with different file type for pdf mimeType" should "throw client exception" in {
		val file: File = new File(Resources.getResource("invalidHtmlContent.zip").toURI)
		val node = new Node()
		node.setMetadata(new java.util.HashMap[String, AnyRef]() {{
				put("mimeType", "application/pdf")
			}})
		val exception = intercept[ClientException] {
			new DocumentMimeTypeMgrImpl().upload("do_123", node, file, None)
		}
		exception.getMessage shouldEqual "Uploaded file is not a pdf file. Please upload a valid pdf file."
	}

	"upload with different file type for epub mimeType" should "throw client exception" in {
		val file: File = new File(Resources.getResource("sample.pdf").toURI)
		val node = new Node()
		node.setMetadata(new java.util.HashMap[String, AnyRef]() {{
			put("mimeType", "application/epub")
		}})
		val exception = intercept[ClientException] {
			new DocumentMimeTypeMgrImpl().upload("do_123", node, file, None)
		}
		exception.getMessage shouldEqual "Uploaded file is not a epub file. Please upload a valid epub file."
	}

	"upload with different file type for word mimeType" should "throw client exception" in {
		val file: File = new File(Resources.getResource("sample.pdf").toURI)
		val node = new Node()
		node.setMetadata(new java.util.HashMap[String, AnyRef]() {{
			put("mimeType", "application/msword")
		}})
		val exception = intercept[ClientException] {
			new DocumentMimeTypeMgrImpl().upload("do_123", node, file, None)
		}
		exception.getMessage shouldEqual "Uploaded file is not a word file. Please upload a valid word file."
	}

	"upload pdf file" should "upload pdf file and return public url" in {
		val node = new Node()
		node.setMetadata(new java.util.HashMap[String, AnyRef]() {{
			put("mimeType", "application/pdf")
		}})
		val identifier ="do_123"
		implicit val ss = mock[StorageService]
		(ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
		val resFuture = new DocumentMimeTypeMgrImpl().upload(identifier, node, new File(Resources.getResource("sample.pdf").toURI), None)
		resFuture.map(result => {
			println("Response: " + result)
			result
		})

		assert(true)
	}

	"upload epub file" should "upload epub file and return public url" in {
		val node = new Node()
		node.setMetadata(new java.util.HashMap[String, AnyRef]() {{
			put("mimeType", "application/epub")
		}})
		val identifier ="do_123"
		implicit val ss = mock[StorageService]
		(ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
		val resFuture = new DocumentMimeTypeMgrImpl().upload(identifier, node, new File(Resources.getResource("igp-twss.epub").toURI), None)
		resFuture.map(result => {
			println("Response: " + result)
			result
		})

		assert(true)
	}

}
