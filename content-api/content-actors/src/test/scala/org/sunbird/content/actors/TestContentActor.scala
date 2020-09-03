package org.sunbird.content.actors

import java.util

import org.sunbird.graph.dac.model.Node
import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.{HttpUtil, JsonUtils}
import org.sunbird.common.dto.{Property, Request, Response, ResponseHandler}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.kafka.client.KafkaClient

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class TestContentActor extends BaseSpec with MockFactory {

    "ContentActor" should "return failed response for 'unknown' operation" in {
        implicit val ss = mock[StorageService]
        implicit val oec: OntologyEngineContext = new OntologyEngineContext
        testUnknownOperation(Props(new ContentActor()), getContentRequest())
    }

    it should "validate input before creating content" in {
        implicit val ss = mock[StorageService]
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val request = getContentRequest()
        val content = mapAsJavaMap(Map("name" -> "New Content", "code" -> "1234", "mimeType"-> "application/pdf", "contentType" -> "Resource"))
        request.put("content", content)
        assert(true)
        val response = callActor(request, Props(new ContentActor()))
        println("Response: " + JsonUtils.serialize(response))

    }

    it should "create a node and store it in neo4j" in {
        implicit val ss = mock[StorageService]
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB)
        (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(getValidNode()))
        val request = getContentRequest()
        request.getRequest.putAll( mapAsJavaMap(Map("name" -> "New Content", "code" -> "1234", "mimeType"-> "application/vnd.ekstep.content-collection", "contentType" -> "Course", "primaryCategory" -> "LearningResource", "channel" -> "in.ekstep")))
        request.setOperation("createContent")
        val response = callActor(request, Props(new ContentActor()))
        assert(response.get("identifier") != null)
        assert(response.get("versionKey") != null)
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

    it should "discard node in draft state should return success" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).repeated(2)
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getValidNodeToDiscard()))
        (graphDB.deleteNode(_: String, _: String, _: Request)).expects(*, *, *).returns(Future(true))
        implicit val ss = mock[StorageService]
        val request = getContentRequest()
        request.getRequest.putAll(mapAsJavaMap(Map("identifier" -> "do_12346")))
        request.setOperation("discardContent")
        val response = callActor(request, Props(new ContentActor()))
        assert(response.getResponseCode == ResponseCode.OK)
        assert(response.get("identifier") == "do_12346")
        assert(response.get("message") == "Draft version of the content with id : do_12346 is discarded")

    }

    it should "discard node in Live state should return client error" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).repeated(1)
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getInValidNodeToDiscard()))
        implicit val ss = mock[StorageService]
        val request = getContentRequest()
        request.getRequest.putAll(mapAsJavaMap(Map("identifier" -> "do_12346")))
        request.setOperation("discardContent")
        val response = callActor(request, Props(new ContentActor()))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    }

    it should "return client error response for retireContent" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        implicit val ss = mock[StorageService]
        val request = getContentRequest()
        request.getContext.put("identifier","do_1234.img")
        request.getRequest.putAll(mapAsJavaMap(Map("identifier" -> "do_1234.img")))
        request.setOperation("retireContent")
        val response = callActor(request, Props(new ContentActor()))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    }

    private def getContentRequest(): Request = {
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "Content")
                put("schemaName", "content")
                put("X-Channel-Id", "in.ekstep")
            }
        })
        request.setObjectType("Content")
        request
    }

    private def getValidNodeToDiscard(): Node = {
        val node = new Node()
        node.setIdentifier("do_12346")
        node.setNodeType("DATA_NODE")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_12346")
                put("mimeType", "application/pdf")
                put("status", "Draft")
                put("contentType", "Resource")
                put("name", "Node To discard")
                put("primaryCategory", "LearningResource")
            }
        })
        node
    }

    private def getInValidNodeToDiscard(): Node = {
        val node = new Node()
        node.setIdentifier("do_12346")
        node.setNodeType("DATA_NODE")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_12346")
                put("mimeType", "application/pdf")
                put("status", "Live")
                put("contentType", "Resource")
                put("name", "Node To discard")
                put("primaryCategory", "LearningResource")
            }
        })
        node
    }

    private def getValidNode(): Node = {
        val node = new Node()
        node.setIdentifier("do_1234")
        node.setNodeType("DATA_NODE")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_1234")
                put("mimeType", "application/vnd.ekstep.content-collection")
                put("status", "Draft")
                put("contentType", "Course")
                put("name", "Course_1")
                put("versionKey", "1878141")
                put("primaryCategory", "LearningResource")
            }
        })
        node
    }

    it should "return success response for retireContent" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).repeated(2)
        val node = getNode("Content", None)
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.updateNodes(_: String, _: util.List[String], _: util.HashMap[String, AnyRef])).expects(*, *, *).returns(Future(new util.HashMap[String, Node]))
        implicit val ss = mock[StorageService]
        val request = getContentRequest()
        request.getContext.put("identifier","do1234")
        request.getRequest.putAll(mapAsJavaMap(Map("identifier" -> "do_1234")))
        request.setOperation("retireContent")
        val response = callActor(request, Props(new ContentActor()))
        assert("successful".equals(response.getParams.getStatus))
    }

    it should "return success response for 'readContent'" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB)
        val node = getNode("Content", None)
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
        implicit val ss = mock[StorageService]
        val request = getContentRequest()
        request.getContext.put("identifier","do1234")
        request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "fields" -> "")))
        request.setOperation("readContent")
        val response = callActor(request, Props(new ContentActor()))
        assert("successful".equals(response.getParams.getStatus))
    }

    it should "return success response for 'updateContent'" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val node = getNode()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("test_123"))))
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
        implicit val ss = mock[StorageService]
        val request = getContentRequest()
        request.getContext.put("identifier","do_1234")
        request.putAll(mapAsJavaMap(Map("description" -> "test desc", "versionKey" -> "test_123")))
        request.setOperation("updateContent")
        val response = callActor(request, Props(new ContentActor()))
        assert("successful".equals(response.getParams.getStatus))
        assert("do_1234".equals(response.get("identifier")))
        assert("test_123".equals(response.get("versionKey")))
    }

    it should "return client exception for 'updateContent' with invalid versionKey" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val node = getNode()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("test_xyz"))))
        implicit val ss = mock[StorageService]
        val request = getContentRequest()
        request.getContext.put("identifier","do_1234")
        request.putAll(mapAsJavaMap(Map("description" -> "test desc", "versionKey" -> "test_123")))
        request.setOperation("updateContent")
        val response = callActor(request, Props(new ContentActor()))
        assert("failed".equals(response.getParams.getStatus))
        assert("CLIENT_ERROR".equals(response.getParams.getErr))
        assert("Invalid version Key".equals(response.getParams.getErrmsg))
    }

    it should "return client exception for 'updateContent' without versionKey" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        implicit val ss = mock[StorageService]
        val request = getContentRequest()
        request.getContext.put("identifier","do1234")
        request.putAll(mapAsJavaMap(Map("description" -> "updated description")))
        request.setOperation("updateContent")
        val response = callActor(request, Props(new ContentActor()))
        assert("failed".equals(response.getParams.getStatus))
        assert("ERR_INVALID_REQUEST".equals(response.getParams.getErr))
        assert("Please Provide Version Key!".equals(response.getParams.getErrmsg))
    }

    it should "return success response for 'copyContent'" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val node = getNode()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(node))
        implicit val ss = mock[StorageService]
        val request = getContentRequest()
        request.getContext.put("identifier","do1234")
        request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "createdBy" -> "username_1",
            "createdFor" -> new util.ArrayList[String]() {{ add("NCF2") }}, "framework" -> "NCF2",
            "organisation" -> new util.ArrayList[String]() {{ add("NCF2") }})))
        request.setOperation("copy")
        val response = callActor(request, Props(new ContentActor()))
        assert("successful".equals(response.getParams.getStatus))
        assert(response.getResult.containsKey("node_id"))
        assert("test_321".equals(response.get("versionKey")))
    }

    it should "send events to kafka topic" in {
        implicit val ss = mock[StorageService]
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val kfClient = mock[KafkaClient]
        val hUtil = mock[HttpUtil]
        (oec.httpUtil _).expects().returns(hUtil)
        val resp :Response = ResponseHandler.OK()
        resp.put("content", new util.HashMap[String, AnyRef](){{
            put("framework", "NCF")
            put("artifactUrl", "http://test.com/test.pdf")
            put("channel", "test")
        }})
        (hUtil.get(_: String, _: String, _: util.Map[String, String])).expects(*, *, *).returns(resp)
        (oec.kafkaClient _).expects().returns(kfClient)
        (kfClient.send(_: String, _: String)).expects(*, *).returns(None)
        val request = getContentRequest()
        request.getRequest.put("content", new util.HashMap[String, AnyRef](){{
            put("source", "https://dock.sunbirded.org/api/content/v1/read/do_11307822356267827219477")
            put("metadata", new util.HashMap[String, AnyRef](){{
                put("name", "Test Content")
                put("description", "Test Content")
                put("mimeType", "application/pdf")
                put("code", "test.res.1")
                put("contentType", "Resource")
                put("primaryCategory", "LearningResource")
            }})
        }})
        request.setOperation("importContent")
        val response = callActor(request, Props(new ContentActor()))
        assert(response.get("processId") != null)
    }

    private def getNodeToUpload(): Node = {
        val node = new Node()
        node.setIdentifier("do_1234")
        node.setNodeType("DATA_NODE")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_1234")
                put("mimeType", "application/pdf")
                put("status", "Live")
                put("contentType", "Resource")
                put("name", "Resource_1")
                put("versionKey", "test_321")
                put("channel", "in.ekstep")
                put("code", "Resource_1")
                put("artifactUrl", "testurl")
                put("primaryCategory", "LearningResource")
            }
        })
        node
    }

    private def getNode(): Node = {
        val node = new Node()
        node.setIdentifier("do_1234")
        node.setNodeType("DATA_NODE")
        node.setObjectType("Content")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_1234")
                put("mimeType", "application/pdf")
                put("status", "Live")
                put("contentType", "Resource")
                put("name", "Resource_1")
                put("versionKey", "test_321")
                put("channel", "in.ekstep")
                put("code", "Resource_1")
                put("primaryCategory", "LearningResource")
            }
        })
        node
    }
}
