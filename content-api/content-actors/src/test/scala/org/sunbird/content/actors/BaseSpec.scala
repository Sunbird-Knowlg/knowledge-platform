package org.sunbird.content.actors

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

    def getNode(objectType: String, metadata: Option[util.Map[String, AnyRef]]): Node = {
        val node = new Node("domain", "DATA_NODE", objectType)
        node.setGraphId("domain")
        val nodeMetadata = metadata.getOrElse(new util.HashMap[String, AnyRef]() {{
            put("name", "Sunbird Node")
            put("code", "sunbird-node")
            put("status", "Draft")
        }})
        node.setMetadata(nodeMetadata)
        node
    }
}
