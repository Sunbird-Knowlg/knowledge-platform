package org.sunbird.content.actors
import scala.jdk.CollectionConverters._

import java.util
import java.util.concurrent.TimeUnit

import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.testkit.TestKit
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.schema.FrameworkMasterCategoryMap

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
        probe.expectMsgType[Response](FiniteDuration.apply(30, TimeUnit.SECONDS))
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
        node.setObjectType(objectType)
        node.setIdentifier("test_id")
        node
    }

    def getCategoryNode(): util.List[Node] = {
        val node = new Node()
        node.setIdentifier("board")
        node.setNodeType("DATA_NODE")
        node.setObjectType("Category")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("code", "board")
                put("orgIdFieldName", "boardIds")
                put("targetIdFieldName", "targetBoardIds")
                put("searchIdFieldName", "se_boardIds")
                put("searchLabelFieldName", "se_boards")
                put("status", "Live")
            }
        })
        util.Arrays.asList(node)
    }

    def enrichFrameworkMasterCategoryMap() = {
        val node = new Node()
        node.setIdentifier("board")
        node.setNodeType("DATA_NODE")
        node.setObjectType("Category")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("code", "board")
                put("orgIdFieldName", "boardIds")
                put("targetIdFieldName", "targetBoardIds")
                put("searchIdFieldName", "se_boardIds")
                put("searchLabelFieldName", "se_boards")
                put("status", "Live")
            }
        })
        val masterCategories: scala.collection.immutable.Map[String, AnyRef] = Map[String,AnyRef](
            node.getMetadata.getOrDefault("code", "").asInstanceOf[String] ->
              Map[String, AnyRef]("code" -> node.getMetadata.getOrDefault("code", "").asInstanceOf[String],
                  "orgIdFieldName" -> node.getMetadata.getOrDefault("orgIdFieldName", "").asInstanceOf[String],
                  "targetIdFieldName" -> node.getMetadata.getOrDefault("targetIdFieldName", "").asInstanceOf[String],
                  "searchIdFieldName" -> node.getMetadata.getOrDefault("searchIdFieldName", "").asInstanceOf[String],
                  "searchLabelFieldName" -> node.getMetadata.getOrDefault("searchLabelFieldName", "").asInstanceOf[String])
        )
        FrameworkMasterCategoryMap.put("masterCategories", masterCategories)
    }
}
