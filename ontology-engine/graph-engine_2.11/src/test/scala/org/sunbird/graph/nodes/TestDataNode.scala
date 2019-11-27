package org.sunbird.graph.nodes

import java.util

import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ResourceNotFoundException
import org.sunbird.graph.BaseSpec
import org.sunbird.graph.dac.model.Node

import scala.concurrent.Future

class TestDataNode extends BaseSpec {

    def getContextMap(): java.util.Map[String, AnyRef] = {
        new util.HashMap[String, AnyRef](){{
            put("graph_id", "domain")
            put("version" , "1.0")
            put("objectType" , "Content")
            put("schemaName", "content")
        }}
    }
    "createNode" should "create a node successfully" in {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())

        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))}}
    }


    "createNode with relation" should "create a node successfully" in {
        createRelationData()
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("concepts", new util.ArrayList[util.Map[String, AnyRef]](){{
            add(new util.HashMap[String, AnyRef](){{
                put("identifier", "Num:C3:SC2")
            }})
        }})
        request.put("children", new util.ArrayList[util.Map[String, AnyRef]](){{
            add(new util.HashMap[String, AnyRef](){{
                put("identifier", "do_11232724509261824014")
            }})
        }})

        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            assert(!node.getOutRelations.isEmpty)
            assert(node.getOutRelations.get(0).getEndNodeId.equalsIgnoreCase("Num:C3:SC2"))
        }}
    }

    "createNode with external properties" should "create a node successfully" in {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("body", "body")
        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))}}
    }


    "createNode with invalid relation" should "throw resource not found exception" in  {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("concepts", new util.ArrayList[util.Map[String, AnyRef]](){{
            add(new util.HashMap[String, AnyRef](){{
                put("identifier", "invalidConcept")
            }})
        }})

        recoverToSucceededIf[ResourceNotFoundException](DataNode.create(request))
    }

}
