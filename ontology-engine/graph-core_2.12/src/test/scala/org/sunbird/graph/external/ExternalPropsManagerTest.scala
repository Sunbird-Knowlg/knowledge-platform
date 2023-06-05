package org.sunbird.graph.external

import java.util
import org.sunbird.graph.BaseSpec
import org.apache.commons.lang3.StringUtils
import org.scalatest.Ignore
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.ResponseCode

import scala.concurrent.Future
@Ignore
class ExternalPropsManagerTest extends BaseSpec {

    def getContextMap(): java.util.Map[String, AnyRef] = {
        new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "ObjectCategoryDefinition")
                put("schemaName", "objectcategorydefinition")
            }
        }
    }

    "saveProps" should "create a cassandra record successfully" in {
        val request = new Request()
        request.setObjectType("ObjectCategoryDefinition")
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "ObjectCategoryDefinition")
                put("schemaName", "objectcategorydefinition")
            }
        })
        request.put("identifier", "obj-cat:test_content_all")
        request.put("objectMetadata", new util.HashMap[String, AnyRef]() {
            {
                put("schema", "{\"properties\":{\"trackable\":{\"type\":\"object\",\"properties\":{\"enabled\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"},\"autoBatch\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"}},\"default\":{\"enabled\":\"Yes\",\"autoBatch\":\"Yes\"},\"additionalProperties\":false},\"monitorable\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"progress-report\",\"score-report\"]}},\"credentials\":{\"type\":\"object\",\"properties\":{\"enabled\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"}},\"default\":{\"enabled\":\"Yes\"},\"additionalProperties\":false},\"userConsent\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"}}}")
                put("config", "{}")
            }
        })

        val future: Future[Response] = ExternalPropsManager.saveProps(request)
        future map { response => {
            assert(null != response)
            assert(response.getResponseCode == ResponseCode.OK)
        }
        }
    }

    "fetchProps" should "read a cassandra record successfully" in {
        val request = new Request()
        request.setObjectType("ObjectCategoryDefinition")
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "ObjectCategoryDefinition")
                put("schemaName", "objectcategorydefinition")
            }
        })
        request.put("identifier", "obj-cat:course_collection_all")
        val future: Future[Response] = ExternalPropsManager.fetchProps(request, List("objectMetadata"))
        future map { response => {
            assert(null != response)
            assert(response.getResponseCode == ResponseCode.OK)
            assert(StringUtils.isNotBlank(response.getResult.get("objectMetadata").asInstanceOf[util.Map[String, AnyRef]].get("config").asInstanceOf[String]))
        }
        }
    }

    "fetchProps" should "read list cassandra record successfully" in {
        val request = new Request()
        request.setObjectType("ObjectCategoryDefinition")
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "ObjectCategoryDefinition")
                put("schemaName", "objectcategorydefinition")
            }
        })
        request.put("identifiers", List("obj-cat:course_collection_all"))
        val future: Future[Response] = ExternalPropsManager.fetchProps(request, List("objectMetadata"))
        future map { response => {
            assert(null != response)
            assert(response.getResponseCode == ResponseCode.OK)
            assert(StringUtils.isNotBlank(response.getResult.get("obj-cat:course_collection_all").asInstanceOf[util.Map[String, AnyRef]].get("objectMetadata").asInstanceOf[util.Map[String, AnyRef]].get("config").asInstanceOf[String]))
        }
        }
    }

    "deleteProps" should "delete a cassandra record successfully" in {
        val request = new Request()
        request.setObjectType("ObjectCategoryDefinition")
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "ObjectCategoryDefinition")
                put("schemaName", "objectcategorydefinition")
            }
        })
        request.put("identifiers", List("obj-cat:course_content_all"))
        val future: Future[Response] = ExternalPropsManager.deleteProps(request)
        future map { response => {
            assert(null != response)
            assert(response.getResponseCode == ResponseCode.OK)
        }
        }
    }

    "update" should "update a cassandra record successfully" in {
        val request = new Request()
        request.setObjectType("ObjectCategoryDefinition")
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "ObjectCategoryDefinition")
                put("schemaName", "objectcategorydefinition")
            }
        })
        val objectMetadata = new util.HashMap[String, AnyRef]() {{
            put("schema", "{\"properties\":{\"trackable\":{\"type\":\"object\",\"properties\":{\"enabled\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"},\"autoBatch\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"}},\"default\":{\"enabled\":\"Yes\",\"autoBatch\":\"Yes\"},\"additionalProperties\":false},\"monitorable\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"progress-report\",\"score-report\"]}},\"credentials\":{\"type\":\"object\",\"properties\":{\"enabled\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"}},\"default\":{\"enabled\":\"Yes\"},\"additionalProperties\":false},\"userConsent\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"}}}")
            put("config", "{}")
        }}
        request.put("identifier", "obj-cat:course_collection_all")
        request.put("fields", List("objectMetadata"))
        request.put("values", List(objectMetadata))
        val future: Future[Response] = ExternalPropsManager.update(request)
        future map { response => {
            assert(null != response)
            assert(response.getResponseCode == ResponseCode.OK)
        }
        }
    }

}


