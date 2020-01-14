package org.sunbird.graph.utils

import java.util
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.graph.dac.model.Node

class NodeUtilTest extends FlatSpec with Matchers {

	"getLanguageCodes with node having language with string type" should "return language code successfully" in {
		val node:Node = new Node("domain","DATA_NODE","Content");
		node.setMetadata(new util.HashMap[String, AnyRef](){{
			put("language","English")
		}})
		val result:util.List[String] = NodeUtil.getLanguageCodes(node)
		result.contains("en") shouldBe true
	}

	"getLanguageCodes with node having language with array type" should "return language code successfully" in {
		val node:Node = new Node("domain","DATA_NODE","Content");
		node.setMetadata(new util.HashMap[String, AnyRef](){{
			put("language", Array("English","Hindi"))
		}})
		val result:util.List[String] = NodeUtil.getLanguageCodes(node)
		result.contains("en") shouldBe true
		result.contains("hi") shouldBe true
	}

	"getLanguageCodes with node having language with list type" should "return language code successfully" in {
		val node:Node = new Node("domain","DATA_NODE","Content");
		node.setMetadata(new util.HashMap[String, AnyRef](){{
			put("language", new util.ArrayList[String](){{
				add("English")
				add("Hindi")
			}})
		}})
		val result:util.List[String] = NodeUtil.getLanguageCodes(node)
		result.contains("en") shouldBe true
		result.contains("hi") shouldBe true
	}

	"getLanguageCodes with node not having language attribute" should "return empty language code successfully" in {
		val node:Node = new Node("domain","DATA_NODE","Content");
		node.setMetadata(new util.HashMap[String, AnyRef]())
		val result:util.List[String] = NodeUtil.getLanguageCodes(node)
		result.isEmpty shouldBe true
	}

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
		val result: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, "content", "1.0")
		result.isEmpty shouldBe false
		result.size() shouldBe 4
		result.containsKey("identifier") shouldBe true
		result.containsKey("contentType") shouldBe true
		result.containsKey("name") shouldBe true
		result.containsKey("languageCode") shouldBe true
	}
}
