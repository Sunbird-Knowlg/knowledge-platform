package org.sunbird.actors

import org.apache.pekko.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.HttpUtil
import org.sunbird.common.dto.{Request, Response, ResponseHandler, ResponseParams}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.graph.utils.ScalaJsonUtils
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.kafka.client.KafkaClient
import org.sunbird.utils.{AssessmentConstants, BranchingUtil, JavaJsonUtils}
import java.util

import scala.collection.convert.ImplicitConversions._
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class QuestionSetActorTest extends BaseSpec with MockFactory {

    "questionSetActor" should "return failed response for 'unknown' operation" in {
        implicit val oec: OntologyEngineContext = new OntologyEngineContext
        testUnknownOperation(Props(new QuestionSetActor()), getQuestionSetRequest())
    }

    it should "return success response for 'createQuestionSet'" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val node = getNode("QuestionSet", None)
        (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(node))
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(new Response())).anyNumberOfTimes()
        val nodes: util.List[Node] = getCategoryNode()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

        val request = getQuestionSetRequest()
        request.getContext.put("identifier", "do1234")
        request.putAll(mapAsJavaMap(Map("name" -> "question_1",
            "visibility" -> "Default",
            "code" -> "finemanfine",
            "navigationMode" -> "linear",
            "allowSkip" -> "Yes",
            "requiresSubmit" -> "No",
            "shuffle" -> true.asInstanceOf[AnyRef],
            "showFeedback" -> "Yes",
            "showSolutions" -> "Yes",
            "showHints" -> "Yes",
            "summaryType" -> "Complete",
            "mimeType" -> "application/vnd.sunbird.questionset",
            "primaryCategory" -> "Practice Question Set")))
        request.setOperation("createQuestionSet")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert("successful".equals(response.getParams.getStatus))
    }

    it should "return success response for 'readQuestionSet'" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val node = getNode("QuestionSet", Some(new util.HashMap[String, AnyRef]() {
            {
                put("name", "QuestionSet")
                put("description", "Updated question Set")
            }
        }))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
        val request = getQuestionSetRequest()
        request.getContext.put("identifier", "do1234")
        request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "fields" -> "")))
        request.setOperation("readQuestionSet")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert("successful".equals(response.getParams.getStatus))
    }


    it should "return client error response for 'readQuestionSet' if visibility is 'Private'" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val node = getNode("QuestionSet", Some(new util.HashMap[String, AnyRef]() {
            {
                put("name", "QuestionSet")
                put("description", "Updated question Set")
                put("visibility","Private")
            }
        }))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
        val request = getQuestionSetRequest()
        request.getContext.put("identifier", "do1234")
        request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "fields" -> "")))
        request.setOperation("readQuestionSet")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    }  

    it should "return success response for 'readPrivateQuestionSet'" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB)
        val node = getNode("QuestionSet", Some(new util.HashMap[String, AnyRef]() {
            {
                put("name", "QuestionSet")
                put("visibility","Private")
                put("channel","abc-123")
            }
        }))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
        val request = getQuestionSetRequest()
        request.getContext.put("identifier","do1234")
        request.getRequest.put("channel", "abc-123")
        request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "fields" -> "")))
        request.setOperation("readPrivateQuestionSet")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert("successful".equals(response.getParams.getStatus))
    }

    it should "return client error for 'readPrivateQuestionSet' if channel is 'blank'" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        val request = getQuestionSetRequest()
        request.getContext.put("identifier","do1234")
        request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "fields" -> "")))
        request.setOperation("readPrivateQuestionSet")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr == "ERR_INVALID_CHANNEL")
        assert(response.getParams.getErrmsg == "Please Provide Channel!")
    }

    it should "return client error for 'readPrivateQuestionSet' if channel is mismatched" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB)
        val node = getNode("QuestionSet", Some(new util.HashMap[String, AnyRef]() {
            {
                put("name", "QuestionSet")
                put("visibility","Private")
                put("channel","abc-123")
            }
        }))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        val request = getQuestionSetRequest()
        request.getContext.put("identifier","do1234")
        request.getRequest.put("channel", "abc")
        request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "fields" -> "")))
        request.setOperation("readPrivateQuestionSet")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(response.getParams.getErr == "ERR_ACCESS_DENIED")
        assert(response.getParams.getErrmsg == "Channel id is not matched")
    }
    
    it should "return success response for 'updateQuestionSet'" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val nodes: util.List[Node] = getCategoryNode()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

        val node = getNode("QuestionSet", None)
        node.getMetadata.putAll(mapAsJavaMap(Map("name" -> "question_1",
            "visibility" -> "Default",
            "code" -> "finemanfine",
            "description" -> "Updated description",
            "navigationMode" -> "linear",
            "allowSkip" -> "Yes",
            "requiresSubmit" -> "No",
            "shuffle" -> true.asInstanceOf[AnyRef],
            "showFeedback" -> "Yes",
            "showSolutions" -> "Yes",
            "showHints" -> "Yes",
            "summaryType" -> "Complete",
            "mimeType" -> "application/vnd.sunbird.questionset",
            "primaryCategory" -> "Practice Question Set")))
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
        val request = getQuestionSetRequest()
        request.getContext.put("identifier", "do1234")
        request.putAll(mapAsJavaMap(Map("versionKey" -> "1234", "description" -> "updated desc")))
        request.setOperation("updateQuestionSet")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert("successful".equals(response.getParams.getStatus))
    }

    it should "return success response for 'reviewQuestionSet'" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val nodes: util.List[Node] = getCategoryNode()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

        val node = getNode("QuestionSet", None)
        node.getMetadata.putAll(mapAsJavaMap(Map("name" -> "question_1",
            "visibility" -> "Default",
            "code" -> "finemanfine",
            "navigationMode" -> "linear",
            "allowSkip" -> "Yes",
            "requiresSubmit" -> "No",
            "shuffle" -> true.asInstanceOf[AnyRef],
            "showFeedback" -> "Yes",
            "showSolutions" -> "Yes",
            "showHints" -> "Yes",
            "summaryType" -> "Complete",
            "versionKey" -> "1234",
            "mimeType" -> "application/vnd.sunbird.questionset",
            "primaryCategory" -> "Practice Question Set")))
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes
        (graphDB.updateExternalProps(_: Request)).expects(*).returns(Future(new Response())).anyNumberOfTimes
        (graphDB.updateNodes(_:String, _:util.List[String], _: util.Map[String, AnyRef])).expects(*, *, *).returns(Future(Map[String, Node]().asJava)).anyNumberOfTimes
        val request = getQuestionSetRequest()
        request.getContext.put("identifier", "do1234")
        request.putAll(mapAsJavaMap(Map("versionKey" -> "1234", "description" -> "updated desc")))
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
            "visibility" -> "Default",
            "code" -> "finemanfine",
            "navigationMode" -> "linear",
            "allowSkip" -> "Yes",
            "requiresSubmit" -> "No",
            "shuffle" -> true.asInstanceOf[AnyRef],
            "showFeedback" -> "Yes",
            "showSolutions" -> "Yes",
            "showHints" -> "Yes",
            "summaryType" -> "Complete",
            "mimeType" -> "application/vnd.sunbird.questionset",
            "primaryCategory" -> "Practice Question Set")))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
        (graphDB.updateNodes(_: String, _: util.List[String], _: util.HashMap[String, AnyRef])).expects(*, *, *).returns(Future(new util.HashMap[String, Node]))
        val request = getQuestionSetRequest()
        request.getContext.put("identifier", "do1234")
        request.putAll(mapAsJavaMap(Map("versionKey" -> "1234", "description" -> "updated desc")))
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

        val qsNode = getNode("QuestionSet", None)
        qsNode.setIdentifier("do_213771312330227712135")
        qsNode.getMetadata.putAll(mapAsJavaMap(Map("name" -> "QuestionSet-1",
            "visibility" -> "Default",
            "identifier" -> "do_213771312330227712135",
            "objectType" -> "QuestionSet",
            "code" -> "sunbird.qs.1",
            "allowSkip" -> "Yes",
            "requiresSubmit" -> "No",
            "shuffle" -> true.asInstanceOf[AnyRef],
            "showFeedback" -> "No",
            "showSolutions" -> "No",
            "showHints" -> "No",
            "versionKey" -> "1681066321610",
            "mimeType" -> "application/vnd.sunbird.questionset",
            "createdBy" -> "sunbird-user-1",
            "primaryCategory" -> "Practice Question Set")))

        val qNode1 = getNode("Question", None)
        qNode1.setIdentifier("do_213771313474650112136")
        qNode1.getMetadata.putAll(mapAsJavaMap(Map("name" -> "Question-1",
            "visibility" -> "Parent",
            "code" -> "sunbird.q.parent.1",
            "identifier" -> "do_213771313474650112136",
            "description" -> "Question-1",
            "objectType" -> "Question",
            "mimeType" -> "application/vnd.sunbird.question",
            "createdBy" -> "sunbird-user-1",
            "primaryCategory" -> "Multiple Choice Question",
            "interactionTypes"->List("choice").asJava)))

        val qNode2 = getNode("Question", None)
        qNode2.setIdentifier("do_213771313474830336138")
        qNode2.getMetadata.putAll(mapAsJavaMap(Map("name" -> "Question-2",
            "visibility" -> "Default",
            "identifier" -> "do_213771313474830336138",
            "objectType" -> "Question",
            "code" -> "sunbird.q.default.2",
            "mimeType" -> "application/vnd.sunbird.question",
            "createdBy" -> "sunbird-user-1",
            "primaryCategory" -> "Multiple Choice Question",
            "interactionTypes"->List("choice").asJava)))
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(util.Arrays.asList(qNode2))).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "do_213771312330227712135.img", *, *).returns(Future(qsNode)).atLeastOnce()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "do_213771313474650112136.img", *, *).returns(Future(qNode1)).atLeastOnce()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "do_213771313474830336138.img", *, *).returns(Future(qNode2)).atLeastOnce()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, List("objectMetadata")).returns(Future(getSuccessfulResponse())).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, List("solutions","body","editorState","interactions","hints","responseDeclaration","media","answer","instructions")).returns(Future(getReadPropsResponseForQuestion())).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, List("hierarchy")).returns(Future(getQuestionSetHierarchy())).anyNumberOfTimes
        (kfClient.send(_: String, _: String)).expects(*, *).once()
        val request = getQuestionSetRequest()
        request.getContext.put("identifier", "do_213771312330227712135")
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
            "visibility" -> "Default",
            "code" -> "finemanfine",
            "navigationMode" -> "linear",
            "allowSkip" -> "Yes",
            "requiresSubmit" -> "No",
            "shuffle" -> true.asInstanceOf[AnyRef],
            "showFeedback" -> "Yes",
            "showSolutions" -> "Yes",
            "showHints" -> "Yes",
            "summaryType" -> "Complete",
            "versionKey" -> "1234",
            "mimeType" -> "application/vnd.sunbird.questionset",
            "primaryCategory" -> "Practice Question Set")))
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(List(node).asJava)).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes
        (graphDB.saveExternalProps(_: Request)).expects(*).returns(Future(new Response())).anyNumberOfTimes
        val request = getQuestionSetRequest()
        request.getContext.put("identifier", "do1234")
        request.putAll((Map("children" -> List("do_749").asJava.asInstanceOf[AnyRef], "rootId" -> "do1234")).asJava)
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
            "visibility" -> "Default",
            "code" -> "finemanfine",
            "navigationMode" -> "linear",
            "allowSkip" -> "Yes",
            "requiresSubmit" -> "No",
            "shuffle" -> true.asInstanceOf[AnyRef],
            "showFeedback" -> "Yes",
            "showSolutions" -> "Yes",
            "showHints" -> "Yes",
            "summaryType" -> "Complete",
            "versionKey" -> "1234",
            "mimeType" -> "application/vnd.sunbird.questionset",
            "primaryCategory" -> "Practice Question Set")))
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(List(node).asJava)).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes
        (graphDB.saveExternalProps(_: Request)).expects(*).returns(Future(new Response())).anyNumberOfTimes
        val request = getQuestionSetRequest()
        request.getContext.put("identifier", "do1234")
        request.putAll((Map("children" -> List("do_914").asJava.asInstanceOf[AnyRef], "rootId" -> "do1234")).asJava)
        request.setOperation("removeQuestion")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert("successful".equals(response.getParams.getStatus))
    }

    it should "return error response for 'updateHierarchyQuestionSet'" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val request = getInvalidUpdateHierarchyReq()
        request.getContext.put("rootId", "do_123")
        request.setOperation("updateHierarchy")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert("failed".equals(response.getParams.getStatus))
    }


    it should "return success response for 'updateHierarchyQuestionSet'" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val rootNode = getRootNode()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(rootNode))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(rootNode)).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getEmptyCassandraHierarchy())).anyNumberOfTimes
        (graphDB.updateExternalProps(_: Request)).expects(*).returns(Future(new Response())).anyNumberOfTimes
        val nodes: util.List[Node] = getCategoryNode()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

        val request = getUpdateHierarchyReq()
        request.getContext.put("rootId", "do_1234")
        request.setOperation("updateHierarchy")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert("successful".equals(response.getParams.getStatus))
    }


    it should "return success response for 'rejectQuestionSet'" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val node = getNode("QuestionSet", None)
        node.getMetadata.putAll(mapAsJavaMap(Map("name" -> "question_1",
            "visibility" -> "Default",
            "code" -> "finemanfine",
            "navigationMode" -> "linear",
            "allowSkip" -> "Yes",
            "requiresSubmit" -> "No",
            "shuffle" -> true.asInstanceOf[AnyRef],
            "showFeedback" -> "Yes",
            "showSolutions" -> "Yes",
            "status" -> "Review",
            "showHints" -> "Yes",
            "summaryType" -> "Complete",
            "versionKey" -> "1234",
            "mimeType" -> "application/vnd.sunbird.questionset",
            "primaryCategory" -> "Practice Question Set")))
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes
        (graphDB.updateExternalProps(_: Request)).expects(*).returns(Future(new Response())).anyNumberOfTimes
        (graphDB.updateNodes(_:String, _:util.List[String], _: util.Map[String, AnyRef])).expects(*, *, *).returns(Future(Map[String, Node]().asJava)).anyNumberOfTimes
         val nodes: util.List[Node] = getCategoryNode()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

        val request = getQuestionSetRequest()
        request.getContext.put("identifier", "do1234")
        request.putAll(mapAsJavaMap(Map("versionKey" -> "1234", "description" -> "updated desc")))
        request.setOperation("rejectQuestionSet")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert("successful".equals(response.getParams.getStatus))
    }

    it should "send events to kafka topic" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val kfClient = mock[KafkaClient]
        val hUtil = mock[HttpUtil]
        (oec.httpUtil _).expects().returns(hUtil)
        val resp :Response = ResponseHandler.OK()
        resp.put("questionset", new util.HashMap[String, AnyRef](){{
            put("framework", "NCF")
            put("channel", "test")
            put("status", "Live")
        }})
        (hUtil.get(_: String, _: String, _: util.Map[String, String])).expects(*, *, *).returns(resp)
        (oec.kafkaClient _).expects().returns(kfClient)
        (kfClient.send(_: String, _: String)).expects(*, *).returns(None)
        val request = getQuestionSetRequest()
        request.getRequest.put("questionset", new util.HashMap[String, AnyRef](){{
            put("source", "https://dock.sunbirded.org/api/questionset/v1/read/do_113486480153952256140")
            put("metadata", new util.HashMap[String, AnyRef](){{
                put("name", "Test QuestionSet")
                put("description", "Test QuestionSet")
                put("mimeType", "application/vnd.sunbird.questionset")
                put("code", "test.ques.1")
                put("primaryCategory", "Learning Resource")
            }})
        }})
        request.setOperation("importQuestionSet")
        request.setObjectType("QuestionSet")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert(response.get("processId") != null)
    }

    it should "return success response for 'systemUpdateQuestionSet'" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val node = getNode("QuestionSet", None)
        node.setIdentifier("test_id")
        node.getMetadata.putAll(mapAsJavaMap(Map("name" -> "question_1",
            "visibility" -> "Default",
            "code" -> "finemanfine",
            "description" -> "Updated description",
            "navigationMode" -> "linear",
            "allowSkip" -> "Yes",
            "requiresSubmit" -> "No",
            "shuffle" -> true.asInstanceOf[AnyRef],
            "showFeedback" -> "Yes",
            "status" -> "Draft",
            "showSolutions" -> "Yes",
            "showHints" -> "Yes",
            "summaryType" -> "Complete",
            "mimeType" -> "application/vnd.sunbird.questionset",
            "primaryCategory" -> "Practice Question Set")))
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(List(node))).once()
        val nodes: util.List[Node] = getCategoryNode()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).noMoreThanOnce()

        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes
        val request = getQuestionSetRequest()
        request.getContext.put("identifier", "test_id")
        request.putAll(mapAsJavaMap(Map("versionKey" -> "1234", "description" -> "updated desc")))
        request.setOperation("systemUpdateQuestionSet")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert("successful".equals(response.getParams.getStatus))
    }

    it should "return success response for 'systemUpdateQuestionSet' with image Node" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val node = getNode("QuestionSet", None)
        val imageNode = getNode("QuestionSet", None)
        node.setIdentifier("test_id")
        imageNode.setIdentifier("test_id.img")
        node.getMetadata.putAll(mapAsJavaMap(Map("name" -> "question_1",
            "visibility" -> "Default",
            "code" -> "finemanfine",
            "description" -> "Updated description",
            "navigationMode" -> "linear",
            "allowSkip" -> "Yes",
            "requiresSubmit" -> "No",
            "shuffle" -> true.asInstanceOf[AnyRef],
            "showFeedback" -> "Yes",
            "status" -> "Live",
            "showSolutions" -> "Yes",
            "showHints" -> "Yes",
            "summaryType" -> "Complete",
            "mimeType" -> "application/vnd.sunbird.questionset",
            "primaryCategory" -> "Practice Question Set")))
        imageNode.getMetadata.putAll(mapAsJavaMap(Map("name" -> "question_1",
            "visibility" -> "Default",
            "code" -> "finemanfine",
            "description" -> "Updated description",
            "navigationMode" -> "linear",
            "allowSkip" -> "Yes",
            "requiresSubmit" -> "No",
            "shuffle" -> true.asInstanceOf[AnyRef],
            "showFeedback" -> "Yes",
            "showSolutions" -> "Yes",
            "showHints" -> "Yes",
            "summaryType" -> "Complete",
            "status" -> "Draft",
            "mimeType" -> "application/vnd.sunbird.questionset",
            "primaryCategory" -> "Practice Question Set")))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(List(node, imageNode))).once()
        val nodes: util.List[Node] = getCategoryNode()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes
        (graphDB.updateExternalProps(_: Request)).expects(*).returns(Future(new Response())).anyNumberOfTimes
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node)).anyNumberOfTimes()
        val request = getQuestionSetRequest()
        request.getContext.put("identifier", "test_id")
        request.putAll(mapAsJavaMap(Map("versionKey" -> "1234", "description" -> "updated desc")))
        request.setOperation("systemUpdateQuestionSet")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert("successful".equals(response.getParams.getStatus))
    }

    it should "return success response for 'copyQuestionSet' (Deep)" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val nodes: util.List[Node] = getCategoryNode()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects("domain", "do_1234", false, *).returns(Future(CopySpec.getExistingRootNode())).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects("domain", "do_9876", false, *).returns(Future(CopySpec.getNewRootNode())).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects("domain", "do_9876.img", false, *).returns(Future(CopySpec.getNewRootNode())).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects("domain", *, false, *).returns(Future(CopySpec.getQuestionNode())).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, List("objectMetadata")).returns(Future(CopySpec.getSuccessfulResponse())).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(CopySpec.getExternalPropsResponseWithData())).anyNumberOfTimes()
        (graphDB.updateExternalProps(_: Request)).expects(*).returns(Future(CopySpec.getSuccessfulResponse())).anyNumberOfTimes
        (graphDB.saveExternalProps(_: Request)).expects(*).returns(Future(CopySpec.getSuccessfulResponse())).anyNumberOfTimes
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(CopySpec.getUpsertNode())).anyNumberOfTimes()
        inSequence {
            (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(CopySpec.getNewRootNode()))
            (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(CopySpec.getQuestionNode()))
        }
        val request = CopySpec.getQuestionSetCopyRequest()
        request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "mode" -> "", "copyType"-> "deep")))
        request.setOperation("copyQuestionSet")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert("successful".equals(response.getParams.getStatus))
    }

    it should "return success response for 'copyQuestionSet' (Shallow)" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val nodes: util.List[Node] = getCategoryNode()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects("domain", "do_1234", false, *).returns(Future(CopySpec.getExistingRootNode())).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects("domain", "do_5678", false, *).returns(Future(CopySpec.getNewRootNode())).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, List("objectMetadata")).returns(Future(CopySpec.getSuccessfulResponse())).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(CopySpec.getExternalPropsResponseWithData())).anyNumberOfTimes()
        (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(CopySpec.getQuestionNode()))
        (graphDB.saveExternalProps(_: Request)).expects(*).returns(Future(CopySpec.getSuccessfulResponse())).anyNumberOfTimes
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(CopySpec.getUpsertNode())).anyNumberOfTimes()
        (graphDB.updateExternalProps(_: Request)).expects(*).returns(Future(CopySpec.getSuccessfulResponse())).anyNumberOfTimes
        val request = CopySpec.getQuestionSetCopyRequest()
        request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "mode" -> "", "copyType"-> "shallow")))
        request.setOperation("copyQuestionSet")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert("successful".equals(response.getParams.getStatus))
    }

    it should "return error response for 'copyQuestionSet' when createdFor & createdBy is missing" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val request = CopySpec.getInvalidQuestionCopyRequest()
        request.putAll(mapAsJavaMap(Map("identifier" -> "do_1234", "mode" -> "", "copyType"-> "deep")))
        request.setOperation("copyQuestionSet")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert("failed".equals(response.getParams.getStatus))
    }

    it should "return expected result for 'generateBranchingRecord'" in {
        val result = BranchingUtil.generateBranchingRecord(CopySpec.generateNodesModified("afa2bef1-b5db-45d9-b0d7-aeea757906c3", true))
        assert(result == CopySpec.generateBranchingRecord)
    }

    it should "return expected result for 'hierarchyRequestModifier'" in {
        val result = BranchingUtil.hierarchyRequestModifier(CopySpec.generateUpdateRequest(false, "afa2bef1-b5db-45d9-b0d7-aeea757906c3"), CopySpec.generateBranchingRecord(), CopySpec.generateIdentifiers())
        val expectedResult = CopySpec.generateUpdateRequest(true, "do_11351201604857856013")
        assert(result.getRequest.get(AssessmentConstants.NODES_MODIFIED) == expectedResult.getRequest.get(AssessmentConstants.NODES_MODIFIED))
        assert(result.getRequest.get(AssessmentConstants.HIERARCHY) == expectedResult.getRequest.get(AssessmentConstants.HIERARCHY))
    }

    it should "return success response for 'reviewQuestionSet' having all data of children" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        val qsNode = getNode("QuestionSet", None)
        qsNode.setIdentifier("do_213771312330227712135")
        qsNode.getMetadata.putAll(mapAsJavaMap(Map("name" -> "QuestionSet-1",
            "visibility" -> "Default",
            "identifier" -> "do_213771312330227712135",
            "objectType" -> "QuestionSet",
            "code" -> "sunbird.qs.1",
            "allowSkip" -> "Yes",
            "requiresSubmit" -> "No",
            "shuffle" -> true.asInstanceOf[AnyRef],
            "showFeedback" -> "No",
            "showSolutions" -> "No",
            "showHints" -> "No",
            "versionKey" -> "1681066321610",
            "mimeType" -> "application/vnd.sunbird.questionset",
            "createdBy" -> "sunbird-user-1",
            "primaryCategory" -> "Practice Question Set")))

        val qNode1 = getNode("Question", None)
        qNode1.setIdentifier("do_213771313474650112136")
        qNode1.getMetadata.putAll(mapAsJavaMap(Map("name" -> "Question-1",
            "visibility" -> "Parent",
            "code" -> "sunbird.q.parent.1",
            "identifier" -> "do_213771313474650112136",
            "description" -> "Question-1",
            "objectType" -> "Question",
            "mimeType" -> "application/vnd.sunbird.question",
            "createdBy" -> "sunbird-user-1",
            "primaryCategory" -> "Multiple Choice Question",
            "interactionTypes"->List("choice").asJava)))

        val qNode2 = getNode("Question", None)
        qNode2.setIdentifier("do_213771313474830336138")
        qNode2.getMetadata.putAll(mapAsJavaMap(Map("name" -> "Question-2",
            "visibility" -> "Default",
            "identifier" -> "do_213771313474830336138",
            "objectType" -> "Question",
            "code" -> "sunbird.q.default.2",
            "mimeType" -> "application/vnd.sunbird.question",
            "createdBy" -> "sunbird-user-1",
            "primaryCategory" -> "Multiple Choice Question",
            "interactionTypes"->List("choice").asJava)))
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(util.Arrays.asList(qNode2))).anyNumberOfTimes()
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, qsNode, *).returns(Future(qsNode))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "do_213771312330227712135", *, *).returns(Future(qsNode)).atLeastOnce()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "do_213771312330227712135.img", *, *).returns(Future(qsNode)).atLeastOnce()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "do_213771313474650112136.img", *, *).returns(Future(qNode1)).atLeastOnce()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "do_213771313474830336138.img", *, *).returns(Future(qNode2)).atLeastOnce()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, List("objectMetadata")).returns(Future(getSuccessfulResponse())).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, List("solutions","body","editorState","interactions","hints","responseDeclaration","media","answer","instructions")).returns(Future(getReadPropsResponseForQuestion())).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, List("hierarchy")).returns(Future(getQuestionSetHierarchy())).anyNumberOfTimes
        (graphDB.updateExternalProps(_: Request)).expects(*).returns(Future(new Response())).anyNumberOfTimes
        (graphDB.updateNodes(_:String, _:util.List[String], _: util.Map[String, AnyRef])).expects(*, *, *).returns(Future(Map[String, Node]().asJava)).anyNumberOfTimes
        val request = getQuestionSetRequest()
        request.getContext.put("identifier", "do_213771312330227712135")
        request.setOperation("reviewQuestionSet")
        val response = callActor(request, Props(new QuestionSetActor()))
        assert("successful".equals(response.getParams.getStatus))
    }

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

    def getReadPropsResponseForQuestion(): Response = {
        val response = getSuccessfulResponse()
        response.put("body", "<div class='question-body' tabindex='-1'><div class='mcq-title' tabindex='0'><p><span style=\"background-color:#ffffff;color:#202124;\">Which of the following crops is a commercial crop?</span></p></div><div data-choice-interaction='response1' class='mcq-vertical'></div></div>")
        response.put("editorState", "{\n                \"options\": [\n                    {\n                        \"answer\": false,\n                        \"value\": {\n                            \"body\": \"<p>Wheat</p>\",\n                            \"value\": 0\n                        }\n                    },\n                    {\n                        \"answer\": false,\n                        \"value\": {\n                            \"body\": \"<p>Barley</p>\",\n                            \"value\": 1\n                        }\n                    },\n                    {\n                        \"answer\": false,\n                        \"value\": {\n                            \"body\": \"<p>Maize</p>\",\n                            \"value\": 2\n                        }\n                    },\n                    {\n                        \"answer\": true,\n                        \"value\": {\n                            \"body\": \"<p>Tea</p>\",\n                            \"value\": 3\n                        }\n                    }\n                ],\n                \"question\": \"<p><span style=\\\"background-color:#ffffff;color:#202124;\\\">Which of the following crops is a commercial crop?</span></p>\",\n                \"solutions\": [\n                    {\n                        \"id\": \"f8e65cff-1451-4353-b281-3ceaf874b5b8\",\n                        \"type\": \"html\",\n                        \"value\": \"<p>Tea is the <span style=\\\"background-color:#ffffff;color:#202124;\\\">commercial crop</span></p><figure class=\\\"image image-style-align-left\\\"><img src=\\\"/assets/public/content/assets/do_2137498365362995201237/tea.jpeg\\\" alt=\\\"tea\\\" data-asset-variable=\\\"do_2137498365362995201237\\\"></figure>\"\n                    }\n                ]\n            }")
        response.put("responseDeclaration", "{\n                \"response1\": {\n                    \"maxScore\": 1,\n                    \"cardinality\": \"single\",\n                    \"type\": \"integer\",\n                    \"correctResponse\": {\n                        \"value\": \"3\",\n                        \"outcomes\": {\n                            \"SCORE\": 1\n                        }\n                    },\n                    \"mapping\": [\n                        {\n                            \"response\": 3,\n                            \"outcomes\": {\n                                \"score\": 1\n                            }\n                        }\n                    ]\n                }\n            }")
        response.put("interactions","{\n                \"response1\": {\n                    \"type\": \"choice\",\n                    \"options\": [\n                        {\n                            \"label\": \"<p>Wheat</p>\",\n                            \"value\": 0\n                        },\n                        {\n                            \"label\": \"<p>Barley</p>\",\n                            \"value\": 1\n                        },\n                        {\n                            \"label\": \"<p>Maize</p>\",\n                            \"value\": 2\n                        },\n                        {\n                            \"label\": \"<p>Tea</p>\",\n                            \"value\": 3\n                        }\n                    ]\n                },\n                \"validation\": {\n                    \"required\": \"Yes\"\n                }\n            }")
        response.put("answer","")
        //response.put("solutions", "[\n                    {\n                        \"id\": \"f8e65cff-1451-4353-b281-3ceaf874b5b8\",\n                        \"type\": \"html\",\n                        \"value\": \"<p>Tea is the <span style=\\\"background-color:#ffffff;color:#202124;\\\">commercial crop</span></p><figure class=\\\"image image-style-align-left\\\"><img src=\\\"/assets/public/content/assets/do_2137498365362995201237/tea.jpeg\\\" alt=\\\"tea\\\" data-asset-variable=\\\"do_2137498365362995201237\\\"></figure>\"\n                    }\n                ]")
        response.put("instructions", null)
        response.put("media", "[\n                {\n                    \"id\": \"do_2137498365362995201237\",\n                    \"type\": \"image\",\n                    \"src\": \"/assets/public/content/assets/do_2137498365362995201237/tea.jpeg\",\n                    \"baseUrl\": \"https://dev.inquiry.sunbird.org\"\n                }\n            ]")
        response
    }

    def getSuccessfulResponse(): Response = {
        val response = new Response
        val responseParams = new ResponseParams
        responseParams.setStatus("successful")
        response.setParams(responseParams)
        response.setResponseCode(ResponseCode.OK)
        response
    }

    def getQuestionSetHierarchy(): Response = {
        val hierarchyString: String = """{"code":"sunbird.qs.1","allowSkip":"Yes","containsUserData":"No","language":["English"],"mimeType":"application/vnd.sunbird.questionset","showHints":"No","createdOn":"2023-04-09T19:26:39.714+0000","objectType":"QuestionSet","scoreCutoffType":"AssessmentLevel","primaryCategory":"Practice Question Set","children":[{"parent":"do_213771312330227712135","code":"sunbird.q.parent.1","description":"Question-1","language":["English"],"mimeType":"application/vnd.sunbird.question","createdOn":"2023-04-09T19:28:59.386+0000","objectType":"Question","primaryCategory":"Multiple Choice question","contentDisposition":"inline","lastUpdatedOn":"2023-04-09T19:28:59.386+0000","contentEncoding":"gzip","showSolutions":"No","allowAnonymousAccess":"Yes","identifier":"do_213771313474650112136","lastStatusChangedOn":"2023-04-09T19:28:59.386+0000","visibility":"Parent","showTimer":"No","index":1,"languageCode":["en"],"version":1,"versionKey":"1681068539409","showFeedback":"No","license":"CC BY 4.0","interactionTypes":["choice"],"depth":1,"createdBy":"sunbird-user-1","compatibilityLevel":4,"name":"Question-1","status":"Draft"},{"parent":"do_213771312330227712135","code":"sunbird.q.default.2","description":"Question-2","language":["English"],"mimeType":"application/vnd.sunbird.question","createdOn":"2023-04-09T19:28:59.408+0000","objectType":"Question","primaryCategory":"Multiple Choice question","contentDisposition":"inline","lastUpdatedOn":"2023-04-09T19:28:59.414+0000","contentEncoding":"gzip","showSolutions":"No","allowAnonymousAccess":"Yes","identifier":"do_213771313474830336138","lastStatusChangedOn":"2023-04-09T19:28:59.408+0000","visibility":"Default","showTimer":"No","index":2,"languageCode":["en"],"version":1,"versionKey":"1681068539414","showFeedback":"No","license":"CC BY 4.0","interactionTypes":["choice"],"depth":1,"createdBy":"sunbird-user-1","compatibilityLevel":4,"name":"Question-2","status":"Draft"}],"contentDisposition":"inline","lastUpdatedOn":"2023-04-09T19:28:59.482+0000","contentEncoding":"gzip","generateDIALCodes":"No","showSolutions":"No","trackable":{"enabled":"No","autoBatch":"No"},"allowAnonymousAccess":"Yes","identifier":"do_213771312330227712135","lastStatusChangedOn":"2023-04-09T19:26:39.714+0000","requiresSubmit":"No","visibility":"Default","showTimer":"No","childNodes":["do_213771313474650112136","do_213771313474830336138"],"setType":"materialised","languageCode":["en"],"version":1,"versionKey":"1681068539482","showFeedback":"No","license":"CC BY 4.0","depth":0,"createdBy":"sunbird-user-1","compatibilityLevel":5,"name":"QuestionSet-1","navigationMode":"non-linear","allowBranching":"No","shuffle":true,"status":"Draft"}"""
        val response = new Response
        response.put("hierarchy", hierarchyString)
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
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_749")
                put("mimeType", "application/vnd.sunbird.question")
                put("visibility", "Default")
                put("status", "Draft")
                put("primaryCategory", "Practice Question Set")
            }
        })
        node.setObjectType("Question")
        node.setNodeType("DATA_NODE")
        node
    }

    private def getRelationNode_1(): Node = {
        val node = new Node()
        node.setGraphId("domain")
        node.setIdentifier("do_914")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_914")
                put("visibility", "Default")
                put("mimeType", "application/vnd.sunbird.question")
                put("status", "Draft")
                put("primaryCategory", "Practice Question Set")
            }
        })
        node.setObjectType("Question")
        node.setNodeType("DATA_NODE")
        node
    }

    def getDefinitionNode(): Node = {
        val node = new Node()
        node.setIdentifier("obj-cat:practice-question-set_question-set_all")
        node.setNodeType("DATA_NODE")
        node.setObjectType("ObjectCategoryDefinition")
        node.setGraphId("domain")
        node.setMetadata(mapAsJavaMap(
            ScalaJsonUtils.deserialize[Map[String, AnyRef]]("{\n    \"objectCategoryDefinition\": {\n      \"name\": \"Learning Resource\",\n      \"description\": \"Content Playlist\",\n      \"categoryId\": \"obj-cat:practice_question_set\",\n      \"targetObjectType\": \"Content\",\n      \"objectMetadata\": {\n        \"config\": {},\n        \"schema\": {\n          \"required\": [\n            \"author\",\n            \"copyright\",\n            \"license\",\n            \"audience\"\n          ],\n          \"properties\": {\n            \"audience\": {\n              \"type\": \"array\",\n              \"items\": {\n                \"type\": \"string\",\n                \"enum\": [\n                  \"Student\",\n                  \"Teacher\"\n                ]\n              },\n              \"default\": [\n                \"Student\"\n              ]\n            },\n            \"mimeType\": {\n              \"type\": \"string\",\n              \"enum\": [\n                \"application/pdf\"\n              ]\n            }\n          }\n        }\n      }\n    }\n  }")))
        node
    }

    def getCassandraHierarchy(): Response = {
        val hierarchyString: String = """{"status":"Live","children":[{"parent":"do_113165166851596288123","totalQuestions":0,"code":"QS_V_Parent_Old","allowSkip":"No","description":"QS-2_parent","language":["English"],"mimeType":"application/vnd.sunbird.questionset","showHints":"No","createdOn":"2020-12-04T15:31:45.948+0530","objectType":"QuestionSet","primaryCategory":"Practice Question Set","lastUpdatedOn":"2020-12-04T15:31:45.947+0530","showSolutions":"No","identifier":"do_11316516745992601613","lastStatusChangedOn":"2020-12-04T15:31:45.948+0530","requiresSubmit":"No","visibility":"Parent","maxQuestions":0,"index":1,"setType":"materialised","languageCode":["en"],"version":1,"versionKey":"1607076105948","showFeedback":"No","depth":1,"name":"QS_V_Parent_2","navigationMode":"non-linear","shuffle":true,"status":"Draft"},{"parent":"do_113165166851596288123","totalQuestions":0,"code":"QS_V_Parent_New","allowSkip":"No","description":"QS-1_parent","language":["English"],"mimeType":"application/vnd.sunbird.questionset","showHints":"No","createdOn":"2020-12-04T15:31:45.872+0530","objectType":"QuestionSet","primaryCategory":"Practice Question Set","children":[{"parent":"do_11316516745922969611","identifier":"do_11316399038283776016","lastStatusChangedOn":"2020-12-02T23:36:59.783+0530","code":"question.code","visibility":"Default","index":1,"language":["English"],"mimeType":"application/vnd.sunbird.question","languageCode":["en"],"createdOn":"2020-12-02T23:36:59.783+0530","version":1,"objectType":"Question","versionKey":"1606932419783","depth":2,"primaryCategory":"Practice Question Set","name":"question_1","lastUpdatedOn":"2020-12-02T23:36:59.783+0530","status":"Draft"}],"lastUpdatedOn":"2020-12-04T15:31:45.861+0530","showSolutions":"No","identifier":"do_11316516745922969611","lastStatusChangedOn":"2020-12-04T15:31:45.876+0530","requiresSubmit":"No","visibility":"Parent","maxQuestions":0,"index":2,"setType":"materialised","languageCode":["en"],"version":1,"versionKey":"1607076105872","showFeedback":"No","depth":1,"name":"QS_V_Parent_1","navigationMode":"non-linear","shuffle":true,"status":"Draft"},{"identifier":"do_11315445058114355211","parent":"do_113165166851596288123","lastStatusChangedOn":"2020-11-19T12:08:13.854+0530","code":"finemanfine","visibility":"Default","index":4,"language":["English"],"mimeType":"application/vnd.sunbird.question","languageCode":["en"],"createdOn":"2020-11-19T12:08:13.854+0530","version":1,"objectType":"Question","versionKey":"1605767893854","depth":1,"name":"question_1","lastUpdatedOn":"2020-11-19T12:08:13.854+0530","contentType":"Resource","status":"Draft"},{"identifier":"do_11315319237189632011","parent":"do_113165166851596288123","lastStatusChangedOn":"2020-11-17T17:28:23.277+0530","code":"finemanfine","visibility":"Default","index":3,"language":["English"],"mimeType":"application/vnd.sunbird.question","languageCode":["en"],"createdOn":"2020-11-17T17:28:23.277+0530","version":1,"objectType":"Question","versionKey":"1605614303277","depth":1,"name":"question_1","lastUpdatedOn":"2020-11-17T17:28:23.277+0530","contentType":"Resource","status":"Draft"}],"identifier":"do_113165166851596288123"}"""
        val response = new Response
        response.put("hierarchy", hierarchyString)
    }

    def getDraftCassandraHierarchy(): Response = {
        val hierarchyString: String = """{"identifier":"do_11348469558523494411","children":[{"parent":"do_11348469558523494411","code":"Q1","description":"Q1","language":["English"],"mimeType":"application/vnd.sunbird.question","createdOn":"2022-03-01T02:15:30.917+0530","objectType":"Question","primaryCategory":"Multiple Choice question","contentDisposition":"inline","lastUpdatedOn":"2022-03-01T02:15:30.915+0530","contentEncoding":"gzip","showSolutions":"No","allowAnonymousAccess":"Yes","identifier":"do_11348469662446387212","lastStatusChangedOn":"2022-03-01T02:15:30.917+0530","visibility":"Parent","showTimer":"No","index":1,"languageCode":["en"],"version":1,"versionKey":"1646081131087","showFeedback":"No","license":"CC BY 4.0","depth":1,"createdBy":"g-001","compatibilityLevel":4,"name":"Q1","status":"Draft"},{"parent":"do_11348469558523494411","code":"Q2","description":"Q2","language":["English"],"mimeType":"application/vnd.sunbird.question","createdOn":"2022-03-01T02:15:31.113+0530","objectType":"Question","primaryCategory":"Multiple Choice question","contentDisposition":"inline","lastUpdatedOn":"2022-03-01T02:15:31.126+0530","contentEncoding":"gzip","showSolutions":"No","allowAnonymousAccess":"Yes","identifier":"do_11348469662607769614","lastStatusChangedOn":"2022-03-01T02:15:31.113+0530","visibility":"Default","showTimer":"No","index":2,"languageCode":["en"],"version":1,"versionKey":"1646081131126","showFeedback":"No","license":"CC BY 4.0","depth":1,"createdBy":"g-002","compatibilityLevel":4,"name":"Q2","status":"Draft"}]}"""
        val response = new Response
        response.put("hierarchy", hierarchyString)
    }

    def getEmptyCassandraHierarchy(): Response = {
        val response = new Response
        response.put("hierarchy", "{}")
    }

    def getInvalidUpdateHierarchyReq() = {
        val nodesModified = "{\n                \"do_1234\": {\n                    \"metadata\": {\n                        \"code\": \"updated_code_of_root\"\n                    },\n                    \"root\": true,\n                    \"isNew\": false\n                },\n                \"QS_V_Parent_New\": {\n                    \"metadata\": {\n                        \"code\": \"QS_V_Parent\",\n                        \"name\": \"QS_V_Parent_1\",\n                        \"description\": \"QS-1_parent\",\n                        \"mimeType\": \"application/vnd.sunbird.questionset\",\n                        \"visibility\": \"Parent\",\n                        \"primaryCategory\": \"Practice Question Set\"\n                    },\n                    \"root\": false,\n                    \"objectType\": \"QuestionSet\",\n                    \"isNew\": true\n                },\n                \"QS_V_Parent_Old\": {\n                    \"metadata\": {\n                        \"code\": \"QS_V_Parent\",\n                        \"name\": \"QS_V_Parent_2\",\n                        \"description\": \"QS-2_parent\",\n                        \"mimeType\": \"application/vnd.sunbird.questionset\",\n                        \"visibility\": \"Parent\",\n                        \"primaryCategory\": \"Practice Question Set\"\n                    },\n                    \"root\": false,\n                    \"objectType\": \"QuestionSet\",\n                    \"isNew\": true\n                },\n                \"do_113178560758022144113\": {\n                    \"metadata\": {\n                        \"code\": \"Q_NEW_PARENT\",\n                        \"name\": \"Q_NEW_PARENT\",\n                        \"description\": \"Q_NEW_PARENT\",\n                        \"mimeType\": \"application/vnd.sunbird.question\",\n                        \"visibility\": \"Parent\",\n                        \"primaryCategory\": \"Practice Question Set\"\n                    },\n                    \"root\": false,\n                    \"objectType\": \"Question\",\n                    \"isNew\": true\n                }\n            }"
        val hierarchy = "{\n                \"do_1234\": {\n                    \"children\": [\n                        \"QS_V_Parent_Old\",\n                        \"QS_V_Parent_New\"\n                    ],\n                    \"root\": true\n                },\n                \"QS_V_Parent_Old\": {\n                    \"children\": [],\n                    \"root\": false\n                },\n                \"QS_V_Parent_New\": {\n                    \"children\": [\n                        \"do_113178560758022144113\"\n                    ],\n                    \"root\": false\n                },\n                \"do_113178560758022144113\": {\n\n                }\n            }"
        val request = getQuestionSetRequest()
        request.put("nodesModified", JavaJsonUtils.deserialize[java.util.Map[String, AnyRef]](nodesModified))
        request.put("hierarchy", JavaJsonUtils.deserialize[java.util.Map[String, AnyRef]](hierarchy))
        request
    }

    def getUpdateHierarchyReq() = {
        val nodesModified =
            """
              |{
              |            "UUID": {
              |            "metadata": {
              |            "mimeType": "application/vnd.sunbird.questionset",
              |            "name": "Subjective",
              |            "primaryCategory": "Practice Question Set",
              |            "code": "questionset"
              |            },
              |            "objectType": "QuestionSet",
              |            "root": false,
              |            "isNew": true
              |            }
              |            }
            """.stripMargin
        val hierarchy =
            """
              |{
              |                "do_1234": {
              |                    "children": [
              |                        "UUID"
              |                    ],
              |                    "root": true
              |                }
              |            }
            """.stripMargin
        val request = getQuestionSetRequest()
        request.put("nodesModified", JavaJsonUtils.deserialize[java.util.Map[String, AnyRef]](nodesModified))
        request.put("hierarchy", JavaJsonUtils.deserialize[java.util.Map[String, AnyRef]](hierarchy))
        request
    }

    def getRootNode(): Node = {
        val node = getNode("QuestionSet", None)
        node.setIdentifier("do_1234")
        node.getMetadata.putAll(mapAsJavaMap(Map("name" -> "question_1",
            "visibility" -> "Default",
            "code" -> "finemanfine",
            "navigationMode" -> "linear",
            "allowSkip" -> "Yes",
            "requiresSubmit" -> "No",
            "shuffle" -> true.asInstanceOf[AnyRef],
            "showFeedback" -> "Yes",
            "showSolutions" -> "Yes",
            "showHints" -> "Yes",
            "summaryType" -> "Complete",
            "versionKey" -> "1234",
            "mimeType" -> "application/vnd.sunbird.questionset",
            "primaryCategory" -> "Practice Question Set",
            "channel" -> "in.ekstep"
        )))
        node
    }

    def getQuestionSetNode(identifier:String): Node = {
        val node = getNode("QuestionSet", None)
        node.setIdentifier(identifier)
        node.getMetadata.putAll(mapAsJavaMap(Map("name" -> "question_1",
            "visibility" -> "Default",
            "code" -> "finemanfine",
            "versionKey" -> "1234",
            "mimeType" -> "application/vnd.sunbird.questionset",
            "primaryCategory" -> "Practice Question Set")))
        node
    }
}
