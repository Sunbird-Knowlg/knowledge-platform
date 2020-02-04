package org.sunbird.managers

import java.util
import org.apache.commons.lang3.BooleanUtils
import org.parboiled.common.StringUtils
import org.sunbird.common.JsonUtils
import org.sunbird.common.dto.Request
import org.sunbird.utils.HierarchyConstants

class TestUpdateHierarchy extends BaseSpec {

    private val KEYSPACE_CREATE_SCRIPT = "CREATE KEYSPACE IF NOT EXISTS hierarchy_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
    private val TABLE_CREATE_SCRIPT = "CREATE TABLE IF NOT EXISTS hierarchy_store.content_hierarchy (identifier text, hierarchy text,PRIMARY KEY (identifier));"
    private val HIERARCHY_TO_MIGRATE_SCRIPT = "INSERT INTO hierarchy_store.content_hierarchy(identifier, hierarchy) values ('do_11283193441064550414.img', '{\"identifier\":\"do_11283193441064550414\",\"children\":[{\"parent\":\"do_11283193441064550414\",\"identifier\":\"do_11283193463014195215\",\"copyright\":\"Sunbird\",\"lastStatusChangedOn\":\"2019-08-21T14:37:50.281+0000\",\"code\":\"2e837725-d663-45da-8ace-9577ab111982\",\"visibility\":\"Parent\",\"index\":1,\"mimeType\":\"application/vnd.ekstep.content-collection\",\"createdOn\":\"2019-08-21T14:37:50.281+0000\",\"versionKey\":\"1566398270281\",\"framework\":\"tpd\",\"depth\":1,\"children\":[],\"name\":\"U1\",\"lastUpdatedOn\":\"2019-08-21T14:37:50.281+0000\",\"contentType\":\"CourseUnit\",\"status\":\"Draft\"}]}');"
//    private val EXISTING_HIERARCHY_do_112949210157768704111 = "INSERT INTO hierarchy_store.content_hierarchy(identifier, hierarchy) values ('do_112949210157768704111', '{\"identifier\":\"do_112949210157768704111\",\"children\":[{\"ownershipType\":[\"createdBy\"],\"parent\":\"do_112949210157768704111\",\"code\":\"b9a50833-eff6-4ef5-a2a4-2413f2d51f6c\",\"channel\":\"in.ekstep\",\"description\":\"Test_CourseUnit_desc_1\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2020-02-04T10:53:23.491+0530\",\"objectType\":\"Content\",\"children\":[{\"ownershipType\":[\"createdFor\"],\"parent\":\"do_11294986283819827217\",\"previewUrl\":\"https://youtu.be/v7YZhQ86Adw\",\"keywords\":[\"10th\",\"Science\",\"Jnana Prabodhini\",\"Maharashtra Board\",\"#gyanqr\"],\"subject\":[\"Science\"],\"channel\":\"01261732844414566415\",\"downloadUrl\":\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/ecar_files/do_312776559940476928110909/vnsptiinmdhye-laingik-prjnn_1560157123850_do_312776559940476928110909_1.0.ecar\",\"organisation\":[\"Jnana Prabodhini\"],\"language\":[\"English\"],\"mimeType\":\"video/x-youtube\",\"variants\":{\"spine\":{\"ecarUrl\":\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/ecar_files/do_312776559940476928110909/vnsptiinmdhye-laingik-prjnn_1560157123878_do_312776559940476928110909_1.0_spine.ecar\",\"size\":51205.0}},\"objectType\":\"Content\",\"gradeLevel\":[\"Class 10\"],\"appIcon\":\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/do_312776559940476928110909/artifact/10th_mar_2_1547715340679.thumb.png\",\"appId\":\"prod.diksha.portal\",\"contentEncoding\":\"identity\",\"artifactUrl\":\"https://youtu.be/v7YZhQ86Adw\",\"lockKey\":\"772b40b3-4de0-44c3-8474-0fe8f8ec2d91\",\"sYS_INTERNAL_LAST_UPDATED_ON\":\"2019-07-31T01:57:11.210+0000\",\"contentType\":\"Resource\",\"lastUpdatedBy\":\"bf4df886-bb42-4f91-9f33-c88da1653535\",\"identifier\":\"do_312776559940476928110909\",\"audience\":[\"Learner\"],\"visibility\":\"Default\",\"consumerId\":\"89490534-126f-4f0b-82ac-3ff3e49f3468\",\"index\":1,\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"lastPublishedBy\":\"7a3358d5-e290-49a4-b7ea-3e3d47a2af30\",\"languageCode\":[\"en\"],\"version\":1,\"pragma\":[\"external\"],\"license\":\"Creative Commons Attribution (CC BY)\",\"prevState\":\"Review\",\"size\":51204.0,\"lastPublishedOn\":\"2019-06-10T08:58:43.846+0000\",\"name\":\"वनस्पतींमध्ये लैंगिक प्रजनन\",\"attributions\":[\"Jnana Prabodhini\"],\"status\":\"Live\",\"code\":\"99a9c6e4-ec56-40ab-9a8c-b66e3a551273\",\"creators\":\"Jnana Prabodhini\",\"description\":\"सजीवांतील जीवनप्रक्रिया भाग - २\",\"streamingUrl\":\"https://youtu.be/v7YZhQ86Adw\",\"medium\":[\"Marathi\"],\"posterImage\":\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/do_31267888406854041612953/artifact/10th_mar_2_1547715340679.png\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2018-09-06T06:35:10.427+0000\",\"contentDisposition\":\"online\",\"lastUpdatedOn\":\"6019-06-10T08:35:10.582+0000\",\"dialcodeRequired\":\"No\",\"owner\":\"Jnana Prabodhini\",\"lastStatusChangedOn\":\"2019-06-10T08:58:43.925+0000\",\"createdFor\":[\"01261732844414566415\"],\"creator\":\"Pallavi Paradkar\",\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1560157123651\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"NCF\",\"depth\":2,\"s3Key\":\"ecar_files/do_312776559940476928110909/vnsptiinmdhye-laingik-prjnn_1560157123850_do_312776559940476928110909_1.0.ecar\",\"me_averageRating\":3.0,\"lastSubmittedOn\":\"2019-06-04T09:02:44.995+0000\",\"createdBy\":\"bf4df886-bb42-4f91-9f33-c88da1653535\",\"compatibilityLevel\":4,\"ownedBy\":\"01261732844414566415\",\"board\":\"State (Maharashtra)\",\"resourceType\":\"Learn\"}],\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2020-02-04T10:53:23.490+0530\",\"contentEncoding\":\"gzip\",\"contentType\":\"TextBookUnit\",\"dialcodeRequired\":\"No\",\"identifier\":\"do_11294986283819827217\",\"lastStatusChangedOn\":\"2020-02-04T10:53:23.492+0530\",\"audience\":[\"Learner\"],\"os\":[\"All\"],\"visibility\":\"Parent\",\"index\":1,\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"languageCode\":[\"en\"],\"versionKey\":\"1580793803491\",\"license\":\"CC BY 4.0\",\"idealScreenDensity\":\"hdpi\",\"depth\":1,\"compatibilityLevel\":1,\"name\":\"Test_CourseUnit_1\",\"status\":\"Draft\"}]}');"

    override def beforeAll(): Unit = {
        super.beforeAll()
        graphDb.execute("UNWIND[{ownershipType:[\"createdFor\"],previewUrl:\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/assets/do_31277445725602611218167/b370u2p50hs-32agna-2.-sr.-zigzag-authors-interview.mp4\",keywords:[\"32AGNA\"],subject:[\"English\"],downloadUrl:\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/ecar_files/do_31277445725602611218167/2-sr-zigzag-authors-interview_1559450571892_do_31277445725602611218167_1.0.ecar\",channel:\"01235953109336064029450\",organisation:[\"Tamilnadu\"],showNotification:true,language:[\"English\"],variants:\"{\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/ecar_files/do_31277445725602611218167/2-sr-zigzag-authors-interview_1559450575858_do_31277445725602611218167_1.0_spine.ecar\\\",\\\"size\\\":35199.0}}\",mimeType:\"video/mp4\",appIcon:\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/do_31277445826415820819471/artifact/b370u2p50hs-32agna-2.-sr.-zigzag-authors-interview_1559382112486.jpg\",gradeLevel:[\"Class 10\"],appId:\"prod.diksha.app\",artifactUrl:\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/assets/do_31277445725602611218167/b370u2p50hs-32agna-2.-sr.-zigzag-authors-interview.mp4\",contentEncoding:\"identity\",lockKey:\"3949f2e6-11bc-4ce5-929f-a198054a9a7b\",contentType:\"Resource\",lastUpdatedBy:\"032b9b66-5faa-4391-8090-ffe3c97a0811\",audience:[\"Learner\"],visibility:\"Default\",consumerId:\"e85bcfb5-a8c2-4e65-87a2-0ebb43b45f01\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"cca54c3d-a959-47ed-9f5f-2748f65a62fb\",version:1,prevState:\"Review\",license:\"Creative Commons Attribution (CC BY)\",lastPublishedOn:\"2019-06-02T04:42:51.872+0000\",size:42570576,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"2  SR   Zigzag - Authors Interview\",status:\"Live\",code:\"1ae08c4a-7c40-49d7-8915-c790c9927c85\",description:\"B370U2P50HS\",medium:[\"English\"],streamingUrl:\"https://ntpprodmedia-inct.streaming.media.azure.net/c8187d55-921b-4ecb-a325-9e51e29c2751/b370u2p50hs-32agna-2.-sr.-zigzag.ism/manifest(format=m3u8-aapl-v3)\",idealScreenSize:\"normal\",createdOn:\"2019-06-01T09:39:49.002+0000\",contentDisposition:\"inline\",lastUpdatedOn:\"9719-06-02T04:42:23.796+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-07-31T01:56:40.485+0000\",dialcodeRequired:\"No\",owner:\"Tamilnadu\",createdFor:[\"01235953109336064029450\"],creator:\"TN SCERT SCERT\",lastStatusChangedOn:\"2019-06-17T05:40:56.320+0000\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",pkgVersion:1,versionKey:\"1559450571716\",idealScreenDensity:\"hdpi\",s3Key:\"ecar_files/do_31277445725602611218167/2-sr-zigzag-authors-interview_1559450571892_do_31277445725602611218167_1.0.ecar\",framework:\"tn_k-12_5\",me_averageRating:3,lastSubmittedOn:\"2019-06-01T09:42:40.129+0000\",createdBy:\"032b9b66-5faa-4391-8090-ffe3c97a0811\",compatibilityLevel:1,ownedBy:\"01235953109336064029450\",IL_UNIQUE_ID:\"do_31277445725602611218167\",board:\"State (Tamil Nadu)\",resourceType:\"Learn\"},{ownershipType:[\"createdBy\"],code:\"citrusCode\",channel:\"in.ekstep\",language:[\"English\"],mimeType:\"application/vnd.ekstep.content-collection\",idealScreenSize:\"normal\",createdOn:\"2020-01-29T17:45:55.620+0530\",contentDisposition:\"inline\",contentEncoding:\"gzip\",lastUpdatedOn:\"2020-01-29T17:46:35.471+0530\",contentType:\"TextBook\",dialcodeRequired:\"No\",identifier:\"do_11294581887465881611\",audience:[\"Learner\"],lastStatusChangedOn:\"2020-01-29T17:45:55.620+0530\",os:[\"All\"],visibility:\"Default\",IL_SYS_NODE_TYPE:\"DATA_NODE\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",version:2,versionKey:\"1580300195471\",idealScreenDensity:\"hdpi\",license:\"CC BY 4.0\",framework:\"NCF\",compatibilityLevel:1,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"Update Hierarchy Test_03\",IL_UNIQUE_ID:\"do_11294581887465881611\",status:\"Draft\"},{ownershipType:[\"createdBy\"],code:\"citrusCode\",channel:\"in.ekstep\",description:\"New text book description_01\",language:[\"English\"],mimeType:\"application/vnd.ekstep.content-collection\",\"idealScreenSize\":\"normal\",createdOn:\"2020-02-03T12:45:30.605+0530\",\"contentDisposition\":\"inline\",contentEncoding:\"gzip\",lastUpdatedOn:\"2020-02-03T12:46:01.439+0530\",contentType:\"TextBook\",dialcodeRequired:\"No\",\"identifier\":\"do_112949210157768704111\",audience:[\"Learner\"],\"lastStatusChangedOn\":\"2020-02-03T12:45:30.605+0530\",os:[\"All\"],visibility:\"Default\",IL_SYS_NODE_TYPE:\"DATA_NODE\",childNodes:[\"do_112949210410000384114\"],mediaType:\"content\",osId:\"org.ekstep.quiz.app\",\"version\":2,versionKey:\"1580714161439\",license:\"CC BY 4.0\",\"idealScreenDensity\":\"hdpi\",depth:0,framework:\"NCF\",compatibilityLevel:1,\"IL_FUNC_OBJECT_TYPE\":\"Content\",name:\"Update Hierarchy Test_01\",\"IL_UNIQUE_ID\":\"do_112949210157768704111\",status:\"Draft\"}] as row CREATE (n:domain) SET n += row")
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
        })
        assert(BooleanUtils.isFalse(readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'").iterator().hasNext))
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
        })
        assert(BooleanUtils.isFalse(readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'").iterator().hasNext))
    }

    "deleteHierarchy with invalid id" should "Delete the hierarchy data from cassandra from identifier with .img extension" in {
        val request = new Request()
        val context = getContext()
        context.put(HierarchyConstants.SCHEMA_NAME, "collection")
        context.put(HierarchyConstants.ROOT_ID, "123")
        request.setContext(context)
        UpdateHierarchyManager.deleteHierarchy(request).map(response => {
            assert(response.getResponseCode.code() == 200)
        })
        val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'")
            .one().getString("hierarchy")
        assert(StringUtils.isNotEmpty(hierarchy))
    }

//    ResourceId = "do_31277445725602611" and TextBook id ="do_112945818874658816"
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
        val request = new Request()
        val context = getContext()
        context.put(HierarchyConstants.SCHEMA_NAME, "collection")
        request.setContext(context)
        request.put(HierarchyConstants.NODES_MODIFIED, getNodesModified_1())
        request.put(HierarchyConstants.HIERARCHY, getHierarchy_1())
        UpdateHierarchyManager.updateHierarchy(request).map(response => {
            assert(response.getResponseCode.code() == 200)
            val identifiers = response.getResult.get(HierarchyConstants.IDENTIFIERS).asInstanceOf[Map[String, AnyRef]]
            val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_112949210157768704111'")
                .one().getString(HierarchyConstants.HIERARCHY)
            assert(StringUtils.isNotEmpty(hierarchy))
            request.put(HierarchyConstants.NODES_MODIFIED, getNodesModified_2("do_112949210157768704111", identifiers.get("b9a50833-eff6-4ef5-a2a4-2413f2d51f6c").asInstanceOf[String]))
            request.put(HierarchyConstants.HIERARCHY, getHierarchy_2("do_112949210157768704111", identifiers.get("b9a50833-eff6-4ef5-a2a4-2413f2d51f6c").asInstanceOf[String]))
            UpdateHierarchyManager.updateHierarchy(request).map(resp => {
                assert(response.getResponseCode.code() == 200)
                val hierarchy_updated = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_112949210157768704111'")
                    .one().getString(HierarchyConstants.HIERARCHY)
                assert(StringUtils.equalsIgnoreCase(hierarchy, hierarchy_updated))
            })
        }).flatMap(f => f)
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
            "            \t\t\"children\": [\"do_31277445725602611218167\"]\n"+
            "            \t}\n"+
            "            }"
        JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]])
    }

    def getNodesModified_2(rootId:String, unit_1: String): util.HashMap[String, AnyRef] = {
        val nodesModifiedString: String = "\"nodesModified\": {\n"+
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
            "        }\n"+
            "      }"
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
            "            \"" + unit_2 + "\"\n" +
            "          ]\n" +
            "        },\n" +
            "        \"" + unit_2 + "\": {\n" +
            "          \"root\": false,\n" +
            "          \"children\": [\n" +
            "            \"do_31277445725602611218167\"\n" +
            "          ]\n" +
            "        }\n" +
            "      }"
        JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]])
    }
}
