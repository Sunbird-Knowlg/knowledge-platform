package org.sunbird.content.enrichmentobject.actors

import org.apache.pekko.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.content.actors.BaseSpec
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.graph.{GraphService, OntologyEngineContext}

import java.util
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._

/**
 * Covers EnrichmentObjectManager's generic operations (create, list, update, approve,
 * reject) through EnrichmentObjectActor, using Transcript as the registered category
 * under test - identical to the real registration used in manual testing (uniqueOn on
 * sourceLanguage/languageCode).
 */
class TestEnrichmentObjectActor extends BaseSpec with MockFactory {

  "EnrichmentObjectActor" should "return failed response for 'unknown' operation" in {
    implicit val oec: OntologyEngineContext = new OntologyEngineContext
    testUnknownOperation(Props(new EnrichmentObjectActor()), getRequest("createEnrichmentObject"))
  }

  it should "throw an exception when creating without enrichmentObjectType" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getRequest("createEnrichmentObject")
    request.put("parentId", "do_content_1")
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    assert(response.getParams.getErr == "ERR_ENRICHMENT_OBJECT_TYPE_REQUIRED")
  }

  it should "throw an exception when creating without parentId" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getRequest("createEnrichmentObject")
    request.put("enrichmentObjectType", "Transcript")
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    assert(response.getParams.getErr == "ERR_PARENT_ID_REQUIRED")
  }

  it should "create a new source Transcript when no existing sibling matches" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "do_content_1", *, *).returns(Future(getContentNode())).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCategoryDefinitionResponse())).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(getEnrichmentObjectNode("Draft"))).anyNumberOfTimes()
    (graphDB.createRelation(_: String, _: util.List[util.Map[String, AnyRef]])).expects(*, *).returns(Future(new Response())).anyNumberOfTimes()

    val request = getRequest("createEnrichmentObject")
    request.put("enrichmentObjectType", "Transcript")
    request.put("parentId", "do_content_1")
    request.put("sourceLanguage", true.asInstanceOf[AnyRef])
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("identifier").equals("do_eo_1"))
    assert(response.get("status").equals("Draft"))
  }

  it should "return the existing sibling instead of creating a duplicate when uniqueOn matches" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "do_content_1", *, *).returns(Future(getContentNode())).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCategoryDefinitionResponse())).anyNumberOfTimes()
    val existing = getEnrichmentObjectNode("Draft")
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(util.Arrays.asList(existing))).anyNumberOfTimes()

    val request = getRequest("createEnrichmentObject")
    request.put("enrichmentObjectType", "Transcript")
    request.put("parentId", "do_content_1")
    request.put("sourceLanguage", true.asInstanceOf[AnyRef])
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("identifier").equals("do_eo_1"))
  }

  it should "reject create when the request matches no uniqueOn identity field" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()

    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, "do_content_1", *, *).returns(Future(getContentNode())).anyNumberOfTimes()
    (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCategoryDefinitionResponse())).anyNumberOfTimes()

    val request = getRequest("createEnrichmentObject")
    request.put("enrichmentObjectType", "Transcript")
    request.put("parentId", "do_content_1")
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    assert(response.getParams.getErr == "ERR_NO_IDENTITY_SIGNAL")
  }

  it should "return parentId missing error for list" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getRequest("listEnrichmentObject")
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    assert(response.getParams.getErr == "ERR_PARENT_ID_REQUIRED")
  }

  it should "list EnrichmentObjects under a parent" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(util.Arrays.asList(getEnrichmentObjectNode("Draft")))).anyNumberOfTimes()

    val request = getRequest("listEnrichmentObject")
    request.put("parentId", "do_content_1")
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("count").asInstanceOf[Integer] == 1)
  }

  it should "reject update when current status is Live" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getEnrichmentObjectNode("Live"))).anyNumberOfTimes()

    val request = getRequest("updateEnrichmentObject")
    request.getContext.put("identifier", "do_eo_1")
    request.put("languageCode", "en")
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    assert(response.getParams.getErr == "ERR_EDIT_LOCKED")
  }

  it should "reject update trying to set status directly to Live" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getEnrichmentObjectNode("Draft"))).anyNumberOfTimes()

    val request = getRequest("updateEnrichmentObject")
    request.getContext.put("identifier", "do_eo_1")
    request.put("status", "Live")
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    assert(response.getParams.getErr == "ERR_STATUS_TRANSITION_NOT_ALLOWED")
  }

  it should "allow update to self-report status as Processing" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getEnrichmentObjectNode("Draft"))).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getEnrichmentObjectNode("Processing")))

    val request = getRequest("updateEnrichmentObject")
    request.getContext.put("identifier", "do_eo_1")
    request.put("status", "Processing")
    request.put("languageCode", "en")
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("identifier").equals("do_eo_1"))
  }

  it should "reject approve with a target status other than Live/Review" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val request = getRequest("approveEnrichmentObject")
    request.getContext.put("identifier", "do_eo_1")
    request.put("status", "Draft")
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    assert(response.getParams.getErr == "ERR_INVALID_STATUS_TRANSITION")
  }

  it should "reject approve when current status is already Live" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getEnrichmentObjectNode("Live"))).anyNumberOfTimes()

    val request = getRequest("approveEnrichmentObject")
    request.getContext.put("identifier", "do_eo_1")
    request.put("status", "Live")
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    assert(response.getParams.getErr == "ERR_APPROVE_NOT_ALLOWED")
  }

  it should "approve a Draft EnrichmentObject to Live" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getEnrichmentObjectNode("Draft"))).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getEnrichmentObjectNode("Live")))

    val request = getRequest("approveEnrichmentObject")
    request.getContext.put("identifier", "do_eo_1")
    request.put("status", "Live")
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("status").equals("Live"))
  }

  it should "reject reject(Draft) when current status is Live" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getEnrichmentObjectNode("Live"))).anyNumberOfTimes()

    val request = getRequest("rejectEnrichmentObject")
    request.getContext.put("identifier", "do_eo_1")
    request.put("status", "Draft")
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    assert(response.getParams.getErr == "ERR_INVALID_STATUS_TRANSITION")
  }

  it should "reject reject(Retired) when current status is not Live" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getEnrichmentObjectNode("Draft"))).anyNumberOfTimes()

    val request = getRequest("rejectEnrichmentObject")
    request.getContext.put("identifier", "do_eo_1")
    request.put("status", "Retired")
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
    assert(response.getParams.getErr == "ERR_INVALID_STATUS_TRANSITION")
  }

  it should "retire a Live EnrichmentObject via reject" in {
    implicit val ss = mock[StorageService]
    implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
    val graphDB = mock[GraphService]
    (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
    (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getEnrichmentObjectNode("Live"))).anyNumberOfTimes()
    (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(new util.ArrayList[Node]())).anyNumberOfTimes()
    (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getEnrichmentObjectNode("Retired")))

    val request = getRequest("rejectEnrichmentObject")
    request.getContext.put("identifier", "do_eo_1")
    request.put("status", "Retired")
    val response = callActor(request, Props(new EnrichmentObjectActor()))
    assert("successful".equals(response.getParams.getStatus))
    assert(response.get("status").equals("Retired"))
  }

  private def getRequest(operation: String): Request = {
    val request = new Request()
    request.setContext(new util.HashMap[String, AnyRef]() {
      {
        put("graph_id", "domain")
        put("version", "1.0")
        put("objectType", "EnrichmentObject")
        put("schemaName", "enrichmentobject")
      }
    })
    request.setObjectType("EnrichmentObject")
    request.setOperation(operation)
    request
  }

  private def getContentNode(): Node = {
    val node = new Node()
    node.setIdentifier("do_content_1")
    node.setNodeType("DATA_NODE")
    node.setObjectType("Content")
    node.setMetadata(new util.HashMap[String, AnyRef]() {{
      put("identifier", "do_content_1")
      put("status", "Live")
    }})
    node
  }

  private def getEnrichmentObjectNode(status: String): Node = {
    val node = new Node()
    node.setIdentifier("do_eo_1")
    node.setNodeType("DATA_NODE")
    node.setObjectType("EnrichmentObject")
    node.setMetadata(new util.HashMap[String, AnyRef]() {{
      put("identifier", "do_eo_1")
      put("enrichmentObjectType", "Transcript")
      put("parentId", "do_content_1")
      put("parentType", "Content")
      put("status", status)
      put("versionKey", "test_version_key")
    }})
    node
  }

  /**
   * Mirrors the real Transcript registration used in manual testing: uniqueOn on
   * sourceLanguage (matchValue true) / languageCode.
   */
  private def getCategoryDefinitionResponse(): Response = {
    val schemaJson =
      """{"properties":{"languageCode":{"type":"string"},"language":{"type":"string"},"sourceLanguage":{"type":"boolean","default":false},"artifactUrl":{"type":"string","format":"uri"},"captionsUrl":{"type":"string","format":"uri"}},"required":[]}"""
    val configJson =
      """{"uniqueOn":[{"field":"sourceLanguage","matchValue":true},{"field":"languageCode"}]}"""
    val objectMetadata = new util.HashMap[String, AnyRef]()
    objectMetadata.put("schema", schemaJson)
    objectMetadata.put("config", configJson)
    val result = new util.HashMap[String, AnyRef]()
    result.put("objectMetadata", objectMetadata)
    ResponseHandler.OK.putAll(result)
  }
}
