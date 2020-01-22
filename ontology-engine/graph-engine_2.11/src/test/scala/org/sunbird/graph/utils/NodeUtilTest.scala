package org.sunbird.graph.utils

import java.util
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.graph.dac.model.Node

class NodeUtilTest extends FlatSpec with Matchers {

	"serialize with fields" should "return only node property which are present in fields" in {
		val node: Node = new Node("do_1234", "DATA_NODE", "Content");
		node.setMetadata(new util.HashMap[String, AnyRef]() {{
			put("language", new util.ArrayList[String]() {{
				add("English")
			}})
			put("contentType", "Resource")
			put("name", "Test Resource Content")
			put("code", "test.res.1")
		}})
		val fields: util.ArrayList[String] = new util.ArrayList[String]() {{
			add("contentType")
			add("name")
		}}
		val result: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, "content")
		result.isEmpty shouldBe false
		result.size() shouldBe 4
		result.containsKey("identifier") shouldBe true
		result.containsKey("contentType") shouldBe true
		result.containsKey("name") shouldBe true
		result.containsKey("languageCode") shouldBe true
	}
}
