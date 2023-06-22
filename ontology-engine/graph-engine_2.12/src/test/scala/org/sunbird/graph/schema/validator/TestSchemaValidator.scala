package org.sunbird.graph.schema.validator

import org.scalatest.Ignore

import java.util
import org.sunbird.graph.BaseSpec
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.schema.DefinitionFactory

import scala.concurrent.Future
@Ignore
class TestSchemaValidator extends BaseSpec {

  /*"check health api" should "return true" in {
    val future: Future[Response] = HealthCheckManager.checkAllSystemHealth()
    future map { response => {
      assert(ResponseCode.OK == response.getResponseCode)
      assert(response.get("healthy") == true)
    }
    }
  }*/

  "check schemaValidate api" should "return true" in {
    val definition = DefinitionFactory.getDefinition("domain", "collection", "1.0")

    val a = new util.ArrayList[AnyRef](){{ add(new util.HashMap[String, AnyRef](){{
      put("name","abc")
    }}) }}

    val metaData = new util.HashMap[String, AnyRef](){{
      put("name","abc")
      put("code", "code")
      put("contentType", "TextBook")
      put("mimeType", "application/vnd.ekstep.content-collection")
      put("channel", "in.ekstep")
      put("contentCredits", a)
      put("primaryCategory", "Learning Resource")
      put("boardIds", Array("ncf_board_cbse"))
      put("mediumIds", Array("ncf_medium_english"))
      put("subjectIds", Array("ncf_subject_cbse"))
      put("gradeLevelIds", Array("ncf_gradelevel_grade1"))
    }}


    val node: Node = new Node("abc", "DATA_NODE", "Content");
    node.setGraphId("domain")
    node.setMetadata(metaData)

    val future: Future[Node] = definition.validate(node, "create")
    future map { node => assert(null != node) }
  }
}
