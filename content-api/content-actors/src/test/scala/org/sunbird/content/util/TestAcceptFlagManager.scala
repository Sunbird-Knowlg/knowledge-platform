package org.sunbird.content.util

import java.util

import org.scalamock.scalatest.MockFactory
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.Request
import org.sunbird.content.actors.BaseSpec
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.Node

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestAcceptFlagManager extends BaseSpec with MockFactory {

  it should "return success response for acceptFlag" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val nodeMetaData = new util.HashMap[String, AnyRef]() {{
      put("name", "acceptFlag_test")
      put("code", "acceptFlag_test")
      put("status", "Flagged")
      put("identifier", "do_1234")
      put("versionKey", "01234")
    }}
    val node = getNode("Content", Option(nodeMetaData))
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
    val request = getRequest()
    request.getContext.put("identifier","do_1234")
    request.getRequest.putAll(mapAsJavaMap(Map("identifier" -> "do_1234")))
    request.setOperation("acceptFlag")
    val response = AcceptFlagManager.acceptFlag(request)
    response.map(res => {
      assert("successful".equals(res.getParams.getStatus))
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
