package org.sunbird.content.actors

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import java.util

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestAssetActor extends BaseSpec with MockFactory {

  "AssetActor" should "return failed response for 'unknown' operation" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = new OntologyEngineContext
    testUnknownOperation(Props(new AssetActor()), getContentRequest())
  }

  it should "return success response for 'copyAsset'" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getNode()))
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(node))
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(new Response())).anyNumberOfTimes()

    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    implicit val ss = mock[StorageService]
    val request = getContentRequest()
    request.getContext.put("identifier","do_1234")
    request.put("identifier","do_1234")
    request.setOperation("copy")
    val response = callActor(request, Props(new AssetActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.getResult.containsKey("node_id"))
    assert("test_321".equals(response.get("versionKey")))
  }

  it should "copy asset with invalid objectType, should through client exception" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getInvalidNode()))
    val request = getContentRequest()
    request.setOperation("copy")
    val response = callActor(request, Props(new AssetActor()))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    assert(response.getParams.getErrmsg == "Only asset can be copied")
  }

  private def getNode(): Node = {
    val node = new Node()
    node.setIdentifier("do_1234")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Asset")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "do_1234")
        put("mimeType", "application/vnd.ekstep.content-archive")
        put("status", "Live")
        put("name", "Asset_Test")
        put("versionKey", "test_321")
        put("channel", "in.ekstep")
        put("code", "Asset_Test")
        put("contentType", "Asset")
        put("primaryCategory", "Asset")
        put("artifactUrl", "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1234/artifact/file-0130860005482086401.svg")
      }
    })
    node
  }

  private def getContentRequest(): Request = {
    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() {
      {
        put("graph_id", "domain")
        put("version", "1.0")
        put("objectType", "Asset")
        put("schemaName", "asset")
        put("X-Channel-Id", "in.ekstep")
      }
    })
    request.setObjectType("Asset")
    request
  }

  private def getInvalidNode(): Node = {
    val node = new Node()
    node.setIdentifier("do_1234")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Content")
    node
  }

  def getFrameworkNode(): Node = {
    val node = new Node()
    node.setIdentifier("NCF")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Framework")
    node.setGraphId("domain")
    node.setMetadata(mapAsJavaMap(Map("name"-> "NCF")))
    node
  }

  def getBoardNode(): Node = {
    val node = new Node()
    node.setIdentifier("ncf_board_cbse")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Term")
    node.setGraphId("domain")
    node.setMetadata(mapAsJavaMap(Map("name"-> "CBSE")))
    node
  }
}
