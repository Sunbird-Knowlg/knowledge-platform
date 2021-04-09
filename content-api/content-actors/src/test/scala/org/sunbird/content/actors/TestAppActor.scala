package org.sunbird.content.actors

import akka.actor.Props
import org.apache.hadoop.util.StringUtils
import org.scalamock.scalatest.MockFactory
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.Request
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.{GraphService, OntologyEngineContext}

import scala.concurrent.ExecutionContext.Implicits.global
import java.util
import scala.collection.JavaConverters._
import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.Future

class TestAppActor extends BaseSpec with MockFactory {

  "AppActor" should "return failed response for 'unknown' operation" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = new OntologyEngineContext
    testUnknownOperation(Props(new AppActor()), getRequest())
  }

  it should "return success response for 'create' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB)
    val node = new Node("domain", "DATA_NODE", "App")
    node.setIdentifier("android-org.test.sunbird.integration")
    node.setObjectType("App")
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(node))
    val request = getRequest()
    request.getRequest.put("name", "Test Integration App")
    request.getRequest.put("logo", "logo url")
    request.getRequest.put("description", "Description of Test Integration App")
    request.getRequest.put("provider", Map("name" -> "Test Organisation", "copyright" -> "CC BY 4.0").asJava)
    request.getRequest.put("osType", "Android")
    request.getRequest.put("osMetadata", Map("packageId" -> "org.test.integration", "appVersion" -> "1.0", "compatibilityVer" -> "1.0").asJava)
    request.setOperation("create")
    val response = callActor(request, Props(new AppActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "throw client exception to have all the required properties for app register" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getRequest()
    request.getRequest.put("name", "Test Integration App")
    request.setOperation("create")
    val response = callActor(request, Props(new AppActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "return success response for update" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).repeated(2)
    val node = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
    val request = getRequest()
    request.putAll(mapAsJavaMap(Map("description" -> "test desc")))
    request.setOperation("update")
    val response = callActor(request, Props(new AppActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("identifier").equals("android-org.test.sunbird.integration"))
  }

  it should "return success response for read app" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).repeated(1)
    val node = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    val request = getRequest()
    request.putAll(mapAsJavaMap(Map("fields" -> "")))
    request.setOperation("read")
    val response = callActor(request, Props(new AppActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(StringUtils.equalsIgnoreCase(response.get("app").asInstanceOf[util.Map[String, AnyRef]].get("identifier").asInstanceOf[String], "android-org.test.sunbird.integration"))
    assert(StringUtils.equalsIgnoreCase(response.get("app").asInstanceOf[util.Map[String, AnyRef]].get("status").asInstanceOf[String], "Draft"))
  }

  private def getRequest(): Request = {
    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() {
      {
        put("graph_id", "domain")
        put("version", "1.0")
        put("objectType", "App")
        put("schemaName", "app")
        put("X-Channel-Id", "org.sunbird")
      }
    })
    request.setObjectType("App")
    request
  }

  private def getValidNode(): Node = {
    val node = new Node()
    node.setIdentifier("android-org.test.sunbird.integration")
    node.setNodeType("DATA_NODE")
    node.setObjectType("App")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "android-org.test.sunbird.integration")
        put("status", "Draft")
        put("name", "Test Integration App")
        put("logo", "logo url")
        put("description", "Description of Test Integration App")
        put("provider", Map("name" -> "Test Organisation", "copyright" -> "CC BY 4.0").asJava)
        put("osType", "Android")
        put("osMetadata", Map("packageId" -> "org.test.sunbird.integration", "appVersion" -> "1.0", "compatibilityVer" -> "1.0").asJava)
      }
    })
    node
  }

}
