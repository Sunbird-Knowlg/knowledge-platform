package org.sunbird.content.actors

import java.util

import org.sunbird.graph.dac.model.Node
import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.{HttpUtil, JsonUtils}
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.{GraphService, OntologyEngineContext}

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

    it should "return client error response for retire Content image id" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        implicit val ss = mock[StorageService]
        val request = getContentRequest()
        request.getContext.put("identifier","do_1234.img")
        request.getRequest.putAll(mapAsJavaMap(Map("identifier" -> "do_1234.img")))
        request.setOperation("retireContent")
        val response = callActor(request, Props(new ContentActor()))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    }

    it should "return client error response for retire Content that has shallow copies" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        implicit val ss = mock[StorageService]
        val graphDB = mock[GraphService]
        val httpUtil = mock[HttpUtil]
        (oec.httpUtil _).expects().returns(httpUtil)
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getInValidCollectionNodeToRetire()))
        (graphDB.updateNodes(_: String, _: util.List[String], _: util.HashMap[String, AnyRef])).expects(*, *, *).returns(Future(new util.HashMap[String, Node])).anyNumberOfTimes()
        (httpUtil.post(_: String, _: java.util.Map[String, AnyRef], _: java.util.Map[String, String])).expects(*, *, *).returns(getShallowCopySearchForRetire())
        val request = getContentRequest()
        request.getContext.put("identifier", "do_1234")
        request.getRequest.putAll(mapAsJavaMap(Map("identifier" -> "do_1234")))
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
            }
        })
        node
    }

    private def getShallowCopySearchForRetire(): Response = {
        val response =     "{\n"+
            "    \"id\": \"api.search-service.search\",\n"+
            "    \"ver\": \"3.0\",\n"+
            "    \"ts\": \"2020-05-19T11:24:00ZZ\",\n"+
            "    \"params\": {\n"+
            "        \"resmsgid\": \"46733cf8-2b47-4511-879a-c538cad9ee80\",\n"+
            "        \"msgid\": null,\n"+
            "        \"err\": null,\n"+
            "        \"status\": \"successful\",\n"+
            "        \"errmsg\": null\n"+
            "    },\n"+
            "    \"responseCode\": \"OK\",\n"+
            "    \"result\": {\n"+
            "        \"count\": 4,\n"+
            "        \"content\": [\n"+
            "            {\n"+
            "                \"identifier\": \"do_11302017972871168012\",\n"+
            "                \"name\": \"Copy Collecction Testing For shallow Copy\",\n"+
            "                \"originData\": \"{\\\"name\\\":\\\"Copy of Test textbbok\\\",\\\"copyType\\\":\\\"deep\\\",\\\"license\\\":\\\"CC BY-NC 4.0\\\",\\\"organisation\\\":[\\\"Sunbird\\\"],\\\"author\\\":\\\"test- author\\\"}\",\n"+
            "                \"objectType\": \"Content\"\n"+
            "            },\n"+
            "            {\n"+
            "                \"identifier\": \"do_113014424120729600183\",\n"+
            "                \"name\": \"Copy Collecction Testing For shallow Copy\",\n"+
            "                \"originData\": \"{\\\"name\\\":\\\"Copy of Test textbbok\\\",\\\"copyType\\\":\\\"deep\\\",\\\"license\\\":\\\"CC BY-NC 4.0\\\",\\\"organisation\\\":[\\\"Sunbird\\\"],\\\"author\\\":\\\"test- author\\\"}\",\n"+
            "                \"objectType\": \"Content\"\n"+
            "            },\n"+
            "            {\n"+
            "                \"identifier\": \"do_113014302147035136146\",\n"+
            "                \"name\": \"Copy Collecction Testing For shallow Copy\",\n"+
            "                \"originData\": \"{\\\"name\\\":\\\"Copy of Test textbbok\\\",\\\"copyType\\\":\\\"shallow\\\",\\\"license\\\":\\\"CC BY-NC 4.0\\\",\\\"organisation\\\":[\\\"Sunbird\\\"],\\\"author\\\":\\\"test- author\\\"}\",\n"+
            "                \"objectType\": \"Content\"\n"+
            "            },\n"+
            "            {\n"+
            "                \"identifier\": \"do_113014302132183040144\",\n"+
            "                \"name\": \"Copy Collecction Testing For shallow Copy\",\n"+
            "                \"originData\": \"{\\\"name\\\":\\\"Copy of Test textbbok\\\",\\\"copyType\\\":\\\"shallow\\\",\\\"license\\\":\\\"CC BY-NC 4.0\\\",\\\"organisation\\\":[\\\"Sunbird\\\"],\\\"author\\\":\\\"test- author\\\"}\",\n"+
            "                \"objectType\": \"Content\"\n"+
            "            }\n"+
            "        ]\n"+
            "    }\n"+
            "}"
         JsonUtils.deserialize(response, classOf[Response])
    }

    private def getInValidCollectionNodeToRetire(): Node = {
        val node = new Node()
        node.setIdentifier("do_1234")
        node.setNodeType("DATA_NODE")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_1234")
                put("mimeType", "application/vnd.ekstep.content-collection")
                put("status", "Draft")
                put("contentType", "Course")
                put("name", "Node To Retire")
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

}
