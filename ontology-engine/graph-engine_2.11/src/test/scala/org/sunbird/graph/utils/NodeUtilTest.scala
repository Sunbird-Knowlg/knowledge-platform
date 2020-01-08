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
}
