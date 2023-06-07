package org.sunbird.actors

import java.util

import akka.actor.Props
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.dto.Request
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.utils.Constants

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CategoryActorTest extends BaseSpec with MockFactory{

      "CategoryActor" should "return failed response for 'unknown' operation" in {
          implicit val oec: OntologyEngineContext = new OntologyEngineContext
          testUnknownOperation(Props(new CategoryActor()), getCategoryRequest())
      }

      it should "return success response for 'createCategory' operation" in {
          implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
          val graphDB = mock[GraphService]
          (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
          val node = new Node("domain", "DATA_NODE", "Category")
          node.setIdentifier("category_test")
          node.setObjectType("Category")
          (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(node))

          val nodes: util.List[Node] = getCategoryNode()
          (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

          val request = getCategoryRequest()
          request.getRequest.put("name", "category_test")
          request.getRequest.put("code", "category_test")
          request.setOperation(Constants.CREATE_CATEGORY)
          val response = callActor(request, Props(new CategoryActor()))
          assert("successful".equals(response.getParams.getStatus))
          assert(response.get(Constants.IDENTIFIER).equals("category_test"))
          assert(response.get(Constants.NODE_ID).equals("category_test"))
      }

      it should "throw exception if status sent in request" in {
          implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
          val request = getCategoryRequest()
          request.getRequest.put("name", "category_test")
          request.getRequest.put("code", "category_test")
          request.getRequest.put("status", "Live")
          request.setOperation("createCategory")
          val response = callActor(request, Props(new CategoryActor()))
          assert("failed".equals(response.getParams.getStatus))
      }

      it should "throw exception if code not sent in request" in {
          implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
          val request = getCategoryRequest()
          request.getRequest.put("name", "category_test")
          request.setOperation("createCategory")
          val response = callActor(request, Props(new CategoryActor()))
          assert("failed".equals(response.getParams.getStatus))
      }

      it should "return success response for 'readCategory'" in {
          implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
          val graphDB = mock[GraphService]
          (oec.graphService _).expects().returns(graphDB)
          val node = getValidNode()
          (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
          val request = getCategoryRequest()
          request.getContext.put("identifier", "category_test")
          request.putAll(mapAsJavaMap(Map("identifier" -> "category_test")))
          request.setOperation("readCategory")
          val response = callActor(request, Props(new CategoryActor()))
          assert("successful".equals(response.getParams.getStatus))
      }

      it should "return success response for updateCategory" in {
          implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
          val graphDB = mock[GraphService]
          (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
          val node = getValidNode()
          (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
          (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getValidNode()))
          val nodes: util.List[Node] = getCategoryNode()
          (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

          val request = getCategoryRequest()
          request.getContext.put(Constants.IDENTIFIER, "category_test")
          request.putAll(mapAsJavaMap(Map("description" -> "test desc")))
          request.setOperation(Constants.UPDATE_CATEGORY)
          val response = callActor(request, Props(new CategoryActor()))
          assert("successful".equals(response.getParams.getStatus))
      }

      it should "return success response for 'retireCategory' operation" in {
          implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
          val graphDB = mock[GraphService]
          (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
          val node = getValidNode()
          node.setObjectType("Category")
          (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node))
          (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(node))

          val nodes: util.List[Node] = getCategoryNode()
          (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

          val request = getCategoryRequest()
          request.getContext.put("identifier", "channel_test");
          request.getRequest.put("identifier", "channel_test")
          request.setOperation("retireCategory")
          val response = callActor(request, Props(new CategoryActor()))
          assert("successful".equals(response.getParams.getStatus))
      }

      private def getCategoryRequest(): Request = {
          val request = new Request()
          request.setContext(new util.HashMap[String, AnyRef]() {
            {
              put("graph_id", "domain")
              put("version", "1.0")
              put("objectType", "Category")
              put("schemaName", "category")

            }
          })
          request.setObjectType("Category")
          request
      }

  private def getValidNode(): Node = {
      val node = new Node()
      node.setIdentifier("category_test")
      node.setNodeType("DATA_NODE")
      node.setObjectType("Category")
      node.setMetadata(new util.HashMap[String, AnyRef]() {
        {
          put("identifier", "category_test")
          put("objectType", "Category")
          put("name", "category_test")
        }
      })
      node
  }
}
