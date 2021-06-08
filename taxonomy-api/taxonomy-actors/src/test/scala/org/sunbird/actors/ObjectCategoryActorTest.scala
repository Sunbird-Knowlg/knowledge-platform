package org.sunbird.actors

import java.util

import akka.actor.Props
import org.apache.commons.lang3.StringUtils
import org.scalamock.scalatest.MockFactory
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.utils.Constants

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ObjectCategoryActorTest  extends BaseSpec with MockFactory {

    "CategoryActor" should "return failed response for 'unknown' operation" in {
        implicit val oec: OntologyEngineContext = new OntologyEngineContext
        testUnknownOperation(Props(new ObjectCategoryActor()), getCategoryRequest())
    }

    it should "create a categoryNode and store it in neo4j" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(getValidNode()))
        val nodes: util.List[Node] = getCategoryNode()
        (graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

        val request = getCategoryRequest()
        request.putAll(mapAsJavaMap(Map("name" -> "1234")))
        request.setOperation(Constants.CREATE_OBJECT_CATEGORY)
        val response = callActor(request, Props(new ObjectCategoryActor()))
        assert(response.get(Constants.IDENTIFIER) != null)
        assert(response.get(Constants.IDENTIFIER).equals("obj-cat:1234"))
    }

    it should "return exception for categoryNode without name" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val request = getCategoryRequest()
        request.putAll(mapAsJavaMap(Map("translations" -> Map("en" -> "english", "hi" -> "hindi"))))
        request.setOperation(Constants.CREATE_OBJECT_CATEGORY)
        val response = callActor(request, Props(new ObjectCategoryActor()))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR)
        assert(StringUtils.equalsIgnoreCase(response.getParams.getErrmsg, "name will be set as identifier"))
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
        request.getContext.put(Constants.IDENTIFIER, "obj-cat:1234")
        request.putAll(mapAsJavaMap(Map("description" -> "test desc")))
        request.setOperation(Constants.UPDATE_OBJECT_CATEGORY)
        val response = callActor(request, Props(new ObjectCategoryActor()))
        assert("successful".equals(response.getParams.getStatus))
    }


    it should "return success response for readCategory" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).repeated(1)
        val node = getValidNode()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(node)).anyNumberOfTimes()
        val request = getCategoryRequest()
        request.getContext.put(Constants.IDENTIFIER, "obj-cat:1234")
        request.putAll(mapAsJavaMap(Map("fields" -> "")))
        request.setOperation(Constants.READ_OBJECT_CATEGORY)
        val response = callActor(request, Props(new ObjectCategoryActor()))
        assert("successful".equals(response.getParams.getStatus))
    }

    private def getCategoryRequest(): Request = {
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "ObjectCategory")
                put("schemaName", "objectcategory")

            }
        })
        request.setObjectType("ObjectCategory")
        request
    }

    private def getValidNode(): Node = {
        val node = new Node()
        node.setIdentifier("obj-cat:1234")
        node.setNodeType("DATA_NODE")
        node.setObjectType("ObjectCategory")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "obj-cat:1234")
                put("objectType", "ObjectCategory")
                put("name", "1234")
            }
        })
        node
    }
}
