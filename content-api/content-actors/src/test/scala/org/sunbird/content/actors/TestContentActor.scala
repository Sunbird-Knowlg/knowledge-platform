package org.sunbird.content.actors

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.graph.OntologyEngineContext

class TestContentActor extends BaseSpec {

    "ContentActor" should "return failed response for 'unknown' operation" in {
        implicit val oec: OntologyEngineContext = new OntologyEngineContext
        testUnknownOperation(Props(new ContentActor()))
    }

//    it should "validate input before creating content" in {
//
//    }
//
//    it should "return node for given identifier" in {
//        val identifier = "do_1234"
//    }


}
