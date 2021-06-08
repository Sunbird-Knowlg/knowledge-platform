package org.sunbird.content.actors

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.Request
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import java.util
import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestObjectActor extends BaseSpec with MockFactory{

  "ObjectActor" should "return failed response for 'unknown' operation" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = new OntologyEngineContext
    testUnknownOperation(Props(new ObjectActor()), getRequest())
  }

  it should "return success response for 'readObject'" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB)
    val node = getNode("Content", None)
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
    implicit val ss = mock[StorageService]
    val request = getRequest()
    request.getContext.put("identifier","do1234")
    request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "fields" -> "")))
    request.setOperation("readObject")
    val response = callActor(request, Props(new ObjectActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  private def getRequest(): Request = {
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
}