package org.sunbird.actors

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.dto.Request
import org.sunbird.graph.{GraphService, OntologyEngineContext}

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class QuestionActorTest extends BaseSpec with MockFactory {

	"questionActor" should "return failed response for 'unknown' operation" in {
		implicit val oec: OntologyEngineContext = new OntologyEngineContext
		testUnknownOperation(Props(new QuestionActor()), getQuestionRequest())
	}

	it should "return success response for 'readContent'" in {
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
