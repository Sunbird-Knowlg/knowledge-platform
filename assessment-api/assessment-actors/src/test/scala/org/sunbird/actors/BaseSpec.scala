package org.sunbird.actors

import java.util
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node

import scala.concurrent.duration.FiniteDuration

class BaseSpec extends FlatSpec with Matchers {
  val system = ActorSystem.create("system")

  def testUnknownOperation(props: Props, request: Request)(implicit oec: OntologyEngineContext) = {
    request.setOperation("unknown")
    val response = callActor(request, props)
    assert("failed".equals(response.getParams.getStatus))
  }

  def callActor(request: Request, props: Props): Response = {
    val probe = new TestKit(system)
    val actorRef = system.actorOf(props)
    actorRef.tell(request, probe.testActor)
    probe.expectMsgType[Response](FiniteDuration.apply(100, TimeUnit.SECONDS))
  }

  def getNode(objectType: String, metadata: Option[util.Map[String, AnyRef]]): Node = {
    val node = new Node("domain", "DATA_NODE", objectType)
    node.setGraphId("domain")
    val nodeMetadata = metadata.getOrElse(new util.HashMap[String, AnyRef]() {{
      put("name", "Sunbird Node")
      put("code", "sunbird-node")
      put("status", "Draft")
    }})
    node.setNodeType("DATA_NODE")
    node.setMetadata(nodeMetadata)
    node.setObjectType(objectType)
    node.setIdentifier("test_id")
    node
  }
  def getCategoryNode(): util.List[Node] = {
    val node = new Node()
    node.setIdentifier("board")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Category")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("code", "board")
        put("orgIdFieldName", "boardIds")
        put("targetIdFieldName", "targetBoardIds")
        put("searchIdFieldName", "se_boardIds")
        put("searchLabelFieldName", "se_boards")
        put("status", "Live")
      }
    })
    util.Arrays.asList(node)
  }

  def getNode(identifier: String, objectType: String, metadata: Option[util.Map[String, AnyRef]]): Node = {
    val node = new Node("domain", "DATA_NODE", objectType)
    node.setGraphId("domain")
    val nodeMetadata = metadata.getOrElse(new util.HashMap[String, AnyRef]() {{
      put("name", "Question 1")
      put("code", "ques.1")
      put("status", "Draft")
      put("primaryCategory", "Multiple Choice Question")
    }})
    node.setNodeType("DATA_NODE")
    node.setMetadata(nodeMetadata)
    node.setObjectType(objectType)
    node.setIdentifier(identifier)
    node
  }
}
