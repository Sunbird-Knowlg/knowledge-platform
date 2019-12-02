package org.sunbird.graph.nodes

import java.util

import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException}
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
        future map { node => {
            assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
        }
            val req = new Request(request)
            req.put("identifier", node.getIdentifier)
            req.put("fields", util.Arrays.asList("body"))
            val readFuture = DataNode.read(req)
            readFuture map { node => {
                assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
                assert(node.getMetadata.get("body").asInstanceOf[String].equalsIgnoreCase("body"))
            }
            }

        } flatMap (f => f)
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

    "update content with valid data" should "update node" in {
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
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            val req = new Request(request)
            req.getContext.put("identifier", node.getIdentifier)
            req.put("name", "updated name")
            val updateFuture = DataNode.update(req)
            updateFuture map { node => {
                    assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("updated name"))
                }
            }
            }
        } flatMap(f => f)
    }

    "update content with valid relation" should "update node with relation" in {
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
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            val req = new Request(request)
            req.getContext.put("identifier", node.getIdentifier)
            req.put("name", "updated name")
            req.put("concepts", new util.ArrayList[util.Map[String, AnyRef]](){{
                add(new util.HashMap[String, AnyRef](){{
                    put("identifier", "Num:C3:SC2")
                }})
            }})
            val updateFuture = DataNode.update(req)
            updateFuture map { node => {
                assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("updated name"))
                assert(node.getOutRelations.get(0).getEndNodeId().equalsIgnoreCase("Num:C3:SC2"))
            }
            }
        }
        } flatMap(f => f)
    }

    "update content with invalid versionKey" should "throw client exception" in {
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
        future map { node => {
            assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            val req = new Request(request)
            req.getContext.put("identifier", node.getIdentifier)
            req.put("name", "updated name")
            req.put("versionKey", "invalidVersionKey")
            recoverToSucceededIf[ClientException](DataNode.update(req))
        }
        }flatMap(f => f)
    }

    "update content with invalid relation" should "update node with relation" in {
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
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            val req = new Request(request)
            req.getContext.put("identifier", node.getIdentifier)
            req.put("name", "updated name")
            req.put("concepts", new util.ArrayList[util.Map[String, AnyRef]](){{
                add(new util.HashMap[String, AnyRef](){{
                    put("identifier", "invalidId")
                }})
            }})
            recoverToSucceededIf[ResourceNotFoundException](DataNode.update(req))
        }
        } flatMap(f => f)
    }

    "listNode" should "lists a node successfully" in {
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
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))}
            val req = new Request(request)
            req.put("identifiers", new util.ArrayList[String](){{ add(node.getIdentifier)}})
            val listFuture = DataNode.list(req)
            listFuture map { nodes => {
                assert(!nodes.isEmpty)
                assert(nodes.get(0).getIdentifier.equalsIgnoreCase(node.getIdentifier))
            }

            }
        }flatMap(f => f)
    }
}
