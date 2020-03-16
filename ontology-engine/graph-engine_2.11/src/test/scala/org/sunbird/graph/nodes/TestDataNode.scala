package org.sunbird.graph.nodes

import java.util

import org.neo4j.graphdb.Result
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException}
import org.sunbird.graph.{BaseSpec, OntologyEngineContext}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.utils.ScalaJsonUtils

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
        executeNeo4jQuery("CREATE (n:domain{IL_UNIQUE_ID:'rel_content_0000000001',IL_FUNC_OBJECT_TYPE:'Content',status:'Live'});")
        executeNeo4jQuery("CREATE (n:domain{IL_UNIQUE_ID:'rel_concept_0000000001',IL_FUNC_OBJECT_TYPE:'Concept',status:'Live'});")
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("children", new util.ArrayList[util.Map[String, AnyRef]](){{
            add(new util.HashMap[String, AnyRef](){{
                put("identifier", "rel_content_0000000001")
            }})
        }})
        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            val req = new Request(request)
            req.getContext.put("identifier", node.getIdentifier)
            req.put("name", "updated name")
            req.put("concepts", new util.ArrayList[util.Map[String, AnyRef]](){{
                add(new util.HashMap[String, AnyRef](){{
                    put("identifier", "rel_concept_0000000001")
                }})
            }})
            val updateFuture = DataNode.update(req)
            updateFuture.map(node => {
                val readRequest = new Request(request)
                readRequest.put("identifier", node.getIdentifier)
                DataNode.read(readRequest).map(node => {
                    assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("updated name"))
                    assert(node.getOutRelations.size() == 2)
                })
            }) flatMap(f => f)
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

    "update live node with external props" should "update image node with existing external props in image node" in {
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],copyright:\"Sunbird\",previewUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_1129067102240194561252-latest\",keywords:[\"Test\"],plugins:\"[{\\\"identifier\\\":\\\"org.ekstep.stage\\\",\\\"semanticVersion\\\":\\\"1.0\\\"},{\\\"identifier\\\":\\\"org.ekstep.shape\\\",\\\"semanticVersion\\\":\\\"1.0\\\"},{\\\"identifier\\\":\\\"org.ekstep.text\\\",\\\"semanticVersion\\\":\\\"1.2\\\"},{\\\"identifier\\\":\\\"org.ekstep.image\\\",\\\"semanticVersion\\\":\\\"1.1\\\"},{\\\"identifier\\\":\\\"org.ekstep.navigation\\\",\\\"semanticVersion\\\":\\\"1.0\\\"}]\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1129067102240194561252/test-g-kp-2.0-001_1575527499420_do_1129067102240194561252_2.0.ecar\",channel:\"b00bc992ef25f1a9a8d63291e20efc8d\",organisation:[\"Sunbird\"],language:[\"English\"],variants:\"{\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1129067102240194561252/test-g-kp-2.0-001_1575527499615_do_1129067102240194561252_2.0_spine.ecar\\\",\\\"size\\\":36069.0}}\",mimeType:\"application/vnd.ekstep.ecml-archive\",editorState:\"{\\\"plugin\\\":{\\\"noOfExtPlugins\\\":7,\\\"extPlugins\\\":[{\\\"plugin\\\":\\\"org.ekstep.contenteditorfunctions\\\",\\\"version\\\":\\\"1.2\\\"},{\\\"plugin\\\":\\\"org.ekstep.keyboardshortcuts\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.richtext\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.iterator\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.navigation\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.reviewercomments\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.questionunit.ftb\\\",\\\"version\\\":\\\"1.1\\\"}]},\\\"stage\\\":{\\\"noOfStages\\\":5,\\\"currentStage\\\":\\\"c5ead48c-d574-488b-80d0-6d7db2d60637\\\",\\\"selectedPluginObject\\\":\\\"5b6a5e3d-5e44-4254-8c70-d82d6c13cc2c\\\"},\\\"sidebar\\\":{\\\"selectedMenu\\\":\\\"settings\\\"}}\",appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1129067102240194561252/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",assets:[\"do_112835334818643968148\"],appId:\"dev.sunbird.portal\",contentEncoding:\"gzip\",artifactUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1129067102240194561252/artifact/1575527499196_do_1129067102240194561252.zip\",lockKey:\"b7992ea7-f326-40d0-abdd-1601146bca84\",contentType:\"Resource\",lastUpdatedBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",audience:[\"Learner\"],visibility:\"Default\",consumerId:\"b3e90b00-1e9f-4692-9290-d014c20625f2\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"Ekstep\",version:2,pragma:[],prevState:\"Review\",license:\"CC BY 4.0\",lastPublishedOn:\"2019-12-05T06:31:39.415+0000\",size:74105,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"TEST-G-KP-2.0-001\",status:\"Live\",totalQuestions:0,code:\"org.sunbird.3qKh9v\",description:\"Test ECML Content\",streamingUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_1129067102240194561252-latest\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-12-05T06:09:10.490+0000\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-12-05T06:31:37.880+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-12-05T06:31:40.528+0000\",dialcodeRequired:\"No\",creator:\"Creation\",createdFor:[\"ORG_001\"],lastStatusChangedOn:\"2019-12-05T06:31:37.869+0000\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",totalScore:0,pkgVersion:2,versionKey:\"1575527498230\",idealScreenDensity:\"hdpi\",s3Key:\"ecar_files/do_1129067102240194561252/test-g-kp-2.0-001_1575527499420_do_1129067102240194561252_2.0.ecar\",lastSubmittedOn:\"2019-12-05T06:22:33.347+0000\",createdBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",compatibilityLevel:2,IL_UNIQUE_ID:\"do_1129067102240194561252\",resourceType:\"Learn\"}] as row CREATE (n:domain) SET n += row")
        executeCassandraQuery("INSERT into content_store.content_data(content_id, body) values('do_1129067102240194561252', textAsBlob('body'));")
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.getContext.put("identifier", "do_1129067102240194561252")
        request.put("name", "updated name")
        request.put("versionKey", "1575527498230")
        val updateFuture = DataNode.update(request)
        updateFuture.map(node => {
            assert(node.getIdentifier.equalsIgnoreCase("do_1129067102240194561252.img"))
            val resultSet = session.execute("select blobAsText(body) as body from content_store.content_data where content_id='do_1129067102240194561252.img'")
            assert(resultSet.one().getString("body").equalsIgnoreCase("body"))
            val result: Result = graphDb.execute("Match (n:domain{IL_UNIQUE_ID:'do_1129067102240194561252.img'}) return n.status as status, n.prevStatus as prevStatus")
            val resMap = result.next()
            assert("Draft".contentEquals(resMap.get("status").asInstanceOf[String]))
            assert("Live".contentEquals(resMap.get("prevStatus").asInstanceOf[String]))
        })
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

    "read Live node twice one from neo4j and one from cache" should "read node from neo4j and from cache" in {
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],copyright:\"Sunbird\",previewUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_1129067102240194561252-latest\",keywords:[\"Test\"],plugins:\"[{\\\"identifier\\\":\\\"org.ekstep.stage\\\",\\\"semanticVersion\\\":\\\"1.0\\\"},{\\\"identifier\\\":\\\"org.ekstep.shape\\\",\\\"semanticVersion\\\":\\\"1.0\\\"},{\\\"identifier\\\":\\\"org.ekstep.text\\\",\\\"semanticVersion\\\":\\\"1.2\\\"},{\\\"identifier\\\":\\\"org.ekstep.image\\\",\\\"semanticVersion\\\":\\\"1.1\\\"},{\\\"identifier\\\":\\\"org.ekstep.navigation\\\",\\\"semanticVersion\\\":\\\"1.0\\\"}]\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1129067102240194561252/test-g-kp-2.0-001_1575527499420_do_1129067102240194561252_2.0.ecar\",channel:\"b00bc992ef25f1a9a8d63291e20efc8d\",organisation:[\"Sunbird\"],language:[\"English\"],variants:\"{\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1129067102240194561252/test-g-kp-2.0-001_1575527499615_do_1129067102240194561252_2.0_spine.ecar\\\",\\\"size\\\":36069.0}}\",mimeType:\"application/vnd.ekstep.ecml-archive\",editorState:\"{\\\"plugin\\\":{\\\"noOfExtPlugins\\\":7,\\\"extPlugins\\\":[{\\\"plugin\\\":\\\"org.ekstep.contenteditorfunctions\\\",\\\"version\\\":\\\"1.2\\\"},{\\\"plugin\\\":\\\"org.ekstep.keyboardshortcuts\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.richtext\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.iterator\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.navigation\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.reviewercomments\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.questionunit.ftb\\\",\\\"version\\\":\\\"1.1\\\"}]},\\\"stage\\\":{\\\"noOfStages\\\":5,\\\"currentStage\\\":\\\"c5ead48c-d574-488b-80d0-6d7db2d60637\\\",\\\"selectedPluginObject\\\":\\\"5b6a5e3d-5e44-4254-8c70-d82d6c13cc2c\\\"},\\\"sidebar\\\":{\\\"selectedMenu\\\":\\\"settings\\\"}}\",appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1129067102240194561252/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",assets:[\"do_112835334818643968148\"],appId:\"dev.sunbird.portal\",contentEncoding:\"gzip\",artifactUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1129067102240194561252/artifact/1575527499196_do_1129067102240194561252.zip\",lockKey:\"b7992ea7-f326-40d0-abdd-1601146bca84\",contentType:\"Resource\",lastUpdatedBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",audience:[\"Learner\"],visibility:\"Default\",consumerId:\"b3e90b00-1e9f-4692-9290-d014c20625f2\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"Ekstep\",version:2,pragma:[],prevState:\"Review\",license:\"CC BY 4.0\",lastPublishedOn:\"2019-12-05T06:31:39.415+0000\",size:74105,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"TEST-G-KP-2.0-001\",status:\"Live\",totalQuestions:0,code:\"org.sunbird.3qKh9v\",description:\"Test ECML Content\",streamingUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_1129067102240194561252-latest\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-12-05T06:09:10.490+0000\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-12-05T06:31:37.880+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-12-05T06:31:40.528+0000\",dialcodeRequired:\"No\",creator:\"Creation\",createdFor:[\"ORG_001\"],lastStatusChangedOn:\"2019-12-05T06:31:37.869+0000\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",totalScore:0,pkgVersion:2,versionKey:\"1575527498230\",idealScreenDensity:\"hdpi\",s3Key:\"ecar_files/do_1129067102240194561252/test-g-kp-2.0-001_1575527499420_do_1129067102240194561252_2.0.ecar\",lastSubmittedOn:\"2019-12-05T06:22:33.347+0000\",createdBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",compatibilityLevel:2,IL_UNIQUE_ID:\"do_1129067102240194561252\",resourceType:\"Learn\", subject: [\"Hindi\"], framework:\"NCF\"}] as row CREATE (n:domain) SET n += row")
        createRelationData()
        graphDb.execute("MATCH (n:domain{IL_UNIQUE_ID:'do_1129067102240194561252'}) match (m:domain{IL_UNIQUE_ID:'Num:C3:SC2'}) CREATE (n)-[r:associatedTo]->(m)")
        graphDb.execute("MATCH (n:domain{IL_UNIQUE_ID:'do_1129067102240194561252'}) match (m:domain{IL_UNIQUE_ID:'do_11232724509261824014'}) CREATE (m)-[r:associatedTo]->(n)")
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("identifier", "do_1129067102240194561252")
        RedisCache.delete("do_1129067102240194561252")
        ScalaJsonUtils.deserialize("{\"IL_SYS_NODE_TYPE\":\"ROOT_NODE\",\"consumerId\":\"72e54829-6402-4cf0-888e-9b30733c1b88\",\"appId\":\"ekstep_portal\",\"channel\":\"in.ekstep\",\"lastUpdatedOn\":\"2018-02-28T13:18:01.346+0000\",\"IL_UNIQUE_ID\":\"do_ROOT_NODE\",\"versionKey\":\"1519823881346\"}")(manifest[Map[String, AnyRef]])
        val readFuture = DataNode.read(request)
        readFuture.map(node => {
            assert(node.getIdentifier.equalsIgnoreCase("do_1129067102240194561252"))
            assert(null != RedisCache.get("do_1129067102240194561252"))
            val readFromCache = DataNode.read(request)
            readFromCache.map(node => {
                assert(node.getIdentifier.equalsIgnoreCase("do_1129067102240194561252"))
            })
        }).flatMap(f => f)
    }

    "bulkUpdate with multiple node" should "should update all node successfully" in {
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

    "bulkUpdate with single node" should "should update the node successfully" in {
        executeNeo4jQuery("CREATE (n:domain{IL_UNIQUE_ID:'do_0000456'});")
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("identifiers", new util.ArrayList[String]() {
            {
                add("do_0000456");
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
            assert(data.size() == 1)
        }
        }
    }

    "update content with valid relations having type assosiatedTo and hasSequenceMember" should "update node with relation" in {
        executeNeo4jQuery("CREATE (n:domain{IL_UNIQUE_ID:'rel_concept_00000001',IL_FUNC_OBJECT_TYPE:'Concept',status:'Live'});")
        executeNeo4jQuery("CREATE (n:domain{IL_UNIQUE_ID:'rel_concept_00000002',IL_FUNC_OBJECT_TYPE:'Concept',status:'Live'});")
        executeNeo4jQuery("CREATE (n:domain{IL_UNIQUE_ID:'rel_itemset_00000001',IL_FUNC_OBJECT_TYPE:'ItemSet',status:'Live'});")
        executeNeo4jQuery("CREATE (n:domain{IL_UNIQUE_ID:'rel_collections_00000001',IL_FUNC_OBJECT_TYPE:'Content',status:'Live', contentType:'TextBook'});")
        executeNeo4jQuery("CREATE (n:domain{IL_UNIQUE_ID:'rel_collections_00000002',IL_FUNC_OBJECT_TYPE:'Content',status:'Live', contentType:'TextBook'});")
        executeNeo4jQuery("CREATE (n:domain{IL_UNIQUE_ID:'rel_collections_00000003',IL_FUNC_OBJECT_TYPE:'ContentImage',status:'Live', contentType:'TextBook'});")
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
                put("identifier", "rel_concept_00000001")
            }})
        }})
        request.put("collections", new util.ArrayList[util.Map[String, AnyRef]](){{
            add(new util.HashMap[String, AnyRef](){{
                put("identifier", "rel_collections_00000001")
            }})
        }})
        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            val req = new Request(request)
            req.getContext.put("identifier", node.getIdentifier)
            req.put("name", "updated name")
            req.put("concepts", new util.ArrayList[util.Map[String, AnyRef]](){{
                add(new util.HashMap[String, AnyRef](){{
                    put("identifier", "rel_concept_00000002")
                }})
            }})
            req.put("itemSets", new util.ArrayList[util.Map[String, AnyRef]](){{
                add(new util.HashMap[String, AnyRef](){{
                    put("identifier", "rel_itemset_00000001")
                }})
            }})
            req.put("collections", new util.ArrayList[util.Map[String, AnyRef]](){{
                add(new util.HashMap[String, AnyRef](){{
                    put("identifier", "rel_collections_00000002")
                }})
                add(new util.HashMap[String, AnyRef](){{
                    put("identifier", "rel_collections_00000003")
                }})
            }})
            val updateFuture = DataNode.update(req)
            updateFuture.map(node => {
                val readRequest = new Request(request)
                readRequest.put("identifier", node.getIdentifier)
                DataNode.read(readRequest).map(node => {
                    assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("updated name"))
                    assert(node.getOutRelations.size() == 2)
                    assert(node.getInRelations.size() == 2)
                })
            }) flatMap(f => f)
        }
        } flatMap(f => f)
    }

    "update content with valid relations having in direction" should "update node with relation" in {
        executeNeo4jQuery("CREATE (n:domain{IL_UNIQUE_ID:'rel_collections_0000000101',IL_FUNC_OBJECT_TYPE:'Content',status:'Live', contentType:'TextBook'});")
        executeNeo4jQuery("CREATE (n:domain{IL_UNIQUE_ID:'rel_collections_0000000102',IL_FUNC_OBJECT_TYPE:'Content',status:'Live', contentType:'TextBook'});")
        executeNeo4jQuery("CREATE (n:domain{IL_UNIQUE_ID:'rel_usedbycontent_0000000101',IL_FUNC_OBJECT_TYPE:'Content',status:'Live', IL_SYS_NODE_TYPE:'DATA_NODE', contentType:'TextBook'});")
        executeNeo4jQuery("CREATE (n:domain{IL_UNIQUE_ID:'rel_usedbycontent_0000000102',IL_FUNC_OBJECT_TYPE:'Content',status:'Live', IL_SYS_NODE_TYPE:'DATA_NODE',contentType:'TextBook'});")
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.put("code", "test")
        request.put("name", "testResource")
        request.put("mimeType", "application/pdf")
        request.put("contentType", "Resource")
        request.put("description", "test")
        request.put("channel", "in.ekstep")
        request.put("collections", new util.ArrayList[util.Map[String, AnyRef]](){{
            add(new util.HashMap[String, AnyRef](){{
                put("identifier", "rel_collections_0000000101")
            }})
            add(new util.HashMap[String, AnyRef](){{
                put("identifier", "rel_collections_0000000102")
            }})
        }})
        val future: Future[Node] = DataNode.create(request)
        future map {node => {assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            val req = new Request(request)
            req.getContext.put("identifier", node.getIdentifier)
            req.put("name", "updated name")
            req.put("usedByContent", new util.ArrayList[util.Map[String, AnyRef]](){{
                add(new util.HashMap[String, AnyRef](){{
                    put("identifier", "rel_usedbycontent_0000000101")
                }})
                add(new util.HashMap[String, AnyRef](){{
                    put("identifier", "rel_usedbycontent_0000000102")
                }})
            }})
            val updateFuture = DataNode.update(req)
            updateFuture.map(node => {
                val readRequest = new Request(request)
                readRequest.put("identifier", node.getIdentifier)
                DataNode.read(readRequest).map(node => {
                    assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("updated name"))
                    assert(node.getInRelations.size() == 4)
                })
            }) flatMap(f => f)
        }
        } flatMap(f => f)
    }
}
