package org.sunbird.actors

import java.util
import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.common.enums.GraphDACParams
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.utils.Constants

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class TermActorTest extends BaseSpec with MockFactory{

  "TermActor" should "return failed response for 'unknown' operation" in {
    implicit val oec: OntologyEngineContext = new OntologyEngineContext
    testUnknownOperation(Props(new TermActor()), getTermRequest())
  }

  it should "create a Term node and store it in neo4j" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node()
    node.setIdentifier("ncf_board")
    node.setObjectType("CategoryInstance")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "ncf_board");
        put("objectType", "CategoryInstance")
        put("name", "ncf_board")
      }
    })

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    val nodes: util.List[Node] = getCategoryInstanceNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(getTermOfNode()))
    val loopResult: util.Map[String, Object] = new util.HashMap[String, Object]()
    loopResult.put(GraphDACParams.loop.name, new java.lang.Boolean(false))
    (graphDB.checkCyclicLoop _).expects(*, *, *, *).returns(loopResult).anyNumberOfTimes()
    (graphDB.createRelation _).expects(*, *).returns(Future(new Response()))

    val request = getTermRequest()
    request.putAll(mapAsJavaMap(Map("code"->"class1", "name"->"Class1", "description"->"Class1", "framework"->"NCF", "category"->"board", "channel"->"sunbird", "categories"-> "[{identifier=ncf_board}]", "identifier"->"ncf_board_class1")))
    request.setOperation(Constants.CREATE_TERM)
    val response = callActor(request, Props(new TermActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get(Constants.IDENTIFIER) != null)
    assert(response.get(Constants.IDENTIFIER).equals("ncf_board_class1"))
    assert(response.get(Constants.NODE_ID).equals("ncf_board_class1"))
  }

  it should "throw exception if categoryId and identifier are same" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node()
    node.setIdentifier("ncf_board")
    node.setObjectType("CategoryInstance")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "ncf_board");
        put("objectType", "CategoryInstance")
        put("name", "ncf_board")
      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    val request = getTermRequest()
    request.putAll(mapAsJavaMap(Map("code"->"class1", "name"->"Class1", "description"->"Class1", "framework"->"NCF", "category"->"board", "channel"->"sunbird", "categories"-> "[{identifier=ncf_board}]", "identifier"->"ncf_board_class1")))
    request.setOperation(Constants.CREATE_TERM)
    val response = callActor(request, Props(new TermActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw exception if identifier is null" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node()
    node.setIdentifier("")
    node.setObjectType("CategoryInstance")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "");
        put("objectType", "CategoryInstance")
        put("name", "ncf_board")
      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    val request = getTermRequest()
    request.putAll(mapAsJavaMap(Map("code" -> "class1", "name" -> "Class1", "description" -> "Class1", "framework" -> "NCF", "category" -> "board", "channel" -> "sunbird", "categories" -> "[{identifier=ncf_board}]", "identifier" -> "ncf_board_class1")))
    request.setOperation(Constants.CREATE_TERM)
    val response = callActor(request, Props(new TermActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw exception if categoryId is null" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node()
    node.setIdentifier("ncf_board")
    node.setObjectType("CategoryInstance")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "ncf_board");
        put("objectType", "CategoryInstance")
        put("name", "ncf_board")
      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    val request = getTermRequest()
    request.putAll(mapAsJavaMap(Map("code" -> "class1", "name" -> "Class1", "description" -> "Class1", "framework" -> "NCF", "category" ->"", "channel" -> "sunbird", "categories" -> "[{identifier=ncf_board}]", "identifier" -> "ncf_board_class1")))
    request.setOperation(Constants.CREATE_TERM)
    val response = callActor(request, Props(new TermActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw exception if frameworkId is null" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node()
    node.setIdentifier("ncf_board")
    node.setObjectType("CategoryInstance")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "ncf_board");
        put("objectType", "CategoryInstance")
        put("name", "ncf_board")
      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    val request = getTermRequest()
    request.putAll(mapAsJavaMap(Map("code" -> "class1", "name" -> "Class1", "description" -> "Class1", "framework" -> "", "category" -> "board", "channel" -> "sunbird", "categories" -> "[{identifier=ncf_board}]", "identifier" -> "ncf_board_class1")))
    request.setOperation(Constants.CREATE_TERM)
    val response = callActor(request, Props(new TermActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw exception if code is not sent in the request" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node()
    node.setIdentifier("ncf_board")
    node.setObjectType("CategoryInstance")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "ncf_board");
        put("objectType", "CategoryInstance")
        put("name", "ncf_board")
      }
    })

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()

    val request = getTermRequest()
    request.putAll(mapAsJavaMap(Map("code" -> "", "name" -> "Class1", "description" -> "Class1", "framework" -> "NCF", "category" -> "board", "channel" -> "sunbird", "categories" -> "[{identifier=ncf_board}]", "identifier" -> "ncf_board_class1")))
    request.setOperation(Constants.CREATE_TERM)
    val response = callActor(request, Props(new TermActor()))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    assert(response.getParams.getErr == "CLIENT_ERROR")
    assert(response.getParams.getErrmsg == "Validation Errors")
  }

  it should "return success response for 'readTerm'" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val categoryInstanceNode = getCategoryInstanceOfNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "ncf_board", *, *).returns(Future(categoryInstanceNode))
    val termNode = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "ncf_board_class1", *, *).returns(Future(termNode))
    val request = getTermRequest()
    request.getContext.put("identifier", "ncf_board_class1")
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF", "term" -> "class1", "category" -> "board", "channel" -> "sunbird")))
    request.setOperation(Constants.READ_TERM)
    val response = callActor(request, Props(new TermActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "throw exception if identifier is empty for 'readTerm'" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val categoryInstanceNode = getCategoryInstanceOfNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "ncf_board", *, *).returns(Future(categoryInstanceNode))
    val node = new Node()
    node.setIdentifier("")
    node.setGraphId("domain")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Term")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("code", "ncf_board_class1")
        put("objectType", "Term")
        put("name", "ncf_board_class1")
        put("channel", "sunbird")
        put("category", "ncf_board")
      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "ncf_board_class1", *, *).returns(Future(node))
    val request = getTermRequest()
    request.getContext.put("identifier", "ncf_board_class1")
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF", "term" -> "class1", "category" -> "board", "channel" -> "sunbird")))
    request.setOperation(Constants.READ_TERM)
    val response = callActor(request, Props(new TermActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw exception if termId is empty for 'readTerm'" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val categoryInstanceNode = getCategoryInstanceOfNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "ncf_board", *, *).returns(Future(categoryInstanceNode))
    val request = getTermRequest()
    request.getContext.put("identifier", "ncf_board_class1")
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF", "term" -> "", "category" -> "board", "channel" -> "sunbird")))
    request.setOperation(Constants.READ_TERM)
    val response = callActor(request, Props(new TermActor()))
    assert("failed".equals(response.getParams.getStatus))
  }


  it should "return success response for 'updateTerm'" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getValidNode()))
    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getTermRequest()
    request.getContext.put(Constants.IDENTIFIER, "ncf_board_class1")
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF", "name" -> "Board", "description" -> "Board", "code" -> "board", "term" ->"class1" ,"channel" -> "sunbird", "category" -> "board")))
    request.setOperation(Constants.UPDATE_TERM)
    val response = callActor(request, Props(new TermActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "throw exception if identifier is sent in updateCategoryInstance request" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    val request = getTermRequest()
    request.getContext.put(Constants.IDENTIFIER, "ncf_board_class1")
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF", "identifier"->"ncf_board_class1","name" -> "Board", "description" -> "Board", "code" -> "board", "term" -> "class1", "channel" -> "sunbird", "category" -> "board")))
    request.setOperation(Constants.UPDATE_TERM)
    val response = callActor(request, Props(new TermActor()))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    assert(response.getParams.getErr == "ERROR_RESTRICTED_PROP")
    assert(response.getParams.getErrmsg == "Properties in list [identifier] are not allowed in request")
  }

  it should "return success response for 'retireTerm' " in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getValidNode()))
    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()
    val request = getTermRequest()
    request.getContext.put("identifier", "ncf_board_class1")
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF", "name" -> "Board", "description" -> "Board", "code" -> "board", "term" ->"class1" ,"channel" -> "sunbird", "category" -> "board")))
    request.putAll(mapAsJavaMap(Map("identifier" -> "ncf_board_class1")))
    request.setOperation(Constants.RETIRE_TERM)
    val response = callActor(request, Props(new TermActor()))
    assert("successful".equals(response.getParams.getStatus))
  }
  private def getValidNode(): Node = {
    val node = new Node()
    node.setIdentifier("ncf_board_class1")
    node.setGraphId("domain")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Term")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("code", "ncf_board_class1")
        put("objectType", "Term")
        put("name", "ncf_board_class1")
        put("channel", "sunbird")
        put("category", "ncf_board")
      }
    })
    node
  }

  private def getTermOfNode(): Node = {
    val node = new Node()
    node.setIdentifier("ncf_board_class1")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Term")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "ncf_board_class1")
        put("framework", "NCF")
        put("category", "board")
        put("objectType", "Term")
        put("name", "Class1")
        put("code", "class1")
        put("versionKey", "12345")
      }
    })
    node
  }
  private def getTermRequest(): Request = {
    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() {
      {
        put("graph_id", "domain")
        put("version", "1.0")
        put("objectType", "Term")
        put("schemaName", "term")

      }
    })
    request.setObjectType("Term")
    request
  }

  private def getCategoryInstanceOfNode(): Node = {
    val node = new Node()
    node.setIdentifier("ncf_board")
    node.setNodeType("DATA_NODE")
    node.setObjectType("CategoryInstance")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "ncf_board")
        put("framework", "NCF")
        put("objectType", "CategoryInstance")
        put("name", "board")
        put("code", "board")
        put("versionKey", "12345")
      }
    })
    node
  }
}
