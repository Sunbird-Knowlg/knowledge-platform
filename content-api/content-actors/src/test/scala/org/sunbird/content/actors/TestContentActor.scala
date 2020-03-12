package org.sunbird.content.actors

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.dto.Request
import org.sunbird.graph.OntologyEngineContext

import scala.collection.JavaConversions._

class TestContentActor extends BaseSpec with MockFactory {

    "ContentActor" should "return failed response for 'unknown' operation" in {
        implicit val oec: OntologyEngineContext = new OntologyEngineContext
        testUnknownOperation(Props(new ContentActor()))
    }

    it should "validate input before creating content" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val request = new Request()
        val content = mapAsJavaMap(Map("name" -> "New Content"))
        request.put("content", content)
        request.setOperation("createContent")
        val response = callActor(request, Props(new ContentActor()))
//        println("Response: " + JsonUtils.serialize(response))

    }
//
//    it should "return node for given identifier" in {
//        val identifier = "do_1234"
//    }


}
