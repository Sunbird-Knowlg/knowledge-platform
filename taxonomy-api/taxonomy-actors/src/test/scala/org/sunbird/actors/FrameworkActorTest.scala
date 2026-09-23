package org.sunbird.actors

import java.util
import org.apache.pekko.actor.Props
import org.apache.commons.lang3.StringUtils
import org.scalamock.scalatest.MockFactory
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.dto.{Request, Response, ResponseParams}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.{Node, Relation, SearchCriteria, SubGraph}
import org.sunbird.utils.Constants
import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class FrameworkActorTest extends BaseSpec with MockFactory {

  "FrameworkActor" should "return failed response for 'unknown' operation" in {
    implicit val oec: OntologyEngineContext = new OntologyEngineContext
    testUnknownOperation(Props(new FrameworkActor()), getFrameworkRequest())
  }


  it should "return success response for 'createFramework' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node("domain", "DATA_NODE", "Channel")
    node.setIdentifier("channel_test")
    node.setObjectType("Channel")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "channel_test");
        put("objectType", "Channel")
        put("name", "Channel")
      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    val nodes: util.List[Node] = getFrameworkNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(getFrameworkOfNode()))

    val request = getFrameworkRequest()
    request.putAll(mutable.Map[String, AnyRef]("name" ->"framework_test", "code"-> "framework_test", "description" -> "desc_test", "channel"->"channel_test").asJava)
    request.setOperation(Constants.CREATE_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get(Constants.NODE_ID).equals("framework_test"))
  }

  it should "throw exception if code is sent empty for 'createFramework' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val request = getFrameworkRequest()
    request.putAll(mutable.Map[String, AnyRef]("name" -> "framework_test", "code" -> "", "description" -> "desc_test", "channel" -> "channel_test").asJava)
    request.setOperation(Constants.CREATE_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw exception if channel is not sent in the request for 'createFramework' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val request = getFrameworkRequest()
    request.putAll(mutable.Map[String, AnyRef]("name" -> "framework_test", "code" -> "framework_test", "description" -> "desc_test").asJava)
    request.setOperation(Constants.CREATE_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "throw exception if invalid translations sent in the request 'createFramework'" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node("domain", "DATA_NODE", "Channel")
    node.setIdentifier("channel_test")
    node.setObjectType("Channel")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "channel_test");
        put("objectType", "Channel")
        put("name", "Channel")
      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    val nodes: util.List[Node] = getFrameworkNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()
    val translations = new java.util.HashMap[String, String]()
    translations.put("sta", "trnm")
    val request = getFrameworkRequest()
    request.put("translations", translations)
    request.putAll(mutable.Map[String, AnyRef]("name" -> "framework_test", "code" -> "framework_test", "description" -> "desc_test", "channel" -> "channel_test").asJava)
    request.setOperation(Constants.CREATE_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("failed".equals(response.getParams.getStatus))
    assert(StringUtils.equalsIgnoreCase(response.getParams.getErrmsg, "Please Provide Valid Language Code For translations. Valid Language Codes are : [as, bn, en, gu, hi, hoc, jun, ka, mai, mr, unx, or, san, sat, ta, te, urd, pj]"))

  }

  it should "throw exception if empty channel identifier is sent in the request 'createFramework' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = new Node("domain", "DATA_NODE", "Channel")
    node.setIdentifier("")
    node.setObjectType("Channel")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "channel_test");
        put("objectType", "Channel")
        put("name", "Channel")
      }
    })
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    val request = getFrameworkRequest()
    request.putAll(mutable.Map[String, AnyRef]("name" -> "framework_test", "code" -> "framework_test", "description" -> "desc_test", "channel" -> "channel_test").asJava)
    request.setOperation(Constants.CREATE_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("failed".equals(response.getParams.getStatus))
  }

  it should "return success response for updateFramework" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getValidNode()))
    val nodes: util.List[Node] = getFrameworkNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getFrameworkRequest()
    request.putAll(mutable.Map[String, AnyRef]("description" -> "test desc").asJava)
    request.setOperation(Constants.UPDATE_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "return node_id with the .img suffix when updating a Live framework, without flipping the live node's own status" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val liveNode = getValidNode()
    liveNode.getMetadata.put("status", "Live")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request))
      .expects(*, "framework_test", *, *).returns(Future(liveNode)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request))
      .expects(*, "framework_test.img", *, *)
      .returns(Future.failed(new java.util.concurrent.CompletionException(
        new org.sunbird.common.exception.ResourceNotFoundException("ERR_NODE_NOT_FOUND", "not found")))).anyNumberOfTimes()
    val imgClone = getValidNode()
    imgClone.setIdentifier("framework_test.img")
    imgClone.setObjectType("FrameworkImage")
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(imgClone)) // becomes the .img clone
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(new Response())).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(imgClone))
    val nodes: util.List[Node] = getFrameworkNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getFrameworkRequest()
    request.getContext.put("identifier", "framework_test")
    request.putAll(mutable.Map[String, AnyRef]("description" -> "test desc").asJava)
    request.setOperation(Constants.UPDATE_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("node_id").asInstanceOf[String].endsWith(".img"))
  }

  it should "write the base node in place when updating a Draft (never-published) framework" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getValidNode() // no "status" set -> not in statusList -> no .img clone
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
    val nodes: util.List[Node] = getFrameworkNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getFrameworkRequest()
    request.putAll(mutable.Map[String, AnyRef]("description" -> "test desc").asJava)
    request.setOperation(Constants.UPDATE_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("node_id").equals("framework_test"))
  }

  it should "return success response for 'retireCategory' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getValidNode()
    node.setObjectType("Framework")
    // Argument-matched (not wildcard): retire()'s new deleteImageNodeIfExists probe for ".img" must
    // miss, while DataNode.update's own fetch of the base id must hit -- a single wildcard stub can no
    // longer stand in for both calls.
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request))
      .expects(*, "framework_test.img", *, *)
      .returns(Future.failed(new java.util.concurrent.CompletionException(
        new org.sunbird.common.exception.ResourceNotFoundException("ERR_NODE_NOT_FOUND", "not found")))).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request))
      .expects(*, "framework_test", *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))

    val nodes: util.List[Node] = getFrameworkNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getFrameworkRequest()
    request.getContext.put("identifier", "framework_test");
    request.getRequest.put("identifier", "framework_test")
    request.setOperation(Constants.RETIRE_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "retire with an existing .img: delete it before/alongside the retire update" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getValidNode()
    node.setObjectType("Framework")
    val imgNode = getValidNode()
    imgNode.setIdentifier("framework_test.img")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request))
      .expects(*, "framework_test.img", *, *).returns(Future(imgNode)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request))
      .expects(*, "framework_test", *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.deleteNode(_: String, _: String, _: Request)).expects(*, "framework_test.img", *).returns(Future(true))
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
    val nodes: util.List[Node] = getFrameworkNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getFrameworkRequest()
    request.getContext.put("identifier", "framework_test")
    request.getRequest.put("identifier", "framework_test")
    request.setOperation(Constants.RETIRE_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "retire with no .img: clean no-op, no exception surfaces, no deleteNode call" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getValidNode()
    node.setObjectType("Framework")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request))
      .expects(*, "framework_test.img", *, *)
      .returns(Future.failed(new java.util.concurrent.CompletionException(
        new org.sunbird.common.exception.ResourceNotFoundException("ERR_NODE_NOT_FOUND", "not found")))).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request))
      .expects(*, "framework_test", *, *).returns(Future(node)).anyNumberOfTimes()
    // graphDB.deleteNode is intentionally left un-stubbed: ScalaMock fails the test if it's called.
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))
    val nodes: util.List[Node] = getFrameworkNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getFrameworkRequest()
    request.getContext.put("identifier", "framework_test")
    request.getRequest.put("identifier", "framework_test")
    request.setOperation(Constants.RETIRE_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("successful".equals(response.getParams.getStatus))
  }

  it should "return success response for 'copyFramework' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getFrameworkOfNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(node)).anyNumberOfTimes()
    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getFrameworkRequest()
    request.putAll(mutable.Map[String, AnyRef](Constants.IDENTIFIER -> "NCF", "createdBy" -> "username_1", Constants.CODE -> "NCF_COPY").asJava)
    request.setOperation(Constants.COPY_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.getResult.containsKey(Constants.NODE_ID))
  }

  it should "throw exception if code not sent in the request 'copyFramework' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getFrameworkOfNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(node)).anyNumberOfTimes()
    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getFrameworkRequest()
    request.putAll(mutable.Map[String, AnyRef](Constants.IDENTIFIER -> "NCF").asJava)
    request.setOperation(Constants.COPY_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("failed".equals(response.getParams.getStatus))
    assert("ERR_FRAMEWORK_CODE_REQUIRED".equals(response.getParams.getErr))
  }

  it should "throw exception if code & identifier values same in the request 'copyFramework' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getFrameworkOfNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(node)).anyNumberOfTimes()
    val nodes: util.List[Node] = getCategoryNode()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

    val request = getFrameworkRequest()
    request.putAll(mutable.Map[String, AnyRef](Constants.IDENTIFIER -> "NCF", Constants.CODE -> "NCF").asJava)
    request.setOperation(Constants.COPY_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("failed".equals(response.getParams.getStatus))
    assert("ERR_FRAMEWORKID_CODE_MATCHES".equals(response.getParams.getErr))
  }

  private def getPublishChannelNode(): Node = {
    val node = new Node("domain", "DATA_NODE", "Channel")
    node.setIdentifier("sunbird")
    node.setObjectType("Channel")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "sunbird");
        put("objectType", "Channel")
        put("name", "Channel")
      }
    })
    node
  }

  private def notFoundFailure(): Future[Node] = Future.failed(new java.util.concurrent.CompletionException(
    new org.sunbird.common.exception.ResourceNotFoundException("ERR_NODE_NOT_FOUND", "not found")))

  it should "return success response for 'publishFramework' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getPublishChannelNode()
    val liveNode = getValidNode()
    // Argument-matched: the new publishFramework() orchestration probes ".img" (miss, so no relation/
    // metadata promote runs) and separately fetches the live framework node (with relations, getTags=true).
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "sunbird", *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "framework_test.img", *, *).returns(notFoundFailure()).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "framework_test", *, *).returns(Future(liveNode)).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(liveNode)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    val subGraph = getSubGraphData()
    (graphDB.getSubGraph(_: String, _: String, _: Int)).expects(*, *, *).returns(Future(subGraph)).anyNumberOfTimes()
    (graphDB.saveExternalProps(_: Request)).expects(*).returns(Future(getSuccessfulResponse())).anyNumberOfTimes

    val request = getFrameworkRequest()
    request.getContext.put(Constants.IDENTIFIER, "framework_test")
    request.putAll(mutable.Map[String, AnyRef](Constants.IDENTIFIER -> "framework_test", "channel" -> "sunbird").asJava)
    request.setOperation(Constants.PUBLISH_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("version") != null)
    assert("Live".equals(response.get("status")))
  }

  it should "return success response for 'publishFramework' when context schemaName is competencyframework" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getPublishChannelNode()
    val liveNode = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "sunbird", *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "framework_test.img", *, *).returns(notFoundFailure()).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "framework_test", *, *).returns(Future(liveNode)).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(liveNode)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    val subGraph = getSubGraphData()
    (graphDB.getSubGraph(_: String, _: String, _: Int)).expects(*, *, *).returns(Future(subGraph)).anyNumberOfTimes()
    (graphDB.saveExternalProps(_: Request)).expects(*).returns(Future(getSuccessfulResponse())).anyNumberOfTimes

    val request = getFrameworkRequest()
    request.getContext.put(Constants.SCHEMA_NAME, Constants.COMPETENCY_FRAMEWORK_SCHEMA_NAME)
    request.getContext.put(Constants.IDENTIFIER, "framework_test")
    request.putAll(mutable.Map[String, AnyRef](Constants.IDENTIFIER -> "framework_test", "channel" -> "sunbird").asJava)
    request.setOperation(Constants.PUBLISH_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("version") != null)
    assert("Live".equals(response.get("status")))
  }

  it should "promote an in-progress .img edit onto the live node during publish" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    val node = getPublishChannelNode()
    val liveNode = getValidNode()
    val imgNode = getValidNode()
    imgNode.setIdentifier("framework_test.img")
    imgNode.getMetadata.put("description", "draft edit")
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "sunbird", *, *).returns(Future(node)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "framework_test.img", *, *).returns(Future(imgNode)).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "framework_test", *, *).returns(Future(liveNode)).anyNumberOfTimes()
    (graphDB.deleteNode(_: String, _: String, _: Request)).expects(*, "framework_test.img", *).returns(Future(true))
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).onCall((_: String, n: Node, _: Request) => {
      assert("draft edit".equals(n.getMetadata.get("description")))
      Future(n)
    })
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    val subGraph = getSubGraphData()
    (graphDB.getSubGraph(_: String, _: String, _: Int)).expects(*, *, *).returns(Future(subGraph)).anyNumberOfTimes()
    (graphDB.saveExternalProps(_: Request)).expects(*).returns(Future(getSuccessfulResponse())).anyNumberOfTimes

    val request = getFrameworkRequest()
    request.getContext.put(Constants.IDENTIFIER, "framework_test")
    request.putAll(mutable.Map[String, AnyRef](Constants.IDENTIFIER -> "framework_test", "channel" -> "sunbird").asJava)
    request.setOperation(Constants.PUBLISH_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("successful".equals(response.getParams.getStatus))
  }


  it should "return success response for 'readFramework' operation" in {
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(new Response()))
    val node = getValidNode()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
//    val frameworkMetadata = """{"name":"Framework1"}"""
//    val cacheKey = "fw_framework_test_categories_test"
//    RedisCache.set(cacheKey, frameworkMetadata)
    val request = getFrameworkRequest()
    request.getContext.put("identifier", "frameworkTest")
    request.putAll(mutable.Map[String, AnyRef]("identifier" -> "framework_test", "channel" -> "sunbird", Constants.CATEGORIES -> "").asJava)
    request.setOperation(Constants.READ_FRAMEWORK)
    val response = callActor(request, Props(new FrameworkActor()))
    assert("successful".equals(response.getParams.getStatus))
  }


  private def getFrameworkOfNode(): Node = {
    val node = new Node()
    node.setIdentifier("framework_test")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Framework")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("identifier", "framework_test")
        put("objectType", "Framework")
        put("name", "framework_test")
        put("code", "framework_test")
        put("channel", "channel_test")
      }
    })
    node
  }

  private def getValidNode(): Node = {
    val node = new Node()
    node.setIdentifier("framework_test")
    node.setGraphId("domain")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Framework")
    node.setMetadata(new util.HashMap[String, AnyRef]() {
      {
        put("code", "framework_test")
        put("objectType", "Framework")
        put("name", "framework_test")
        put("channel", "sunbird")
      }
    })
    node
  }

  private def getFrameworkRequest(): Request = {
    val request = new Request()
    request.setContext(getContext())
    request
  }

  private def getContext(): util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {
    {
      put("graph_id", "domain")
      put("version", "1.0")
      put("objectType", "Framework")
      put("schemaName", "framework")

    }
  }

  private def getFramwrokRequest(): Request = {
    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() {
      {
        put("graph_id", "domain")
        put("version", "1.0")
        put("objectType", "Framework")
        put("schemaName", "framework")
        put("channel", "sunbird")
      }
    })
    request.setObjectType("Framework")
    request
  }

  def getSubGraphData(): SubGraph = {
    val nodeMap: Map[String, Node] = Map[String, Node]("framework_test" -> getValidNode())
    val relationsList: util.List[Relation] = new util.ArrayList[Relation]()
    val subGraphFData = new SubGraph(nodeMap.asJava, relationsList)
    subGraphFData
  }

  def getSuccessfulResponse(): Response = {
    val response = new Response
    response.setVer("3.0")
    val responseParams = new ResponseParams
    responseParams.setStatus("successful")
    response.setParams(responseParams)
    response.setResponseCode(ResponseCode.OK)
    response
  }

}
