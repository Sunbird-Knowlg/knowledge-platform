package org.sunbird.content.actors

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.graph.OntologyEngineContext

class BaseSpec extends FlatSpec with Matchers {

    val system = ActorSystem.create("system")

    def testUnknownOperation(props: Props)(implicit oec: OntologyEngineContext) = {
        val request = new Request()
        request.setOperation("unknown")
        val response = callActor(request, props)
        assert("failed".equals(response.getParams.getStatus))
    }

    def callActor(request: Request, props: Props): Response = {
        val probe = new TestKit(system)
        val actorRef = system.actorOf(props)
        actorRef.tell(request, probe.testActor)
        probe.expectMsgClass(classOf[Response])
    }
}
