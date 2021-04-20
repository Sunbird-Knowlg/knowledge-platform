package org.sunbird.graph.nodes

import java.util

import org.neo4j.graphdb.Result
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.JsonUtils
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
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
        request.put("primaryCategory", "Learning Resource")
        val future: Future[Node] = DataNode.create(request,dataModifier)
        future map {node => {assert(null != node)
            print(node)
            
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("testResource"))
            assert(node.getMetadata.get("trackable").asInstanceOf[String].contains("{\"enabled\":\"No\",\"autoBatch\":\"No\"}"))
            assert(node.getMetadata.get("contentType").asInstanceOf[String].equalsIgnoreCase("Resource"))}}
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
        request.put("primaryCategory", "Learning Resource")
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

    "update live node with external props" should "update image node with existing external props in image node" in {
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],copyright:\"Sunbird\",previewUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_1129067102240194561252-latest\",keywords:[\"Test\"],plugins:\"[{\\\"identifier\\\":\\\"org.ekstep.stage\\\",\\\"semanticVersion\\\":\\\"1.0\\\"},{\\\"identifier\\\":\\\"org.ekstep.shape\\\",\\\"semanticVersion\\\":\\\"1.0\\\"},{\\\"identifier\\\":\\\"org.ekstep.text\\\",\\\"semanticVersion\\\":\\\"1.2\\\"},{\\\"identifier\\\":\\\"org.ekstep.image\\\",\\\"semanticVersion\\\":\\\"1.1\\\"},{\\\"identifier\\\":\\\"org.ekstep.navigation\\\",\\\"semanticVersion\\\":\\\"1.0\\\"}]\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1129067102240194561252/test-g-kp-2.0-001_1575527499420_do_1129067102240194561252_2.0.ecar\",channel:\"b00bc992ef25f1a9a8d63291e20efc8d\",organisation:[\"Sunbird\"],language:[\"English\"],variants:\"{\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1129067102240194561252/test-g-kp-2.0-001_1575527499615_do_1129067102240194561252_2.0_spine.ecar\\\",\\\"size\\\":36069.0}}\",mimeType:\"application/vnd.ekstep.ecml-archive\",editorState:\"{\\\"plugin\\\":{\\\"noOfExtPlugins\\\":7,\\\"extPlugins\\\":[{\\\"plugin\\\":\\\"org.ekstep.contenteditorfunctions\\\",\\\"version\\\":\\\"1.2\\\"},{\\\"plugin\\\":\\\"org.ekstep.keyboardshortcuts\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.richtext\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.iterator\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.navigation\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.reviewercomments\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.questionunit.ftb\\\",\\\"version\\\":\\\"1.1\\\"}]},\\\"stage\\\":{\\\"noOfStages\\\":5,\\\"currentStage\\\":\\\"c5ead48c-d574-488b-80d0-6d7db2d60637\\\",\\\"selectedPluginObject\\\":\\\"5b6a5e3d-5e44-4254-8c70-d82d6c13cc2c\\\"},\\\"sidebar\\\":{\\\"selectedMenu\\\":\\\"settings\\\"}}\",appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1129067102240194561252/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",assets:[\"do_112835334818643968148\"],appId:\"dev.sunbird.portal\",contentEncoding:\"gzip\",artifactUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1129067102240194561252/artifact/1575527499196_do_1129067102240194561252.zip\",lockKey:\"b7992ea7-f326-40d0-abdd-1601146bca84\",contentType:\"Resource\",lastUpdatedBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",audience:[\"Student\"],visibility:\"Default\",consumerId:\"b3e90b00-1e9f-4692-9290-d014c20625f2\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"Ekstep\",version:2,pragma:[],prevState:\"Review\",license:\"CC BY 4.0\",lastPublishedOn:\"2019-12-05T06:31:39.415+0000\",size:74105,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"TEST-G-KP-2.0-001\",status:\"Live\",totalQuestions:0,code:\"org.sunbird.3qKh9v\",description:\"Test ECML Content\",streamingUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_1129067102240194561252-latest\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-12-05T06:09:10.490+0000\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-12-05T06:31:37.880+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-12-05T06:31:40.528+0000\",dialcodeRequired:\"No\",creator:\"Creation\",createdFor:[\"ORG_001\"],lastStatusChangedOn:\"2019-12-05T06:31:37.869+0000\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",totalScore:0,pkgVersion:2,versionKey:\"1575527498230\",idealScreenDensity:\"hdpi\",s3Key:\"ecar_files/do_1129067102240194561252/test-g-kp-2.0-001_1575527499420_do_1129067102240194561252_2.0.ecar\",lastSubmittedOn:\"2019-12-05T06:22:33.347+0000\",createdBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",compatibilityLevel:2,IL_UNIQUE_ID:\"do_1129067102240194561252\",resourceType:\"Learn\"}] as row CREATE (n:domain) SET n += row")
        executeCassandraQuery("INSERT into content_store.content_data(content_id, body) values('do_1129067102240194561252', textAsBlob('body'));")
        val request = new Request()
        request.setObjectType("Content")
        request.setContext(getContextMap())
        request.getContext.put("identifier", "do_1129067102240194561252")
        request.put("name", "updated name")
        request.put("versionKey", "1575527498230")
        request.put("primaryCategory", "Learning Resource")
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

    "update live collection with external props" should "update image node with existing external props in image node for collection" in {
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],copyright:\"Sunbird\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550414/test-prad-course-cert_1566398313947_do_11283193441064550414_1.0_spine.ecar\",channel:\"b00bc992ef25f1a9a8d63291e20efc8d\",organisation:[\"Sunbird\"],language:[\"English\"],variants:\"{\\\"online\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550414/test-prad-course-cert_1566398314186_do_11283193441064550414_1.0_online.ecar\\\",\\\"size\\\":4034.0},\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550414/test-prad-course-cert_1566398313947_do_11283193441064550414_1.0_spine.ecar\\\",\\\"size\\\":73256.0}}\",mimeType:\"application/vnd.ekstep.content-collection\",leafNodes:[\"do_112831862871203840114\"],c_sunbird_dev_private_batch_count:0,appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11283193441064550414/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",appId:\"local.sunbird.portal\",contentEncoding:\"gzip\",lockKey:\"b079cf15-9e45-4865-be56-2edafa432dd3\",mimeTypesCount:\"{\\\"application/vnd.ekstep.content-collection\\\":1,\\\"video/mp4\\\":1}\",totalCompressedSize:416488,contentType:\"Course\",primaryCategory:\"Course\",lastUpdatedBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",audience:[\"Student\"],toc_url:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11283193441064550414/artifact/do_11283193441064550414_toc.json\",visibility:\"Default\",contentTypesCount:\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":1}\",author:\"b00bc992ef25f1a9a8d63291e20efc8d\",childNodes:[\"do_11283193463014195215\"],consumerId:\"273f3b18-5dda-4a27-984a-060c7cd398d3\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"System\",version:2,license:\"CC BY-NC 4.0\",prevState:\"Draft\",size:73256,lastPublishedOn:\"2019-08-21T14:38:33.816+0000\",IL_FUNC_OBJECT_TYPE:\"Collection\",name:\"test prad course cert\",status:\"Live\",code:\"org.sunbird.SUi47U\",description:\"Enter description for Course\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-08-21T14:37:23.486+0000\",reservedDialcodes:\"{\\\"I1X4R4\\\":0}\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-08-21T14:38:33.212+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-11-13T12:54:08.295+0000\",dialcodeRequired:\"No\",creator:\"Creation\",createdFor:[\"ORG_001\"],lastStatusChangedOn:\"2019-08-21T14:38:34.540+0000\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",pkgVersion:1,versionKey:\"1566398313212\",idealScreenDensity:\"hdpi\",dialcodes:[\"I1X4R4\"],s3Key:\"ecar_files/do_11283193441064550414/test-prad-course-cert_1566398313947_do_11283193441064550414_1.0_spine.ecar\",depth:0,framework:\"tpd\",me_averageRating:5,createdBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",leafNodesCount:1,compatibilityLevel:4,IL_UNIQUE_ID:\"do_11283193441064550414\",c_sunbird_dev_open_batch_count:0,resourceType:\"Course\"}] as row CREATE (n:domain) SET n += row")
        executeCassandraQuery("INSERT into hierarchy_store.content_hierarchy(identifier, hierarchy) values('do_11283193441064550414', '{\"identifier\": \"do_11283193441064550414\"}');")
        val request = new Request()
        request.setObjectType("Collection")
        request.setContext(getContextMap())
        request.getContext.put("identifier", "do_11283193441064550414")
        request.put("name", "updated name")
        request.put("versionKey", "1566389713020")
        request.put("primaryCategory", "Course")
        val updateFuture = DataNode.update(request)
        updateFuture.map(node => {
            assert(node.getIdentifier.equalsIgnoreCase("do_11283193441064550414.img"))
            val resultSet = session.execute("select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'")
            assert(resultSet.one().getString("hierarchy").equalsIgnoreCase("{\"identifier\": \"do_11283193441064550414\"}"))
            val result: Result = graphDb.execute("Match (n:domain{IL_UNIQUE_ID:'do_11283193441064550414.img'}) return n.status as status, n.prevStatus as prevStatus")
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

    "read Live node twice one from neo4j and one from cache" should "read node from neo4j and from cache" in {
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],copyright:\"Sunbird\",previewUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_1129067102240194561252-latest\",keywords:[\"Test\"],plugins:\"[{\\\"identifier\\\":\\\"org.ekstep.stage\\\",\\\"semanticVersion\\\":\\\"1.0\\\"},{\\\"identifier\\\":\\\"org.ekstep.shape\\\",\\\"semanticVersion\\\":\\\"1.0\\\"},{\\\"identifier\\\":\\\"org.ekstep.text\\\",\\\"semanticVersion\\\":\\\"1.2\\\"},{\\\"identifier\\\":\\\"org.ekstep.image\\\",\\\"semanticVersion\\\":\\\"1.1\\\"},{\\\"identifier\\\":\\\"org.ekstep.navigation\\\",\\\"semanticVersion\\\":\\\"1.0\\\"}]\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1129067102240194561252/test-g-kp-2.0-001_1575527499420_do_1129067102240194561252_2.0.ecar\",channel:\"b00bc992ef25f1a9a8d63291e20efc8d\",organisation:[\"Sunbird\"],language:[\"English\"],variants:\"{\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1129067102240194561252/test-g-kp-2.0-001_1575527499615_do_1129067102240194561252_2.0_spine.ecar\\\",\\\"size\\\":36069.0}}\",mimeType:\"application/vnd.ekstep.ecml-archive\",editorState:\"{\\\"plugin\\\":{\\\"noOfExtPlugins\\\":7,\\\"extPlugins\\\":[{\\\"plugin\\\":\\\"org.ekstep.contenteditorfunctions\\\",\\\"version\\\":\\\"1.2\\\"},{\\\"plugin\\\":\\\"org.ekstep.keyboardshortcuts\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.richtext\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.iterator\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.navigation\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.reviewercomments\\\",\\\"version\\\":\\\"1.0\\\"},{\\\"plugin\\\":\\\"org.ekstep.questionunit.ftb\\\",\\\"version\\\":\\\"1.1\\\"}]},\\\"stage\\\":{\\\"noOfStages\\\":5,\\\"currentStage\\\":\\\"c5ead48c-d574-488b-80d0-6d7db2d60637\\\",\\\"selectedPluginObject\\\":\\\"5b6a5e3d-5e44-4254-8c70-d82d6c13cc2c\\\"},\\\"sidebar\\\":{\\\"selectedMenu\\\":\\\"settings\\\"}}\",appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1129067102240194561252/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",assets:[\"do_112835334818643968148\"],appId:\"dev.sunbird.portal\",contentEncoding:\"gzip\",artifactUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1129067102240194561252/artifact/1575527499196_do_1129067102240194561252.zip\",lockKey:\"b7992ea7-f326-40d0-abdd-1601146bca84\",contentType:\"Resource\",lastUpdatedBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",audience:[\"Student\"],visibility:\"Default\",consumerId:\"b3e90b00-1e9f-4692-9290-d014c20625f2\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"Ekstep\",version:2,pragma:[],prevState:\"Review\",license:\"CC BY 4.0\",lastPublishedOn:\"2019-12-05T06:31:39.415+0000\",size:74105,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"TEST-G-KP-2.0-001\",status:\"Live\",totalQuestions:0,code:\"org.sunbird.3qKh9v\",description:\"Test ECML Content\",streamingUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_1129067102240194561252-latest\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-12-05T06:09:10.490+0000\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-12-05T06:31:37.880+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-12-05T06:31:40.528+0000\",dialcodeRequired:\"No\",creator:\"Creation\",createdFor:[\"ORG_001\"],lastStatusChangedOn:\"2019-12-05T06:31:37.869+0000\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",totalScore:0,pkgVersion:2,versionKey:\"1575527498230\",idealScreenDensity:\"hdpi\",s3Key:\"ecar_files/do_1129067102240194561252/test-g-kp-2.0-001_1575527499420_do_1129067102240194561252_2.0.ecar\",lastSubmittedOn:\"2019-12-05T06:22:33.347+0000\",createdBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",compatibilityLevel:2,IL_UNIQUE_ID:\"do_1129067102240194561252\",resourceType:\"Learn\", subject: [\"Hindi\"], framework:\"NCF\"}] as row CREATE (n:domain) SET n += row")
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
        request.put("primaryCategory", "Learning Resource")
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
        request.put("primaryCategory", "Learning Resource")
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

    "systemUpdate content with valid data" should "update node metadata" in {
        val request = new Request()
        request.setObjectType("question")
        val context = new util.HashMap[String, AnyRef]() {
            {
                put("graph_id", "domain")
                put("version", "1.0")
                put("objectType", "Question")
                put("schemaName", "question")
            }
        }
        request.setContext(context)
        request.put("code", "finemanfine")
        request.put("showFeedback", "Yes")
        request.put("showSolutions", "Yes")
        request.put("mimeType", "application/vnd.sunbird.question")
        request.put("primaryCategory", "Practice Question")
        request.put("name", "Test Question")
        request.put("visibility", "Default")
        request.put("description", "hey")

        val future: Future[Node] = DataNode.create(request)
        future map { node => {
            assert(null != node)
            print(node)
            assert(node.getMetadata.get("name").asInstanceOf[String].equalsIgnoreCase("Test Question"))
            val req = new Request(request)
            req.getContext.put("identifier", node.getIdentifier)
            req.put("name", "updated name")
            req.put("description", "Updated Description")
            val updateFuture = DataNode.systemUpdate(req, util.Arrays.asList(node), "", Option(getHierarchy))
            updateFuture map { node => {
                assert(node.getIdentifier.equals(node.getIdentifier))
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
