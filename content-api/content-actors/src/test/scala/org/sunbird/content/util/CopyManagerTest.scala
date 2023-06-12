package org.sunbird.content.util

import org.apache.commons.collections4.MapUtils
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Property, Request}
import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.utils.ScalaJsonUtils
import org.sunbird.graph.{GraphService, OntologyEngineContext}

import java.util
import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.Future

class CopyManagerTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {

    "CopyManager" should "return copied node identifier when content is copied" ignore {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        implicit val ss: StorageService = mock[StorageService]
        val graphDB = mock[GraphService]
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getNode()))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getDefinitionNode_channel()))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getDefinitionNode_channel()))
        (graphDB.addNode(_: String, _: Node)).expects(*, *).returns(Future(getCopiedNode()))
        (graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getCopiedNode()))
        (graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getCopiedNode()))
        (graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234"))))
        CopyManager.copy(getCopyRequest()).map(resp => {
            assert(resp != null)
            assert(resp.getResponseCode == ResponseCode.OK)
            assert(resp.getResult.get("node_id").asInstanceOf[util.HashMap[String, AnyRef]].get("do_1234").asInstanceOf[String] == "do_1234_copy")
        })
    }

    ignore should "return copied node identifier and safe hierarchy in cassandra when collection is copied" in {
        assert(true)
    }

    "Required property not sent" should "return client error response for" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        implicit val ss = mock[StorageService]
        val request = getInvalidCopyRequest_2()
        request.getContext.put("identifier","do_1234")
        request.getRequest.putAll(mapAsJavaMap(Map("identifier" -> "do_1234")))
        val exception = intercept[ClientException] {
            CopyManager.validateRequest(request)
        }
        exception.getMessage shouldEqual "Please provide valid value for List(createdBy)"
    }

    "Shallow Copy along with copy scheme" should "return client error response for copy content" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        implicit val ss = mock[StorageService]
        val request = getInvalidCopyRequest_1()
        request.getContext.put("identifier","do_1234")
        request.getRequest.putAll(mapAsJavaMap(Map("identifier" -> "do_1234")))
        val exception = intercept[ClientException] {
            CopyManager.validateRequest(request)
        }
        exception.getMessage shouldEqual "Content can not be shallow copied with copy scheme."
    }

    "Wrong CopyScheme sent" should "return client error response for copy content" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        implicit val ss = mock[StorageService]
        val request = getInvalidCopyRequest_3()
        request.getContext.put("identifier","do_1234")
        request.getRequest.putAll(mapAsJavaMap(Map("identifier" -> "do_1234")))
        val exception = intercept[ClientException] {
            CopyManager.validateRequest(request)
        }
        exception.getMessage shouldEqual "Invalid copy scheme, Please provide valid copy scheme"
    }

    "Valid scheme type update" should "should populate new contentType in metadata" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        implicit val ss = mock[StorageService]
        val request = getInvalidCopyRequest_3()
        request.getContext.put("identifier","do_1234")
        request.getRequest.putAll(mapAsJavaMap(Map("identifier" -> "do_1234")))
        val metadata = new util.HashMap[String,Object]()
        CopyManager.updateToCopySchemeContentType(getValidCopyRequest_1(), "TextBook", metadata)
        assert(MapUtils.isNotEmpty(metadata))
    }

    private def getNode(): Node = {
        val node = new Node()
        node.setGraphId("domain")
        node.setIdentifier("do_1234")
        node.setNodeType("DATA_NODE")
        node.setObjectType("Content")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_1234")
                put("mimeType", "application/pdf")
                put("status", "Draft")
                put("contentType", "Resource")
                put("primaryCategory", "Learning Resource")
                put("name", "Copy content")
                put("artifactUrl", "https://ntpstagingall.blob.core.windows.net/ntp-content-staging/content/assets/do_212959046431154176151/hindi3.pdf")
                put("channel", "in.ekstep")
                put("code", "xyz")
                put("versionKey", "1234")
            }
        })
        node
    }

    private def getCopiedNode(): Node = {
        val node = new Node()
        node.setIdentifier("do_1234_copy")
        node.setNodeType("DATA_NODE")
        node.setObjectType("Content")
        node.setGraphId("domain")
        node.setMetadata(new util.HashMap[String, AnyRef]() {
            {
                put("identifier", "do_1234_copy")
                put("mimeType", "application/pdf")
                put("status", "Draft")
                put("contentType", "Resource")
                put("primaryCategory", "Learning Resource")
                put("name", "Copy content")
                put("artifactUrl", "https://ntpstagingall.blob.core.windows.net/ntp-content-staging/content/assets/do_212959046431154176151/hindi3.pdf")
                put("channel", "in.ekstep")
                put("code", "xyz")
                put("versionKey", "1234")
            }
        })
        node
    }

    private def getCopyRequest(): Request = {
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "Content")
                put("schemaName", "content")

            }
        })
        request.setObjectType("Content")
        request.putAll(new util.HashMap[String, AnyRef]() {
            {
                put("createdBy", "EkStep")
                put("createdFor", new util.ArrayList[String]() {
                    {
                        add("Ekstep")
                    }
                })
                put("organisation", new util.ArrayList[String]() {
                    {
                        add("ekstep")
                    }
                })
                put("framework", "DevCon-NCERT")
            }
        })
        request
    }

    private def getInvalidCopyRequest_1(): Request = {
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "Content")
                put("schemaName", "content")
                put("copyScheme", "TextBookToCourse")
            }
        })
        request.setObjectType("Content")
        request.putAll(new util.HashMap[String, AnyRef]() {
            {
                put("createdBy", "EkStep")
                put("createdFor", new util.ArrayList[String]() {
                    {
                        add("Ekstep")
                    }
                })
                put("organisation", new util.ArrayList[String]() {
                    {
                        add("ekstep")
                    }
                })
                put("framework", "DevCon-NCERT")
                put("copyType", "shallow")
            }
        })
        request
    }

    private def getInvalidCopyRequest_2(): Request = {
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "Content")
                put("schemaName", "content")
                put("copyScheme", "TextBookToCourse")
            }
        })
        request.setObjectType("Content")
        request.putAll(new util.HashMap[String, AnyRef]() {
            {
                put("createdFor", new util.ArrayList[String]() {
                    {
                        add("Ekstep")
                    }
                })
                put("organisation", new util.ArrayList[String]() {
                    {
                        add("ekstep")
                    }
                })
                put("framework", "DevCon-NCERT")
            }
        })
        request
    }

    private def getInvalidCopyRequest_3(): Request = {
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "Content")
                put("schemaName", "content")
                put("copyScheme", "TextBookToCurriculumCourse")
            }
        })
        request.setObjectType("Content")
        request.putAll(new util.HashMap[String, AnyRef]() {
            {
                put("createdBy", "EkStep")
                put("createdFor", new util.ArrayList[String]() {
                    {
                        add("Ekstep")
                    }
                })
                put("organisation", new util.ArrayList[String]() {
                    {
                        add("ekstep")
                    }
                })
                put("framework", "DevCon-NCERT")
            }
        })
        request
    }

    private def getValidCopyRequest_1(): Request = {
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "Content")
                put("schemaName", "content")
                put("copyScheme", "TextBookToCourse")
            }
        })
        request.setObjectType("Content")
        request.putAll(new util.HashMap[String, AnyRef]() {
            {
                put("createdBy", "EkStep")
                put("createdFor", new util.ArrayList[String]() {
                    {
                        add("Ekstep")
                    }
                })
                put("organisation", new util.ArrayList[String]() {
                    {
                        add("ekstep")
                    }
                })
                put("framework", "DevCon-NCERT")
            }
        })
        request
    }

    def getDefinitionNode_channel(): Node = {
        val node = new Node()
        node.setIdentifier("obj-cat:learning-resource_content_in.ekstep")
        node.setNodeType("DATA_NODE")
        node.setObjectType("Content")
        node.setGraphId("domain")
        node.setMetadata(mapAsJavaMap(
            ScalaJsonUtils.deserialize[Map[String,AnyRef]]("{\n    \"objectCategoryDefinition\": {\n      \"name\": \"Learning Resource\",\n      \"description\": \"Content Playlist\",\n      \"categoryId\": \"obj-cat:learning-resource\",\n      \"targetObjectType\": \"Content\",\n      \"objectMetadata\": {\n        \"config\": {},\n        \"schema\": {\n          \"required\": [\n            \"author\",\n            \"copyright\",\n        \"audience\"\n          ],\n          \"properties\": {\n            \"audience\": {\n              \"type\": \"array\",\n              \"items\": {\n                \"type\": \"string\",\n                \"enum\": [\n                  \"Student\",\n                  \"Teacher\"\n                ]\n              },\n              \"default\": [\n                \"Student\"\n              ]\n            },\n            \"mimeType\": {\n              \"type\": \"string\",\n              \"enum\": [\n                \"application/pdf\"\n              ]\n            }\n          }\n        }\n      }\n    }\n  }")))
        node
    }

    def getDefinitionNode(): Node = {
        val node = new Node()
        node.setIdentifier("obj-cat:learning-resource_content_all")
        node.setNodeType("DATA_NODE")
        node.setObjectType("Content")
        node.setGraphId("domain")
        node.setMetadata(mapAsJavaMap(
            ScalaJsonUtils.deserialize[Map[String,AnyRef]]("{\n    \"objectCategoryDefinition\": {\n      \"name\": \"Learning Resource\",\n      \"description\": \"Content Playlist\",\n      \"categoryId\": \"obj-cat:learning-resource\",\n      \"targetObjectType\": \"Content\",\n      \"objectMetadata\": {\n        \"config\": {},\n        \"schema\": {\n          \"required\": [\n            \"author\",\n            \"copyright\",\n         \"audience\"\n          ],\n          \"properties\": {\n            \"audience\": {\n              \"type\": \"array\",\n              \"items\": {\n                \"type\": \"string\",\n                \"enum\": [\n                  \"Student\",\n                  \"Teacher\"\n                ]\n              },\n              \"default\": [\n                \"Student\"\n              ]\n            },\n            \"mimeType\": {\n              \"type\": \"string\",\n              \"enum\": [\n                \"application/pdf\"\n              ]\n            }\n          }\n        }\n      }\n    }\n  }")))
        node
    }
}
