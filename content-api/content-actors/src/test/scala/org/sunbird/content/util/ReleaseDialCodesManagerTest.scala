package org.sunbird.content.util

import java.util

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.cloudstore.StorageService
import org.sunbird.content.actors.{BaseSpec, ContentActor}
import org.sunbird.common.dto.Request
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.{GraphService, OntologyEngineContext}

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ReleaseDialCodesManagerTest extends BaseSpec with MockFactory {

  it should "return channel blank Error for release dialcode" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getNode("Content", None)
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_:String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
    val request = getRequest()
    request.getRequest.remove("channel")
    val response = callActor(request, Props(new ContentActor()))
    println("response :"+response.getResponseCode)
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "return content not found Error for release dialcode" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getNode("Content", None)
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_:String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
    val request = getRequest()
    val response = callActor(request, Props(new ContentActor()))
    println("response :"+response.getResponseCode)
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "return response for release dialcode" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val nodeMetaData = getMetaData()
    val node = getNode("Content", Option(nodeMetaData))
    (graphDB.updateNodes(_:String, _: util.ArrayList[String], _: util.Map[String,AnyRef])).expects(*, *, *).returns(Future(Map("Node" -> node)))
    val request = getRequest()
    val response  = ReleaseDialCodesManager.getResponse(request, Map(ContentConstants.RESERVED_DIALCODES -> ""), new util.HashSet[String](), Map("" -> ""))
    response.map(res => {
      println("response :"+res.getResponseCode)
      assert("successful".equals(res.getParams.getStatus))
    })
  }

  it should "validate the request" in {
    val hierarchyMap = getMetaData()
    ReleaseDialCodesManager.validateRequest(hierarchyMap , "Test")
  }

  it should "return updated map" in {
    val request = getRequest()
    val hierarchyMap = getMetaData()
    val assignedDialCodes = new util.HashSet[String]()
    val releasedDialCodes = new util.HashSet[String]()
    val reservedDialcodesMap = ReleaseDialCodesManager.getUpdatedMap(request, hierarchyMap, assignedDialCodes, releasedDialCodes)
    assert(reservedDialcodesMap.containsKey("reservedDialcodes"))
  }

  private def getRequest(): Request = {
    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() {
      {
        put("objectType", "Content")
        put("graph_id", "domain")
        put("version", "1.0")
        put("schemaName", "content")
        put("identifier","domain")
      }
    })
    request.putAll(Map("identifier" -> "domain", "channel" -> "Test"))
    request.setOperation("releaseDialcodes")
    request
  }

  private def getMetaData(): util.Map[String, AnyRef] ={
    new util.HashMap[String, AnyRef]() {{
      put("name", "Domain")
      put("code", "domain")
      put("status", "Live")
      put("identifier", "domain")
      put("versionKey", "1234")
      put("contentType", "TextBook")
      put("channel", "Test")
      put("reservedDialcodes", new util.HashMap[String, Integer]() {{ put("L8W5V1", 0)
        put("T2I6C9", 1) }})
      put("mimeType", "application/vnd.ekstep.content-collection")
    }}
  }

}
