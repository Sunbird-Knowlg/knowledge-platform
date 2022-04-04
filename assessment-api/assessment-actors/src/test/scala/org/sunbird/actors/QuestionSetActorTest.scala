package org.sunbird.actors

import java.util
import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.HttpUtil
import org.sunbird.common.dto.{Property, Request, Response, ResponseHandler}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.dac.model.{Node, Relation, SearchCriteria}
import org.sunbird.graph.nodes.DataNode.getRelationMap
import org.sunbird.graph.utils.ScalaJsonUtils
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.kafka.client.KafkaClient
import org.sunbird.utils.JavaJsonUtils

import java.util
import scala.collection.JavaConversions._
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
        val node = getNode("do_11348469558523494411","QuestionSet", None)
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
            "createdBy" -> "g-001",
            "primaryCategory" -> "Practice Question Set")))
        val nodeList = new util.ArrayList[Node]() {{
            add(getNode("do_11348469662446387212","Question", Some(Map("visibility"-> "Parent", "createdBy"-> "g-001"))))
            add(getNode("do_11348469662607769614","Question", Some(Map("visibility"-> "Default", "createdBy"-> "g-002"))))
        }}
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).atLeastOnce()
        (graphDB.getNodeByUniqueIds(_ :String, _: SearchCriteria)).expects(*, *).returns(Future(nodeList)).atLeastOnce()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getDraftCassandraHierarchy)).anyNumberOfTimes
        (kfClient.send(_: String, _: String)).expects(*, *).once()
        val request = getQuestionSetRequest()
        request.getContext.put("identifier", "do1234")
        request.putAll(mapAsJavaMap(Map("versionKey" -> "1234", "description" -> "updated desc")))
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
