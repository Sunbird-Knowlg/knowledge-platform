package org.sunbird.actors

import java.util
import akka.actor.Props
import org.apache.commons.lang3.StringUtils
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.{ResourceNotFoundException, ResponseCode}
import org.sunbird.graph.common.enums.GraphDACParams
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.utils.Constants
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions.mapAsJavaMap

class CategoryInstanceActorTest extends BaseSpec with MockFactory {

  "CategoryInstanceActor" should "return failed response for 'unknown' operation" in {
    implicit val oec: OntologyEngineContext = new OntologyEngineContext
    testUnknownOperation(Props(new CategoryInstanceActor()), getCategoryInstanceRequest())
  }

  it should "create a CategoryInstance node and store it in neo4j" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node()
    node.setIdentifier("NCF")
    node.setObjectType("Framework")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "NCF");
        put("objectType", "Framework")
        put("name", "NCF")
      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "NCF", *, *).returns(Future(node)).anyNumberOfTimes()
    val nodes: util.List[Node] = getFrameworkNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val categoryNode = new Node()
    categoryNode.setIdentifier("board")
    categoryNode.setObjectType("Category")
    categoryNode.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "board");
        put("objectType", "Category")
        put("name", "board")
      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "board", *, *).returns(Future(categoryNode)).anyNumberOfTimes()
    val categoryNodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(categoryNodes)).anyNumberOfTimes()

    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(getCategoryInstanceOfNode()))
    val loopResult: util.Map[String, Object] = new util.HashMap[String, Object]()
    loopResult.put(GraphDACParams.loop.name, new java.lang.Boolean(false))
    (graphDB.checkCyclicLoop _).expects(*, *, *, *).returns(loopResult).anyNumberOfTimes()
    (graphDB.createRelation _).expects(*, *).returns(Future(new Response()))

    val request = getCategoryInstanceRequest()
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF","code" -> "board" ,"name" -> "Board")))
    request.setOperation(Constants.CREATE_CATEGORY_INSTANCE)
    val response = callActor(request, Props(new CategoryInstanceActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get(Constants.IDENTIFIER) != null)
    assert(response.get("versionKey") != null)
  }

  it should "throw error if category does not belong to master category" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node()
    node.setIdentifier("NCF")
    node.setObjectType("Framework")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "NCF");
        put("objectType", "Framework")
        put("name", "NCF")
      }
    })

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()

    val request = getCategoryInstanceRequest()
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF", "code" -> "board", "name" -> "Board")))
    request.setOperation(Constants.CREATE_CATEGORY_INSTANCE)
    val response = callActor(request, Props(new CategoryInstanceActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw error if code value is empty " in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node()
    node.setIdentifier("NCF")
    node.setObjectType("Framework")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "NCF");
        put("objectType", "Framework")
        put("name", "NCF")
      }
    })

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()

    val request = getCategoryInstanceRequest()
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF", "code" -> "", "name" -> "Board")))
    request.setOperation(Constants.CREATE_CATEGORY_INSTANCE)
    val response = callActor(request, Props(new CategoryInstanceActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throws exception if identifier is empty" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node()
    node.setIdentifier("")
    node.setObjectType("Framework")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "");
        put("objectType", "Framework")
        put("name", "NCF")
      }
    })

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()

    val request = getCategoryInstanceRequest()
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF", "code" -> "board", "name" -> "Board")))
    request.setOperation(Constants.CREATE_CATEGORY_INSTANCE)
    val response = callActor(request, Props(new CategoryInstanceActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw exception if frameworkId is not sent in request" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val request = getCategoryInstanceRequest()
    request.putAll(mapAsJavaMap(Map("code" -> "board", "name" -> "Board")))
    request.setOperation(Constants.CREATE_CATEGORY_INSTANCE)
    val response = callActor(request, Props(new CategoryInstanceActor()))
    assert("failed".equals(response.getParams.getStatus))

  }

  it should "throw exception if frameworkId is null " in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val request = getCategoryInstanceRequest()
    request.putAll(mapAsJavaMap(Map("framework" -> "", "code" -> "board", "name" -> "Board")))
    request.setOperation(Constants.CREATE_CATEGORY_INSTANCE)
    val response = callActor(request, Props(new CategoryInstanceActor()))
    assert("failed".equals(response.getParams.getStatus))

  }

  it should "throw exception if node id is empty for 'readCategoryInstance'" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB)
    val node = new Node()
    node.setIdentifier("")
    node.setNodeType("DATA_NODE")
    node.setObjectType("CategoryInstance")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "")
        put("objectType", "CategoryInstance")
        put("name", "Board")
        put("code", "board")
        put("description", "Board")
        put("versionKey", "1234")

      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
    val request = getCategoryInstanceRequest()
    request.getContext.put("identifier", "ncf_board")
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF", "name" -> "Board", "description" -> "Board", "code" -> "board", "identifier" -> "ncf_board", "channel" -> "sunbird", "category" -> "board")))
    request.setOperation("readCategoryInstance")
    val response = callActor(request, Props(new CategoryInstanceActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw exception if code is not sent in request" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val request = getCategoryInstanceRequest()
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF", "name" -> "Board", "frameworks" -> "[{identifier=NCF_TEST1}]}]")))
    request.setOperation(Constants.CREATE_CATEGORY_INSTANCE)
    val response = callActor(request, Props(new CategoryInstanceActor()))
    assert("failed".equals(response.getParams.getStatus))

  }

  it should "throw exception if status is sent in createCategoryInstance request" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    val request = getCategoryInstanceRequest()
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF","code" -> "board","status" -> "Live", "name" -> "Board", "frameworks" -> "[{identifier=NCF_TEST1}]}]")))
    request.setOperation(Constants.CREATE_CATEGORY_INSTANCE)
    val response = callActor(request, Props(new CategoryInstanceActor()))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    assert(response.getParams.getErr == "ERROR_RESTRICTED_PROP")
    assert(response.getParams.getErrmsg == "Properties in list [status] are not allowed in request")
  }

  it should "return success response for 'readCategoryInstance'" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB)
    val node = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
    val request = getCategoryInstanceRequest()
    request.getContext.put("identifier", "ncf_board")
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF","name" -> "Board", "description" -> "Board", "code" -> "board","identifier" -> "ncf_board", "channel" ->"sunbird","category" -> "board")))
    request.setOperation("readCategoryInstance")
    val response = callActor(request, Props(new CategoryInstanceActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "throw exception if  cateogry has null values for 'readCategoryInstance'" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getCategoryInstanceRequest()
    request.getContext.put("identifier", "ncf_board")
    request.putAll(mapAsJavaMap(Map("framework" -> "ncf", "name" -> "Board", "description" -> "Board", "code" -> "board", "identifier" -> "ncf_board", "channel" -> "sunbird", "category" -> "")))
    request.setOperation("readCategoryInstance")
    val response = callActor(request, Props(new CategoryInstanceActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw exception if frameworkId has null values for 'readCategoryInstance'" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getCategoryInstanceRequest()
    request.getContext.put("identifier", "ncf_board")
    request.putAll(mapAsJavaMap(Map("framework" -> "", "name" -> "Board", "description" -> "Board", "code" -> "board", "identifier" -> "ncf_board", "channel" -> "sunbird", "category" -> "board")))
    request.setOperation("readCategoryInstance")
    val response = callActor(request, Props(new CategoryInstanceActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "return success response for updateCategoryInstance" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getValidNode()))
    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getCategoryInstanceRequest()
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF","name" -> "Board", "description" -> "Board", "code" -> "board", "channel" ->"sunbird","category" -> "board")))
    request.setOperation(Constants.UPDATE_CATEGORY_INSTANCE)
    val response = callActor(request, Props(new CategoryInstanceActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get(Constants.IDENTIFIER) != null)
    assert(response.get(Constants.VERSION_KEY) != null)
    assert(response.get(Constants.IDENTIFIER).equals("ncf_board"))
  }

  it should "throw exception if identifier is sent in updateCategoryInstance request" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val request = getCategoryInstanceRequest()
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF", "name" -> "Board", "identifier"->"ncf_board", "description" -> "Board", "code" -> "board", "channel" -> "sunbird", "category" -> "board")))
    request.setOperation(Constants.UPDATE_CATEGORY_INSTANCE)
    val response = callActor(request, Props(new CategoryInstanceActor()))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    assert(response.getParams.getErr == "ERROR_RESTRICTED_PROP")
    assert(response.getParams.getErrmsg == "Properties in list [identifier] are not allowed in request")
  }

  it should "return success response for retireCategoryInstance" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getValidNode()))
    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()
    val request = getCategoryInstanceRequest()
    request.getContext.put("identifier", "ncf_board")
    request.putAll(mapAsJavaMap(Map("framework" -> "NCF","name" -> "Board", "description" -> "Board", "code" -> "board", "channel" ->"sunbird","category" -> "board")))
    request.putAll(mapAsJavaMap(Map("identifier" -> "ncf_board")))
    request.setOperation("retireCategoryInstance")
    val response = callActor(request, Props(new CategoryInstanceActor()))
    assert("successful".equals(response.getParams.getStatus))
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



  private def getCategoryInstanceRequest(): Request = {
    val request = new Request()
    request.setContext(getContext())
    request
  }

  private def getContext(): util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {
    {
      put("graph_id", "domain")
      put("version", "1.0")
      put("objectType", "CategoryInstance")
      put("schemaName", "CategoryInstance")
    }
  }



  private def getValidNode(): Node = {
    val node = new Node()
    node.setIdentifier("ncf_board")
    node.setNodeType("DATA_NODE")
    node.setObjectType("CategoryInstance")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "Board")
        put("objectType", "CategoryInstance")
        put("name", "Board")
        put("code", "board")
        put("description", "Board")
        put("versionKey", "1234")

      }
    })
    node
  }


}
