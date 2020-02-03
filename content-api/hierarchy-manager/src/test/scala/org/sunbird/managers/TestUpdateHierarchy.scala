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


    override def beforeAll(): Unit = {
        super.beforeAll()
        graphDb.execute("UNWIND[{ownershipType:[\"createdFor\"],previewUrl:\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/assets/do_31277445725602611218167/b370u2p50hs-32agna-2.-sr.-zigzag-authors-interview.mp4\",keywords:[\"32AGNA\"],subject:[\"English\"],downloadUrl:\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/ecar_files/do_31277445725602611218167/2-sr-zigzag-authors-interview_1559450571892_do_31277445725602611218167_1.0.ecar\",channel:\"01235953109336064029450\",organisation:[\"Tamilnadu\"],showNotification:true,language:[\"English\"],variants:\"{\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/ecar_files/do_31277445725602611218167/2-sr-zigzag-authors-interview_1559450575858_do_31277445725602611218167_1.0_spine.ecar\\\",\\\"size\\\":35199.0}}\",mimeType:\"video/mp4\",appIcon:\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/do_31277445826415820819471/artifact/b370u2p50hs-32agna-2.-sr.-zigzag-authors-interview_1559382112486.jpg\",gradeLevel:[\"Class 10\"],appId:\"prod.diksha.app\",artifactUrl:\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/assets/do_31277445725602611218167/b370u2p50hs-32agna-2.-sr.-zigzag-authors-interview.mp4\",contentEncoding:\"identity\",lockKey:\"3949f2e6-11bc-4ce5-929f-a198054a9a7b\",contentType:\"Resource\",lastUpdatedBy:\"032b9b66-5faa-4391-8090-ffe3c97a0811\",audience:[\"Learner\"],visibility:\"Default\",consumerId:\"e85bcfb5-a8c2-4e65-87a2-0ebb43b45f01\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"cca54c3d-a959-47ed-9f5f-2748f65a62fb\",version:1,prevState:\"Review\",license:\"Creative Commons Attribution (CC BY)\",lastPublishedOn:\"2019-06-02T04:42:51.872+0000\",size:42570576,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"2  SR   Zigzag - Authors Interview\",status:\"Live\",code:\"1ae08c4a-7c40-49d7-8915-c790c9927c85\",description:\"B370U2P50HS\",medium:[\"English\"],streamingUrl:\"https://ntpprodmedia-inct.streaming.media.azure.net/c8187d55-921b-4ecb-a325-9e51e29c2751/b370u2p50hs-32agna-2.-sr.-zigzag.ism/manifest(format=m3u8-aapl-v3)\",idealScreenSize:\"normal\",createdOn:\"2019-06-01T09:39:49.002+0000\",contentDisposition:\"inline\",lastUpdatedOn:\"9719-06-02T04:42:23.796+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-07-31T01:56:40.485+0000\",dialcodeRequired:\"No\",owner:\"Tamilnadu\",createdFor:[\"01235953109336064029450\"],creator:\"TN SCERT SCERT\",lastStatusChangedOn:\"2019-06-17T05:40:56.320+0000\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",pkgVersion:1,versionKey:\"1559450571716\",idealScreenDensity:\"hdpi\",s3Key:\"ecar_files/do_31277445725602611218167/2-sr-zigzag-authors-interview_1559450571892_do_31277445725602611218167_1.0.ecar\",framework:\"tn_k-12_5\",me_averageRating:3,lastSubmittedOn:\"2019-06-01T09:42:40.129+0000\",createdBy:\"032b9b66-5faa-4391-8090-ffe3c97a0811\",compatibilityLevel:1,ownedBy:\"01235953109336064029450\",IL_UNIQUE_ID:\"do_31277445725602611218167\",board:\"State (Tamil Nadu)\",resourceType:\"Learn\"},{ownershipType:[\"createdBy\"],code:\"citrusCode\",channel:\"in.ekstep\",language:[\"English\"],mimeType:\"application/vnd.ekstep.content-collection\",idealScreenSize:\"normal\",createdOn:\"2020-01-29T17:45:55.620+0530\",contentDisposition:\"inline\",contentEncoding:\"gzip\",lastUpdatedOn:\"2020-01-29T17:46:35.471+0530\",contentType:\"TextBook\",dialcodeRequired:\"No\",identifier:\"do_11294581887465881611\",audience:[\"Learner\"],lastStatusChangedOn:\"2020-01-29T17:45:55.620+0530\",os:[\"All\"],visibility:\"Default\",IL_SYS_NODE_TYPE:\"DATA_NODE\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",version:2,versionKey:\"1580300195471\",idealScreenDensity:\"hdpi\",license:\"CC BY 4.0\",framework:\"NCF\",compatibilityLevel:1,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"Update Hierarchy Test_03\",IL_UNIQUE_ID:\"do_11294581887465881611\",status:\"Draft\"}   ] as row CREATE (n:domain) SET n += row")
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
            println(hierarchy)
            assert(StringUtils.isNotEmpty(hierarchy))
        })
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
}
