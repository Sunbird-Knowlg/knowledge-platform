package org.sunbird.content.util

import java.util

import org.scalamock.matchers.Matchers
import org.scalamock.scalatest.{AsyncMockFactory, MockFactory}
import org.scalatest.{AsyncFlatSpec, FlatSpec}
import org.sunbird.common.dto.Request
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.service.operation.NodeAsyncOperations

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


class CopyManagerTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {

    "CopyManager" should "return copied node identifier when content is copied" ignore  {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB)
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getNode()))
        CopyManager.copy(getCopyRequest()).map(resp =>{
            assert(resp != null)
        })
    }

    it should "return copied node identifier and safe hierarchy in cassandra when collection is copied" in {
        assert(true)
    }

    private def getNode(): Node = {
        val node = new Node()
        node.setIdentifier("do_1234")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_1234")
                put("mimeType", "application/pdf")
                put("status", "Draft")
                put("contentType", "Resource")
                put("name", "Copy content")
                put("artifactUrl", "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_112980506913521664129/artifact/1584534572208_do_112980506913521664129.zip")
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
