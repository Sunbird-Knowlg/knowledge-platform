package org.sunbird.graph.utils

import java.util
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.common.JsonUtils
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

	"deserialize with valid data" should "return a node with all data" in {
		val nodeString :String = """{
		                           |  "ownershipType": [
		                           |    "createdBy"
		                           |  ],
		                           |  "code": "test.res.1",
		                           |  "channel": "test",
		                           |  "language": [
		                           |    "English"
		                           |  ],
		                           |  "mimeType": "application/pdf",
		                           |  "idealScreenSize": "normal",
		                           |  "createdOn": "2020-01-17T16:17:39.931+0530",
		                           |  "objectType": "Content",
		                           |  "collections": [
		                           |    {
		                           |      "identifier": "LP_FT-74320",
		                           |      "name": "LP_FT-74320",
		                           |      "description": "test desc",
		                           |      "objectType": "Content",
		                           |      "relation": "hasSequenceMember",
		                           |      "status": "Live"
		                           |    }
		                           |  ],
		                           |  "contentDisposition": "inline",
		                           |  "lastUpdatedOn": "2020-01-17T16:17:39.931+0530",
		                           |  "contentEncoding": "identity",
		                           |  "contentType": "Resource",
		                           |  "dialcodeRequired": "No",
		                           |  "identifier": "do_11293728197296947212",
		                           |  "lastStatusChangedOn": "2020-01-17T16:17:39.931+0530",
		                           |  "audience": [
		                           |    "Learner"
		                           |  ],
		                           |  "os": [
		                           |    "All"
		                           |  ],
		                           |  "visibility": "Default",
		                           |  "resources": [
		                           |    "Speaker",
		                           |    "Touch"
		                           |  ],
		                           |  "mediaType": "content",
		                           |  "osId": "org.ekstep.quiz.app",
		                           |  "languageCode": [
		                           |    "en"
		                           |  ],
		                           |  "version": 2,
		                           |  "versionKey": "1579258059931",
		                           |  "license": "CC BY 4.0",
		                           |  "idealScreenDensity": "hdpi",
		                           |  "framework": "NCF",
		                           |  "concepts": [
		                           |    {
		                           |      "identifier": "LO1",
		                           |      "name": "Word Meaning",
		                           |      "description": "Understanding meaning of words",
		                           |      "objectType": "Concept",
		                           |      "relation": "associatedTo",
		                           |      "status": "Live"
		                           |    }
		                           |  ],
		                           |  "compatibilityLevel": 1,
		                           |  "name": "Resource Content 1",
		                           |  "status": "Live"
		                           |}""".stripMargin
		val nodeMap: util.Map[String, AnyRef] = JsonUtils.deserialize(nodeString.asInstanceOf[String], classOf[java.util.Map[String, AnyRef]])
		val relString = """{
		                  |  "concepts": {
		                  |    "objects": [
		                  |      "Concept"
		                  |    ],
		                  |    "type": "associatedTo",
		                  |    "direction": "out"
		                  |  },
		                  |  "children": {
		                  |    "objects": [
		                  |      "Content",
		                  |      "ContentImage"
		                  |    ],
		                  |    "type": "hasSequenceMember",
		                  |    "direction": "out"
		                  |  },
		                  |  "collections": {
		                  |    "objects": [
		                  |      "Content",
		                  |      "ContentImage"
		                  |    ],
		                  |    "type": "hasSequenceMember",
		                  |    "direction": "in"
		                  |  },
		                  |  "usesContent": {
		                  |    "objects": [
		                  |      "Content"
		                  |    ],
		                  |    "type": "associatedTo",
		                  |    "direction": "out"
		                  |  },
		                  |  "questions": {
		                  |    "objects": [
		                  |      "AssessmentItem"
		                  |    ],
		                  |    "type": "associatedTo",
		                  |    "direction": "out"
		                  |  },
		                  |  "usedByContent": {
		                  |    "objects": [
		                  |      "Content"
		                  |    ],
		                  |    "type": "associatedTo",
		                  |    "direction": "in"
		                  |  }
		                  |}""".stripMargin
		val relationMap: util.Map[String, AnyRef] = JsonUtils.deserialize(relString.asInstanceOf[String], classOf[java.util.Map[String, AnyRef]])
		val node = NodeUtil.deserialize(nodeMap, "content", relationMap)
		node.getIdentifier shouldBe "do_11293728197296947212"
		node.getOutRelations.size() shouldEqual 1
		node.getOutRelations().get(0).getRelationType shouldEqual "associatedTo"
		node.getOutRelations.get(0).getEndNodeId shouldEqual "LO1"
		node.getInRelations.size() shouldEqual 1
		node.getInRelations().get(0).getRelationType shouldEqual "hasSequenceMember"
		node.getInRelations.get(0).getStartNodeId shouldEqual "LP_FT-74320"
	}
}
