package org.sunbird.actors

import java.util

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.dto.{Property, Request}
import org.sunbird.graph.dac.model.Node
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

	it should "return success response for 'createQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB)
		val node = getNode("Question", None)
		(graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(node))
		val request = getQuestionRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map("channel"-> "in.ekstep","name" -> "New Content", "code" -> "1234", "mimeType"-> "application/vnd.ekstep.qml-archive", "contentType" -> "Course", "visibility" -> "Public")))
		request.setOperation("createQuestion")
		val response = callActor(request, Props(new QuestionActor()))
		assert("successful".equals(response.getParams.getStatus))
	}

	it should "return success response for 'updateQuestion'" in {
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val graphDB = mock[GraphService]
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		val node = getNode("Question", None)
		node.getMetadata.putAll(Map("versionKey" -> "1234", "contentType" -> "Course", "name" -> "Updated New Content", "code" -> "1234", "mimeType"-> "application/vnd.ekstep.qml-archive").asJava)
		(graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
		(graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234"))))
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
		node.getMetadata.putAll(Map("versionKey" -> "1234", "contentType" -> "Course", "name" -> "Updated New Content", "code" -> "1234", "mimeType"-> "application/vnd.ekstep.qml-archive").asJava)
		(graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
		(graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234"))))
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
		node.getMetadata.putAll(Map("versionKey" -> "1234", "contentType" -> "Course", "name" -> "Updated New Content", "code" -> "1234", "mimeType"-> "application/vnd.ekstep.qml-archive").asJava)
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
		node.getMetadata.putAll(Map("versionKey" -> "1234", "contentType" -> "Course", "name" -> "Updated New Content", "code" -> "1234", "mimeType"-> "application/vnd.ekstep.qml-archive").asJava)
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
		(kfClient.send(_:String, _:String)).expects(*,*).once()
		val request = getQuestionRequest()
		request.getContext.put("identifier", "do1234")
		request.putAll(mapAsJavaMap(Map( "versionKey" -> "1234", "description" -> "updated desc")))
		request.setOperation("publishQuestion")
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
}
