package org.sunbird.content.util
import scala.jdk.CollectionConverters._

import java.util

import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.common.dto.{Property, Request}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.utils.ScalaJsonUtils

// import scala.jdk.CollectionConverters.mapAsJavaMap replaced with .asJava)
import scala.concurrent.Future

class TestAssetManager extends AsyncFlatSpec with Matchers with AsyncMockFactory {
  "AssetCopyManager" should "return copied node identifier when asset is copied" ignore {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getNode()))
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getDefinitionNode_channel()))
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getDefinitionNode_channel()))
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(getCopiedNode()))
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getCopiedNode()))
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getCopiedNode()))
    (graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", "1234")))
    AssetCopyManager.copy(getCopyRequest()).map(resp => {
      assert(resp != null)
      assert(resp.getResponseCode == ResponseCode.OK)
      assert(resp.getResult.get("node_id").asInstanceOf[util.HashMap[String, AnyRef]].get("do_1234").asInstanceOf[String] == "do_1234_copy")
    })
  }

  private def getCopyRequest(): Request = {
    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() {
      {
        put("graph_id", "domain")
        put("version", "1.0")
        put("objectType", "Asset")
        put("schemaName", "content")

      }
    })
    request.setObjectType("Asset")
    request.putAll(new util.HashMap[String, AnyRef]() {
      {
        put("name", "test")
      }
    })
    request
  }

  private def getNode(): Node = {
    val node = new Node()
    node.setGraphId("domain")
    node.setIdentifier("do_1234")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Asset")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "do_1234")
        put("mimeType", "application/pdf")
        put("status", "Draft")
        put("contentType", "Resource")
        put("primaryCategory", "Learning Resource")
        put("name", "Copy content")
        put("artifactUrl", "https://ntpstagingall.blob.core.windows.net/ntp-content-staging/content/assets/do_212959046431154176151/hindi3.pdf")
        put("channel", "in.ekstep")
        put("code", "xyz")
        put("versionKey", "1234")
      }
    })
    node
  }


  def getDefinitionNode_channel(): Node = {
    val node = new Node()
    node.setIdentifier("obj-cat:learning-resource_content_in.ekstep")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Asset")
    node.setGraphId("domain")
    node.setMetadata(
      ScalaJsonUtils.deserialize[Map[String, AnyRef]]("{\n    \"objectCategoryDefinition\": {\n      \"name\": \"Learning Resource\",\n      \"description\": \"Content Playlist\",\n      \"categoryId\": \"obj-cat:learning-resource\",\n      \"targetObjectType\": \"Content\",\n      \"objectMetadata\": {\n        \"config\": {},\n        \"schema\": {\n          \"required\": [\n            \"author\",\n            \"copyright\",\n        \"audience\"\n          ],\n          \"properties\": {\n            \"audience\": {\n              \"type\": \"array\",\n              \"items\": {\n                \"type\": \"string\",\n                \"enum\": [\n                  \"Student\",\n                  \"Teacher\"\n                ]\n              },\n              \"default\": [\n                \"Student\"\n              ]\n            },\n            \"mimeType\": {\n              \"type\": \"string\",\n              \"enum\": [\n                \"application/pdf\"\n              ]\n            }\n          }\n        }\n      }\n    }\n  }").asJava)
    node
  }

  private def getCopiedNode(): Node = {
    val node = new Node()
    node.setIdentifier("do_1234_copy")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Asset")
    node.setGraphId("domain")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "do_1234_copy")
        put("mimeType", "application/pdf")
        put("status", "Draft")
        put("contentType", "Resource")
        put("primaryCategory", "Learning Resource")
        put("name", "Copy content")
        put("artifactUrl", "https://ntpstagingall.blob.core.windows.net/ntp-content-staging/content/assets/do_212959046431154176151/hindi3.pdf")
        put("channel", "in.ekstep")
        put("code", "xyz")
        put("versionKey", "1234")
      }
    })
    node
  }

}
