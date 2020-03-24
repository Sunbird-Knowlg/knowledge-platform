package org.sunbird.content.upload.mgr

import java.io.File
import java.util

import com.google.common.io.Resources
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.common.dto.Request
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node

import scala.concurrent.ExecutionContext.Implicits.global

class UploadManagerTest extends FlatSpec with Matchers with MockFactory {

	"getUploadResponse with valid node object" should "return response with artifactUrl" in {
		val node = new Node()
		node.setIdentifier("do_1234")
		node.setMetadata(new util.HashMap[String, AnyRef](){{
			put("artifactUrl", "testurl")
			put("versionKey",123456.asInstanceOf[AnyRef])
		}})
		val response = UploadManager.getUploadResponse(node)
		val result = response.getResult
		assert(null != response)
		assert("OK"==response.getResponseCode.toString)
		assert(result.size()==5)
		assert(result.get("artifactUrl").toString.equals("testurl"))
	}

	it should "return response after upload file" in {
		val identifier ="do_123"
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val request = getRequest()
		request.setRequest(new util.HashMap[String, AnyRef](){{put("file",new File(Resources.getResource("sample.pdf").toURI))}})
		val node = new Node()
		node.setMetadata(new util.HashMap[String, AnyRef](){{
			put("mimeType","application/pdf")
			put("contentType","Resource")
		}})
		node.setIdentifier(identifier)
		UploadManager.upload(request, node).map(response => {
			assert("successful".equals(response.getParams.getStatus))
		})
	}

	private def getRequest(): Request = {
		val request = new Request()
		request.setContext(new util.HashMap[String, AnyRef]() {
			{
				put("objectType", "Content")
				put("graph_id", "domain")
				put("version", "1.0")
				put("schemaName", "content")
			}
		})
		request
	}

}
