package org.sunbird.content.actors

import java.util

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.channel.actors.ChannelActor
import org.sunbird.common.dto.Request
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.Node
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._

class TestChannelActor extends BaseSpec with MockFactory {

  "ChannelActor" should "return failed response for 'unknown' operation" in {
    implicit val oec: OntologyEngineContext = new OntologyEngineContext
    testUnknownOperation(Props(new ChannelActor()))
  }

  "ChannelActor" should "return success response for 'createChannel' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB)
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(new Node("domain", "DATA_NODE", "Channel")))
    val request = getRequest()
    request.getRequest.put("name", "channel_test")
    request.getRequest.put("code", "channel_test")
    request.setOperation("createChannel")
    val response = callActor(request, Props(new ChannelActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  "ChannelActor" should "throw exception code is required for createChannel" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getRequest()
    request.getRequest.put("name", "channel_test")
    request.setOperation("createChannel")
    val response = callActor(request, Props(new ChannelActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  "ChannelActor" should "throw invalid identifier exception for channelUpdate" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getRequest()
    request.getRequest.put("name", "channel_test2")
    request.setOperation("updateChannel")
    val response = callActor(request, Props(new ChannelActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  "ChannelActor" should "return success response for 'readChannel' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB)
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(new Node("domain",mapAsJavaMap(Map("identifier" -> "channel_test", "nodeType"->"DATA_NODE", "objectType"->"Channel")))))
    val request = getRequest()
    request.getRequest.put("identifier", "channel_test")
    request.setOperation("readChannel")
    val response = callActor(request, Props(new ChannelActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  private def getRequest(): Request = {
    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() {
      {
        put("objectType", "Channel")
        put("graph_id", "domain")
        put("version", "1.0")
        put("schemaName", "channel")
      }
    })
    request
  }

}