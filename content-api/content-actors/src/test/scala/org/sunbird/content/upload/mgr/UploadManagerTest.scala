package org.sunbird.content.upload.mgr
import scala.jdk.CollectionConverters._

import java.util
import java.io.File

import org.scalatest.{AsyncFlatSpec, Matchers}
import org.scalamock.scalatest.MockFactory
import org.sunbird.graph.dac.model.Node
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.common.Platform

import scala.concurrent.{ExecutionContext, Future}

class UploadManagerTest extends AsyncFlatSpec with Matchers with MockFactory {

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

	"updateNode with empty artifactUrl" should "return server error" in {
		val request = new Request()
		val result = Map(
			"artifactUrl" -> "",
			"size" -> 1024.0.asInstanceOf[AnyRef]
		)

		val futureResponse = UploadManager.updateNode(request, "do_test_error", "image", "Asset", result)
		
		futureResponse.map { response =>
			assert(response.getResponseCode == ResponseCode.SERVER_ERROR)
			assert(response.getParams.getErr == "ERR_UPLOAD_FILE")
		}
	}

	"File validation" should "confirm test resources exist and are accessible" in {
		Future {
			// Validate that our test files exist and have the expected properties
			val jpegFile = new File(getClass.getResource("/jpegImage.jpeg").getPath)
			val pdfFile = new File(getClass.getResource("/sample.pdf").getPath)
			
			assert(jpegFile.exists(), "JPEG test file should exist")
			assert(pdfFile.exists(), "PDF test file should exist")
			assert(jpegFile.length() > 0, "JPEG file should have content")
			assert(pdfFile.length() > 0, "PDF file should have content")
			assert(jpegFile.getName == "jpegImage.jpeg", "JPEG file name should match")
			assert(pdfFile.getName == "sample.pdf", "PDF file name should match")
			
			// Log file sizes for reference
			println(s"JPEG file size: ${jpegFile.length()} bytes")
			println(s"PDF file size: ${pdfFile.length()} bytes")
			
			succeed // Return a successful assertion for AsyncFlatSpec
		}
	}
}
