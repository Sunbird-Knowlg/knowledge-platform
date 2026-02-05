package org.sunbird.actors.v5

import org.apache.pekko.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.actors.{BaseSpec, CopySpec}
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.v5.actors.QuestionSetActor

import scala.collection.JavaConverters._
import scala.concurrent.Future
import java.util
import scala.concurrent.ExecutionContext.Implicits.global

class QuestionSetActorTest extends BaseSpec with MockFactory {

  it should "return success response for 'updateCommentQuestionSet'" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val qsNode = getNode("QuestionSet", None)
    qsNode.setIdentifier("do_9876")
    qsNode.getMetadata.putAll(mapAsJavaMap(Map(
      "name" -> "Test Question Set",
      "visibility" -> "Default",
      "identifier" -> "do_9876",
      "objectType" -> "QuestionSet",
      "mimeType" -> "application/vnd.sunbird.questionset",
      "primaryCategory" -> "Practice Question Set",
      "rejectComment" -> "reviewer wants the question_set in Upper case",
      "status" -> "Review"
    )))
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(util.Arrays.asList(qsNode))).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(qsNode)).atLeastOnce()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(new Response())).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, qsNode, *).returns(Future(CopySpec.getUpsertNode())).anyNumberOfTimes()
    val request = getQuestionSetRequest()
    request.getRequest.put("reviewComment", "Comments made by the reviewer-1")
    request.getContext.put("identifier", "do_9876")
    request.setOperation("updateCommentQuestionSet")
    val response = callActor(request, Props(new QuestionSetActor()))
    response.getParams.getStatus shouldBe "successful"
  }

  it should "return error response for 'updateCommentQuestionSet' when comments key is missing or empty in the request body" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getQuestionSetRequest()
    //Not passing comments key
    request.getRequest.put("reviewComment", " ")
    request.getContext.put("identifier", "do_9876")
    request.setOperation("updateCommentQuestionSet")
    val response = callActor(request, Props(new QuestionSetActor()))
    response.getParams.getErrmsg shouldBe "Comment key is missing or value is empty in the request body."
  }

  it should "return error response for 'updateCommentQuestionSet' when status of the review is in Draft state" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val qsNode = getNode("QuestionSet", None)
    qsNode.setIdentifier("do_1234")
    qsNode.getMetadata.putAll(mapAsJavaMap(Map(
      "status" -> "Draft"
    )))
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(qsNode)).atLeastOnce()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, qsNode, *).returns(Future(CopySpec.getUpsertNode())).anyNumberOfTimes()
    val request = getQuestionSetRequest()
    request.getContext.put("identifier", "do_1234")
    request.put("reviewComment", "Comments made by the reviewer-1")
    request.setOperation("updateCommentQuestionSet")
    val response = callActor(request, Props(new QuestionSetActor()))
    response.getParams.getErrmsg shouldBe "Node with Identifier do_1234 does not have a status Review."
  }

  it should "return error response for 'updateCommentQuestionSet' when objectType is not QuestionSet" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val qsNode = getNode("Question", None)
    qsNode.setIdentifier("do_1234")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(qsNode)).atLeastOnce()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, qsNode, *).returns(Future(CopySpec.getUpsertNode())).anyNumberOfTimes()
    val request = getQuestionSetRequest()
    request.getContext.put("identifier", "do_1234")
    request.put("reviewComment", "Comments made by the reviewer-1")
    request.setOperation("updateCommentQuestionSet")
    val response = callActor(request, Props(new QuestionSetActor()))
    response.getParams.getErrmsg shouldBe "Node with Identifier do_1234 is not a Question Set."
  }

  it should "return success response for 'readCommentQuestionSet'" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getNode("QuestionSet", Some(new util.HashMap[String, AnyRef]() {
      {
        put("name", "Test Question Set")
        put("description", "Updated question Set")
      }
    }))
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
    val request = getQuestionSetRequest()
    request.getContext.put("identifier", "do1234")
    request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "fields" -> "")))
    request.setOperation("readCommentQuestionSet")
    val response = callActor(request, Props(new QuestionSetActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "return error response for 'readCommentQuestionSet' when objectType is not QuestionSet" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getNode("Question", Some(new util.HashMap[String, AnyRef]() {
      {
        put("name", "Test Question Set")
        put("description", "Updated question Set")
      }
    }))
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
    val request = getQuestionSetRequest()
    request.getContext.put("identifier", "test_id")
    request.putAll(mapAsJavaMap(Map("identifier" -> "test_id", "fields" -> "")))
    request.setOperation("readCommentQuestionSet")
    val response = callActor(request, Props(new QuestionSetActor()))
    response.getParams.getErrmsg shouldBe "Node with Identifier test_id is not a Question Set."
  }

  it should "return error response for 'readCommentQuestionSet' when visibility is Private" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getNode("QuestionSet", Some(new util.HashMap[String, AnyRef]() {
      {
        put("name", "Test Question Set")
        put("visibility", "Private")
      }
    }))
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
    val request = getQuestionSetRequest()
    request.getContext.put("identifier", "test_id")
    request.putAll(mapAsJavaMap(Map("identifier" -> "test_id", "fields" -> "")))
    request.setOperation("readCommentQuestionSet")
    val response = callActor(request, Props(new QuestionSetActor()))
    response.getParams.getErrmsg shouldBe "visibility of test_id is private hence access denied"
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

}
