package org.sunbird.actors

import java.util

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.dto.{Property, Request, Response}
import org.sunbird.graph.dac.model.{Node, Relation, SearchCriteria}
import org.sunbird.graph.nodes.DataNode.getRelationMap
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.kafka.client.KafkaClient

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class QuestionSetActorTest extends BaseSpec with MockFactory {

	"questionSetActor" should "return failed response for 'unknown' operation" in {
		implicit val oec: OntologyEngineContext = new OntologyEngineContext
		testUnknownOperation(Props(new QuestionSetActor()), getQuestionSetRequest())
	}

	it should "return success response for 'readQuestionSet'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB)
		val node = getNode("QuestionSet", Some(new util.HashMap[String, AnyRef] () {{
			put("name", "QuestionSet")
			put("description", "Updated question Set")
		}}))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
		val request = getQuestionSetRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "fields" -> "")))
		request.setOperation("readQuestionSet")
		val response = callActor(request, Props(new QuestionSetActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "return success response for 'createQuestionSet'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB)
		val node = getNode("QuestionSet", None)
		(graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(node))
		val request = getQuestionSetRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map("name" -> "question_1",
			"visibility" -> "Public",
			"code" -> "finemanfine",
			"navigationMode" -> "linear",
			"allowSkip" -> true.asInstanceOf[Object],
			"requiresSubmit" -> false.asInstanceOf[Object],
			"shuffle" -> true.asInstanceOf[Object],
			"showFeedback" -> true.asInstanceOf[Object],
			"showSolutions" -> true.asInstanceOf[Object],
			"showHints" -> true.asInstanceOf[Object],
			"summaryType" -> "Complete",
			"mimeType" -> "application/vnd.ekstep.questionset")))
		request.setOperation("createQuestionSet")
		val response = callActor(request, Props(new QuestionSetActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "return success response for 'updateQuestionSet'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		val node = getNode("QuestionSet", None)
		node.getMetadata.putAll(mapAsJavaMap(Map("name" -> "question_1",
			"visibility" -> "Public",
			"code" -> "finemanfine",
			"description" -> "Updated description",
			"navigationMode" -> "linear",
			"allowSkip" -> true.asInstanceOf[Object],
			"requiresSubmit" -> false.asInstanceOf[Object],
			"shuffle" -> true.asInstanceOf[Object],
			"showFeedback" -> true.asInstanceOf[Object],
			"showSolutions" -> true.asInstanceOf[Object],
			"showHints" -> true.asInstanceOf[Object],
			"summaryType" -> "Complete",
			"mimeType" -> "application/vnd.ekstep.questionset")))
		(graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
		(graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234"))))
		val request = getQuestionSetRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map( "versionKey" -> "1234", "description" -> "updated desc")))
		request.setOperation("updateQuestionSet")
		val response = callActor(request, Props(new QuestionSetActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "return success response for 'reviewQuestionSet'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		val node = getNode("QuestionSet", None)
		node.getMetadata.putAll(mapAsJavaMap(Map("name" -> "question_1",
			"visibility" -> "Public",
			"code" -> "finemanfine",
			"navigationMode" -> "linear",
			"allowSkip" -> true.asInstanceOf[Object],
			"requiresSubmit" -> false.asInstanceOf[Object],
			"shuffle" -> true.asInstanceOf[Object],
			"showFeedback" -> true.asInstanceOf[Object],
			"showSolutions" -> true.asInstanceOf[Object],
			"showHints" -> true.asInstanceOf[Object],
			"summaryType" -> "Complete",
			"versionKey" -> "1234",
			"mimeType" -> "application/vnd.ekstep.questionset")))
		(graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
		(graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234"))))
		val request = getQuestionSetRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map( "versionKey" -> "1234", "description" -> "updated desc")))
		request.setOperation("reviewQuestionSet")
		val response = callActor(request, Props(new QuestionSetActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "return success response for 'retireQuestionSet" +
		"'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		val node = getNode("QuestionSet", None)
		node.getMetadata.putAll(mapAsJavaMap(Map("name" -> "question_1",
			"visibility" -> "Public",
			"code" -> "finemanfine",
			"navigationMode" -> "linear",
			"allowSkip" -> true.asInstanceOf[Object],
			"requiresSubmit" -> false.asInstanceOf[Object],
			"shuffle" -> true.asInstanceOf[Object],
			"showFeedback" -> true.asInstanceOf[Object],
			"showSolutions" -> true.asInstanceOf[Object],
			"showHints" -> true.asInstanceOf[Object],
			"summaryType" -> "Complete",
			"mimeType" -> "application/vnd.ekstep.questionset")))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
		(graphDB.updateNodes(_: String, _: util.List[String], _: util.HashMap[String, AnyRef])).expects(*, *, *).returns(Future(new util.HashMap[String, Node]))
		val request = getQuestionSetRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map( "versionKey" -> "1234", "description" -> "updated desc")))
		request.setOperation("retireQuestionSet")
		val response = callActor(request, Props(new QuestionSetActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "return success response for 'publishQuestionSet" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		val kfClient = mock[KafkaClient]
		(oec.kafkaClient _).expects().returns(kfClient).anyNumberOfTimes()
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		val node = getNode("QuestionSet", None)
		node.getMetadata.putAll(mapAsJavaMap(Map("name" -> "question_1",
			"visibility" -> "Public",
			"code" -> "finemanfine",
			"navigationMode" -> "linear",
			"allowSkip" -> true.asInstanceOf[Object],
			"requiresSubmit" -> false.asInstanceOf[Object],
			"shuffle" -> true.asInstanceOf[Object],
			"showFeedback" -> true.asInstanceOf[Object],
			"showSolutions" -> true.asInstanceOf[Object],
			"showHints" -> true.asInstanceOf[Object],
			"summaryType" -> "Complete",
			"mimeType" -> "application/vnd.ekstep.questionset")))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
		(kfClient.send(_:String, _:String)).expects(*,*).once()
		val request = getQuestionSetRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map( "versionKey" -> "1234", "description" -> "updated desc")))
		request.setOperation("publishQuestionSet")
		val response = callActor(request, Props(new QuestionSetActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "return success response for 'addQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		val node = getNode("QuestionSet", None)
		node.setIdentifier("do_1234")
		node.getMetadata.putAll(mapAsJavaMap(Map("name" -> "question_1",
			"visibility" -> "Public",
			"code" -> "finemanfine",
			"navigationMode" -> "linear",
			"allowSkip" -> true.asInstanceOf[Object],
			"requiresSubmit" -> false.asInstanceOf[Object],
			"shuffle" -> true.asInstanceOf[Object],
			"showFeedback" -> true.asInstanceOf[Object],
			"showSolutions" -> true.asInstanceOf[Object],
			"showHints" -> true.asInstanceOf[Object],
			"summaryType" -> "Complete",
			"versionKey" -> "1234",
			"mimeType" -> "application/vnd.ekstep.questionset")))
		(graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
		(graphDB.checkCyclicLoop(_: String, _: String, _: String, _: String)).expects(*, *, *, *).returns(new util.HashMap[String, AnyRef]() {{ put("loop", false.asInstanceOf[AnyRef])}}).anyNumberOfTimes()
		(graphDB.getNodeByUniqueIds(_:String, _: SearchCriteria)).expects(*,*).returns(Future(List(getRelationNode).asJava))
		(graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234"))))
		(graphDB.createRelation(_:String,_:util.List[util.Map[String,AnyRef]])).expects(*,*).returns(Future(new Response()))
		val request = getQuestionSetRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll((Map("children" -> List("do_749").asJava.asInstanceOf[AnyRef])).asJava)
		request.setOperation("addQuestion")
		val response = callActor(request, Props(new QuestionSetActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "return success response for 'removeQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		val node = getNode("QuestionSet", None)
		node.setIdentifier("do_1234")
		node.getMetadata.putAll(mapAsJavaMap(Map("name" -> "question_1",
			"visibility" -> "Public",
			"code" -> "finemanfine",
			"navigationMode" -> "linear",
			"allowSkip" -> true.asInstanceOf[AnyRef],
			"requiresSubmit" -> false.asInstanceOf[AnyRef],
			"shuffle" -> true.asInstanceOf[AnyRef],
			"showFeedback" -> true.asInstanceOf[AnyRef],
			"showSolutions" -> true.asInstanceOf[AnyRef],
			"showHints" -> true.asInstanceOf[AnyRef],
			"summaryType" -> "Complete",
			"versionKey" -> "1234",
			"mimeType" -> "application/vnd.ekstep.questionset")))
		val relation1 = new Relation("do_1234", "hasSequenceMember", "do_749")
		relation1.setEndNodeId("do_749")
		relation1.setEndNodeMetadata(Map("visibility" -> "Public"))
		val relation2 = new Relation("do_1234", "hasSequenceMember", "do_914")
		relation2.setEndNodeId("do_914")
		relation2.setEndNodeMetadata(Map("visibility" -> "Public"))
		node.setOutRelations(new util.ArrayList[Relation]() {{
			add(relation1)
			add(relation2)
		}})
		(graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).twice()
		(graphDB.checkCyclicLoop(_: String, _: String, _: String, _: String)).expects(*, *, *, *).returns(new util.HashMap[String, AnyRef]() {{ put("loop", false.asInstanceOf[AnyRef])}}).anyNumberOfTimes()
		(graphDB.getNodeByUniqueIds(_:String, _: SearchCriteria)).expects(*,*).returns(Future(List(getRelationNode).asJava))
		(graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234"))))
		(graphDB.removeRelation(_:String,_:util.List[util.Map[String,AnyRef]])).expects(*,*).returns(Future(new Response()))
		(graphDB.createRelation(_:String,_:util.List[util.Map[String,AnyRef]])).expects(*,*).returns(Future(new Response()))
		val request = getQuestionSetRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll((Map("children" -> List("do_914").asJava.asInstanceOf[AnyRef])).asJava)
		request.setOperation("removeQuestion")
		val response = callActor(request, Props(new QuestionSetActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	private def getQuestionSetRequest(): Request = {
		val request = new Request()
		request.setContext(new java.util.HashMap[String, AnyRef]() {
			{
				put("graph_id", "domain")
				put("version", "1.0")
				put("objectType", "QuestionSet")
				put("schemaName", "questionset")
			}
		})
		request.setObjectType("QuestionSet")
		request
	}


	private def getRelationNode(): Node = {
		val node = new Node()
		node.setGraphId("domain")
		node.setIdentifier("do_749")
		node.setMetadata(new util.HashMap[String, AnyRef](){{
			put("identifier", "do_749")
			put("mimeType", "application/vnd.ekstep.qml-archive")
			put("visibility", "Public")
			put("status","Draft")
			put("contentType", "Question")
		}})
		node.setObjectType("Question")
		node.setNodeType("DATA_NODE")
		node
	}

	private def getRelationNode_1(): Node = {
		val node = new Node()
		node.setGraphId("domain")
		node.setIdentifier("do_914")
		node.setMetadata(new util.HashMap[String, AnyRef](){{
			put("identifier", "do_914")
			put("visibility", "Public")
			put("mimeType", "application/vnd.ekstep.qml-archive")
			put("status","Draft")
			put("contentType", "Question")
		}})
		node.setObjectType("Question")
		node.setNodeType("DATA_NODE")
		node
	}




}
