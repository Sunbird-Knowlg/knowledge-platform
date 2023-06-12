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
        probe.expectMsgType[Response](FiniteDuration.apply(10, TimeUnit.SECONDS))
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

    def getFrameworkNode(): util.List[Node] = {
        val node = new Node()
        node.setIdentifier("NCF")
        node.setNodeType("DATA_NODE")
        node.setObjectType("Framework")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "NCF")
                put("objectType", "Framework")
                put("name", "NCF")
            }
        })
        util.Arrays.asList(node)
    }

    def getCategoryInstanceNode(): util.List[Node] = {
        val node = new Node()
        node.setIdentifier("ncf_board")
        node.setNodeType("DATA_NODE")
        node.setObjectType("CategoryInstance")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "ncf_board")
                put("objectType", "CategoryInstance")
                put("name", "ncf_board")
            }
        })
        util.Arrays.asList(node)
    }
}
