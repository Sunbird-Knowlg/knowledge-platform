package org.sunbird.managers

import java.util
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.managers.content.HierarchyManager
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._
import org.sunbird.common.JsonUtils


class TestHierarchyManagerSerialization extends AsyncFlatSpec with Matchers {

    implicit val ec: ExecutionContext = ExecutionContext.global

    // Mock GraphService
    class MockGraphService extends GraphService {
        override def readExternalProps(request: Request, fields: List[String]): Future[Response] = {
            val hierarchyMap = Map(
                "identifier" -> "do_123",
                "ownershipType" -> "[\"createdBy\"]", // Stringified array - The Issue
                "language" -> "[\"English\"]",         // Stringified array
                "name" -> "Test Content",
                "children" -> new util.ArrayList[util.Map[String, AnyRef]]()
            )
            val hierarchyString = JsonUtils.serialize(hierarchyMap.asJava)
            val result = new util.HashMap[String, AnyRef]()
            result.put("hierarchy", hierarchyString)
            val response = ResponseHandler.OK()
            response.putAll(result)
            Future.successful(response)
        }
    }

    // Mock OntologyEngineContext
    class MockOntologyEngineContext extends OntologyEngineContext {
        override def graphService: GraphService = new MockGraphService()
    }

    "HierarchyManager" should "return deserialized list for stringified array fields" in {
        implicit val oec: OntologyEngineContext = new MockOntologyEngineContext()

        val request = new Request()
        request.put("rootId", "do_123")
        // request.put("mode", "edit") // Removed to trigger getPublishedHierarchy

        // We suspect getPublishedHierarchy calls getCassandraHierarchy which calls fetchHierarchy
        // fetchHierarchy in HierarchyManager.scala:
        // val responseFuture = oec.graphService.readExternalProps(req, List("hierarchy"))


        HierarchyManager.fetchHierarchy(request, "do_123").map(content => {
            
            // This assertion expects the BUG to be present (so it should FAIL if we assert it's a list, or we assert it is a String to confirm reproduction)
            // For reproduction, we want to see it fail when we expect a List.
            // But to confirm it IS the bug, let's print what we got.
            
            println("ownershipType type: " + content.get("ownershipType").get.getClass.getName)
            println("ownershipType value: " + content.get("ownershipType").get)

            // The Goal: It should be a List (e.g. ArrayList)
            // Note: content is Scala Map, get returns Option
            content.get("ownershipType").get shouldBe a [util.List[_]]
            content.get("language").get shouldBe a [util.List[_]]
        })
    }
}
