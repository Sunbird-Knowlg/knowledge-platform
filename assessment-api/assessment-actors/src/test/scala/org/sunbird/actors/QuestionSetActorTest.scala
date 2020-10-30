package org.sunbird.actors

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.dto.Request
import org.sunbird.graph.OntologyEngineContext

class QuestionSetActorTest extends BaseSpec with MockFactory {

	"questionSetActor" should "return failed response for 'unknown' operation" in {
		implicit val oec: OntologyEngineContext = new OntologyEngineContext
		testUnknownOperation(Props(new QuestionSetActor()), getQuestionRequest())
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
