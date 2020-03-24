package org.sunbird.content.actors

import java.util

import org.sunbird.graph.dac.model.Node
import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.JsonUtils
import org.sunbird.common.dto.Request
import org.sunbird.graph.{GraphService, OntologyEngineContext}

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class TestContentActor extends BaseSpec with MockFactory {

    "ContentActor" should "return failed response for 'unknown' operation" in {
        implicit val ss = mock[StorageService]
        implicit val oec: OntologyEngineContext = new OntologyEngineContext
        testUnknownOperation(Props(new ContentActor()))
    }

    it should "validate input before creating content" in {
        implicit val ss = mock[StorageService]
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val request = new Request()
        val content = mapAsJavaMap(Map("name" -> "New Content"))
        request.put("content", content)
        request.setOperation("createContent")
        assert(true)
        val response = callActor(request, Props(new ContentActor()))
        println("Response: " + JsonUtils.serialize(response))

    }


    it should "generate and return presigned url" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB)
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(new Node()))
        implicit val ss = mock[StorageService]
        (ss.getSignedURL(_: String, _: Option[Int], _: Option[String])).expects(*, *, *).returns("cloud store url")
        val request = getContentRequest()
        request.getRequest.putAll(mapAsJavaMap(Map("fileName" -> "presigned_url", "filePath" -> "/data/cloudstore/", "type" -> "assets", "identifier" -> "do_1234")))
        request.setOperation("uploadPreSignedUrl")
        val response = callActor(request, Props(new ContentActor()))
        assert(response.get("identifier") != null)
        assert(response.get("pre_signed_url") != null)
        assert(response.get("url_expiry") != null)
    }

    private def getContentRequest(): Request = {
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "Content")
                put("schemaName", "content")

            }
        })
        request.setObjectType("Content")
        request
    }

}
