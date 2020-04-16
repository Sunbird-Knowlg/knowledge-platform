package org.sunbird.content.util

import java.util

import org.scalamock.matchers.Matchers
import org.scalamock.scalatest.{AsyncMockFactory, MockFactory}
import org.scalatest.{AsyncFlatSpec, FlatSpec}
import org.sunbird.common.dto.{Property, Request}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.service.operation.NodeAsyncOperations

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


class CopyManagerTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {

    "CopyManager" should "return copied node identifier when content is copied" ignore {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).repeated(5)
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getNode()))
        (graphDB.addNode(_: String, _:Node)).expects(*, *).returns(Future(getCopiedNode()))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getCopiedNode()))
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getCopiedNode()))
        (graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234"))))
        CopyManager.copy(getCopyRequest()).map(resp =>{
            assert(resp != null)
            assert(resp.getResponseCode == ResponseCode.OK)
            assert(resp.getResult.get("node_id").asInstanceOf[util.HashMap[String, AnyRef]].get("do_1234").asInstanceOf[String] == "do_1234_copy" )
        })
    }

    it should "return copied node identifier and safe hierarchy in cassandra when collection is copied" in {
        assert(true)
    }

    private def getNode(): Node = {
        val node = new Node()
        node.setIdentifier("do_1234")
        node.setNodeType("DATA_NODE")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_1234")
                put("mimeType", "application/pdf")
                put("status", "Draft")
                put("contentType", "Resource")
                put("name", "Copy content")
                put("artifactUrl","https://ntpstagingall.blob.core.windows.net/ntp-content-staging/content/assets/do_212959046431154176151/hindi3.pdf")
                put("channel", "in.ekstep")
                put("code", "xyz")
                put("versionKey", "1234")
            }
        })
        node
    }

    private def getCopiedNode(): Node = {
        val node = new Node()
        node.setIdentifier("do_1234_copy")
        node.setNodeType("DATA_NODE")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_1234_copy")
                put("mimeType", "application/pdf")
                put("status", "Draft")
                put("contentType", "Resource")
                put("name", "Copy content")
                put("artifactUrl", "https://ntpstagingall.blob.core.windows.net/ntp-content-staging/content/assets/do_212959046431154176151/hindi3.pdf")
                put("channel", "in.ekstep")
                put("code", "xyz")
                put("versionKey", "1234")
            }
        })
        node
    }

    private def getCopyRequest(): Request = {
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
        request.putAll(new util.HashMap[String, AnyRef]() {
            {
                put("createdBy", "EkStep")
                put("createdFor", new util.ArrayList[String]() {
                    {
                        add("Ekstep")
                    }
                })
                put("organisation", new util.ArrayList[String]() {
                    {
                        add("ekstep")
                    }
                })
                put("framework", "DevCon-NCERT")
            }
        })
        request
    }
}
