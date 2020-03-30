package org.sunbird.managers

import java.util

import org.apache.commons.lang3.BooleanUtils
import org.parboiled.common.StringUtils
import org.sunbird.common.JsonUtils
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.utils.HierarchyConstants

class TestUpdateHierarchy extends BaseSpec {

    private val KEYSPACE_CREATE_SCRIPT = "CREATE KEYSPACE IF NOT EXISTS hierarchy_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
    private val TABLE_CREATE_SCRIPT = "CREATE TABLE IF NOT EXISTS hierarchy_store.content_hierarchy (identifier text, hierarchy text,PRIMARY KEY (identifier));"
    private val HIERARCHY_TO_MIGRATE_SCRIPT = "INSERT INTO hierarchy_store.content_hierarchy(identifier, hierarchy) values ('do_11283193441064550414.img', '{\"identifier\":\"do_11283193441064550414\",\"children\":[{\"parent\":\"do_11283193441064550414\",\"identifier\":\"do_11283193463014195215\",\"copyright\":\"Sunbird\",\"lastStatusChangedOn\":\"2019-08-21T14:37:50.281+0000\",\"code\":\"2e837725-d663-45da-8ace-9577ab111982\",\"visibility\":\"Parent\",\"index\":1,\"mimeType\":\"application/vnd.ekstep.content-collection\",\"createdOn\":\"2019-08-21T14:37:50.281+0000\",\"versionKey\":\"1566398270281\",\"framework\":\"tpd\",\"depth\":1,\"children\":[],\"name\":\"U1\",\"lastUpdatedOn\":\"2019-08-21T14:37:50.281+0000\",\"contentType\":\"CourseUnit\",\"status\":\"Draft\"}]}');"
//    private val EXISTING_HIERARCHY_do_112949210157768704111 = "INSERT INTO hierarchy_store.content_hierarchy(identifier, hierarchy) values ('do_112949210157768704111', '{\"identifier\":\"do_112949210157768704111\",\"children\":[{\"ownershipType\":[\"createdBy\"],\"parent\":\"do_112949210157768704111\",\"code\":\"b9a50833-eff6-4ef5-a2a4-2413f2d51f6c\",\"channel\":\"in.ekstep\",\"description\":\"Test_CourseUnit_desc_1\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2020-02-04T10:53:23.491+0530\",\"objectType\":\"Content\",\"children\":[{\"ownershipType\":[\"createdFor\"],\"parent\":\"do_11294986283819827217\",\"previewUrl\":\"https://youtu.be/v7YZhQ86Adw\",\"keywords\":[\"10th\",\"Science\",\"Jnana Prabodhini\",\"Maharashtra Board\",\"#gyanqr\"],\"subject\":[\"Science\"],\"channel\":\"01261732844414566415\",\"downloadUrl\":\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/ecar_files/do_312776559940476928110909/vnsptiinmdhye-laingik-prjnn_1560157123850_do_312776559940476928110909_1.0.ecar\",\"organisation\":[\"Jnana Prabodhini\"],\"language\":[\"English\"],\"mimeType\":\"video/x-youtube\",\"variants\":{\"spine\":{\"ecarUrl\":\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/ecar_files/do_312776559940476928110909/vnsptiinmdhye-laingik-prjnn_1560157123878_do_312776559940476928110909_1.0_spine.ecar\",\"size\":51205.0}},\"objectType\":\"Content\",\"gradeLevel\":[\"Class 10\"],\"appIcon\":\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/do_312776559940476928110909/artifact/10th_mar_2_1547715340679.thumb.png\",\"appId\":\"prod.diksha.portal\",\"contentEncoding\":\"identity\",\"artifactUrl\":\"https://youtu.be/v7YZhQ86Adw\",\"lockKey\":\"772b40b3-4de0-44c3-8474-0fe8f8ec2d91\",\"sYS_INTERNAL_LAST_UPDATED_ON\":\"2019-07-31T01:57:11.210+0000\",\"contentType\":\"Resource\",\"lastUpdatedBy\":\"bf4df886-bb42-4f91-9f33-c88da1653535\",\"identifier\":\"do_312776559940476928110909\",\"audience\":[\"Learner\"],\"visibility\":\"Default\",\"consumerId\":\"89490534-126f-4f0b-82ac-3ff3e49f3468\",\"index\":1,\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"7a3358d5-e290-49a4-b7ea-3e3d47a2af30\",\"languageCode\":[\"en\"],\"version\":1,\"pragma\":[\"external\"],\"license\":\"Creative Commons Attribution (CC BY)\",\"prevState\":\"Review\",\"size\":51204.0,\"lastPublishedOn\":\"2019-06-10T08:58:43.846+0000\",\"name\":\"वनस्पतींमध्ये लैंगिक प्रजनन\",\"attributions\":[\"Jnana Prabodhini\"],\"status\":\"Live\",\"code\":\"99a9c6e4-ec56-40ab-9a8c-b66e3a551273\",\"creators\":\"Jnana Prabodhini\",\"description\":\"सजीवांतील जीवनप्रक्रिया भाग - २\",\"streamingUrl\":\"https://youtu.be/v7YZhQ86Adw\",\"medium\":[\"Marathi\"],\"posterImage\":\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/do_31267888406854041612953/artifact/10th_mar_2_1547715340679.png\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2018-09-06T06:35:10.427+0000\",\"contentDisposition\":\"online\",\"lastUpdatedOn\":\"6019-06-10T08:35:10.582+0000\",\"dialcodeRequired\":\"No\",\"owner\":\"Jnana Prabodhini\",\"lastStatusChangedOn\":\"2019-06-10T08:58:43.925+0000\",\"createdFor\":[\"01261732844414566415\"],\"creator\":\"Pallavi Paradkar\",\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1560157123651\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"NCF\",\"depth\":2,\"s3Key\":\"ecar_files/do_312776559940476928110909/vnsptiinmdhye-laingik-prjnn_1560157123850_do_312776559940476928110909_1.0.ecar\",\"me_averageRating\":3.0,\"lastSubmittedOn\":\"2019-06-04T09:02:44.995+0000\",\"createdBy\":\"bf4df886-bb42-4f91-9f33-c88da1653535\",\"compatibilityLevel\":4,\"ownedBy\":\"01261732844414566415\",\"board\":\"State (Maharashtra)\",\"resourceType\":\"Learn\"}],\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2020-02-04T10:53:23.490+0530\",\"contentEncoding\":\"gzip\",\"contentType\":\"TextBookUnit\",\"dialcodeRequired\":\"No\",\"identifier\":\"do_11294986283819827217\",\"lastStatusChangedOn\":\"2020-02-04T10:53:23.492+0530\",\"audience\":[\"Learner\"],\"os\":[\"All\"],\"visibility\":\"Parent\",\"index\":1,\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"languageCode\":[\"en\"],\"versionKey\":\"1580793803491\",\"license\":\"CC BY 4.0\",\"idealScreenDensity\":\"hdpi\",\"depth\":1,\"compatibilityLevel\":1,\"name\":\"Test_CourseUnit_1\",\"status\":\"Draft\"}]}');"

    implicit val oec: OntologyEngineContext = new OntologyEngineContext

    override def beforeAll(): Unit = {
        super.beforeAll()
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],code:\"citrusCode\",channel:\"in.ekstep\",language:[\"English\"],mimeType:\"application/vnd.ekstep.content-collection\",idealScreenSize:\"normal\",createdOn:\"2020-01-29T17:45:55.620+0530\",contentDisposition:\"inline\",contentEncoding:\"gzip\",lastUpdatedOn:\"2020-01-29T17:46:35.471+0530\",contentType:\"TextBook\",dialcodeRequired:\"No\",identifier:\"do_11294581887465881611\",audience:[\"Learner\"],lastStatusChangedOn:\"2020-01-29T17:45:55.620+0530\",os:[\"All\"],visibility:\"Default\",IL_SYS_NODE_TYPE:\"DATA_NODE\",nodeType:\"DATA_NODE\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",version:2,versionKey:\"1580300195471\",idealScreenDensity:\"hdpi\",license:\"CC BY 4.0\",framework:\"NCF\",compatibilityLevel:1,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"Update Hierarchy Test_03\",IL_UNIQUE_ID:\"do_11294581887465881611\",status:\"Draft\"},\n{ownershipType:[\"createdBy\"],code:\"citrusCode\",channel:\"in.ekstep\",description:\"New text book description_01\",language:[\"English\"],mimeType:\"application/vnd.ekstep.content-collection\",idealScreenSize:\"normal\",createdOn:\"2020-02-03T12:45:30.605+0530\",contentDisposition:\"inline\",contentEncoding:\"gzip\",lastUpdatedOn:\"2020-02-03T12:46:01.439+0530\",contentType:\"TextBook\",dialcodeRequired:\"No\",identifier:\"do_112949210157768704111\",audience:[\"Learner\"],lastStatusChangedOn:\"2020-02-03T12:45:30.605+0530\",os:[\"All\"],visibility:\"Default\",IL_SYS_NODE_TYPE:\"DATA_NODE\",nodeType:\"DATA_NODE\",childNodes:[\"do_112949210410000384114\"],mediaType:\"content\",osId:\"org.ekstep.quiz.app\",version:2,versionKey:\"1580714161439\",license:\"CC BY 4.0\",idealScreenDensity:\"hdpi\",depth:0,framework:\"NCF\",compatibilityLevel:1,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"Update Hierarchy Test_01\",IL_UNIQUE_ID:\"do_112949210157768704111\",status:\"Draft\"},\n{ownershipType:[\"createdBy\"],previewUrl:\"https://www.youtube.com/watch?v=JEjUtGkUqus\",downloadUrl:\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/ecar_files/do_31250856200414822416938/mh_chapter-1_science-part-2_grade-10_2_1539192600492_do_31250856200414822416938_2.0.ecar\",channel:\"0123221617357783046602\",showNotification:true,language:[\"English\"],variants:\"{\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/ecar_files/do_31250856200414822416938/mh_chapter-1_science-part-2_grade-10_2_1539192600579_do_31250856200414822416938_2.0_spine.ecar\\\",\\\"size\\\":29518}}\",mimeType:\"video/x-youtube\",appIcon:\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/do_31250856200414822416938/artifact/logo_4221_1513150964_1513150964262.thumb.jpg\",appId:\"prod.diksha.app\",artifactUrl:\"https://www.youtube.com/watch?v=JEjUtGkUqus\",contentEncoding:\"identity\",contentType:\"Resource\",lastUpdatedBy:\"ekstep\",audience:[\"Learner\"],visibility:\"Default\",consumerId:\"89490534-126f-4f0b-82ac-3ff3e49f3468\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"ekstep\",version:1,pragma:[\"external\"],license:\"CC BY-SA 4.0\",prevState:\"Live\",size:29519,lastPublishedOn:\"2018-10-10T17:30:00.491+0000\",IL_FUNC_OBJECT_TYPE:\"Content\",name:\"MH_Chapter 1_Science Part 2_Grade 10_2\",attributions:[\"MSCERT\"],status:\"Live\",code:\"b2099ea5-0070-4930-ae13-b08aa83e5853\",description:\"अानुवांशिकता और उत्क्रांती\",streamingUrl:\"https://www.youtube.com/watch?v=JEjUtGkUqus\",posterImage:\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/do_31239573269507276815296/artifact/logo_4221_1513150964_1513150964262.jpg\",idealScreenSize:\"normal\",createdOn:\"0021-02-04T08:05:49.480+0000\",contentDisposition:\"online\",lastUpdatedOn:\"25182518-10-10T17:29:59.456+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-07-31T01:57:26.148+0000\",dialcodeRequired:\"No\",lastStatusChangedOn:\"2019-06-17T05:41:05.507+0000\",createdFor:[\"0123221617357783046602\"],creator:\"Alaka Potdar\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",nodeType:\"DATA_NODE\",pkgVersion:2,versionKey:\"1539192599456\",idealScreenDensity:\"hdpi\",framework:\"NCF\",s3Key:\"ecar_files/do_31250856200414822416938/mh_chapter-1_science-part-2_grade-10_2_1539192600492_do_31250856200414822416938_2.0.ecar\",me_averageRating:3,lastSubmittedOn:\"2018-05-21T17:37:16.466+0000\",createdBy:\"c5d09e49-6f1d-474b-b6cc-2e590ae15ef8\",compatibilityLevel:4,IL_UNIQUE_ID:\"do_31250856200414822416938\",resourceType:\"Learn\"},\n{copyright:\"\",code:\"org.ekstep.asset.Pant.1773380908\",sources:\"\",channel:\"in.ekstep\",downloadUrl:\"https://ekstep-public-prod.s3-ap-south-1.amazonaws.com/content/do_30109819/artifact/clothes-1294974_960_720_658_1483340550_1483340551056.png\",language:[\"English\"],mimeType:\"image/png\",variants:\"{\\\"high\\\":\\\"https://ekstep-public-prod.s3-ap-south-1.amazonaws.com/content/do_30109819/artifact/clothes-1294974_960_720_658_1483340550_1483340551056.png\\\",\\\"low\\\":\\\"https://ekstep-public-prod.s3-ap-south-1.amazonaws.com/content/do_30109819/artifact/clothes-1294974_960_720_658_1483340550_1483340551056.low.png\\\",\\\"medium\\\":\\\"https://ekstep-public-prod.s3-ap-south-1.amazonaws.com/content/do_30109819/artifact/clothes-1294974_960_720_658_1483340550_1483340551056.medium.png\\\"}\",idealScreenSize:\"normal\",createdOn:\"2017-01-02T07:02:31.021+0000\",contentDisposition:\"inline\",artifactUrl:\"https://ekstep-public-prod.s3-ap-south-1.amazonaws.com/content/do_30109819/artifact/clothes-1294974_960_720_658_1483340550_1483340551056.png\",contentEncoding:\"identity\",lastUpdatedOn:\"2017-03-10T20:48:09.283+0000\",contentType:\"Asset\",owner:\"\",os:[\"All\"],visibility:\"Default\",IL_SYS_NODE_TYPE:\"DATA_NODE\",nodeType:\"DATA_NODE\",portalOwner:\"658\",mediaType:\"image\",osId:\"org.ekstep.quiz.app\",ageGroup:[\"5-6\"],versionKey:\"1489178889283\",license:\"CC BY-SA 4.0\",idealScreenDensity:\"hdpi\",framework:\"NCF\",s3Key:\"content/do_30109819/artifact/clothes-1294974_960_720_658_1483340550_1483340551056.png\",size:167939,createdBy:\"658\",compatibilityLevel:1,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"Pant\",publisher:\"\",IL_UNIQUE_ID:\"do_30109819\",status:\"Live\"}] as row CREATE (n:domain) SET n += row")
        executeCassandraQuery(KEYSPACE_CREATE_SCRIPT, TABLE_CREATE_SCRIPT)
    }

    override def beforeEach(): Unit = {
        executeCassandraQuery( HIERARCHY_TO_MIGRATE_SCRIPT)
    }

    def getContext(): java.util.Map[String, AnyRef] = {
        new util.HashMap[String, AnyRef](){{
            put(HierarchyConstants.GRAPH_ID, HierarchyConstants.TAXONOMY_ID)
            put(HierarchyConstants.VERSION , HierarchyConstants.SCHEMA_VERSION)
            put(HierarchyConstants.OBJECT_TYPE , HierarchyConstants.CONTENT_OBJECT_TYPE)
            put(HierarchyConstants.SCHEMA_NAME, "content")
            put(HierarchyConstants.CHANNEL, "b00bc992ef25f1a9a8d63291e20efc8d")
        }}
    }

    "deleteHierarchy" should "Delete the hierarchy data from cassandra from identifier with .img extension" in {
        val request = new Request()
        val context = getContext()
        context.put(HierarchyConstants.SCHEMA_NAME, "collection")
        context.put(HierarchyConstants.ROOT_ID, "do_11283193441064550414")
        request.setContext(context)
        val oldHierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'")
            .one().getString("hierarchy")
        assert(StringUtils.isNotEmpty(oldHierarchy))
        UpdateHierarchyManager.deleteHierarchy(request).map(response => {
            assert(response.getResponseCode.code() == 200)
            assert(BooleanUtils.isFalse(readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'").iterator().hasNext))
        })
    }

    "deleteHierarchy with image id" should "Delete the hierarchy data from cassandra from identifier with .img extension" in {
        val request = new Request()
        val context = getContext()
        context.put(HierarchyConstants.SCHEMA_NAME, "collection")
        context.put(HierarchyConstants.ROOT_ID, "do_11283193441064550414.img")
        request.setContext(context)
        val oldHierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'")
            .one().getString("hierarchy")
        assert(StringUtils.isNotEmpty(oldHierarchy))
        UpdateHierarchyManager.deleteHierarchy(request).map(response => {
            assert(response.getResponseCode.code() == 200)
            assert(BooleanUtils.isFalse(readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'").iterator().hasNext))
        })
    }

    "deleteHierarchy with invalid id" should "Delete the hierarchy data from cassandra from identifier with .img extension" in {
        val request = new Request()
        val context = getContext()
        context.put(HierarchyConstants.SCHEMA_NAME, "collection")
        context.put(HierarchyConstants.ROOT_ID, "123")
        request.setContext(context)
        UpdateHierarchyManager.deleteHierarchy(request).map(response => {
            assert(response.getResponseCode.code() == 200)
            val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'")
                .one().getString("hierarchy")
            assert(StringUtils.isNotEmpty(hierarchy))
        })
    }

    //    ResourceId = "do_31250856200414822416938" and TextBook id ="do_112945818874658816"
    "updateHierarchy with One New Unit and One Live Resource" should "update text book node, create unit and store the hierarchy in cassandra" in {
        val request = new Request()
        val context = getContext()
        context.put(HierarchyConstants.SCHEMA_NAME, "collection")
        request.setContext(context)
        request.put(HierarchyConstants.NODES_MODIFIED, getNodesModified_1())
        request.put(HierarchyConstants.HIERARCHY, getHierarchy_1())
        UpdateHierarchyManager.updateHierarchy(request).map(response => {
            assert(response.getResponseCode.code() == 200)
            val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11294581887465881611'")
                .one().getString("hierarchy")
            assert(StringUtils.isNotEmpty(hierarchy))
        })
    }

    "updateHierarchy on already existing hierarchy" should "recompose the hierarchy structure and store in in cassandra and also update neo4j" in {
        UpdateHierarchyManager.getContentNode("do_31250856200414822416938", HierarchyConstants.TAXONOMY_ID).map(node => {
            println("Node data from neo4j ----- id: " + node.getIdentifier + "node type:  " + node.getNodeType + " node metadata : " + node.getMetadata)
            val request = new Request()
            val context = getContext()
            context.put(HierarchyConstants.SCHEMA_NAME, "collection")
            request.setContext(context)
            request.put(HierarchyConstants.NODES_MODIFIED, getNodesModified_1())
            request.put(HierarchyConstants.HIERARCHY, getHierarchy_1())
            UpdateHierarchyManager.updateHierarchy(request).map(response => {
                assert(response.getResponseCode.code() == 200)
                val identifiers =  response.get(HierarchyConstants.IDENTIFIERS).asInstanceOf[util.Map[String, AnyRef]]
                val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11294581887465881611'")
                    .one().getString(HierarchyConstants.HIERARCHY)
                assert(StringUtils.isNotEmpty(hierarchy))
                request.put(HierarchyConstants.NODES_MODIFIED, getNodesModified_2("do_11294581887465881611", identifiers.get("b9a50833-eff6-4ef5-a2a4-2413f2d51f6c").asInstanceOf[String]))
                request.put(HierarchyConstants.HIERARCHY, getHierarchy_2("do_11294581887465881611", identifiers.get("b9a50833-eff6-4ef5-a2a4-2413f2d51f6c").asInstanceOf[String]))
                UpdateHierarchyManager.updateHierarchy(request).map(resp => {
                    assert(response.getResponseCode.code() == 200)
                    val hierarchy_updated = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11294581887465881611'")
                        .one().getString(HierarchyConstants.HIERARCHY)
                    assert(!StringUtils.equalsIgnoreCase(hierarchy, hierarchy_updated))
                })
            }).flatMap(f => f)
        }).flatMap(f => f)
    }

    "updateHierarchy with New Unit and Invalid Resource" should "throw resource not found exception" in {
        val request = new Request()
        val context = getContext()
        context.put(HierarchyConstants.SCHEMA_NAME, "collection")
        request.setContext(context)
        request.put(HierarchyConstants.NODES_MODIFIED, getNodesModified_1())
        request.put(HierarchyConstants.HIERARCHY, getHierarchy_Content_Resource_Invalid_ID())
        recoverToSucceededIf[ResourceNotFoundException](UpdateHierarchyManager.updateHierarchy(request))
    }

    "updateHierarchy with empty nodes modified and hierarchy" should "throw client exception" in {
        val request = new Request()
        val context = getContext()
        context.put(HierarchyConstants.SCHEMA_NAME, "collection")
        request.setContext(context)
        request.put(HierarchyConstants.NODES_MODIFIED, new util.HashMap())
        request.put(HierarchyConstants.HIERARCHY, new util.HashMap())
        val exception = intercept[ClientException] {
            UpdateHierarchyManager.updateHierarchy(request)
        }
        exception.getMessage shouldEqual "Please Provide Valid Root Node Identifier"
    }
    //Text Book -> root, New Unit
    def getNodesModified_1(): util.HashMap[String, AnyRef] = {
        val nodesModifiedString: String =    "{\n"+
            "                \"do_11294581887465881611\": {\n"+
            "                    \"isNew\": false,\n"+
            "                    \"root\": true\n"+
            "                },\n"+
            "                \"b9a50833-eff6-4ef5-a2a4-2413f2d51f6c\": {\n"+
            "                    \"isNew\": true,\n"+
            "                    \"root\": false,\n"+
            "                    \"metadata\": {\n"+
            "                        \"mimeType\": \"application/vnd.ekstep.content-collection\",\n"+
            "                        \"contentType\": \"TextBookUnit\",\n"+
            "                        \"code\": \"updateHierarchy\",\n"+
            "                        \"name\": \"Test_CourseUnit_1\",\n"+
            "                        \"description\": \"updated hierarchy\",\n"+
            "                        \"channel\": \"in.ekstep\"\n"+
            "                    }\n"+
            "                }\n"+
            "            }"
        JsonUtils.deserialize(nodesModifiedString, classOf[util.HashMap[String, AnyRef]])
    }
    //Text
    def getHierarchy_1(): util.HashMap[String, AnyRef] = {
        val hierarchyString =     "{\n"+
            "            \t\"do_11294581887465881611\" : {\n"+
            "            \t\t\"root\": true,\n"+
            "            \t\t\"children\": [\"b9a50833-eff6-4ef5-a2a4-2413f2d51f6c\"]\n"+
            "            \t},\n"+
            "            \t\"b9a50833-eff6-4ef5-a2a4-2413f2d51f6c\": {\n"+
            "            \t\t\"root\": false,\n"+
            "            \t\t\"children\": [\"do_31250856200414822416938\"]\n"+
            "            \t}\n"+
            "            }"
        JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]])
    }

    def getNodesModified_2(rootId:String, unit_1: String): util.HashMap[String, AnyRef] = {
        val nodesModifiedString: String = "{\n"+
            "        \""+ rootId +"\": {\n"+
            "          \"isNew\": false,\n"+
            "          \"root\": true,\n"+
            "          \"metadata\": {\n"+
            "            \"name\": \"updated text book name check\"\n"+
            "          }\n"+
            "        },\n"+
            "        \""+ unit_1 +"\": {\n"+
            "          \"isNew\": false,\n"+
            "          \"root\": false\n"+
            "        },\n"+
            "        \"b9a50833-eff6-4ef5-a2a4-2413f2d51f6c\": {\n"+
            "          \"isNew\": true,\n"+
            "          \"root\": false,\n"+
            "          \"metadata\": {\n"+
            "            \"mimeType\": \"application/vnd.ekstep.content-collection\",\n"+
            "            \"contentType\": \"TextBookUnit\",\n"+
            "            \"code\": \"updateHierarchy\",\n"+
            "            \"name\": \"Test_CourseUnit_1\",\n"+
            "            \"description\": \"Test_CourseUnit_desc_1\"\n"+
            "          }\n"+
            "        }" +
            "}"
        JsonUtils.deserialize(nodesModifiedString, classOf[util.HashMap[String, AnyRef]])
    }

    def getHierarchy_2(rootId: String, unit_2: String): util.HashMap[String, AnyRef] = {
        val hierarchyString: String = "{\n" +
            "        \"" + rootId + "\": {\n" +
            "          \"root\": true,\n" +
            "          \"children\": [\n" +
            "            \"b9a50833-eff6-4ef5-a2a4-2413f2d51f6c\"\n" +
            "          ]\n" +
            "        },\n" +
            "        \"b9a50833-eff6-4ef5-a2a4-2413f2d51f6c\": {\n" +
            "          \"root\": false,\n" +
            "          \"children\": [\n" +
            "            \"" + unit_2 + "\",\n" +
            "            \"do_30109819\"\n" +
            "          ]\n" +
            "        },\n" +
            "        \"" + unit_2 + "\": {\n" +
            "          \"root\": false,\n" +
            "          \"children\": [\n" +
            "            \"do_31250856200414822416938\"\n" +
            "          ]\n" +
            "        }\n" +
            "      }"
        JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]])
    }

    //Text
    def getHierarchy_Content_Resource_Invalid_ID(): util.HashMap[String, AnyRef] = {
        val hierarchyString =     "{\n"+
            "            \t\"do_11294581887465881611\" : {\n"+
            "            \t\t\"root\": true,\n"+
            "            \t\t\"children\": [\"b9a50833-eff6-4ef5-a2a4-2413f2d51f6c\"]\n"+
            "            \t},\n"+
            "            \t\"b9a50833-eff6-4ef5-a2a4-2413f2d51f6c\": {\n"+
            "            \t\t\"root\": false,\n"+
            "            \t\t\"children\": [\"do_3125085620041482241\"]\n"+
            "            \t}\n"+
            "            }"
        JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]])
    }

}
