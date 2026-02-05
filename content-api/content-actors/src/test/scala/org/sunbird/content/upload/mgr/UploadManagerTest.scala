package org.sunbird.content.upload.mgr
import scala.jdk.CollectionConverters._

import java.util
import java.io.File

import org.scalatest.{FlatSpec, Matchers}
import org.scalamock.scalatest.MockFactory
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.OntologyEngineContext

import scala.concurrent.ExecutionContext

class UploadManagerTest extends FlatSpec with Matchers with MockFactory {

	implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
	implicit val ec: ExecutionContext = ExecutionContext.global

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

	"getUploadResponse with node having .img extension" should "return response with cleaned identifier" in {
		val jpegFile = new File(getClass.getResource("/jpegImage.jpeg").getPath)
		val jpegUrl = jpegFile.toURI.toString
		val node = new Node()
		node.setIdentifier("do_1234.img")
		node.setMetadata(new util.HashMap[String, AnyRef](){{
			put("artifactUrl", jpegUrl)
			put("versionKey", "v1.0".asInstanceOf[AnyRef])
		}})
		val response = UploadManager.getUploadResponse(node)
		val result = response.getResult
		assert(null != response)
		assert("OK" == response.getResponseCode.toString)
		assert(result.get("identifier").toString.equals("do_1234"))
		assert(result.get("node_id").toString.equals("do_1234"))
		assert(result.get("artifactUrl").toString.equals(jpegUrl))
	}

	"getUploadResponse with complete metadata" should "return response with all required fields" in {
		val pdfFile = new File(getClass.getResource("/sample.pdf").getPath)
		val pdfUrl = pdfFile.toURI.toString
		val node = new Node()
		node.setIdentifier("do_5678")
		node.setMetadata(new util.HashMap[String, AnyRef](){{
			put("artifactUrl", pdfUrl)
			put("versionKey", "2.1".asInstanceOf[AnyRef])
		}})
		val response = UploadManager.getUploadResponse(node)
		val result = response.getResult
		assert(null != response)
		assert("OK" == response.getResponseCode.toString)
		assert(result.size() == 5)
		assert(result.get("node_id").toString.equals("do_5678"))
		assert(result.get("identifier").toString.equals("do_5678"))
		assert(result.get("artifactUrl").toString.equals(pdfUrl))
		assert(result.get("content_url").toString.equals(pdfUrl))
		assert(result.get("versionKey").toString.equals("2.1"))
	}

	"getUploadResponse with numeric versionKey" should "return response with proper version handling" in {
		val jpegFile = new File(getClass.getResource("/jpegImage.jpeg").getPath)
		val jpegUrl = jpegFile.toURI.toString
		val node = new Node()
		node.setIdentifier("do_9999")
		node.setMetadata(new util.HashMap[String, AnyRef](){{
			put("artifactUrl", jpegUrl)
			put("versionKey", 12345.asInstanceOf[AnyRef])
		}})
		val response = UploadManager.getUploadResponse(node)
		val result = response.getResult
		assert(null != response)
		assert("OK" == response.getResponseCode.toString)
		assert(result.get("versionKey").toString.equals("12345"))
		assert(result.get("artifactUrl").toString.equals(jpegUrl))
		assert(result.get("content_url").toString.equals(jpegUrl))
	}
}
