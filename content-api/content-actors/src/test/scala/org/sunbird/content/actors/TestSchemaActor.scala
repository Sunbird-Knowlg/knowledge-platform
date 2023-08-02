package org.sunbird.content.actors

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.Request
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.{GraphService, OntologyEngineContext}

import java.util
import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestSchemaActor extends BaseSpec with MockFactory{

  "SchemaActor" should "return failed response for 'unknown' operation" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = new OntologyEngineContext
    testUnknownOperation(Props(new ObjectActor()), getRequest())
  }


  it should "return success response for 'readSchema'" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB)
    val node = getNode("Schema", None)
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
    implicit val ss = mock[StorageService]
    val request = getRequest()
    request.getContext.put("identifier", "emp_1234")
    request.putAll(mapAsJavaMap(Map("identifier" -> "emp_1234", "fields" -> "")))
    request.setOperation("readSchema")
    val response = callActor(request, Props(new SchemaActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  private def getRequest(): Request = {
    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() {
      {
        put("graph_id", "domain")
        put("version", "1.0")
        put("objectType", "Schema")
        put("schemaName", "schema")
      }
    })
    request.setObjectType("Schema")
    request
  }

  override def getNode(objectType: String, metadata: Option[util.Map[String, AnyRef]]): Node = {
    val node = new Node("domain", "DATA_NODE", objectType)
    node.setGraphId("domain")
    val nodeMetadata = metadata.getOrElse(new util.HashMap[String, AnyRef]() {
      {
        put("name", "Schema Node")
        put("code", "Schema-node")
        put("status", "Draft")
      }
    })
    node.setMetadata(nodeMetadata)
    node.setObjectType(objectType)
    node.setIdentifier("emp_1234")
    node
  }

}
