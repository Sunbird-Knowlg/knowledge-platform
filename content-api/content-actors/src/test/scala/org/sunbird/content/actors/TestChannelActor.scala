package org.sunbird.content.actors

import java.util

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.channel.actors.ChannelActor
import org.sunbird.common.dto.Request
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.graph.{GraphService, OntologyEngineContext}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class TestChannelActor extends BaseSpec with MockFactory {

  "ChannelActor" should "return failed response for 'unknown' operation" in {
    implicit val oec: OntologyEngineContext = new OntologyEngineContext
    testUnknownOperation(Props(new ChannelActor()), getRequest())
  }

  it should "return success response for 'createChannel' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node("domain", "DATA_NODE", "Channel")
    node.setIdentifier("channel_test")
    node.setObjectType("Channel")
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(node))

    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getRequest()
    request.getRequest.put("name", "channel_test")
    request.getRequest.put("code", "channel_test")
    request.setOperation("createChannel")
    val response = callActor(request, Props(new ChannelActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "throw exception code is required for createChannel" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getRequest()
    request.getRequest.put("name", "channel_test")
    request.setOperation("createChannel")
    val response = callActor(request, Props(new ChannelActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw invalid identifier exception for channelUpdate" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB)
    val node = new Node("domain",mapAsJavaMap(Map("identifier" -> "channel_test", "nodeType"->"DATA_NODE", "objectType"->"Channel")))
    node.setIdentifier("channel_test")
    node.setObjectType("Channel")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
    val request = getRequest()
    request.getRequest.put("name", "channel_test2")
    request.setOperation("updateChannel")
    val response = callActor(request, Props(new ChannelActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "return success response for 'readChannel' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB)
    val node = new Node("domain",mapAsJavaMap(Map("identifier" -> "channel_test", "nodeType"->"DATA_NODE", "objectType"->"Channel")))
    node.setIdentifier("channel_test")
    node.setObjectType("Channel")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
    val request = getRequest()
    request.getRequest.put("identifier", "channel_test")
    request.setOperation("readChannel")
    val response = callActor(request, Props(new ChannelActor()))
//    assert("successful".equals(response.getParams.getStatus))
  }

  it should "return success response for 'updateChannel' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getNode("Channel", None)
    node.setObjectType("Channel")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
    (graphDB.upsertNode(_:String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))

    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getRequest()
    request.getContext.put("identifier", "channel_test");
    request.getRequest.put("name", "channel_test")
    request.setOperation("updateChannel")
    val response = callActor(request, Props(new ChannelActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "return success response for 'retireChannel' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getNode("Channel", None)
    node.setObjectType("Channel")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
    (graphDB.upsertNode(_:String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))

    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getRequest()
    request.getContext.put("identifier", "channel_test");
    request.getRequest.put("identifier", "channel_test")
    request.setOperation("retireChannel")
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
    request.setObjectType("Channel")
    request
  }

}