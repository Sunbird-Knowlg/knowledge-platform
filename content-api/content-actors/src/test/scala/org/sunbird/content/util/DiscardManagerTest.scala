package org.sunbird.content.util

import org.scalamock.scalatest.MockFactory
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException
import org.sunbird.content.actors.BaseSpec
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node

import java.util
import scala.collection.JavaConversions.mapAsJavaMap

class DiscardManagerTest  extends BaseSpec with MockFactory  {

    it should "discard node in Live state should return client error" in {
        implicit val oec: OntologyEngineContext = new OntologyEngineContext
        val request = getContentRequest()
        request.getRequest.putAll(mapAsJavaMap(Map("identifier" -> "")))
        request.setOperation("discardContent")
        val exception = intercept[ClientException] {
            DiscardManager.validateRequest(request)
        }
        exception.getMessage shouldEqual "Please provide valid content identifier"
    }

    private def getContentRequest(): Request = {
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "Content")
                put("schemaName", "content")

            }
        })
        request.setObjectType("Content")
        request
    }

    private def getInValidNodeToDiscard(): Node = {
        val node = new Node()
        node.setIdentifier("do_12346")
        node.setNodeType("DATA_NODE")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_12346")
                put("mimeType", "application/pdf")
                put("status", "Live")
                put("contentType", "Resource")
                put("name", "Node To discard")
            }
        })
        node
    }
}
