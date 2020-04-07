package org.sunbird.content.util

import java.util

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, ResponseHandler}
import org.sunbird.content.actors.{BaseSpec, ContentActor}
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.Node

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestAcceptFlagManager extends BaseSpec with MockFactory {

  private val script_1 = "CREATE KEYSPACE IF NOT EXISTS hierarchy_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
  private val script_2 = "CREATE TABLE IF NOT EXISTS hierarchy_store.content_hierarchy (identifier text, hierarchy text,PRIMARY KEY (identifier));"
  private val script_3 = "INSERT INTO hierarchy_store.content_hierarchy(identifier, hierarchy) values ('domain', '{\"identifier\":\"domain\",\"children\":[{\"parent\":\"domain\",\"identifier\":\"domain\",\"copyright\":\"Sunbird\",\"lastStatusChangedOn\":\"2019-08-21T14:37:50.281+0000\",\"code\":\"2e837725-d663-45da-8ace-9577ab111982\",\"visibility\":\"Parent\",\"index\":1,\"mimeType\":\"application/vnd.ekstep.content-collection\",\"createdOn\":\"2019-08-21T14:37:50.281+0000\",\"versionKey\":\"1566398270281\",\"framework\":\"tpd\",\"depth\":1,\"children\":[],\"name\":\"U1\",\"lastUpdatedOn\":\"2019-08-21T14:37:50.281+0000\",\"contentType\":\"CourseUnit\",\"status\":\"Live\"}],\"status\":\"Live\"}');"

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeCassandraQuery(script_1, script_2, script_3)
  }

  it should "return success response for acceptFlag for Resource" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val nodeMetaData = new util.HashMap[String, AnyRef]() {{
      put("name", "Domain")
      put("code", "domain")
      put("status", "Flagged")
      put("identifier", "domain")
      put("versionKey", "1521106144664")
      put("contentType", "Resource")
      put("channel", "Test")
      put("mimeType", "application/pdf")
    }}
    val node = getNode("Content", Option(nodeMetaData))
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_:String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
    val request = getRequest()
    request.getContext.put("identifier","domain")
    request.getRequest.putAll(mapAsJavaMap(Map("identifier" -> "domain")))
    request.setOperation("acceptFlag")
    val response = callActor(request, Props(new ContentActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "return success response for acceptFlag for Collection" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val nodeMetaData = new util.HashMap[String, AnyRef]() {{
      put("name", "Domain")
      put("code", "domain")
      put("status", "Flagged")
      put("identifier", "domain")
      put("versionKey", "1521106144664")
      put("contentType", "TextBook")
      put("channel", "Test")
      put("mimeType", "application/vnd.ekstep.content-collection")
    }}
    val node = getNode("Content", Option(nodeMetaData))
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_:String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
    val resp = ResponseHandler.OK()
    resp.getResult.put("content", new util.HashMap[String, AnyRef])
    val request = getRequest()
    request.getContext.put("identifier","domain")
    request.getRequest.putAll(mapAsJavaMap(Map("identifier" -> "domain")))
    request.setOperation("acceptFlag")
    val response = callActor(request, Props(new ContentActor()))
    println("response :"+response.getResponseCode)
    assert("successful".equals(response.getParams.getStatus))
  }

  private def getRequest(): Request = {
    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() {
      {
        put("objectType", "Content")
        put("graph_id", "domain")
        put("version", "1.0")
        put("schemaName", "content")
      }
    })
    request
  }
}
