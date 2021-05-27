package org.sunbird.content.util

import java.util

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Property, Request, Response}
import org.sunbird.content.actors.{BaseSpec, ContentActor}
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.{Node, SearchCriteria}

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestAcceptFlagManager extends BaseSpec with MockFactory {

  it should "return success response for acceptFlag for Resource" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val nodeMetaData = new util.HashMap[String, AnyRef]() {{
      put("name", "Domain")
      put("code", "domain")
      put("status", "Flagged")
      put("identifier", "domain")
      put("versionKey", "1234")
      put("contentType", "Resource")
      put("channel", "Test")
      put("mimeType", "application/pdf")
      put("primaryCategory", "Learning Resource")
    }}
    val node = getNode("Content", Option(nodeMetaData))
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_:String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234")))).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(new Response()))
    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getRequest()
    request.getContext.put("identifier","domain")
    request.getRequest.putAll(mapAsJavaMap(Map("identifier" -> "domain")))
    request.setOperation("acceptFlag")
    val response = callActor(request, Props(new ContentActor()))
    assert("successful".equals(response.getParams.getStatus))
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