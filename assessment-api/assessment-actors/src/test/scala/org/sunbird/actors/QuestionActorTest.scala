package org.sunbird.actors
import java.util
import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.HttpUtil
import org.sunbird.common.dto.ResponseHandler
import org.sunbird.common.dto.{Property, Request, Response}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.graph.utils.ScalaJsonUtils
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.kafka.client.KafkaClient

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class QuestionActorTest extends BaseSpec with MockFactory {

	"questionActor" should "return failed response for 'unknown' operation" in {
		implicit val oec: OntologyEngineContext = new OntologyEngineContext
		testUnknownOperation(Props(new QuestionActor()), getQuestionRequest())
	}

	it should "return success response for 'createQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		val node = getNode("Question", None)
		(graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(node))
		(graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(new Response())).anyNumberOfTimes()
		val nodes: util.List[Node] = getCategoryNode()
		(graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

		val request = getQuestionRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map("channel"-> "in.ekstep","name" -> "New Content", "code" -> "1234", "mimeType"-> "application/vnd.sunbird.question", "primaryCategory" -> "Multiple Choice Question", "visibility" -> "Default")))
		request.setOperation("createQuestion")
		val response = callActor(request, Props(new QuestionActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "return success response for 'readQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB)
		val node = getNode("Question", None)
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
		val request = getQuestionRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "fields" -> "")))
		request.setOperation("readQuestion")
		val response = callActor(request, Props(new QuestionActor()))
		assert("successful".equals(response.getParams.getStatus))
	}
  
	it should "return success response for 'readPrivateQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB)
		val node = getNode("Question", Some(new util.HashMap[String, AnyRef]() {
			{
				put("name", "Question")
				put("visibility","Private")
				put("channel","abc-123")
			}
		}))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
		val request = getQuestionRequest()
		request.getContext.put("identifier","do1234")
		request.getRequest.put("channel", "abc-123")
		request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "fields" -> "")))
		request.setOperation("readPrivateQuestion")
		val response = callActor(request, Props(new QuestionActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "return client error for 'readPrivateQuestion' if channel is 'blank'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		val request = getQuestionRequest()
		request.getContext.put("identifier","do1234")
		request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "fields" -> "")))
		request.setOperation("readPrivateQuestion")
		val response = callActor(request, Props(new QuestionActor()))
		assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
		assert(response.getParams.getErr == "ERR_INVALID_CHANNEL")
		assert(response.getParams.getErrmsg == "Please Provide Channel!")
	}

	it should "return client error for 'readPrivateQuestion' if channel is mismatched" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB)
		val node = getNode("Question", Some(new util.HashMap[String, AnyRef]() {
			{
				put("name", "Question")
				put("visibility","Private")
				put("channel","abc-123")
			}
		}))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
		val request = getQuestionRequest()
		request.getContext.put("identifier","do1234")
		request.getRequest.put("channel", "abc")
		request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "fields" -> "")))
		request.setOperation("readPrivateQuestion")
		val response = callActor(request, Props(new QuestionActor()))
		assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
		assert(response.getParams.getErr == "ERR_ACCESS_DENIED")
		assert(response.getParams.getErrmsg == "Channel id is not matched")
	}

	it should "return client error response for 'readQuestion' if visibility is 'Private'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB)
		val node = getNode("Question", Some(new util.HashMap[String, AnyRef]() {
			{
				put("name", "Question")
				put("visibility","Private")
			}
		}))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
		val request = getQuestionRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "fields" -> "")))
		request.setOperation("readQuestion")
		val response = callActor(request, Props(new QuestionActor()))
		assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
	}

	it should "return success response for 'updateQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		val node = getNode("Question", None)
		node.getMetadata.putAll(Map("versionKey" -> "1234", "primaryCategory" -> "Multiple Choice Question", "name" -> "Updated New Content", "code" -> "1234", "mimeType"-> "application/vnd.sunbird.question").asJava)
		(graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
		(graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234"))))
		val nodes: util.List[Node] = getCategoryNode()
		(graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

		val request = getQuestionRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map( "versionKey" -> "1234", "description" -> "updated desc")))
		request.setOperation("updateQuestion")
		val response = callActor(request, Props(new QuestionActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "return success response for 'reviewQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		val node = getNode("Question", None)
		node.getMetadata.putAll(Map("versionKey" -> "1234", "primaryCategory" -> "Multiple Choice Question", "name" -> "Updated New Content", "code" -> "1234", "mimeType"-> "application/vnd.sunbird.question").asJava)
		(graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
		(graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234"))))
		val nodes: util.List[Node] = getCategoryNode()
		(graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

		val request = getQuestionRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map( "versionKey" -> "1234", "description" -> "updated desc")))
		request.setOperation("reviewQuestion")
		val response = callActor(request, Props(new QuestionActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "return success response for 'retireQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		val node = getNode("Question", None)
		node.getMetadata.putAll(Map("versionKey" -> "1234", "primaryCategory" -> "Practice Question Set", "name" -> "Updated New Content", "code" -> "1234", "mimeType"-> "application/vnd.sunbird.question").asJava)
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
		(graphDB.updateNodes(_: String, _: util.List[String], _: util.HashMap[String, AnyRef])).expects(*, *, *).returns(Future(new util.HashMap[String, Node]))
		val request = getQuestionRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map( "versionKey" -> "1234", "description" -> "updated desc")))
		request.setOperation("retireQuestion")
		val response = callActor(request, Props(new QuestionActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "return success response for 'publishQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		val kfClient = mock[KafkaClient]
		(oec.kafkaClient _).expects().returns(kfClient).anyNumberOfTimes()
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		val node = getNode("Question", None)
		node.getMetadata.putAll(Map("versionKey" -> "1234", "primaryCategory" -> "Practice Question Set", "name" -> "Updated New Content", "code" -> "1234", "mimeType"-> "application/vnd.sunbird.question").asJava)
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
		(kfClient.send(_:String, _:String)).expects(*,*).once()
		val request = getQuestionRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map( "versionKey" -> "1234", "description" -> "updated desc")))
		request.setOperation("publishQuestion")
		val response = callActor(request, Props(new QuestionActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "send events to kafka topic" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val kfClient = mock[KafkaClient]
		val hUtil = mock[HttpUtil]
		(oec.httpUtil _).expects().returns(hUtil)
		val resp :Response = ResponseHandler.OK()
		resp.put("question", new util.HashMap[String, AnyRef](){{
			put("framework", "NCF")
			put("channel", "test")
			put("status", "Live")
		}})
		(hUtil.get(_: String, _: String, _: util.Map[String, String])).expects(*, *, *).returns(resp)
		(oec.kafkaClient _).expects().returns(kfClient)
		(kfClient.send(_: String, _: String)).expects(*, *).returns(None)
		val request = getQuestionRequest()
		request.getRequest.put("question", new util.HashMap[String, AnyRef](){{
			put("source", "https://dock.sunbirded.org/api/question/v1/read/do_113486481122729984143")
			put("metadata", new util.HashMap[String, AnyRef](){{
				put("name", "Test Question")
				put("description", "Test Question")
				put("mimeType", "application/vnd.sunbird.question")
				put("code", "test.ques.1")
				put("primaryCategory", "Learning Resource")
			}})
		}})
		request.setOperation("importQuestion")
		request.setObjectType("Question")
		val response = callActor(request, Props(new QuestionActor()))
		assert(response.get("processId") != null)
	}

	it should "return success response for 'systemUpdateQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		val node = getNode("Question", None)
		node.getMetadata.putAll(Map("versionKey" -> "1234", "primaryCategory" -> "Multiple Choice Question", "name" -> "Updated New Content", "code" -> "1234", "mimeType" -> "application/vnd.sunbird.question").asJava)
		(graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(new Response())).anyNumberOfTimes()
		(graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
		(graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(List(node))).once()
		val nodes: util.List[Node] = getCategoryNode()
		(graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
		(graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234"))))
		val request = getQuestionRequest()
		request.getContext.put("identifier", "test_id")
		request.putAll(mapAsJavaMap(Map("versionKey" -> "1234", "description" -> "updated desc")))
		request.setOperation("systemUpdateQuestion")
		val response = callActor(request, Props(new QuestionActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "return success response for 'listQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		val node = getNode("Question", None)
		node.getMetadata.putAll(Map("versionKey" -> "1234", "primaryCategory" -> "Multiple Choice Question", "name" -> "Updated New Content", "code" -> "1234", "mimeType" -> "application/vnd.sunbird.question").asJava)
		(graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(new Response())).anyNumberOfTimes()
		(graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(List(node))).once()
		val request = getQuestionRequest()
		request.put("identifiers", util.Arrays.asList( "test_id"))
		request.put("identifier", util.Arrays.asList( "test_id"))
		request.put("fields", "")
		request.setOperation("listQuestions")
		val response = callActor(request, Props(new QuestionActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "throw exception for 'listQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val request = getQuestionRequest()
		request.put("identifier", null)
		request.put("fields", "")
		request.setOperation("listQuestions")
		val response = callActor(request, Props(new QuestionActor()))
		assert(response.getResponseCode.code == 400)
	}

	it should "throw client exception for 'listQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val request = getQuestionRequest()
		request.put("identifiers",  util.Arrays.asList( "test_id_1","test_id_2","test_id_3","test_id_4","test_id_5","test_id_6","test_id_7","test_id_8","test_id_9","test_id_10","test_id_11","test_id_12","test_id_13","test_id_14","test_id_15","test_id_16","test_id_17","test_id_18","test_id_19","test_id_20","test_id_21"))
		request.setOperation("listQuestions")
		request.put("fields", "")
		val response = callActor(request, Props(new QuestionActor()))
		assert(response.getResponseCode.code == 400)
	}

	it should "return success response for 'rejectQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		val node = getNode("Question", None)
		node.getMetadata.putAll(Map("versionKey" -> "1234", "primaryCategory" -> "Multiple Choice Question", "name" -> "Updated New Content", "code" -> "1234", "mimeType"-> "application/vnd.sunbird.question","status" -> "Review").asJava)
		(graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
		(graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234"))))
		val nodes: util.List[Node] = getCategoryNode()
		(graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

		val request = getQuestionRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map( "versionKey" -> "1234", "description" -> "updated description","rejectComment" -> "Rejected for testing")))
		request.setOperation("rejectQuestion")
		val response = callActor(request, Props(new QuestionActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	private def getQuestionRequest(): Request = {
		val request = new Request()
		request.setContext(new java.util.HashMap[String, AnyRef]() {
			{
				put("graph_id", "domain")
				put("version", "1.0")
				put("objectType", "Question")
				put("schemaName", "question")
			}
		})
		request.setObjectType("Question")
		request
	}

	def getDefinitionNode(): Node = {
		val node = new Node()
		node.setIdentifier("obj-cat:practice-question-set_question_all")
		node.setNodeType("DATA_NODE")
		node.setObjectType("ObjectCategoryDefinition")
		node.setGraphId("domain")
		node.setMetadata(mapAsJavaMap(
			ScalaJsonUtils.deserialize[Map[String,AnyRef]]("{\n    \"objectCategoryDefinition\": {\n      \"name\": \"Learning Resource\",\n      \"description\": \"Content Playlist\",\n      \"categoryId\": \"obj-cat:practice_question_set\",\n      \"targetObjectType\": \"Content\",\n      \"objectMetadata\": {\n        \"config\": {},\n        \"schema\": {\n          \"required\": [\n            \"author\",\n            \"copyright\",\n            \"license\",\n            \"audience\"\n          ],\n          \"properties\": {\n            \"audience\": {\n              \"type\": \"array\",\n              \"items\": {\n                \"type\": \"string\",\n                \"enum\": [\n                  \"Student\",\n                  \"Teacher\"\n                ]\n              },\n              \"default\": [\n                \"Student\"\n              ]\n            },\n            \"mimeType\": {\n              \"type\": \"string\",\n              \"enum\": [\n                \"application/pdf\"\n              ]\n            }\n          }\n        }\n      }\n    }\n  }")))
		node
	}
}
