package org.sunbird.actors

import java.util

import org.apache.pekko.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.dto.Request
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.graph.{GraphService, OntologyEngineContext}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class TestItemSetActor extends BaseSpec with MockFactory {

  "ItemSetActor" should "return failed response for 'unknown' operation" in {
    implicit val oec: OntologyEngineContext = new OntologyEngineContext
    testUnknownOperation(Props(new ItemSetActor()), getItemSetRequest())
  }

  it should "create a itemSetNode and store it in neo4j" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(getValidNode()))
    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getItemSetRequest()
    request.setRequest(Map[String, AnyRef]("name" -> "test-itemset", "code" -> "1234").asJava)
    request.setOperation("createItemSet")
    val response = callActor(request, Props(new ItemSetActor()))
    assert(response.get("identifier") != null)
    assert(response.get("identifier").equals("1234"))
  }

  it should "return success response for updateItemSet" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getItemSetRequest()
    request.getContext.put("identifier", "1234")
    request.put("name", "test")
    request.put("code", "1234")
    request.putAll(Map[String, AnyRef]("description" -> "test desc").asJava)
    request.setOperation("updateItemSet")
    val response = callActor(request, Props(new ItemSetActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("identifier").equals("1234"))
  }


  it should "return success response for readItemSet" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).repeated(1)
    val node = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    val request = getItemSetRequest()
    request.getContext.put("identifier", "1234")
    request.putAll(Map[String, AnyRef]("fields" -> "").asJava)
    request.setOperation("readItemSet")
    val response = callActor(request, Props(new ItemSetActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("itemset").asInstanceOf[util.Map[String, AnyRef]].get("identifier").equals("1234"))
  }

  it should "return success response for reviewItemSet" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getItemSetRequest()
    request.getContext.put("identifier","do_1234")
    request.put("name", "test")
    request.put("code", "1234")
    request.setOperation("reviewItemSet")
    val response = callActor(request, Props(new ItemSetActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("identifier").equals("1234"))
  }

  it should "return success response for retireItemSet" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getItemSetRequest()
    request.getContext.put("identifier","do_1234")
    request.put("name", "test")
    request.put("code", "1234")
    request.setOperation("retireItemSet")
    val response = callActor(request, Props(new ItemSetActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("identifier").equals("1234"))
  }

  private def getItemSetRequest(): Request = {
    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() {
      {
        put("graph_id", "domain")
        put("version", "2.0")
        put("objectType", "ItemSet")
        put("schemaName", "itemset")
      }
    })
    request.setObjectType("ItemSet")
    request
  }

  private def getValidNode(): Node = {
    val node = new Node()
    node.setIdentifier("1234")
    node.setNodeType("DATA_NODE")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "1234")
        put("objectType", "ItemSet")
        put("name", "test-itemset")
      }
    })
    node.setObjectType("ItemSet")
    node
  }
}
