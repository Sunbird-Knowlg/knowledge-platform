package org.sunbird.graph.nodes


import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.JsonUtils
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException}
import org.sunbird.graph.BaseSpec
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.utils.ScalaJsonUtils

import java.util
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
    "createNode with fetching FrameworkMasterCategoryMap from DB" should "create a node successfully" in {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())

        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("primaryCategory", "Learning Resource")
        val future: Future[Node] = DataNode.create(request,dataModifier)
        future map {node => {assert(null != node)
            print("Create Node - Fetch Category from DB: " + node)

            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            assert(node.getMetadata.get("trackable").asInstanceOf[String].contains("{\"enabled\":\"No\",\"autoBatch\":\"No\"}"))
            assert(node.getMetadata.get("contentType").asInstanceOf[String].equalsIgnoreCase("Resource"))}}
    }

    "createNode with enriching FrameworkMasterCategoryMap" should "create a node successfully" in {
        enrichFrameworkMasterCategoryMap()
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())

        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("primaryCategory", "Learning Resource")
        val future: Future[Node] = DataNode.create(request,dataModifier)
        future map {node => {assert(null != node)
            print(node)
            
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            assert(node.getMetadata.get("trackable").asInstanceOf[String].contains("{\"enabled\":\"No\",\"autoBatch\":\"No\"}"))
            assert(node.getMetadata.get("contentType").asInstanceOf[String].equalsIgnoreCase("Resource"))}}
    }

    "createNode with relation" should "create a node successfully" ignore {
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
        request.put("primaryCategory", "Learning Resource")
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
        request.put("primaryCategory", "Learning Resource")
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
        request.put("primaryCategory", "Learning Resource")
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
        request.put("primaryCategory", "Learning Resource")
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
        request.put("primaryCategory", "Learning Resource")
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
        request.put("primaryCategory", "Learning Resource")
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
        request.put("primaryCategory", "Learning Resource")
        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            // Gremlin: Create concept node
            g.addV("domain").property("IL_UNIQUE_ID", "Num:C3:SC2").property("identifier", "Num:C3:SC2").property("name", "Multiplication").property("IL_FUNC_OBJECT_TYPE", "Concept").property("IL_SYS_NODE_TYPE", "DATA_NODE").next()
            g.tx().commit()

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
                assert(node.getOutRelations.size() == 1)
                assert(node.getOutRelations.get(0).getEndNodeId.equalsIgnoreCase("Num:C3:SC2"))
            }
            }
        }
        } flatMap(f => f)
    }

    "update live node with external props" should "update node" in {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("primaryCategory", "Learning Resource")
        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            val req = new Request(request)
            req.getContext.put("identifier", node.getIdentifier)
            req.put("versionKey", node.getMetadata.get("versionKey"))
            req.put("name", "updated name")
            req.put("body", "body")
            val updateFuture = DataNode.update(req)
            updateFuture map { node => {
                assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("updated name"))
                assert(node.getMetadata.get("body").asInstanceOf[String].equalsIgnoreCase("body"))
            }}
        }} flatMap(f => f)
    }

    "update live collection with external props" should "update node" in {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/vnd.ekstep.content-collection")
        request.put("contentType", "TextBook")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("primaryCategory", "Learning Resource")
        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            val req = new Request(request)
            req.getContext.put("identifier", node.getIdentifier)
            req.put("versionKey", node.getMetadata.get("versionKey"))
            req.put("name", "updated name")
            req.put("body", "body")
            val updateFuture = DataNode.update(req)
            updateFuture map { node => {
                assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("updated name"))
                assert(node.getMetadata.get("body").asInstanceOf[String].equalsIgnoreCase("body"))
            }}
        }} flatMap(f => f)
    }

    "read Live node twice one from neo4j and one from cache" should "return node" in {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("primaryCategory", "Learning Resource")
        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            val req = new Request(request)
            req.getContext.put("identifier", node.getIdentifier)
            val readFuture = DataNode.read(req)
            readFuture map { node => {
                assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
                val readFuture2 = DataNode.read(req)
                readFuture2 map { node2 => {
                    assert(node2.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
                }}
            }}
        }} flatMap(f => f) flatMap(f => f)
    }

    "bulkUpdate with single node" should "update node successfully" in {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("primaryCategory", "Learning Resource")
        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            val req = new Request(request)
            req.getContext.put("identifiers", new util.ArrayList[String](){{ add(node.getIdentifier) }})
            req.put("metadata", new util.HashMap[String, AnyRef](){{ put("name", "updated name") }})
            val updateFuture = DataNode.bulkUpdate(req)
            updateFuture map { map => {
                assert(map.get(node.getIdentifier).getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("updated name"))
            }}
        }} flatMap(f => f) flatMap(f => f)
    }

    "update content with valid relations having type assosiatedTo and hasSequenceMember" should "update node with relation" in {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("primaryCategory", "Learning Resource")
        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            // Gremlin: Create related nodes
            g.addV("domain").property("IL_UNIQUE_ID", "do_123").property("identifier", "do_123").property("IL_FUNC_OBJECT_TYPE", "Content").property("IL_SYS_NODE_TYPE", "DATA_NODE").next()
            g.addV("domain").property("IL_UNIQUE_ID", "do_234").property("identifier", "do_234").property("IL_FUNC_OBJECT_TYPE", "Content").property("IL_SYS_NODE_TYPE", "DATA_NODE").next()
            g.tx().commit()

            val req = new Request(request)
            req.getContext.put("identifier", node.getIdentifier)
            req.put("name", "updated name")
            req.put("collections", new util.ArrayList[util.Map[String, AnyRef]](){{
                add(new util.HashMap[String, AnyRef](){{ put("identifier", "do_123"); put("relation", "hasSequenceMember") }})
                add(new util.HashMap[String, AnyRef](){{ put("identifier", "do_234"); put("relation", "associatedTo") }})
            }})
            val updateFuture = DataNode.update(req)
            updateFuture map { node => {
                assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("updated name"))
                assert(node.getOutRelations.size() == 2)
            }
            }
        }
        } flatMap(f => f)
    }

    "update content with valid relations having known relation(default)" should "update node with relation" in {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("primaryCategory", "Learning Resource")
        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            // Gremlin: Create related node
            g.addV("domain").property("IL_UNIQUE_ID", "do_897").property("identifier", "do_897").property("IL_FUNC_OBJECT_TYPE", "Content").property("IL_SYS_NODE_TYPE", "DATA_NODE").next()
            g.tx().commit()

            val req = new Request(request)
            req.getContext.put("identifier", node.getIdentifier)
            req.put("name", "updated name")
            req.put("concepts", new util.ArrayList[util.Map[String, AnyRef]](){{
                add(new util.HashMap[String, AnyRef](){{ put("identifier", "do_897"); put("relation", "associatedTo") }})
            }})
            val updateFuture = DataNode.update(req)
            updateFuture map { node => {
                assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("updated name"))
                assert(node.getOutRelations.size() == 1)
            }
            }
        }
        } flatMap(f => f)
    }

    "search should read data for all identifier" should "return list of nodes" in {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("primaryCategory", "Learning Resource")
        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            val req = new Request(request)
            val ids = new util.ArrayList[String](){{ add(node.getIdentifier) }}
            req.put("identifiers", ids)
            req.put("identifier", ids)
            val searchFuture = DataNode.search(req)
            searchFuture map { nodes => {
                assert(nodes.size == 1)
                assert(nodes.head.getIdentifier.equalsIgnoreCase(node.getIdentifier))
            }}
        }} flatMap(f => f) flatMap(f => f)
    }

    "search should throw Exception for invalid identifier" should "throw exception" in {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("primaryCategory", "Learning Resource")
        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            val req = new Request(request)
            val ids = new util.ArrayList[String](){{ add(node.getIdentifier); add("invalid_id") }}
            req.put("identifiers", ids)
            req.put("identifier", ids)
            recoverToSucceededIf[ClientException](DataNode.search(req))
        }} flatMap(f => f) flatMap(f => f)
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
        request.put("primaryCategory", "Learning Resource")
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





    "update content with valid data and a metadata with list of objects" should "update node" in {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())

        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("primaryCategory", "Learning Resource")
        val contentCredits = new util.ArrayList[AnyRef]() {
            {
                add(new util.HashMap[String, AnyRef]() {
                    {
                        put("id", "12345");
                        put("name", "user1");
                        put("type", "user");
                    }
                });
            }
        }
        request.put("contentCredits", contentCredits)

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



    "bulkUpdate with multiple node" should "should update all node successfully" ignore {
        createBulkNodes()
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("identifiers", new util.ArrayList[String]() {
            {
                add("do_0000123"); add("do_0000234"); add("do_0000345")
            }
        })
        request.put("metadata", new util.HashMap[String, AnyRef]() {
            {
                put("status", "Live")
                put("IL_FUNC_OBJECT_TYPE", "Content")
            }
        })
        val future: Future[util.Map[String, Node]] = DataNode.bulkUpdate(request)
        future map { data => {
            assert(null != data)
            assert(data.size() == 3)
        }
        }
    }







    "createNode with invalid metadata" should "throw client error" in {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())

        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        //TODO: Uncomment this line when schema_restrict_api is true
//        request.put("test", "test")
        //TODO: Remove this when schema_restrict_api is true
        request.put("ownershipType", "test")

        request.put("primaryCategory", "Learning Resource")
        assertThrows[ClientException](DataNode.create(request))
        // recoverToSucceededIf[ClientException](DataNode.create(request))
    }

    "update content with invalid data" should "throw client error" in {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())

        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("primaryCategory", "Learning Resource")
        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            val req = new Request(request)
            req.getContext.put("identifier", node.getIdentifier)
            req.put("name", "updated name")
            //TODO: Uncomment this line when schema_restrict_api is true
//            req.put("test", "test")
//            assertThrows[ClientException](DataNode.update(req))
//            recoverToSucceededIf[ClientException](DataNode.update(req))
            //TODO: Remove this when schema_restrict_api is true
            DataNode.update(req).map(node =>
                assert(node != null)
            )}
        } flatMap(f => f)
    }

    "update content with map external props" should "update node" in {
        val request = new Request()
        request.setObjectType("ObjectCategoryDefinition")
        request.setContext( new util.HashMap[String, AnyRef](){{
            put("graph_id", "domain")
            put("version" , "1.0")
            put("objectType" , "ObjectCategoryDefinition")
            put("schemaName", "objectcategorydefinition")
        }})
        request.put("name", "OK")
        request.put("categoryId", "obj-cat:ok")
        request.put("targetObjectType", "Content")
        request.put("objectMetadata", new util.HashMap[String, AnyRef]() {{
            put("config",  new util.HashMap[String, AnyRef](){{
                put("key", "value")
            }})
        }})

        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("OK"))
            val req = new Request(request)
            req.getContext.put("identifier", node.getIdentifier)
            req.put("objectMetadata", new util.HashMap[String, AnyRef]() {{
                put("schema",  new util.HashMap[String, AnyRef](){{
                    put("key", "value")
                }})
            }})
            val updateFuture = DataNode.update(req)
            updateFuture map { node => {
                assert(node.getExternalData.get("objectMetadata") != null)
            }
            }
        }
        } flatMap(f => f)
    }





    "update node with valid data and delete some unwanted data" should "update node" in {
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())

        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("primaryCategory", "Learning Resource")
        request.put("semanticVersion", "1.0")
        request.put("programId", "test_prog")
        val future: Future[Node] = DataNode.create(request)
        future map { node => {
            assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            val req = new Request()
            req.setContext(getContextMap())
            req.getContext.put("identifier", node.getIdentifier)
            val propsList: util.List[String] = new util.ArrayList[String](){{
                add("semanticVersion")
                add("programId")
            }}
            req.getContext.put("removeProps", propsList)
            req.put("name", "updated name")
            val updateFuture = DataNode.update(req)
            updateFuture map { node => {
                assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("updated name"))
                assert(null == node.getMetadata.get("semanticVersion"))
                assert(null == node.getMetadata.get("programId"))
            }
            }
        }
        } flatMap (f => f)
    }

    def getHierarchy(request: Request) : Future[Response] = {
        val hierarchyString: String = "'{\"identifier\": \"do_11283193441064550414\"}'"
        val rootHierarchy = JsonUtils.deserialize(hierarchyString, classOf[java.util.Map[String, AnyRef]])
        Future(ResponseHandler.OK.put("questionSet", rootHierarchy))
    }

    def dataModifier(node: Node): Node = {
        if(node.getMetadata.containsKey("trackable") &&
                node.getMetadata.getOrDefault("trackable", new java.util.HashMap[String, AnyRef]).asInstanceOf[java.util.Map[String, AnyRef]].containsKey("enabled") &&
                "Yes".equalsIgnoreCase(node.getMetadata.getOrDefault("trackable", new java.util.HashMap[String, AnyRef]).asInstanceOf[java.util.Map[String, AnyRef]].getOrDefault("enabled", "").asInstanceOf[String])) {
            node.getMetadata.put("contentType", "Course")
        }
        node
    }
}
