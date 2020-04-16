package org.sunbird.content.actors

import java.util

import akka.actor.Props
import org.sunbird.common.dto.Request
import org.sunbird.graph.OntologyEngineContext

class TestCollectionActor extends BaseSpec {

    "CollectionActor" should "return failed response for 'unknown' operation" in {
        implicit val oec: OntologyEngineContext = new OntologyEngineContext
        testUnknownOperation( Props(new CollectionActor()), getCollectionRequest())
    }

    private def getCollectionRequest(): Request = {
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "Content")
                put("schemaName", "collection")

            }
        })
        request.setObjectType("Content")
        request
    }
}
