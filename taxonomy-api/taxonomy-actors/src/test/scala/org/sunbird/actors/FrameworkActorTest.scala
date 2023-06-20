package org.sunbird.actors

import java.util

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.dto.Request
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.utils.Constants

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class FrameworkActorTest extends BaseSpec with MockFactory {

  "FrameworkActor" should "return failed response for 'unknown' operation" in {
    implicit val oec: OntologyEngineContext = new OntologyEngineContext
    testUnknownOperation(Props(new FrameworkActor()), getFrameworkRequest())
  }


  it should "return success response for 'createFramework' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node("domain", "DATA_NODE", "Channel")
    node.setIdentifier("channel_test")
    node.setObjectType("Channel")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "channel_test");
        put("objectType", "Channel")
        put("name", "Channel")
      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    val nodes: util.List[Node] = getFrameworkNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(getFrameworkOfNode()))

    val request = getFrameworkRequest()
    request.putAll(mapAsJavaMap(Map("name" ->"framework_test", "code"-> "framework_test", "description" -> "desc_test", "channel"->"channel_test")))
    request.setOperation(Constants.CREATE_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get(Constants.NODE_ID).equals("framework_test"))
  }


  it should "return success response for updateFramework" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getValidNode()))
    val nodes: util.List[Node] = getFrameworkNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getFrameworkRequest()
    request.putAll(mapAsJavaMap(Map("description" -> "test desc")))
    request.setOperation(Constants.UPDATE_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "return success response for 'retireCategory' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getValidNode()
    node.setObjectType("Framework")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))

    val nodes: util.List[Node] = getFrameworkNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getFrameworkRequest()
    request.getContext.put("identifier", "framework_test");
    request.getRequest.put("identifier", "framework_test")
    request.setOperation("retireFramework")
    val response = callActor(request, Props(new FrameworkActor()))
    assert("successful".equals(response.getParams.getStatus))
  }



  private def getFrameworkOfNode(): Node = {
    val node = new Node()
    node.setIdentifier("framework_test")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Framework")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "framework_test")
        put("objectType", "Framework")
        put("name", "framework_test")
        put("code", "framework_test")
      }
    })
    node
  }

  private def getValidNode(): Node = {
    val node = new Node()
    node.setIdentifier("framework_test")
    node.setGraphId("domain")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Framework")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("code", "framework_test")
        put("objectType", "Framework")
        put("name", "framework_test")
        put("channel", "channel_test")
      }
    })
    node
  }

  private def getFrameworkRequest(): Request = {
    val request = new Request()
    request.setContext(getContext())
    request
  }

  private def getContext(): util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {
    {
      put("graph_id", "domain")
      put("version", "1.0")
      put("objectType", "Framework")
      put("schemaName", "framework")

    }

  }
}
