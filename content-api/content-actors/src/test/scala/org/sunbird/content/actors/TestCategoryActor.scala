package org.sunbird.content.actors

import java.util

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.Request
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.Node

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class TestCategoryActor extends BaseSpec with MockFactory{

    "CategoryActor" should "return failed response for 'unknown' operation" in {
        implicit val ss = mock[StorageService]
        implicit val oec: OntologyEngineContext = new OntologyEngineContext
        testUnknownOperation(Props(new CategoryActor()), getCategoryRequest())
    }

    it should "create a categoryNode and store it in neo4j" in {
        implicit val ss = mock[StorageService]
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB)
        (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(getValidNode()))
        val request = getCategoryRequest()
        request.putAll(mapAsJavaMap(Map("name" -> "New Category")))
        request.setOperation("createCategory")
        val response = callActor(request, Props(new CategoryActor()))
        assert(response.get("identifier") != null)
    }


    it should "return success response for updateCategory" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).repeated(2)
        val node = getValidNode()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getValidNode()))
        implicit val ss = mock[StorageService]
        val request = getCategoryRequest()
        request.getContext.put("identifier","do_1234")
        request.putAll(mapAsJavaMap(Map("description" -> "test desc")))
        request.setOperation("updateCategory")
        val response = callActor(request, Props(new CategoryActor()))
        assert("successful".equals(response.getParams.getStatus))
    }


    it should "return success response for readCategory" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).repeated(1)
        val node = getValidNode()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        implicit val ss = mock[StorageService]
        val request = getCategoryRequest()
        request.getContext.put("identifier","do_1234")
        request.putAll(mapAsJavaMap(Map("fields" -> "")))
        request.setOperation("readCategory")
        val response = callActor(request, Props(new CategoryActor()))
        assert("successful".equals(response.getParams.getStatus))
    }

    it should "return success response for retireCategory" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).repeated(2)
        val node = getValidNode()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getValidNode()))
        implicit val ss = mock[StorageService]
        val request = getCategoryRequest()
        request.getContext.put("identifier","do_1234")
        request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234")))
        request.setOperation("retireCategory")
        val response = callActor(request, Props(new CategoryActor()))
        assert("successful".equals(response.getParams.getStatus))
    }

    private def getCategoryRequest(): Request = {
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "Category")
                put("schemaName", "category")

            }
        })
        request.setObjectType("Category")
        request
    }

    private def getValidNode(): Node = {
        val node = new Node()
        node.setIdentifier("do_1234")
        node.setNodeType("DATA_NODE")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_1234")
                put("objectType", "Category")
                put("status", "Live")
                put("name", "do_1234")
                put("versionKey", "1878141")
            }
        })
        node
    }

}
