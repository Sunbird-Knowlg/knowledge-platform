package org.sunbird.content.actors

import java.util

import akka.actor.Props
import org.sunbird.common.dto.Request
import org.sunbird.graph.OntologyEngineContext

class TestLicenseActor extends BaseSpec {

    "LicenseActor" should "return failed response for 'unknown' operation" in {
        implicit val oec: OntologyEngineContext = new OntologyEngineContext
        testUnknownOperation(Props(new LicenseActor()), getLicenseRequest())
    }

    private def getLicenseRequest(): Request = {
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "License")
                put("schemaName", "license")

            }
        })
        request.setObjectType("License")
        request
    }
}
