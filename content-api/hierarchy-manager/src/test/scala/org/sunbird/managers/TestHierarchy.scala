package org.sunbird.managers

import java.util

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext

class TestHierarchy extends BaseSpec {

    private val script_1 = "CREATE KEYSPACE IF NOT EXISTS hierarchy_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
    private val script_2 = "CREATE TABLE IF NOT EXISTS hierarchy_store.content_hierarchy (identifier text, hierarchy text,PRIMARY KEY (identifier));"
    private val script_3 = "INSERT INTO hierarchy_store.content_hierarchy(identifier, hierarchy) values ('do_11283193441064550414', '{\"identifier\":\"do_11283193441064550414\",\"children\":[{\"parent\":\"do_11283193441064550414\",\"identifier\":\"do_11283193463014195215\",\"copyright\":\"Sunbird\",\"lastStatusChangedOn\":\"2019-08-21T14:37:50.281+0000\",\"code\":\"2e837725-d663-45da-8ace-9577ab111982\",\"visibility\":\"Parent\",\"index\":1,\"mimeType\":\"application/vnd.ekstep.content-collection\",\"createdOn\":\"2019-08-21T14:37:50.281+0000\",\"versionKey\":\"1566398270281\",\"framework\":\"tpd\",\"depth\":1,\"children\":[],\"name\":\"U1\",\"lastUpdatedOn\":\"2019-08-21T14:37:50.281+0000\",\"contentType\":\"CourseUnit\",\"status\":\"Draft\", \"objectType\":\"Collection\"}]}');"
    private val script_4 = "INSERT INTO hierarchy_store.content_hierarchy(identifier, hierarchy) values ('do_11283193441064550414', '{\"status\":\"Live\",\"children\":[{\"parent\":\"do_11283193441064550414\",\"identifier\":\"do_11283193463014195215\",\"copyright\":\"Sunbird\",\"lastStatusChangedOn\":\"2019-08-21T14:37:50.281+0000\",\"code\":\"2e837725-d663-45da-8ace-9577ab111982\",\"visibility\":\"Parent\",\"index\":1,\"mimeType\":\"application/vnd.ekstep.content-collection\",\"createdOn\":\"2019-08-21T14:37:50.281+0000\",\"versionKey\":\"1566398270281\",\"framework\":\"tpd\",\"depth\":1,\"children\":[],\"name\":\"U1\",\"lastUpdatedOn\":\"2019-08-21T14:37:50.281+0000\",\"contentType\":\"CourseUnit\",\"status\":\"Draft\"}]}}');"
    private val script_5 = "INSERT INTO hierarchy_store.content_hierarchy(identifier, hierarchy) values ('do_11323126798764441611181', '{\"identifier\":\"do_11323126798764441611181\",\"children\":[{\"ownershipType\":[\"createdBy\"],\"parent\":\"do_11323126798764441611181\",\"code\":\"U1\",\"keywords\":[],\"credentials\":{\"enabled\":\"No\"},\"channel\":\"sunbird\",\"description\":\"U1-For Content\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2021-03-07T19:24:58.991+0000\",\"objectType\":\"Collection\",\"primaryCategory\":\"Textbook Unit\",\"children\":[{\"ownershipType\":[\"createdBy\"],\"parent\":\"do_11323126865092608011182\",\"copyright\":\"Kerala State\",\"previewUrl\":\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/content/assets/do_11307457137049600011786/eng-presentation_1597086905822.pdf\",\"keywords\":[\"By the Hands of the Nature\"],\"subject\":[\"Geography\"],\"channel\":\"0126202691023585280\",\"downloadUrl\":\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/ecar_files/do_11307457137049600011786/by-the-hands-of-the-nature_1597087677810_do_11307457137049600011786_1.0.ecar\",\"organisation\":[\"Kerala State\"],\"textbook_name\":[\"Contemporary India - I\"],\"showNotification\":true,\"language\":[\"English\"],\"source\":\"Kl 4\",\"mimeType\":\"application/pdf\",\"variants\":{\"spine\":{\"ecarUrl\":\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/ecar_files/do_11307457137049600011786/by-the-hands-of-the-nature_1597087678819_do_11307457137049600011786_1.0_spine.ecar\",\"size\":36508.0}},\"objectType\":\"Content\",\"sourceURL\":\"https://diksha.gov.in/play/content/do_312783564254150656111171\",\"gradeLevel\":[\"Class 9\"],\"me_totalRatingsCount\":36,\"appIcon\":\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/do_312783564254150656111171/artifact/screenshot-from-2019-06-14-11-59-39_1560493827569.thumb.png\",\"primaryCategory\":\"Learning Resource\",\"level2Name\":[\"Physical Features of India\"],\"appId\":\"prod.diksha.portal\",\"contentEncoding\":\"identity\",\"artifactUrl\":\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/content/assets/do_11307457137049600011786/eng-presentation_1597086905822.pdf\",\"me_totalPlaySessionCount\":{\"portal\":28},\"sYS_INTERNAL_LAST_UPDATED_ON\":\"2020-08-10T19:27:58.911+0000\",\"contentType\":\"Resource\",\"identifier\":\"do_11307457137049600011786\",\"audience\":[\"Student\"],\"me_totalTimeSpentInSec\":{\"portal\":2444},\"visibility\":\"Default\",\"author\":\"Kerala State\",\"consumerId\":\"89490534-126f-4f0b-82ac-3ff3e49f3468\",\"index\":1,\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"languageCode\":[\"en\"],\"lastPublishedBy\":\"ee2a003a-10a9-4152-8907-a905b9e1f943\",\"version\":2,\"pragma\":[\"external\"],\"license\":\"CC BY 4.0\",\"prevState\":\"Draft\",\"size\":1.000519E7,\"lastPublishedOn\":\"2020-08-10T19:27:57.797+0000\",\"name\":\"By the Hands of the Nature\",\"status\":\"Live\",\"code\":\"6633b233-bc5a-4936-a7a0-da37ebd33868\",\"prevStatus\":\"Processing\",\"origin\":\"do_312783564254150656111171\",\"streamingUrl\":\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/content/assets/do_11307457137049600011786/eng-presentation_1597086905822.pdf\",\"medium\":[\"English\"],\"posterImage\":\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/do_312783565429334016112815/artifact/screenshot-from-2019-06-14-11-59-39_1560493827569.png\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2020-07-29T10:03:33.003+0000\",\"copyrightYear\":2019,\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2020-08-10T19:27:57.376+0000\",\"originData\":{\"identifier\":\"do_312783564254150656111171\",\"repository\":\"https://dock.sunbirded.org/do_312783564254150656111171\"},\"level1Concept\":[\"Physical Features of India\"],\"dialcodeRequired\":\"No\",\"owner\":\"Kerala SCERT\",\"lastStatusChangedOn\":\"2020-08-10T19:27:58.907+0000\",\"createdFor\":[\"0126202691023585280\"],\"creator\":\"SAJEEV THOMAS\",\"os\":[\"All\"],\"level1Name\":[\"Contemporary India - I\"],\"pkgVersion\":1.0,\"versionKey\":\"1597087677376\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"kl_k-12\",\"depth\":2,\"s3Key\":\"ecar_files/do_11307457137049600011786/by-the-hands-of-the-nature_1597087677810_do_11307457137049600011786_1.0.ecar\",\"me_averageRating\":3,\"createdBy\":\"f20a4bbf-df17-425b-8e43-bd3dd57bde83\",\"compatibilityLevel\":4,\"ownedBy\":\"0126202691023585280\",\"board\":\"CBSE\",\"resourceType\":\"Learn\"}],\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2021-03-07T19:24:58.990+0000\",\"contentEncoding\":\"gzip\",\"contentType\":\"TextBookUnit\",\"dialcodeRequired\":\"No\",\"identifier\":\"do_11323126865092608011182\",\"lastStatusChangedOn\":\"2021-03-07T19:24:58.991+0000\",\"audience\":[\"Student\"],\"os\":[\"All\"],\"visibility\":\"Parent\",\"index\":1,\"mediaType\":\"content\",\"osId\":\"org.ekstep.launcher\",\"languageCode\":[\"en\"],\"version\":2,\"versionKey\":\"1615145098991\",\"license\":\"CC BY 4.0\",\"idealScreenDensity\":\"hdpi\",\"depth\":1,\"compatibilityLevel\":1,\"name\":\"U1\",\"status\":\"Draft\"},{\"ownershipType\":[\"createdBy\"],\"parent\":\"do_11323126798764441611181\",\"code\":\"U2\",\"keywords\":[],\"credentials\":{\"enabled\":\"No\"},\"channel\":\"sunbird\",\"description\":\"U2-For Other Objects\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2021-03-07T19:24:58.993+0000\",\"objectType\":\"Collection\",\"primaryCategory\":\"Textbook Unit\",\"children\":[{\"parent\":\"do_11323126865095065611184\",\"code\":\"finemanfine\",\"previewUrl\":\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/questionset/do_113212597854404608111/do_113212597854404608111_html_1612875515166.html\",\"allowSkip\":\"Yes\",\"containsUserData\":\"No\",\"downloadUrl\":\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/questionset/do_113212597854404608111/test-question-set_1612875514981_do_113212597854404608111_5_SPINE.ecar\",\"description\":\"Updated QS Description\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.sunbird.questionset\",\"showHints\":\"No\",\"variants\":{\"spine\":\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/questionset/do_113212597854404608111/test-question-set_1612875514981_do_113212597854404608111_5_SPINE.ecar\",\"online\":\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/questionset/do_113212597854404608111/test-question-set_1612875515115_do_113212597854404608111_5_ONLINE.ecar\"},\"createdOn\":\"2021-02-09T10:19:09.026+0000\",\"objectType\":\"QuestionSet\",\"pdfUrl\":\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/questionset/do_113212597854404608111/do_113212597854404608111_pdf_1612875515932.pdf\",\"primaryCategory\":\"Practice Question Set\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2021-02-09T12:58:36.155+0000\",\"contentEncoding\":\"gzip\",\"contentType\":\"PracticeResource\",\"showSolutions\":\"Yes\",\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_113212597854404608111\",\"lastStatusChangedOn\":\"2021-02-09T12:58:36.155+0000\",\"requiresSubmit\":\"Yes\",\"visibility\":\"Default\",\"showTimer\":\"No\",\"summaryType\":\"Complete\",\"consumerId\":\"fa13b438-8a3d-41b1-8278-33b0c50210e4\",\"childNodes\":[\"do_113212598840246272112\",\"do_113212599692050432114\",\"do_113212600505057280116\"],\"index\":1,\"setType\":\"materialised\",\"languageCode\":[\"en\"],\"version\":1,\"pkgVersion\":5,\"versionKey\":\"1612875494848\",\"showFeedback\":\"Yes\",\"license\":\"CC BY 4.0\",\"depth\":2,\"lastPublishedOn\":\"2021-02-09T12:58:34.976+0000\",\"compatibilityLevel\":5,\"name\":\"Test Question Set\",\"navigationMode\":\"linear\",\"shuffle\":true,\"status\":\"Live\"}],\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2021-03-07T19:24:58.993+0000\",\"contentEncoding\":\"gzip\",\"contentType\":\"TextBookUnit\",\"dialcodeRequired\":\"No\",\"identifier\":\"do_11323126865095065611184\",\"lastStatusChangedOn\":\"2021-03-07T19:24:58.993+0000\",\"audience\":[\"Student\"],\"os\":[\"All\"],\"visibility\":\"Parent\",\"index\":2,\"mediaType\":\"content\",\"osId\":\"org.ekstep.launcher\",\"languageCode\":[\"en\"],\"version\":2,\"versionKey\":\"1615145098993\",\"license\":\"CC BY 4.0\",\"idealScreenDensity\":\"hdpi\",\"depth\":1,\"compatibilityLevel\":1,\"name\":\"U2\",\"status\":\"Draft\"}]}');"
    private val script_6 = "INSERT INTO hierarchy_store.content_hierarchy(identifier, hierarchy) values ('do_26543193441064550414', '{\"identifier\":\"do_26543193441064550414\",\"children\":[{\"parent\":\"do_26543193441064550414\",\"identifier\":\"do_11283193463014195215\",\"copyright\":\"Sunbird\",\"lastStatusChangedOn\":\"2019-08-21T14:37:50.281+0000\",\"code\":\"2e837725-d663-45da-8ace-9577ab111982\",\"visibility\":\"Parent\",\"index\":1,\"mimeType\":\"application/vnd.ekstep.content-collection\",\"createdOn\":\"2019-08-21T14:37:50.281+0000\",\"versionKey\":\"1566398270281\",\"framework\":\"tpd\",\"depth\":1,\"children\":[],\"name\":\"U1\",\"lastUpdatedOn\":\"2019-08-21T14:37:50.281+0000\",\"contentType\":\"CourseUnit\",\"status\":\"Draft\", \"objectType\":\"Collection\"}]}');"
    implicit val oec: OntologyEngineContext = new OntologyEngineContext

    override def beforeAll(): Unit = {
        super.beforeAll()
        graphDb.execute("UNWIND [{code:\"questionId\",subject:[\"Health and Physical Education\"],language:[\"English\"],medium:[\"English\"],mimeType:\"application/vnd.sunbird.question\",createdOn:\"2021-01-13T09:29:06.255+0000\",IL_FUNC_OBJECT_TYPE:\"Question\",gradeLevel:[\"Class 6\"],contentDisposition:\"inline\",lastUpdatedOn:\"2021-02-08T11:19:08.989+0000\",contentEncoding:\"gzip\",showSolutions:\"No\",allowAnonymousAccess:\"Yes\",IL_UNIQUE_ID:\"do_113193462958120275141\",lastStatusChangedOn:\"2021-02-08T11:19:08.989+0000\",visibility:\"Parent\",showTimer:\"No\",author:\"Vaibhav\",qType:\"SA\",languageCode:[\"en\"],version:1,versionKey:\"1611554879383\",showFeedback:\"No\",license:\"CC BY 4.0\",prevState:\"Review\",compatibilityLevel:4,name:\"Subjective\",topic:[\"Leaves\"],board:\"CBSE\",status:\"Live\"},{code:\"questionId\",subject:[\"Health and Physical Education\"],language:[\"English\"],medium:[\"English\"],mimeType:\"application/vnd.sunbird.question\",createdOn:\"2021-01-13T09:29:06.255+0000\",IL_FUNC_OBJECT_TYPE:\"Question\",gradeLevel:[\"Class 6\"],contentDisposition:\"inline\",lastUpdatedOn:\"2021-02-08T11:19:08.989+0000\",contentEncoding:\"gzip\",showSolutions:\"No\",allowAnonymousAccess:\"Yes\",IL_UNIQUE_ID:\"do_113193462958120960141\",lastStatusChangedOn:\"2021-02-08T11:19:08.989+0000\",visibility:\"Parent\",showTimer:\"No\",author:\"Vaibhav\",qType:\"SA\",languageCode:[\"en\"],version:1,versionKey:\"1611554879383\",showFeedback:\"No\",license:\"CC BY 4.0\",prevState:\"Review\",compatibilityLevel:4,name:\"Subjective\",topic:[\"Leaves\"],board:\"CBSE\",status:\"Draft\"},{ownershipType:[\"createdBy\"],copyright:\"ORG_002\",previewUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_112831862871203840114/small.mp4\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_112831862871203840114/test-resource-cert_1566389713658_do_112831862871203840114_1.0.ecar\",channel:\"01246944855007232011\",organisation:[\"ORG_002\"],showNotification:true,language:[\"English\"],mimeType:\"video/mp4\",variants:\"{\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_112831862871203840114/test-resource-cert_1566389714022_do_112831862871203840114_1.0_spine.ecar\\\",\\\"size\\\":35757.0}}\",appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_112831862871203840114/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",appId:\"dev.sunbird.portal\",artifactUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_112831862871203840114/small.mp4\",contentEncoding:\"identity\",lockKey:\"be6bc445-c75e-471d-b46f-71fefe4a1d2f\",contentType:\"Resource\",lastUpdatedBy:\"c4cc494f-04c3-49f3-b3d5-7b1a1984abad\",audience:[\"Student\"],visibility:\"Default\",consumerId:\"273f3b18-5dda-4a27-984a-060c7cd398d3\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"System\",version:1,license:\"Creative Commons Attribution (CC BY)\",prevState:\"Draft\",lastPublishedOn:\"2019-08-21T12:15:13.652+0000\",size:416488,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"Test Resource Cert\",status:\"Live\",code:\"7e6630c7-3818-4319-92ac-4d08c33904d8\",streamingUrl:\"https://sunbirddevmedia-inct.streaming.media.azure.net/25d7a94c-9be3-471c-926b-51eb5d3c4c2c/small.ism/manifest(format=m3u8-aapl-v3)\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-08-21T12:11:50.644+0000\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-08-21T12:15:13.020+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-08-21T12:30:16.783+0000\",dialcodeRequired:\"No\",creator:\"Pradyumna\",lastStatusChangedOn:\"2019-08-21T12:15:14.384+0000\",createdFor:[\"01246944855007232011\"],os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",pkgVersion:1,versionKey:\"1566389713020\",idealScreenDensity:\"hdpi\",s3Key:\"ecar_files/do_112831862871203840114/test-resource-cert_1566389713658_do_112831862871203840114_1.0.ecar\",framework:\"K-12\",createdBy:\"c4cc494f-04c3-49f3-b3d5-7b1a1984abad\",compatibilityLevel:1,IL_UNIQUE_ID:\"do_112831862871203840114\",resourceType:\"Learn\"},{ownershipType:[\"createdBy\"],copyright:\"Sunbird\",certTemplate:\"[{\\\"name\\\":\\\"100PercentCompletionCertificate\\\",\\\"issuer\\\":{\\\"name\\\":\\\"Gujarat Council of Educational Research and Training\\\",\\\"url\\\":\\\"https://gcert.gujarat.gov.in/gcert/\\\",\\\"publicKey\\\":[\\\"1\\\",\\\"2\\\"]},\\\"signatoryList\\\":[{\\\"name\\\":\\\"CEO Gujarat\\\",\\\"id\\\":\\\"CEO\\\",\\\"designation\\\":\\\"CEO\\\",\\\"image\\\":\\\"https://cdn.pixabay.com/photo/2014/11/09/08/06/signature-523237__340.jpg\\\"}],\\\"htmlTemplate\\\":\\\"https://drive.google.com/uc?authuser=1&id=1ryB71i0Oqn2c3aqf9N6Lwvet-MZKytoM&export=download\\\",\\\"notifyTemplate\\\":{\\\"subject\\\":\\\"Course completion certificate\\\",\\\"stateImgUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/orgemailtemplate/img/File-0128212938260643843.png\\\",\\\"regardsperson\\\":\\\"Chairperson\\\",\\\"regards\\\":\\\"Minister of Gujarat\\\",\\\"emailTemplateType\\\":\\\"defaultCertTemp\\\"}}]\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550414/test-prad-course-cert_1566398313947_do_11283193441064550414_1.0_spine.ecar\",channel:\"b00bc992ef25f1a9a8d63291e20efc8d\",organisation:[\"Sunbird\"],language:[\"English\"],variants:\"{\\\"online\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550414/test-prad-course-cert_1566398314186_do_11283193441064550414_1.0_online.ecar\\\",\\\"size\\\":4034.0},\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550414/test-prad-course-cert_1566398313947_do_11283193441064550414_1.0_spine.ecar\\\",\\\"size\\\":73256.0}}\",mimeType:\"application/vnd.ekstep.content-collection\",leafNodes:[\"do_112831862871203840114\"],c_sunbird_dev_private_batch_count:0,appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11283193441064550414/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",appId:\"local.sunbird.portal\",contentEncoding:\"gzip\",lockKey:\"b079cf15-9e45-4865-be56-2edafa432dd3\",mimeTypesCount:\"{\\\"application/vnd.ekstep.content-collection\\\":1,\\\"video/mp4\\\":1}\",totalCompressedSize:416488,contentType:\"Course\",lastUpdatedBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",audience:[\"Student\"],toc_url:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11283193441064550414/artifact/do_11283193441064550414_toc.json\",visibility:\"Default\",contentTypesCount:\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":1}\",author:\"b00bc992ef25f1a9a8d63291e20efc8d\",childNodes:[\"do_11283193463014195215\"],consumerId:\"273f3b18-5dda-4a27-984a-060c7cd398d3\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"System\",version:2,license:\"Creative Commons Attribution (CC BY)\",prevState:\"Draft\",size:73256,lastPublishedOn:\"2019-08-21T14:38:33.816+0000\",IL_FUNC_OBJECT_TYPE:\"Collection\",name:\"test prad course cert\",status:\"Live\",code:\"org.sunbird.SUi47U\",description:\"Enter description for Course\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-08-21T14:37:23.486+0000\",reservedDialcodes:\"{\\\"I1X4R4\\\":0}\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-08-21T14:38:33.212+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-11-13T12:54:08.295+0000\",dialcodeRequired:\"No\",creator:\"Creation\",createdFor:[\"ORG_001\"],lastStatusChangedOn:\"2019-08-21T14:38:34.540+0000\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",pkgVersion:1,versionKey:\"1566398313212\",idealScreenDensity:\"hdpi\",dialcodes:[\"I1X4R4\"],s3Key:\"ecar_files/do_11283193441064550414/test-prad-course-cert_1566398313947_do_11283193441064550414_1.0_spine.ecar\",depth:0,framework:\"tpd\",me_averageRating:5,createdBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",leafNodesCount:1,compatibilityLevel:4,IL_UNIQUE_ID:\"do_11283193441064550414\",c_sunbird_dev_open_batch_count:0,resourceType:\"Course\"}] as row CREATE (n:domain) SET n += row")
        executeCassandraQuery(script_1, script_2, script_3)
        RedisCache.delete("hierarchy_do_11283193441064550414")
    }


    "addLeafNodesToHierarchy" should "addLeafNodesToHierarchy" in {
        executeCassandraQuery(script_3)
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Collection")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "b00bc992ef25f1a9a8d63291e20efc8d")
            }
        })

        request.put("rootId", "do_11283193441064550414")
        request.put("unitId", "do_11283193463014195215")
        request.put("children", util.Arrays.asList("do_112831862871203840114"))
        request.put("mode","edit")
        val future = HierarchyManager.addLeafNodesToHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 200)
            assert(response.getResult.get("do_11283193463014195215").asInstanceOf[util.List[String]].containsAll(request.get("children").asInstanceOf[util.List[String]]))
            val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'")
                    .one().getString("hierarchy")
            assert(!response.getResult.get("do_11283193463014195215").asInstanceOf[util.List[String]].contains("do_11283193463014195215"))
            assert(hierarchy.contains("do_112831862871203840114"))
        })
    }

    "addLeafNodesToHierarchy with children having different objectType than supported one" should "throw ClientException" in {
        executeCassandraQuery(script_3)
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],code:\"SC-2200_3eac25ae-a0c9-4d7c-87be-954406824cb8\",channel:\"sunbird\",description:\"Test-Add/Remove Leaf Node\",language:[\"English\"],mimeType:\"video/mp4\",idealScreenSize:\"normal\",createdOn:\"2021-03-07T19:23:38.025+0000\",IL_FUNC_OBJECT_TYPE:\"Asset\",contentDisposition:\"inline\",additionalCategories:[\"Textbook\"],lastUpdatedOn:\"2021-03-07T19:24:59.023+0000\",contentEncoding:\"gzip\",contentType:\"Resource\",dialcodeRequired:\"No\",IL_UNIQUE_ID:\"do_asset_001\",lastStatusChangedOn:\"2021-03-07T19:23:38.025+0000\",audience:[\"Student\"],os:[\"All\"],visibility:\"Default\",mediaType:\"asset\",osId:\"org.ekstep.quiz.app\",languageCode:[\"en\"],version:2,versionKey:\"1615145099023\",license:\"CC BY 4.0\",idealScreenDensity:\"hdpi\",depth:0,compatibilityLevel:1,userConsent:\"Yes\",name:\"SC-2200-TextBook\",status:\"Draft\"}] as row CREATE (n:domain) SET n += row")
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Collection")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "b00bc992ef25f1a9a8d63291e20efc8d")
            }
        })

        request.put("rootId", "do_11283193441064550414")
        request.put("unitId", "do_11283193463014195215")
        request.put("children", util.Arrays.asList("do_asset_001"))
        request.put("mode","edit")
        recoverToSucceededIf[ClientException](HierarchyManager.addLeafNodesToHierarchy(request))
    }

    ignore  should "add the Question with draft status into hierarchy" in {
        executeCassandraQuery(script_3)
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Collection")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "b00bc992ef25f1a9a8d63291e20efc8d")
            }
        })

        request.put("rootId", "do_11283193441064550414")
        request.put("unitId", "do_11283193463014195215")
        request.put("children", util.Arrays.asList("do_113193462958120960141"))
        request.put("mode","edit")
        val future = HierarchyManager.addLeafNodesToHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 200)
            assert(response.getResult.get("do_11283193463014195215").asInstanceOf[util.List[String]].containsAll(request.get("children").asInstanceOf[util.List[String]]))
            val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'")
              .one().getString("hierarchy")
            assert(!response.getResult.get("do_11283193463014195215").asInstanceOf[util.List[String]].contains("do_11283193463014195215"))
            assert(hierarchy.contains("do_113193462958120960141"))
        })
    }

    ignore should "add Question as Leaf Node" in {
        executeCassandraQuery(script_3)
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Collection")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "b00bc992ef25f1a9a8d63291e20efc8d")
            }
        })

        request.put("rootId", "do_11283193441064550414")
        request.put("unitId", "do_11283193463014195215")
        request.put("children", util.Arrays.asList("do_113193462958120275141"))
        request.put("mode","edit")
        val future = HierarchyManager.addLeafNodesToHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 200)
            assert(response.getResult.get("do_11283193463014195215").asInstanceOf[util.List[String]].containsAll(request.get("children").asInstanceOf[util.List[String]]))
            val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'")
              .one().getString("hierarchy")
            assert(!response.getResult.get("do_11283193463014195215").asInstanceOf[util.List[String]].contains("do_11283193463014195215"))
            assert(hierarchy.contains("do_113193462958120275141"))
        })
    }

    "addLeafNodesToHierarchy for QuestionSet object" should "addLeafNodesToHierarchy" in {
        executeCassandraQuery(script_3)
        graphDb.execute("UNWIND [{copyright:\"Hello\",code:\"do_113193433773948928111\",allowSkip:\"Yes\",keywords:[\"135\",\"666667\"],containsUserData:\"No\",subject:[\"Hindi\"],description:\"Hello\",language:[\"English\"],medium:[\"Hindi\"],mimeType:\"application/vnd.sunbird.questionset\",showHints:\"No\",createdOn:\"2021-01-13T08:29:43.736+0000\",IL_FUNC_OBJECT_TYPE:\"QuestionSet\",gradeLevel:[\"Class 7\"],contentDisposition:\"inline\",additionalCategories:[\"Classroom Teaching Video\",\"Concept Map\",\"Textbook\",\"Curiosity Question Set\"],lastUpdatedOn:\"2021-02-08T12:20:33.201+0000\",contentEncoding:\"gzip\",showSolutions:\"Yes\",allowAnonymousAccess:\"Yes\",IL_UNIQUE_ID:\"do_113193433773948928111\",lastStatusChangedOn:\"2021-01-29T06:13:52.095+0000\",audience:[\"Teacher\"],requiresSubmit:\"Yes\",visibility:\"Default\",showTimer:\"Yes\",author:\"Hello\",summaryType:\"Complete\",consumerId:\"fa13b438-8a3d-41b1-8278-33b0c50210e4\",childNodes:[\"do_113193462958120960141\",\"do_113193463656955904143\",\"do_113197944463515648120\",\"do_113209072358883328150\",\"do_113193462438895616139\"],setType:\"materialised\",languageCode:[\"en\"],version:1,versionKey:\"1612786833201\",showFeedback:\"Yes\",license:\"CC BY 4.0\",prevState:\"Draft\",framework:\"ekstep_ncert_k-12\",depth:0,compatibilityLevel:4,name:\"u0926u0941u0903u0916 u0915u093E u0905u0927u093Fu0915u093Eu0930\",navigationMode:\"linear\",topic:[\"Leaves\",\"Water\"],shuffle:true,attributions:[\"Hello\"],board:\"CBSE\",status:\"Review\"}] as row CREATE (n:domain) SET n += row")
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Collection")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "b00bc992ef25f1a9a8d63291e20efc8d")
            }
        })

        request.put("rootId", "do_11283193441064550414")
        request.put("unitId", "do_11283193463014195215")
        request.put("children", util.Arrays.asList("do_113193433773948928111"))
        request.put("mode","edit")
        val future = HierarchyManager.addLeafNodesToHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 200)
            assert(response.getResult.get("do_11283193463014195215").asInstanceOf[util.List[String]].containsAll(request.get("children").asInstanceOf[util.List[String]]))
            val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'")
              .one().getString("hierarchy")
            assert(!response.getResult.get("do_11283193463014195215").asInstanceOf[util.List[String]].contains("do_11283193463014195215"))
            assert(hierarchy.contains("do_113193433773948928111"))
        })
    }

    ignore  should "remove Question object from hierarchy" in {
        executeCassandraQuery(script_6)
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],code:\"SC-2200_3eac25ae-a0c9-4d7c-87be-954406824cb8\",channel:\"sunbird\",description:\"Test-Add/Remove Leaf Node\",language:[\"English\"],mimeType:\"application/vnd.ekstep.content-collection\",idealScreenSize:\"normal\",createdOn:\"2021-03-07T19:23:38.025+0000\",IL_FUNC_OBJECT_TYPE:\"Collection\",contentDisposition:\"inline\",additionalCategories:[\"Textbook\"],lastUpdatedOn:\"2021-03-07T19:24:59.023+0000\",contentEncoding:\"gzip\",contentType:\"TextBook\",dialcodeRequired:\"No\",IL_UNIQUE_ID:\"do_26543193441064550414\",lastStatusChangedOn:\"2021-03-07T19:23:38.025+0000\",audience:[\"Student\"],os:[\"All\"],visibility:\"Default\",childNodes:[\"do_11307457137049600011786\",\"do_11323126865092608011182\",\"do_113212597854404608111\",\"do_11323126865095065611184\"],mediaType:\"content\",osId:\"org.ekstep.quiz.app\",languageCode:[\"en\"],version:2,versionKey:\"1615145099023\",license:\"CC BY 4.0\",idealScreenDensity:\"hdpi\",depth:0,compatibilityLevel:1,userConsent:\"Yes\",name:\"SC-2200-TextBook\",status:\"Draft\"},{copyright:\"Hello\",code:\"do_113193433773948928111\",allowSkip:\"Yes\",keywords:[\"135\",\"666667\"],containsUserData:\"No\",subject:[\"Hindi\"],description:\"Hello\",language:[\"English\"],medium:[\"Hindi\"],mimeType:\"application/vnd.sunbird.questionset\",showHints:\"No\",createdOn:\"2021-01-13T08:29:43.736+0000\",IL_FUNC_OBJECT_TYPE:\"QuestionSet\",gradeLevel:[\"Class 7\"],contentDisposition:\"inline\",additionalCategories:[\"Classroom Teaching Video\",\"Concept Map\",\"Textbook\",\"Curiosity Question Set\"],lastUpdatedOn:\"2021-02-08T12:20:33.201+0000\",contentEncoding:\"gzip\",showSolutions:\"Yes\",allowAnonymousAccess:\"Yes\",IL_UNIQUE_ID:\"do_113193433773948928111\",lastStatusChangedOn:\"2021-01-29T06:13:52.095+0000\",audience:[\"Teacher\"],requiresSubmit:\"Yes\",visibility:\"Default\",showTimer:\"Yes\",author:\"Hello\",summaryType:\"Complete\",consumerId:\"fa13b438-8a3d-41b1-8278-33b0c50210e4\",childNodes:[\"do_113193462958120960141\",\"do_113193463656955904143\",\"do_113197944463515648120\",\"do_113209072358883328150\",\"do_113193462438895616139\"],setType:\"materialised\",languageCode:[\"en\"],version:1,versionKey:\"1612786833201\",showFeedback:\"Yes\",license:\"CC BY 4.0\",prevState:\"Draft\",framework:\"ekstep_ncert_k-12\",depth:0,compatibilityLevel:4,name:\"u0926u0941u0903u0916 u0915u093E u0905u0927u093Fu0915u093Eu0930\",navigationMode:\"linear\",topic:[\"Leaves\",\"Water\"],shuffle:true,attributions:[\"Hello\"],board:\"CBSE\",status:\"Review\"}] as row CREATE (n:domain) SET n += row")
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Collection")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "b00bc992ef25f1a9a8d63291e20efc8d")
            }
        })

        request.put("rootId", "do_26543193441064550414")
        request.put("unitId", "do_11283193463014195215")
        request.put("children", util.Arrays.asList("do_113193433773948928111"))
        request.put("mode","edit")
        val future = HierarchyManager.addLeafNodesToHierarchy(request)
        future.map(response => {
            println("result ::::=="+response.getResult)
            assert(response.getResponseCode.code() == 200)
            val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_26543193441064550414.img'")
              .one().getString("hierarchy")
            assert(hierarchy.contains("do_113193433773948928111"))
            val removeFuture = HierarchyManager.removeLeafNodesFromHierarchy(request)
            removeFuture.map(resp => {
                assert(resp.getResponseCode.code() == 200)
                val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_26543193441064550414.img'")
                  .one().getString("hierarchy")
                assert(!hierarchy.contains("do_113193433773948928111"))
            })
        }).flatMap(f => f)
    }

    ignore should "removeLeafNodesToHierarchy" in {
        executeCassandraQuery(script_3)
        graphDb.execute("UNWIND [{copyright:\"Hello\",code:\"do_113193433773948928111\",allowSkip:\"Yes\",keywords:[\"135\",\"666667\"],containsUserData:\"No\",subject:[\"Hindi\"],description:\"Hello\",language:[\"English\"],medium:[\"Hindi\"],mimeType:\"application/vnd.sunbird.questionset\",showHints:\"No\",createdOn:\"2021-01-13T08:29:43.736+0000\",IL_FUNC_OBJECT_TYPE:\"QuestionSet\",gradeLevel:[\"Class 7\"],contentDisposition:\"inline\",additionalCategories:[\"Classroom Teaching Video\",\"Concept Map\",\"Textbook\",\"Curiosity Question Set\"],lastUpdatedOn:\"2021-02-08T12:20:33.201+0000\",contentEncoding:\"gzip\",showSolutions:\"Yes\",allowAnonymousAccess:\"Yes\",IL_UNIQUE_ID:\"do_113193433773948928111\",lastStatusChangedOn:\"2021-01-29T06:13:52.095+0000\",audience:[\"Teacher\"],requiresSubmit:\"Yes\",visibility:\"Default\",showTimer:\"Yes\",author:\"Hello\",summaryType:\"Complete\",consumerId:\"fa13b438-8a3d-41b1-8278-33b0c50210e4\",childNodes:[\"do_113193462958120960141\",\"do_113193463656955904143\",\"do_113197944463515648120\",\"do_113209072358883328150\",\"do_113193462438895616139\"],setType:\"materialised\",languageCode:[\"en\"],version:1,versionKey:\"1612786833201\",showFeedback:\"Yes\",license:\"CC BY 4.0\",prevState:\"Draft\",framework:\"ekstep_ncert_k-12\",depth:0,compatibilityLevel:4,name:\"u0926u0941u0903u0916 u0915u093E u0905u0927u093Fu0915u093Eu0930\",navigationMode:\"linear\",topic:[\"Leaves\",\"Water\"],shuffle:true,attributions:[\"Hello\"],board:\"CBSE\",status:\"Review\"}] as row CREATE (n:domain) SET n += row")
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Collection")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "b00bc992ef25f1a9a8d63291e20efc8d")
            }
        })

        request.put("rootId", "do_11283193441064550414")
        request.put("unitId", "do_11283193463014195215")
        request.put("children", util.Arrays.asList("do_113193462958120275141"))
        request.put("mode","edit")
        val future = HierarchyManager.addLeafNodesToHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 200)
            val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'")
              .one().getString("hierarchy")
            assert(hierarchy.contains("do_113193462958120275141"))
            val removeFuture = HierarchyManager.removeLeafNodesFromHierarchy(request)
            removeFuture.map(resp => {
                assert(resp.getResponseCode.code() == 200)
                val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'")
                  .one().getString("hierarchy")
                assert(!hierarchy.contains("do_113193462958120275141"))
            })
        }).flatMap(f => f)
    }

    "removeLeafNodesToHierarchy" should "removeLeafNodesToHierarchy" in {
        executeCassandraQuery(script_3)
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Collection")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "b00bc992ef25f1a9a8d63291e20efc8d")
            }
        })

        request.put("rootId", "do_11283193441064550414")
        request.put("unitId", "do_11283193463014195215")
        request.put("children", util.Arrays.asList("do_112831862871203840114"))
        request.put("mode","edit")
        val future = HierarchyManager.addLeafNodesToHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 200)
            val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'")
                    .one().getString("hierarchy")
            assert(hierarchy.contains("do_112831862871203840114"))
            val removeFuture = HierarchyManager.removeLeafNodesFromHierarchy(request)
            removeFuture.map(resp => {
                assert(resp.getResponseCode.code() == 200)
                val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.content_hierarchy where identifier='do_11283193441064550414.img'")
                        .one().getString("hierarchy")
                assert(!hierarchy.contains("do_112831862871203840114"))
            })
        }).flatMap(f => f)
    }

    "addLeafNodesToHierarchy for shallowcopied" should "throw client exception " in {
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],copyright:\"Sunbird\",certTemplate:\"[{\\\"name\\\":\\\"100PercentCompletionCertificate\\\",\\\"issuer\\\":{\\\"name\\\":\\\"Gujarat Council of Educational Research and Training\\\",\\\"url\\\":\\\"https://gcert.gujarat.gov.in/gcert/\\\",\\\"publicKey\\\":[\\\"1\\\",\\\"2\\\"]},\\\"signatoryList\\\":[{\\\"name\\\":\\\"CEO Gujarat\\\",\\\"id\\\":\\\"CEO\\\",\\\"designation\\\":\\\"CEO\\\",\\\"image\\\":\\\"https://cdn.pixabay.com/photo/2014/11/09/08/06/signature-523237__340.jpg\\\"}],\\\"htmlTemplate\\\":\\\"https://drive.google.com/uc?authuser=1&id=1ryB71i0Oqn2c3aqf9N6Lwvet-MZKytoM&export=download\\\",\\\"notifyTemplate\\\":{\\\"subject\\\":\\\"Course completion certificate\\\",\\\"stateImgUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/orgemailtemplate/img/File-0128212938260643843.png\\\",\\\"regardsperson\\\":\\\"Chairperson\\\",\\\"regards\\\":\\\"Minister of Gujarat\\\",\\\"emailTemplateType\\\":\\\"defaultCertTemp\\\"}}]\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11298480837245337614/test-prad-course-cert_1566398313947_do_11298480837245337614_1.0_spine.ecar\",channel:\"b00bc992ef25f1a9a8d63291e20efc8d\",organisation:[\"Sunbird\"],language:[\"English\"],variants:\"{\\\"online\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11298480837245337614/test-prad-course-cert_1566398314186_do_11298480837245337614_1.0_online.ecar\\\",\\\"size\\\":4034.0},\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11298480837245337614/test-prad-course-cert_1566398313947_do_11298480837245337614_1.0_spine.ecar\\\",\\\"size\\\":73256.0}}\",mimeType:\"application/vnd.ekstep.content-collection\",leafNodes:[\"do_112831862871203840114\"],c_sunbird_dev_private_batch_count:0,appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11298480837245337614/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",appId:\"local.sunbird.portal\",contentEncoding:\"gzip\",lockKey:\"b079cf15-9e45-4865-be56-2edafa432dd3\",mimeTypesCount:\"{\\\"application/vnd.ekstep.content-collection\\\":1,\\\"video/mp4\\\":1}\",totalCompressedSize:416488,contentType:\"Course\",lastUpdatedBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",audience:[\"Student\"],toc_url:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11298480837245337614/artifact/do_11298480837245337614_toc.json\",visibility:\"Default\",contentTypesCount:\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":1}\",author:\"b00bc992ef25f1a9a8d63291e20efc8d\",childNodes:[\"do_11283193463014195215\"],consumerId:\"273f3b18-5dda-4a27-984a-060c7cd398d3\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"System\",version:2,license:\"Creative Commons Attribution (CC BY)\",prevState:\"Draft\",size:73256,lastPublishedOn:\"2019-08-21T14:38:33.816+0000\",IL_FUNC_OBJECT_TYPE:\"Content\",name:\"test prad course cert\",status:\"Live\",code:\"org.sunbird.SUi47U\",description:\"Enter description for Course\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-08-21T14:37:23.486+0000\",reservedDialcodes:\"{\\\"I1X4R4\\\":0}\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-08-21T14:38:33.212+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-11-13T12:54:08.295+0000\",dialcodeRequired:\"No\",creator:\"Creation\",createdFor:[\"ORG_001\"],lastStatusChangedOn:\"2019-08-21T14:38:34.540+0000\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",pkgVersion:1,versionKey:\"1566398313212\",idealScreenDensity:\"hdpi\",dialcodes:[\"I1X4R4\"],s3Key:\"ecar_files/do_11298480837245337614/test-prad-course-cert_1566398313947_do_11298480837245337614_1.0_spine.ecar\",depth:0,framework:\"tpd\",me_averageRating:5,createdBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",leafNodesCount:1,compatibilityLevel:4,IL_UNIQUE_ID:\"do_11298480837245337614\",c_sunbird_dev_open_batch_count:0,resourceType:\"Course\",originData:\"{\\\"name\\\":\\\"Copy Collecction Testing For shallow Copy\\\",\\\"copyType\\\":\\\"shallow\\\",\\\"license\\\":\\\"CC BY 4.0\\\",\\\"organisation\\\":[\\\"test\\\"]}\"}] as row CREATE (n:domain) SET n += row");
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Content")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "b00bc992ef25f1a9a8d63291e20efc8d")
            }
        })

        request.put("rootId", "do_11298480837245337614")
        request.put("unitId", "do_11283193463014195215")
        request.put("children", util.Arrays.asList("do_112831862871203840114"))
        request.put("mode","edit")

        recoverToSucceededIf[ClientException](HierarchyManager.addLeafNodesToHierarchy(request))

    }

    "removeLeafNodesToHierarchy for shallowcopied" should "throw client exception " in {
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],copyright:\"Sunbird\",certTemplate:\"[{\\\"name\\\":\\\"100PercentCompletionCertificate\\\",\\\"issuer\\\":{\\\"name\\\":\\\"Gujarat Council of Educational Research and Training\\\",\\\"url\\\":\\\"https://gcert.gujarat.gov.in/gcert/\\\",\\\"publicKey\\\":[\\\"1\\\",\\\"2\\\"]},\\\"signatoryList\\\":[{\\\"name\\\":\\\"CEO Gujarat\\\",\\\"id\\\":\\\"CEO\\\",\\\"designation\\\":\\\"CEO\\\",\\\"image\\\":\\\"https://cdn.pixabay.com/photo/2014/11/09/08/06/signature-523237__340.jpg\\\"}],\\\"htmlTemplate\\\":\\\"https://drive.google.com/uc?authuser=1&id=1ryB71i0Oqn2c3aqf9N6Lwvet-MZKytoM&export=download\\\",\\\"notifyTemplate\\\":{\\\"subject\\\":\\\"Course completion certificate\\\",\\\"stateImgUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/orgemailtemplate/img/File-0128212938260643843.png\\\",\\\"regardsperson\\\":\\\"Chairperson\\\",\\\"regards\\\":\\\"Minister of Gujarat\\\",\\\"emailTemplateType\\\":\\\"defaultCertTemp\\\"}}]\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11298480837245337614/test-prad-course-cert_1566398313947_do_11298480837245337614_1.0_spine.ecar\",channel:\"b00bc992ef25f1a9a8d63291e20efc8d\",organisation:[\"Sunbird\"],language:[\"English\"],variants:\"{\\\"online\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11298480837245337614/test-prad-course-cert_1566398314186_do_11298480837245337614_1.0_online.ecar\\\",\\\"size\\\":4034.0},\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11298480837245337614/test-prad-course-cert_1566398313947_do_11298480837245337614_1.0_spine.ecar\\\",\\\"size\\\":73256.0}}\",mimeType:\"application/vnd.ekstep.content-collection\",leafNodes:[\"do_112831862871203840114\"],c_sunbird_dev_private_batch_count:0,appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11298480837245337614/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",appId:\"local.sunbird.portal\",contentEncoding:\"gzip\",lockKey:\"b079cf15-9e45-4865-be56-2edafa432dd3\",mimeTypesCount:\"{\\\"application/vnd.ekstep.content-collection\\\":1,\\\"video/mp4\\\":1}\",totalCompressedSize:416488,contentType:\"Course\",lastUpdatedBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",audience:[\"Student\"],toc_url:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11298480837245337614/artifact/do_11298480837245337614_toc.json\",visibility:\"Default\",contentTypesCount:\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":1}\",author:\"b00bc992ef25f1a9a8d63291e20efc8d\",childNodes:[\"do_11283193463014195215\"],consumerId:\"273f3b18-5dda-4a27-984a-060c7cd398d3\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"System\",version:2,license:\"Creative Commons Attribution (CC BY)\",prevState:\"Draft\",size:73256,lastPublishedOn:\"2019-08-21T14:38:33.816+0000\",IL_FUNC_OBJECT_TYPE:\"Content\",name:\"test prad course cert\",status:\"Live\",code:\"org.sunbird.SUi47U\",description:\"Enter description for Course\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-08-21T14:37:23.486+0000\",reservedDialcodes:\"{\\\"I1X4R4\\\":0}\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-08-21T14:38:33.212+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-11-13T12:54:08.295+0000\",dialcodeRequired:\"No\",creator:\"Creation\",createdFor:[\"ORG_001\"],lastStatusChangedOn:\"2019-08-21T14:38:34.540+0000\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",pkgVersion:1,versionKey:\"1566398313212\",idealScreenDensity:\"hdpi\",dialcodes:[\"I1X4R4\"],s3Key:\"ecar_files/do_11298480837245337614/test-prad-course-cert_1566398313947_do_11298480837245337614_1.0_spine.ecar\",depth:0,framework:\"tpd\",me_averageRating:5,createdBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",leafNodesCount:1,compatibilityLevel:4,IL_UNIQUE_ID:\"do_11298480837245337614\",c_sunbird_dev_open_batch_count:0,resourceType:\"Course\",originData:\"{\\\"name\\\":\\\"Copy Collecction Testing For shallow Copy\\\",\\\"copyType\\\":\\\"shallow\\\",\\\"license\\\":\\\"CC BY 4.0\\\",\\\"organisation\\\":[\\\"test\\\"]}\"}] as row CREATE (n:domain) SET n += row");
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Content")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "b00bc992ef25f1a9a8d63291e20efc8d")
            }
        })

        request.put("rootId", "do_11298480837245337614")
        request.put("unitId", "do_11283193463014195215")
        request.put("children", util.Arrays.asList("do_112831862871203840114"))
        request.put("mode","edit")

        recoverToSucceededIf[ClientException](HierarchyManager.removeLeafNodesFromHierarchy(request))

    }

//    "getHierarchyWithInvalidIdentifier" should "Resourse_Not_Found" in {
//        val request = new Request()
//        request.setContext(new util.HashMap[String, AnyRef]() {
//            {
//                put("objectType", "Content")
//                put("graph_id", "domain")
//                put("version", "1.0")
//                put("schemaName", "collection")
//            }
//        })
//        request.put("rootId", "1234")
//        val future = HierarchyManager.getHierarchy(request)
//        future.map(response => {
//            assert(response.getResponseCode.code() == 404)
//        })
//    }

    "getHierarchyForPublishedContent" should "getHierarchy" in {
        val request = new Request()
        executeCassandraQuery(script_4)
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Content")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
            }
        })
        request.put("rootId", "do_11283193441064550414")
        val future = HierarchyManager.getHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 200)
            assert(null != response.getResult.get("content"))
            assert(null != response.getResult.get("content").asInstanceOf[util.Map[String, AnyRef]].get("children"))
        })
    }

    "getHierarchyWithEditMode" should "getHierarchy" in {
        val request = new Request()
        executeCassandraQuery(script_3)
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Content")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
            }
        })
        request.put("mode","edit")
        request.put("rootId", "do_11283193441064550414")
        val future = HierarchyManager.getHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 200)
            assert(null != response.getResult.get("content"))
            assert(null != response.getResult.get("content").asInstanceOf[util.Map[String, AnyRef]].get("children"))
        })
    }

    "getHierarchyWithEditMode having QuestionSet object" should "return the hierarchy" in {
        val request = new Request()
        executeCassandraQuery(script_5)
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],code:\"SC-2200_3eac25ae-a0c9-4d7c-87be-954406824cb8\",channel:\"sunbird\",description:\"Test-Add/Remove Leaf Node\",language:[\"English\"],mimeType:\"application/vnd.ekstep.content-collection\",idealScreenSize:\"normal\",createdOn:\"2021-03-07T19:23:38.025+0000\",IL_FUNC_OBJECT_TYPE:\"Collection\",contentDisposition:\"inline\",additionalCategories:[\"Textbook\"],lastUpdatedOn:\"2021-03-07T19:24:59.023+0000\",contentEncoding:\"gzip\",contentType:\"TextBook\",dialcodeRequired:\"No\",IL_UNIQUE_ID:\"do_11323126798764441611181\",lastStatusChangedOn:\"2021-03-07T19:23:38.025+0000\",audience:[\"Student\"],os:[\"All\"],visibility:\"Default\",childNodes:[\"do_11307457137049600011786\",\"do_11323126865092608011182\",\"do_113212597854404608111\",\"do_11323126865095065611184\"],mediaType:\"content\",osId:\"org.ekstep.quiz.app\",languageCode:[\"en\"],version:2,versionKey:\"1615145099023\",license:\"CC BY 4.0\",idealScreenDensity:\"hdpi\",depth:0,compatibilityLevel:1,userConsent:\"Yes\",name:\"SC-2200-TextBook\",status:\"Draft\"}] as row CREATE (n:domain) SET n += row")
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],copyright:\"Kerala State\",previewUrl:\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/content/assets/do_11307457137049600011786/eng-presentation_1597086905822.pdf\",keywords:[\"By the Hands of the Nature\"],subject:[\"Geography\"],channel:\"0126202691023585280\",downloadUrl:\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/ecar_files/do_11307457137049600011786/by-the-hands-of-the-nature_1597087677810_do_11307457137049600011786_1.0.ecar\",organisation:[\"Kerala State\"],textbook_name:[\"Contemporary India - I\"],showNotification:true,language:[\"English\"],source:\"Kl 4\",mimeType:\"application/pdf\",IL_FUNC_OBJECT_TYPE:\"Content\",sourceURL:\"https://diksha.gov.in/play/content/do_312783564254150656111171\",gradeLevel:[\"Class 9\"],me_totalRatingsCount:36,appIcon:\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/do_312783564254150656111171/artifact/screenshot-from-2019-06-14-11-59-39_1560493827569.thumb.png\",level2Name:[\"Physical Features of India\"],appId:\"prod.diksha.portal\",contentEncoding:\"identity\",artifactUrl:\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/content/assets/do_11307457137049600011786/eng-presentation_1597086905822.pdf\",sYS_INTERNAL_LAST_UPDATED_ON:\"2020-08-10T19:27:58.911+0000\",contentType:\"Resource\",IL_UNIQUE_ID:\"do_11307457137049600011786\",audience:[\"Student\"],visibility:\"Default\",author:\"Kerala State\",consumerId:\"89490534-126f-4f0b-82ac-3ff3e49f3468\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",languageCode:[\"en\"],lastPublishedBy:\"ee2a003a-10a9-4152-8907-a905b9e1f943\",version:2,pragma:[\"external\"],license:\"CC BY 4.0\",prevState:\"Draft\",size:10005190,lastPublishedOn:\"2020-08-10T19:27:57.797+0000\",name:\"By the Hands of the Nature\",status:\"Live\",code:\"6633b233-bc5a-4936-a7a0-da37ebd33868\",prevStatus:\"Processing\",origin:\"do_312783564254150656111171\",streamingUrl:\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/content/assets/do_11307457137049600011786/eng-presentation_1597086905822.pdf\",medium:[\"English\"],posterImage:\"https://ntpproductionall.blob.core.windows.net/ntp-content-production/content/do_312783565429334016112815/artifact/screenshot-from-2019-06-14-11-59-39_1560493827569.png\",idealScreenSize:\"normal\",createdOn:\"2020-07-29T10:03:33.003+0000\",copyrightYear:2019,contentDisposition:\"inline\",lastUpdatedOn:\"2020-08-10T19:27:57.376+0000\",level1Concept:[\"Physical Features of India\"],dialcodeRequired:\"No\",owner:\"Kerala SCERT\",lastStatusChangedOn:\"2020-08-10T19:27:58.907+0000\",createdFor:[\"0126202691023585280\"],creator:\"SAJEEV THOMAS\",os:[\"All\"],level1Name:[\"Contemporary India - I\"],pkgVersion:1,versionKey:\"1597087677376\",idealScreenDensity:\"hdpi\",framework:\"kl_k-12\",s3Key:\"ecar_files/do_11307457137049600011786/by-the-hands-of-the-nature_1597087677810_do_11307457137049600011786_1.0.ecar\",me_averageRating:3,createdBy:\"f20a4bbf-df17-425b-8e43-bd3dd57bde83\",compatibilityLevel:4,ownedBy:\"0126202691023585280\",board:\"CBSE\",resourceType:\"Learn\"},{code:\"finemanfine\",previewUrl:\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/questionset/do_113212597854404608111/do_113212597854404608111_html_1612875515166.html\",allowSkip:\"Yes\",containsUserData:\"No\",downloadUrl:\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/questionset/do_113212597854404608111/test-question-set_1612875514981_do_113212597854404608111_5_SPINE.ecar\",description:\"Updated QS Description\",language:[\"English\"],mimeType:\"application/vnd.sunbird.questionset\",showHints:\"No\",createdOn:\"2021-02-09T10:19:09.026+0000\",IL_FUNC_OBJECT_TYPE:\"QuestionSet\",pdfUrl:\"https://dockstorage.blob.core.windows.net/sunbird-content-dock/questionset/do_113212597854404608111/do_113212597854404608111_pdf_1612875515932.pdf\",contentDisposition:\"inline\",lastUpdatedOn:\"2021-02-09T12:58:36.155+0000\",contentEncoding:\"gzip\",showSolutions:\"Yes\",allowAnonymousAccess:\"Yes\",IL_UNIQUE_ID:\"do_113212597854404608111\",lastStatusChangedOn:\"2021-02-09T12:58:36.155+0000\",requiresSubmit:\"Yes\",visibility:\"Default\",showTimer:\"No\",summaryType:\"Complete\",consumerId:\"fa13b438-8a3d-41b1-8278-33b0c50210e4\",childNodes:[\"do_113212598840246272112\",\"do_113212599692050432114\",\"do_113212600505057280116\"],setType:\"materialised\",languageCode:[\"en\"],version:1,pkgVersion:5,versionKey:\"1612875494848\",showFeedback:\"Yes\",license:\"CC BY 4.0\",depth:0,lastPublishedOn:\"2021-02-09T12:58:34.976+0000\",compatibilityLevel:5,name:\"Test Question Set\",navigationMode:\"linear\",shuffle:true,status:\"Live\"}] as row CREATE (n:domain) SET n += row")
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Content")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
            }
        })
        request.put("mode","edit")
        request.put("rootId", "do_11323126798764441611181")
        val future = HierarchyManager.getHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 200)
            assert(null != response.getResult.get("content"))
            println("hierarchy ::::: "+response.getResult.get("content"))
            //assert(null != response.getResult.get("content").asInstanceOf[util.Map[String, AnyRef]].get("children"))
            val children = response.getResult.get("content").asInstanceOf[util.Map[String, AnyRef]].get("children").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
            assert(null!=children && !children.isEmpty)
        })
    }

    "getHierarchyForDraftAfterUpdateHierarchyWithoutMode" should "getHierarchy" in {
        val request = new Request()
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],copyright:\"ORG_002\",previewUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_112831862871203840114/small.mp4\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_112831862871203840114/test-resource-cert_1566389713658_do_112831862871203840114_1.0.ecar\",channel:\"01246944855007232011\",organisation:[\"ORG_002\"],showNotification:true,language:[\"English\"],mimeType:\"video/mp4\",variants:\"{\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_112831862871203840114/test-resource-cert_1566389714022_do_112831862871203840114_1.0_spine.ecar\\\",\\\"size\\\":35757.0}}\",appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_112831862871203840114/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",appId:\"dev.sunbird.portal\",artifactUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_112831862871203840114/small.mp4\",contentEncoding:\"identity\",lockKey:\"be6bc445-c75e-471d-b46f-71fefe4a1d2f\",contentType:\"Resource\",lastUpdatedBy:\"c4cc494f-04c3-49f3-b3d5-7b1a1984abad\",audience:[\"Student\"],visibility:\"Default\",consumerId:\"273f3b18-5dda-4a27-984a-060c7cd398d3\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"System\",version:1,license:\"Creative Commons Attribution (CC BY)\",prevState:\"Draft\",lastPublishedOn:\"2019-08-21T12:15:13.652+0000\",size:416488,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"Test Resource Cert\",status:\"Draft\",code:\"7e6630c7-3818-4319-92ac-4d08c33904d8\",streamingUrl:\"https://sunbirddevmedia-inct.streaming.media.azure.net/25d7a94c-9be3-471c-926b-51eb5d3c4c2c/small.ism/manifest(format=m3u8-aapl-v3)\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-08-21T12:11:50.644+0000\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-08-21T12:15:13.020+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-08-21T12:30:16.783+0000\",dialcodeRequired:\"No\",creator:\"Pradyumna\",lastStatusChangedOn:\"2019-08-21T12:15:14.384+0000\",createdFor:[\"01246944855007232011\"],os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",pkgVersion:1,versionKey:\"1566389713020\",idealScreenDensity:\"hdpi\",s3Key:\"ecar_files/do_112831862871203840114/test-resource-cert_1566389713658_do_112831862871203840114_1.0.ecar\",framework:\"K-12\",createdBy:\"c4cc494f-04c3-49f3-b3d5-7b1a1984abad\",compatibilityLevel:1,IL_UNIQUE_ID:\"do_112831862871203840114\",resourceType:\"Learn\"},{ownershipType:[\"createdBy\"],copyright:\"Sunbird\",certTemplate:\"[{\\\"name\\\":\\\"100PercentCompletionCertificate\\\",\\\"issuer\\\":{\\\"name\\\":\\\"Gujarat Council of Educational Research and Training\\\",\\\"url\\\":\\\"https://gcert.gujarat.gov.in/gcert/\\\",\\\"publicKey\\\":[\\\"1\\\",\\\"2\\\"]},\\\"signatoryList\\\":[{\\\"name\\\":\\\"CEO Gujarat\\\",\\\"id\\\":\\\"CEO\\\",\\\"designation\\\":\\\"CEO\\\",\\\"image\\\":\\\"https://cdn.pixabay.com/photo/2014/11/09/08/06/signature-523237__340.jpg\\\"}],\\\"htmlTemplate\\\":\\\"https://drive.google.com/uc?authuser=1&id=1ryB71i0Oqn2c3aqf9N6Lwvet-MZKytoM&export=download\\\",\\\"notifyTemplate\\\":{\\\"subject\\\":\\\"Course completion certificate\\\",\\\"stateImgUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/orgemailtemplate/img/File-0128212938260643843.png\\\",\\\"regardsperson\\\":\\\"Chairperson\\\",\\\"regards\\\":\\\"Minister of Gujarat\\\",\\\"emailTemplateType\\\":\\\"defaultCertTemp\\\"}}]\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550411/test-prad-course-cert_1566398313947_do_11283193441064550411_1.0_spine.ecar\",channel:\"b00bc992ef25f1a9a8d63291e20efc8d\",organisation:[\"Sunbird\"],language:[\"English\"],variants:\"{\\\"online\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550411/test-prad-course-cert_1566398314186_do_11283193441064550411_1.0_online.ecar\\\",\\\"size\\\":4034.0},\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550411/test-prad-course-cert_1566398313947_do_11283193441064550411_1.0_spine.ecar\\\",\\\"size\\\":73256.0}}\",mimeType:\"application/vnd.ekstep.content-collection\",leafNodes:[\"do_112831862871203840114\"],c_sunbird_dev_private_batch_count:0,appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11283193441064550411/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",appId:\"local.sunbird.portal\",contentEncoding:\"gzip\",lockKey:\"b079cf15-9e45-4865-be56-2edafa432dd3\",mimeTypesCount:\"{\\\"application/vnd.ekstep.content-collection\\\":1,\\\"video/mp4\\\":1}\",totalCompressedSize:416488,contentType:\"Course\",lastUpdatedBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",audience:[\"Student\"],toc_url:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11283193441064550411/artifact/do_11283193441064550411_toc.json\",visibility:\"Default\",contentTypesCount:\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":1}\",author:\"b00bc992ef25f1a9a8d63291e20efc8d\",childNodes:[\"do_11283193463014195215\"],consumerId:\"273f3b18-5dda-4a27-984a-060c7cd398d3\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"System\",version:2,license:\"Creative Commons Attribution (CC BY)\",prevState:\"Draft\",size:73256,lastPublishedOn:\"2019-08-21T14:38:33.816+0000\",IL_FUNC_OBJECT_TYPE:\"Content\",name:\"test prad course cert\",status:\"Draft\",code:\"org.sunbird.SUi47U\",description:\"Enter description for Course\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-08-21T14:37:23.486+0000\",reservedDialcodes:\"{\\\"I1X4R4\\\":0}\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-08-21T14:38:33.212+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-11-13T12:54:08.295+0000\",dialcodeRequired:\"No\",creator:\"Creation\",createdFor:[\"ORG_001\"],lastStatusChangedOn:\"2019-08-21T14:38:34.540+0000\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",pkgVersion:1,versionKey:\"1566398313212\",idealScreenDensity:\"hdpi\",dialcodes:[\"I1X4R4\"],s3Key:\"ecar_files/do_11283193441064550411/test-prad-course-cert_1566398313947_do_11283193441064550411_1.0_spine.ecar\",depth:0,framework:\"tpd\",me_averageRating:5,createdBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",leafNodesCount:1,compatibilityLevel:4,IL_UNIQUE_ID:\"do_11283193441064550411\",c_sunbird_dev_open_batch_count:0,resourceType:\"Course\"}] as row CREATE (n:domain) SET n += row")
        executeCassandraQuery("INSERT INTO hierarchy_store.content_hierarchy(identifier, hierarchy) values ('do_11283193441064550411', '{\"identifier\":\"do_11283193441064550411\",\"children\":[{\"parent\":\"do_11283193441064550411\",\"identifier\":\"do_11283193463014195215\",\"copyright\":\"Sunbird\",\"lastStatusChangedOn\":\"2019-08-21T14:37:50.281+0000\",\"code\":\"2e837725-d663-45da-8ace-9577ab111982\",\"visibility\":\"Parent\",\"index\":1,\"mimeType\":\"application/vnd.ekstep.content-collection\",\"createdOn\":\"2019-08-21T14:37:50.281+0000\",\"versionKey\":\"1566398270281\",\"framework\":\"tpd\",\"depth\":1,\"children\":[],\"name\":\"U1\",\"lastUpdatedOn\":\"2019-08-21T14:37:50.281+0000\",\"contentType\":\"CourseUnit\",\"status\":\"Draft\"}]}')")
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Content")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "b00bc992ef25f1a9a8d63291e20efc8d")
            }
        })
        request.put("rootId", "do_11283193441064550411")
        val future = HierarchyManager.getHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 404)
        })
    }

    "getHierarchyForDraftAfterUpdateHierarchyWithMode" should "getHierarchy" in {
        val request = new Request()
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],copyright:\"ORG_002\",previewUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_112831862871203840114/small.mp4\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_112831862871203840114/test-resource-cert_1566389713658_do_112831862871203840114_1.0.ecar\",channel:\"01246944855007232011\",organisation:[\"ORG_002\"],showNotification:true,language:[\"English\"],mimeType:\"video/mp4\",variants:\"{\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_112831862871203840114/test-resource-cert_1566389714022_do_112831862871203840114_1.0_spine.ecar\\\",\\\"size\\\":35757.0}}\",appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_112831862871203840114/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",appId:\"dev.sunbird.portal\",artifactUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_112831862871203840114/small.mp4\",contentEncoding:\"identity\",lockKey:\"be6bc445-c75e-471d-b46f-71fefe4a1d2f\",contentType:\"Resource\",lastUpdatedBy:\"c4cc494f-04c3-49f3-b3d5-7b1a1984abad\",audience:[\"Student\"],visibility:\"Default\",consumerId:\"273f3b18-5dda-4a27-984a-060c7cd398d3\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"System\",version:1,license:\"Creative Commons Attribution (CC BY)\",prevState:\"Draft\",lastPublishedOn:\"2019-08-21T12:15:13.652+0000\",size:416488,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"Test Resource Cert\",status:\"Draft\",code:\"7e6630c7-3818-4319-92ac-4d08c33904d8\",streamingUrl:\"https://sunbirddevmedia-inct.streaming.media.azure.net/25d7a94c-9be3-471c-926b-51eb5d3c4c2c/small.ism/manifest(format=m3u8-aapl-v3)\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-08-21T12:11:50.644+0000\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-08-21T12:15:13.020+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-08-21T12:30:16.783+0000\",dialcodeRequired:\"No\",creator:\"Pradyumna\",lastStatusChangedOn:\"2019-08-21T12:15:14.384+0000\",createdFor:[\"01246944855007232011\"],os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",pkgVersion:1,versionKey:\"1566389713020\",idealScreenDensity:\"hdpi\",s3Key:\"ecar_files/do_112831862871203840114/test-resource-cert_1566389713658_do_112831862871203840114_1.0.ecar\",framework:\"K-12\",createdBy:\"c4cc494f-04c3-49f3-b3d5-7b1a1984abad\",compatibilityLevel:1,IL_UNIQUE_ID:\"do_112831862871203840114\",resourceType:\"Learn\"},{ownershipType:[\"createdBy\"],copyright:\"Sunbird\",certTemplate:\"[{\\\"name\\\":\\\"100PercentCompletionCertificate\\\",\\\"issuer\\\":{\\\"name\\\":\\\"Gujarat Council of Educational Research and Training\\\",\\\"url\\\":\\\"https://gcert.gujarat.gov.in/gcert/\\\",\\\"publicKey\\\":[\\\"1\\\",\\\"2\\\"]},\\\"signatoryList\\\":[{\\\"name\\\":\\\"CEO Gujarat\\\",\\\"id\\\":\\\"CEO\\\",\\\"designation\\\":\\\"CEO\\\",\\\"image\\\":\\\"https://cdn.pixabay.com/photo/2014/11/09/08/06/signature-523237__340.jpg\\\"}],\\\"htmlTemplate\\\":\\\"https://drive.google.com/uc?authuser=1&id=1ryB71i0Oqn2c3aqf9N6Lwvet-MZKytoM&export=download\\\",\\\"notifyTemplate\\\":{\\\"subject\\\":\\\"Course completion certificate\\\",\\\"stateImgUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/orgemailtemplate/img/File-0128212938260643843.png\\\",\\\"regardsperson\\\":\\\"Chairperson\\\",\\\"regards\\\":\\\"Minister of Gujarat\\\",\\\"emailTemplateType\\\":\\\"defaultCertTemp\\\"}}]\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550411/test-prad-course-cert_1566398313947_do_11283193441064550411_1.0_spine.ecar\",channel:\"b00bc992ef25f1a9a8d63291e20efc8d\",organisation:[\"Sunbird\"],language:[\"English\"],variants:\"{\\\"online\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550411/test-prad-course-cert_1566398314186_do_11283193441064550411_1.0_online.ecar\\\",\\\"size\\\":4034.0},\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550411/test-prad-course-cert_1566398313947_do_11283193441064550411_1.0_spine.ecar\\\",\\\"size\\\":73256.0}}\",mimeType:\"application/vnd.ekstep.content-collection\",leafNodes:[\"do_112831862871203840114\"],c_sunbird_dev_private_batch_count:0,appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11283193441064550411/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",appId:\"local.sunbird.portal\",contentEncoding:\"gzip\",lockKey:\"b079cf15-9e45-4865-be56-2edafa432dd3\",mimeTypesCount:\"{\\\"application/vnd.ekstep.content-collection\\\":1,\\\"video/mp4\\\":1}\",totalCompressedSize:416488,contentType:\"Course\",lastUpdatedBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",audience:[\"Student\"],toc_url:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11283193441064550411/artifact/do_11283193441064550411_toc.json\",visibility:\"Default\",contentTypesCount:\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":1}\",author:\"b00bc992ef25f1a9a8d63291e20efc8d\",childNodes:[\"do_11283193463014195215\"],consumerId:\"273f3b18-5dda-4a27-984a-060c7cd398d3\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"System\",version:2,license:\"Creative Commons Attribution (CC BY)\",prevState:\"Draft\",size:73256,lastPublishedOn:\"2019-08-21T14:38:33.816+0000\",IL_FUNC_OBJECT_TYPE:\"Content\",name:\"test prad course cert\",status:\"Draft\",code:\"org.sunbird.SUi47U\",description:\"Enter description for Course\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-08-21T14:37:23.486+0000\",reservedDialcodes:\"{\\\"I1X4R4\\\":0}\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-08-21T14:38:33.212+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-11-13T12:54:08.295+0000\",dialcodeRequired:\"No\",creator:\"Creation\",createdFor:[\"ORG_001\"],lastStatusChangedOn:\"2019-08-21T14:38:34.540+0000\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",pkgVersion:1,versionKey:\"1566398313212\",idealScreenDensity:\"hdpi\",dialcodes:[\"I1X4R4\"],s3Key:\"ecar_files/do_11283193441064550411/test-prad-course-cert_1566398313947_do_11283193441064550411_1.0_spine.ecar\",depth:0,framework:\"tpd\",me_averageRating:5,createdBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",leafNodesCount:1,compatibilityLevel:4,IL_UNIQUE_ID:\"do_11283193441064550411\",c_sunbird_dev_open_batch_count:0,resourceType:\"Course\"}] as row CREATE (n:domain) SET n += row")
        executeCassandraQuery("INSERT INTO hierarchy_store.content_hierarchy(identifier, hierarchy) values ('do_11283193441064550411', '{\"identifier\":\"do_11283193441064550411\",\"children\":[{\"parent\":\"do_11283193441064550411\",\"identifier\":\"do_11283193463014195215\",\"copyright\":\"Sunbird\",\"lastStatusChangedOn\":\"2019-08-21T14:37:50.281+0000\",\"code\":\"2e837725-d663-45da-8ace-9577ab111982\",\"visibility\":\"Parent\",\"index\":1,\"mimeType\":\"application/vnd.ekstep.content-collection\",\"createdOn\":\"2019-08-21T14:37:50.281+0000\",\"versionKey\":\"1566398270281\",\"framework\":\"tpd\",\"depth\":1,\"children\":[],\"name\":\"U1\",\"lastUpdatedOn\":\"2019-08-21T14:37:50.281+0000\",\"contentType\":\"CourseUnit\",\"status\":\"Draft\"}]}')")
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Content")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "b00bc992ef25f1a9a8d63291e20efc8d")
            }
        })
        request.put("mode","edit")
        request.put("rootId", "do_11283193441064550411")
        val future = HierarchyManager.getHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 200)
            assert(null != response.getResult.get("content"))
            assert(null != response.getResult.get("content").asInstanceOf[util.Map[String, AnyRef]].get("children"))
        })
    }

    "getHierarchyFromCache" should "getHierarchy" in {
        val request = new Request()
        executeCassandraQuery(script_4)
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Content")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "b00bc992ef25f1a9a8d63291e20efc8d")
            }
        })
        request.put("rootId", "do_11283193441064550414")
        val future = HierarchyManager.getHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 200)
            assert(null != RedisCache.get("hierarchy_do_11283193441064550414"))
        })
    }

    "getHierarchyBeforeUpdateHierarchyWithoutMode" should "getHierarchyWithoutChildren" in {
        val request = new Request()
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],copyright:\"ORG_002\",previewUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_112831862871203840114/small.mp4\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_112831862871203840114/test-resource-cert_1566389713658_do_112831862871203840114_1.0.ecar\",channel:\"01246944855007232011\",organisation:[\"ORG_002\"],showNotification:true,language:[\"English\"],mimeType:\"video/mp4\",variants:\"{\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_112831862871203840114/test-resource-cert_1566389714022_do_112831862871203840114_1.0_spine.ecar\\\",\\\"size\\\":35757.0}}\",appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_112831862871203840114/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",appId:\"dev.sunbird.portal\",artifactUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_112831862871203840114/small.mp4\",contentEncoding:\"identity\",lockKey:\"be6bc445-c75e-471d-b46f-71fefe4a1d2f\",contentType:\"Resource\",lastUpdatedBy:\"c4cc494f-04c3-49f3-b3d5-7b1a1984abad\",audience:[\"Student\"],visibility:\"Default\",consumerId:\"273f3b18-5dda-4a27-984a-060c7cd398d3\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"System\",version:1,license:\"Creative Commons Attribution (CC BY)\",prevState:\"Draft\",lastPublishedOn:\"2019-08-21T12:15:13.652+0000\",size:416488,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"Test Resource Cert\",status:\"Draft\",code:\"7e6630c7-3818-4319-92ac-4d08c33904d8\",streamingUrl:\"https://sunbirddevmedia-inct.streaming.media.azure.net/25d7a94c-9be3-471c-926b-51eb5d3c4c2c/small.ism/manifest(format=m3u8-aapl-v3)\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-08-21T12:11:50.644+0000\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-08-21T12:15:13.020+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-08-21T12:30:16.783+0000\",dialcodeRequired:\"No\",creator:\"Pradyumna\",lastStatusChangedOn:\"2019-08-21T12:15:14.384+0000\",createdFor:[\"01246944855007232011\"],os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",pkgVersion:1,versionKey:\"1566389713020\",idealScreenDensity:\"hdpi\",s3Key:\"ecar_files/do_112831862871203840114/test-resource-cert_1566389713658_do_112831862871203840114_1.0.ecar\",framework:\"K-12\",createdBy:\"c4cc494f-04c3-49f3-b3d5-7b1a1984abad\",compatibilityLevel:1,IL_UNIQUE_ID:\"do_112831862871203840114\",resourceType:\"Learn\"},{ownershipType:[\"createdBy\"],copyright:\"Sunbird\",certTemplate:\"[{\\\"name\\\":\\\"100PercentCompletionCertificate\\\",\\\"issuer\\\":{\\\"name\\\":\\\"Gujarat Council of Educational Research and Training\\\",\\\"url\\\":\\\"https://gcert.gujarat.gov.in/gcert/\\\",\\\"publicKey\\\":[\\\"1\\\",\\\"2\\\"]},\\\"signatoryList\\\":[{\\\"name\\\":\\\"CEO Gujarat\\\",\\\"id\\\":\\\"CEO\\\",\\\"designation\\\":\\\"CEO\\\",\\\"image\\\":\\\"https://cdn.pixabay.com/photo/2014/11/09/08/06/signature-523237__340.jpg\\\"}],\\\"htmlTemplate\\\":\\\"https://drive.google.com/uc?authuser=1&id=1ryB71i0Oqn2c3aqf9N6Lwvet-MZKytoM&export=download\\\",\\\"notifyTemplate\\\":{\\\"subject\\\":\\\"Course completion certificate\\\",\\\"stateImgUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/orgemailtemplate/img/File-0128212938260643843.png\\\",\\\"regardsperson\\\":\\\"Chairperson\\\",\\\"regards\\\":\\\"Minister of Gujarat\\\",\\\"emailTemplateType\\\":\\\"defaultCertTemp\\\"}}]\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550411/test-prad-course-cert_1566398313947_do_11283193441064550411_1.0_spine.ecar\",channel:\"b00bc992ef25f1a9a8d63291e20efc8d\",organisation:[\"Sunbird\"],language:[\"English\"],variants:\"{\\\"online\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550411/test-prad-course-cert_1566398314186_do_11283193441064550411_1.0_online.ecar\\\",\\\"size\\\":4034.0},\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550411/test-prad-course-cert_1566398313947_do_11283193441064550411_1.0_spine.ecar\\\",\\\"size\\\":73256.0}}\",mimeType:\"application/vnd.ekstep.content-collection\",leafNodes:[\"do_112831862871203840114\"],c_sunbird_dev_private_batch_count:0,appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11283193441064550411/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",appId:\"local.sunbird.portal\",contentEncoding:\"gzip\",lockKey:\"b079cf15-9e45-4865-be56-2edafa432dd3\",mimeTypesCount:\"{\\\"application/vnd.ekstep.content-collection\\\":1,\\\"video/mp4\\\":1}\",totalCompressedSize:416488,contentType:\"Course\",lastUpdatedBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",audience:[\"Student\"],toc_url:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11283193441064550411/artifact/do_11283193441064550411_toc.json\",visibility:\"Default\",contentTypesCount:\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":1}\",author:\"b00bc992ef25f1a9a8d63291e20efc8d\",childNodes:[\"do_11283193463014195215\"],consumerId:\"273f3b18-5dda-4a27-984a-060c7cd398d3\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"System\",version:2,license:\"Creative Commons Attribution (CC BY)\",prevState:\"Draft\",size:73256,lastPublishedOn:\"2019-08-21T14:38:33.816+0000\",IL_FUNC_OBJECT_TYPE:\"Content\",name:\"test prad course cert\",status:\"Draft\",code:\"org.sunbird.SUi47U\",description:\"Enter description for Course\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-08-21T14:37:23.486+0000\",reservedDialcodes:\"{\\\"I1X4R4\\\":0}\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-08-21T14:38:33.212+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-11-13T12:54:08.295+0000\",dialcodeRequired:\"No\",creator:\"Creation\",createdFor:[\"ORG_001\"],lastStatusChangedOn:\"2019-08-21T14:38:34.540+0000\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",pkgVersion:1,versionKey:\"1566398313212\",idealScreenDensity:\"hdpi\",dialcodes:[\"I1X4R4\"],s3Key:\"ecar_files/do_11283193441064550411/test-prad-course-cert_1566398313947_do_11283193441064550411_1.0_spine.ecar\",depth:0,framework:\"tpd\",me_averageRating:5,createdBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",leafNodesCount:1,compatibilityLevel:4,IL_UNIQUE_ID:\"do_11283193441064550411\",c_sunbird_dev_open_batch_count:0,resourceType:\"Course\"}] as row CREATE (n:domain) SET n += row")
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Content")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "b00bc992ef25f1a9a8d63291e20efc8d")
            }
        })
        request.put("rootId", "do_11283193441064550411")
        val future = HierarchyManager.getHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 404)
        })
    }

    "getHierarchyBeforeUpdateHierarchyWithMode" should "getHierarchyWithoutChildren" in {
        val request = new Request()
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],copyright:\"ORG_002\",previewUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_112831862871203840114/small.mp4\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_112831862871203840114/test-resource-cert_1566389713658_do_112831862871203840114_1.0.ecar\",channel:\"01246944855007232011\",organisation:[\"ORG_002\"],showNotification:true,language:[\"English\"],mimeType:\"video/mp4\",variants:\"{\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_112831862871203840114/test-resource-cert_1566389714022_do_112831862871203840114_1.0_spine.ecar\\\",\\\"size\\\":35757.0}}\",appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_112831862871203840114/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",appId:\"dev.sunbird.portal\",artifactUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_112831862871203840114/small.mp4\",contentEncoding:\"identity\",lockKey:\"be6bc445-c75e-471d-b46f-71fefe4a1d2f\",contentType:\"Resource\",lastUpdatedBy:\"c4cc494f-04c3-49f3-b3d5-7b1a1984abad\",audience:[\"Student\"],visibility:\"Default\",consumerId:\"273f3b18-5dda-4a27-984a-060c7cd398d3\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"System\",version:1,license:\"Creative Commons Attribution (CC BY)\",prevState:\"Draft\",lastPublishedOn:\"2019-08-21T12:15:13.652+0000\",size:416488,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"Test Resource Cert\",status:\"Draft\",code:\"7e6630c7-3818-4319-92ac-4d08c33904d8\",streamingUrl:\"https://sunbirddevmedia-inct.streaming.media.azure.net/25d7a94c-9be3-471c-926b-51eb5d3c4c2c/small.ism/manifest(format=m3u8-aapl-v3)\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-08-21T12:11:50.644+0000\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-08-21T12:15:13.020+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-08-21T12:30:16.783+0000\",dialcodeRequired:\"No\",creator:\"Pradyumna\",lastStatusChangedOn:\"2019-08-21T12:15:14.384+0000\",createdFor:[\"01246944855007232011\"],os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",pkgVersion:1,versionKey:\"1566389713020\",idealScreenDensity:\"hdpi\",s3Key:\"ecar_files/do_112831862871203840114/test-resource-cert_1566389713658_do_112831862871203840114_1.0.ecar\",framework:\"K-12\",createdBy:\"c4cc494f-04c3-49f3-b3d5-7b1a1984abad\",compatibilityLevel:1,IL_UNIQUE_ID:\"do_112831862871203840114\",resourceType:\"Learn\"},{ownershipType:[\"createdBy\"],copyright:\"Sunbird\",certTemplate:\"[{\\\"name\\\":\\\"100PercentCompletionCertificate\\\",\\\"issuer\\\":{\\\"name\\\":\\\"Gujarat Council of Educational Research and Training\\\",\\\"url\\\":\\\"https://gcert.gujarat.gov.in/gcert/\\\",\\\"publicKey\\\":[\\\"1\\\",\\\"2\\\"]},\\\"signatoryList\\\":[{\\\"name\\\":\\\"CEO Gujarat\\\",\\\"id\\\":\\\"CEO\\\",\\\"designation\\\":\\\"CEO\\\",\\\"image\\\":\\\"https://cdn.pixabay.com/photo/2014/11/09/08/06/signature-523237__340.jpg\\\"}],\\\"htmlTemplate\\\":\\\"https://drive.google.com/uc?authuser=1&id=1ryB71i0Oqn2c3aqf9N6Lwvet-MZKytoM&export=download\\\",\\\"notifyTemplate\\\":{\\\"subject\\\":\\\"Course completion certificate\\\",\\\"stateImgUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/orgemailtemplate/img/File-0128212938260643843.png\\\",\\\"regardsperson\\\":\\\"Chairperson\\\",\\\"regards\\\":\\\"Minister of Gujarat\\\",\\\"emailTemplateType\\\":\\\"defaultCertTemp\\\"}}]\",downloadUrl:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550412/test-prad-course-cert_1566398313947_do_11283193441064550412_1.0_spine.ecar\",channel:\"b00bc992ef25f1a9a8d63291e20efc8d\",organisation:[\"Sunbird\"],language:[\"English\"],variants:\"{\\\"online\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550412/test-prad-course-cert_1566398314186_do_11283193441064550412_1.0_online.ecar\\\",\\\"size\\\":4034.0},\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11283193441064550412/test-prad-course-cert_1566398313947_do_11283193441064550412_1.0_spine.ecar\\\",\\\"size\\\":73256.0}}\",mimeType:\"application/vnd.ekstep.content-collection\",leafNodes:[\"do_112831862871203840114\"],c_sunbird_dev_private_batch_count:0,appIcon:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11283193441064550412/artifact/033019_sz_reviews_feat_1564126718632.thumb.jpg\",appId:\"local.sunbird.portal\",contentEncoding:\"gzip\",lockKey:\"b079cf15-9e45-4865-be56-2edafa432dd3\",mimeTypesCount:\"{\\\"application/vnd.ekstep.content-collection\\\":1,\\\"video/mp4\\\":1}\",totalCompressedSize:416488,contentType:\"Course\",lastUpdatedBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",audience:[\"Student\"],toc_url:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11283193441064550412/artifact/do_11283193441064550412_toc.json\",visibility:\"Default\",contentTypesCount:\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":1}\",author:\"b00bc992ef25f1a9a8d63291e20efc8d\",childNodes:[\"do_11283193463014195215\"],consumerId:\"273f3b18-5dda-4a27-984a-060c7cd398d3\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"System\",version:2,license:\"Creative Commons Attribution (CC BY)\",prevState:\"Draft\",size:73256,lastPublishedOn:\"2019-08-21T14:38:33.816+0000\",IL_FUNC_OBJECT_TYPE:\"Content\",name:\"test prad course cert\",status:\"Draft\",code:\"org.sunbird.SUi47U\",description:\"Enter description for Course\",posterImage:\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11281332607717376012/artifact/033019_sz_reviews_feat_1564126718632.jpg\",idealScreenSize:\"normal\",createdOn:\"2019-08-21T14:37:23.486+0000\",reservedDialcodes:\"{\\\"I1X4R4\\\":0}\",contentDisposition:\"inline\",lastUpdatedOn:\"2019-08-21T14:38:33.212+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2019-11-13T12:54:08.295+0000\",dialcodeRequired:\"No\",creator:\"Creation\",createdFor:[\"ORG_001\"],lastStatusChangedOn:\"2019-08-21T14:38:34.540+0000\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",pkgVersion:1,versionKey:\"1566398313212\",idealScreenDensity:\"hdpi\",dialcodes:[\"I1X4R4\"],s3Key:\"ecar_files/do_11283193441064550412/test-prad-course-cert_1566398313947_do_11283193441064550412_1.0_spine.ecar\",depth:0,framework:\"tpd\",me_averageRating:5,createdBy:\"874ed8a5-782e-4f6c-8f36-e0288455901e\",leafNodesCount:1,compatibilityLevel:4,IL_UNIQUE_ID:\"do_11283193441064550412\",c_sunbird_dev_open_batch_count:0,resourceType:\"Course\"}] as row CREATE (n:domain) SET n += row")
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Content")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "b00bc992ef25f1a9a8d63291e20efc8d")
            }
        })
        request.put("mode","edit")
        request.put("rootId", "do_11283193441064550412")
        val future = HierarchyManager.getHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 200)
            assert(null != response.get("content"))
            assert(CollectionUtils.isEmpty(response.get("content").asInstanceOf[util.Map[String, AnyRef]].get("children").asInstanceOf[util.List[Map[String, AnyRef]]]))
        })
    }

    "getHierarchy mode=edit" should "return latest leafNodes" in {
        val query = "INSERT INTO hierarchy_store.content_hierarchy(identifier, hierarchy) values ('do_11300156035268608015', '{\"identifier\":\"do_11300156035268608015\",\"children\":[{\"ownershipType\":[\"createdBy\"],\"parent\":\"do_11300156035268608015\",\"code\":\"2cb4d698-dc19-4f0c-9990-96f49daff753\",\"channel\":\"in.ekstep\",\"description\":\"Test_TextBookUnit_desc_8330194200\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2020-04-17T11:53:04.855+0530\",\"objectType\":\"Content\",\"children\":[{\"ownershipType\":[\"createdBy\"],\"parent\":\"do_11300156075913216016\",\"code\":\"test-Resourcce\",\"channel\":\"in.ekstep\",\"language\":[\"English\"],\"mimeType\":\"application/pdf\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2020-04-17T11:51:30.230+0530\",\"objectType\":\"Content\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2020-04-17T11:51:30.230+0530\",\"contentEncoding\":\"identity\",\"contentType\":\"Resource\",\"dialcodeRequired\":\"No\",\"identifier\":\"do_11300155996401664014\",\"lastStatusChangedOn\":\"2020-04-17T11:51:30.230+0530\",\"audience\":[\"Student\"],\"os\":[\"All\"],\"visibility\":\"Default\",\"index\":1,\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"languageCode\":[\"en\"],\"version\":2,\"versionKey\":\"1587104490230\",\"license\":\"CC BY 4.0\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"NCF\",\"depth\":2,\"concepts\":[{\"identifier\":\"Num:C2:SC1\",\"name\":\"Counting\",\"description\":\"Counting\",\"objectType\":\"Concept\",\"relation\":\"associatedTo\",\"status\":\"Retired\"}],\"compatibilityLevel\":1,\"name\":\"test resource\",\"status\":\"Draft\"}],\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2020-04-17T11:53:04.855+0530\",\"contentEncoding\":\"gzip\",\"contentType\":\"TextBookUnit\",\"dialcodeRequired\":\"No\",\"identifier\":\"do_11300156075913216016\",\"lastStatusChangedOn\":\"2020-04-17T11:53:04.855+0530\",\"audience\":[\"Student\"],\"os\":[\"All\"],\"visibility\":\"Parent\",\"index\":1,\"mediaType\":\"content\",\"osId\":\"org.ekstep.launcher\",\"languageCode\":[\"en\"],\"versionKey\":\"1587104584855\",\"license\":\"CC BY 4.0\",\"idealScreenDensity\":\"hdpi\",\"depth\":1,\"compatibilityLevel\":1,\"name\":\"Test_TextBookUnit_name_7240493202\",\"status\":\"Draft\"}]}')"
        executeCassandraQuery(query)
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],code:\"txtbk\",channel:\"in.ekstep\",description:\"Text Book Test\",language:[\"English\"],mimeType:\"application/vnd.ekstep.content-collection\",idealScreenSize:\"normal\",createdOn:\"2020-04-17T11:52:15.303+0530\",contentDisposition:\"inline\",contentEncoding:\"gzip\",lastUpdatedOn:\"2020-04-17T11:53:05.434+0530\",contentType:\"Course\",dialcodeRequired:\"No\",identifier:\"do_11300156035268608015\",audience:[\"Student\"],lastStatusChangedOn:\"2020-04-17T11:52:15.303+0530\",os:[\"All\"],visibility:\"Default\",IL_SYS_NODE_TYPE:\"DATA_NODE\",childNodes:[\"do_11300156075913216016\",\"do_11300155996401664014\"],mediaType:\"content\",osId:\"org.ekstep.quiz.app\",version:2,versionKey:\"1587104585434\",license:\"CC BY 4.0\",idealScreenDensity:\"hdpi\",depth:0,framework:\"NCF\",compatibilityLevel:1,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"TextBook\",IL_UNIQUE_ID:\"do_11300156035268608015\",status:\"Draft\"},{ownershipType:[\"createdBy\"],code:\"test-Resourcce\",prevStatus:\"Live\",channel:\"in.ekstep\",language:[\"English\"],mimeType:\"application/pdf\",idealScreenSize:\"normal\",createdOn:\"2020-04-17T11:51:30.230+0530\",contentDisposition:\"inline\",contentEncoding:\"identity\",lastUpdatedOn:\"2020-04-17T13:38:24.720+0530\",contentType:\"Resource\",dialcodeRequired:\"No\",audience:[\"Student\"],lastStatusChangedOn:\"2020-04-17T13:38:22.954+0530\",os:[\"All\"],visibility:\"Default\",IL_SYS_NODE_TYPE:\"DATA_NODE\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",version:2,versionKey:\"1587110904720\",license:\"CC BY 4.0\",idealScreenDensity:\"hdpi\",framework:\"NCF\",compatibilityLevel:1,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"updated\",IL_UNIQUE_ID:\"do_11300155996401664014\",status:\"Draft\"}] as row CREATE (n:domain) SET n += row")
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Content")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "in.ekstep")
            }
        })
        request.put("mode","edit")
        request.put("rootId", "do_11300156035268608015")
        val future = HierarchyManager.getHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 200)
            assert(null != response.get("content"))
            val children = response.get("content").asInstanceOf[util.Map[String, AnyRef]].get("children").asInstanceOf[util.List[Map[String, AnyRef]]]
            assert(CollectionUtils.isNotEmpty(children))
        })
    }

    "getHierarchy mode=edit with bookmark" should "return latest leafNodes for bookmark" in {
        val query = "INSERT INTO hierarchy_store.content_hierarchy(identifier, hierarchy) values ('do_113054617607118848121','{\"identifier\":\"do_113054617607118848121\",\"children\":[{\"ownershipType\":[\"createdBy\"],\"parent\":\"do_113054617607118848121\",\"code\":\"TestBookUnit-01\",\"keywords\":[],\"channel\":\"in.ekstep\",\"description\":\"U-1\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2020-07-01T05:30:02.464+0000\",\"objectType\":\"Content\",\"children\":[{\"ownershipType\":[\"createdBy\"],\"parent\":\"do_113054618848985088126\",\"code\":\"test.res.1\",\"previewUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_test_content_1/artifact/test_1592831799259.pdf\",\"prevStatus\":\"Live\",\"channel\":\"in.ekstep\",\"downloadUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_test_content_1/g-test-pdf-1_1592831801712_do_test_content_1_1.0.ecar\",\"language\":[\"English\"],\"streamingUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_test_content_1/artifact/test_1592831799259.pdf\",\"mimeType\":\"application/pdf\",\"variants\":{\"spine\":{\"ecarUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_test_content_1/g-test-pdf-1_1592831801948_do_test_content_1_1.0_spine.ecar\",\"size\":849}},\"idealScreenSize\":\"normal\",\"createdOn\":\"2020-06-22T13:16:39.135+0000\",\"objectType\":\"ContentImage\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2020-06-22T13:16:40.506+0000\",\"contentEncoding\":\"identity\",\"artifactUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_test_content_1/artifact/test_1592831799259.pdf\",\"sYS_INTERNAL_LAST_UPDATED_ON\":\"2020-06-22T13:16:42.230+0000\",\"contentType\":\"Resource\",\"dialcodeRequired\":\"No\",\"identifier\":\"do_test_content_1\",\"lastStatusChangedOn\":\"2020-06-23T12:07:01.047+0000\",\"audience\":[\"Student\"],\"os\":[\"All\"],\"visibility\":\"Default\",\"cloudStorageKey\":\"content/do_test_content_1/artifact/test_1592831799259.pdf\",\"index\":1,\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"languageCode\":[\"en\"],\"lastPublishedBy\":\"EkStep\",\"version\":2,\"pragma\":[\"external\"],\"pkgVersion\":1,\"versionKey\":\"1592914021107\",\"license\":\"CC BY 4.0\",\"prevState\":\"Draft\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"NCFCOPY\",\"depth\":2,\"s3Key\":\"ecar_files/do_test_content_1/g-test-pdf-1_1592831801712_do_test_content_1_1.0.ecar\",\"size\":1946,\"lastPublishedOn\":\"2020-06-22T13:16:40.672+0000\",\"compatibilityLevel\":4,\"name\":\"G-TEST-PDF-1\",\"status\":\"Live\",\"description\":\"updated for do_test_content_1\"}],\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2020-07-01T05:30:02.463+0000\",\"contentEncoding\":\"gzip\",\"contentType\":\"TextBookUnit\",\"dialcodeRequired\":\"No\",\"identifier\":\"do_113054618848985088126\",\"lastStatusChangedOn\":\"2020-07-01T05:30:02.464+0000\",\"audience\":[\"Student\"],\"os\":[\"All\"],\"visibility\":\"Parent\",\"index\":1,\"mediaType\":\"content\",\"osId\":\"org.ekstep.launcher\",\"languageCode\":[\"en\"],\"versionKey\":\"1593581402464\",\"license\":\"CC BY 4.0\",\"idealScreenDensity\":\"hdpi\",\"depth\":1,\"compatibilityLevel\":1,\"name\":\"U-1\",\"status\":\"Live\"},{\"ownershipType\":[\"createdBy\"],\"parent\":\"do_113054617607118848121\",\"code\":\"TestBookUnit-02\",\"keywords\":[],\"channel\":\"in.ekstep\",\"description\":\"U-2\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2020-07-01T05:30:02.458+0000\",\"objectType\":\"Content\",\"children\":[{\"ownershipType\":[\"createdBy\"],\"parent\":\"do_113054618848935936124\",\"code\":\"test.res.2\",\"previewUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_test_content_2/artifact/test_1592831800654.pdf\",\"prevStatus\":\"Live\",\"channel\":\"in.ekstep\",\"downloadUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_test_content_2/g-test-pdf-2_1592831806405_do_test_content_2_1.0.ecar\",\"language\":[\"English\"],\"streamingUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_test_content_2/artifact/test_1592831800654.pdf\",\"mimeType\":\"application/pdf\",\"variants\":{\"spine\":{\"ecarUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_test_content_2/g-test-pdf-2_1592831806559_do_test_content_2_1.0_spine.ecar\",\"size\":847}},\"idealScreenSize\":\"normal\",\"createdOn\":\"2020-06-22T13:16:40.626+0000\",\"objectType\":\"ContentImage\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2020-06-22T13:16:42.293+0000\",\"contentEncoding\":\"identity\",\"artifactUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_test_content_2/artifact/test_1592831800654.pdf\",\"sYS_INTERNAL_LAST_UPDATED_ON\":\"2020-06-22T13:16:46.836+0000\",\"contentType\":\"Resource\",\"dialcodeRequired\":\"No\",\"identifier\":\"do_test_content_2\",\"lastStatusChangedOn\":\"2020-06-23T12:07:01.269+0000\",\"audience\":[\"Student\"],\"os\":[\"All\"],\"visibility\":\"Default\",\"cloudStorageKey\":\"content/do_test_content_2/artifact/test_1592831800654.pdf\",\"index\":1,\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"languageCode\":[\"en\"],\"lastPublishedBy\":\"EkStep\",\"version\":2,\"pragma\":[\"external\"],\"pkgVersion\":1,\"versionKey\":\"1592914021297\",\"license\":\"CC BY 4.0\",\"prevState\":\"Draft\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"NCFCOPY\",\"depth\":2,\"s3Key\":\"ecar_files/do_test_content_2/g-test-pdf-2_1592831806405_do_test_content_2_1.0.ecar\",\"size\":1941,\"lastPublishedOn\":\"2020-06-22T13:16:42.447+0000\",\"compatibilityLevel\":4,\"name\":\"G-TEST-PDF-2\",\"status\":\"Live\",\"description\":\"updated for do_test_content_2\"}],\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2020-07-01T05:30:02.457+0000\",\"contentEncoding\":\"gzip\",\"contentType\":\"TextBookUnit\",\"dialcodeRequired\":\"No\",\"identifier\":\"do_113054618848935936124\",\"lastStatusChangedOn\":\"2020-07-01T05:30:02.458+0000\",\"audience\":[\"Student\"],\"os\":[\"All\"],\"visibility\":\"Parent\",\"index\":2,\"mediaType\":\"content\",\"osId\":\"org.ekstep.launcher\",\"languageCode\":[\"en\"],\"versionKey\":\"1593581402458\",\"license\":\"CC BY 4.0\",\"idealScreenDensity\":\"hdpi\",\"depth\":1,\"compatibilityLevel\":1,\"name\":\"U-2\",\"status\":\"Live\"}]}')"
        executeCassandraQuery(query)
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],code:\"test.book.1\",channel:\"in.ekstep\",language:[\"English\"],mimeType:\"application/vnd.ekstep.content-collection\",idealScreenSize:\"normal\",createdOn:\"2020-07-01T05:27:30.873+0000\",objectType:\"Content\",contentDisposition:\"inline\",lastUpdatedOn:\"2020-07-01T05:30:02.963+0000\",contentEncoding:\"gzip\",contentType:\"TextBook\",dialcodeRequired:\"No\",identifier:\"do_113054617607118848121\",lastStatusChangedOn:\"2020-07-01T05:27:30.873+0000\",audience:[\"Student\"],os:[\"All\"],visibility:\"Default\",childNodes:[\"do_test_content_1\",\"do_113054618848985088126\",\"do_test_content_2\",\"do_113054618848935936124\"],mediaType:\"content\",osId:\"org.ekstep.quiz.app\",languageCode:[\"en\"],version:2,versionKey:1593581402963,license:\"CC BY 4.0\",idealScreenDensity:\"hdpi\",framework:\"NCF\",depth:0,compatibilityLevel:1,name:\"Test-Get Hierarchy\",status:\"Draft\",IL_UNIQUE_ID:\"do_113054617607118848121\",IL_FUNC_OBJECT_TYPE:\"Content\",IL_SYS_NODE_TYPE:\"DATA_NODE\"},\n{ownershipType:[\"createdBy\"],code:\"test.res.1\",prevStatus:\"Live\",channel:\"in.ekstep\",description:\"updated for do_test_content_1\",language:[\"English\"],mimeType:\"application/pdf\",idealScreenSize:\"normal\",createdOn:\"2020-06-22T13:16:39.135+0000\",objectType:\"ContentImage\",contentDisposition:\"inline\",lastUpdatedOn:\"2020-06-22T13:16:40.506+0000\",contentEncoding:\"identity\",sYS_INTERNAL_LAST_UPDATED_ON:\"2020-06-22T13:16:42.230+0000\",contentType:\"Resource\",dialcodeRequired:\"No\",identifier:\"do_test_content_1\",lastStatusChangedOn:\"2020-06-23T12:07:01.047+0000\",audience:[\"Student\"],os:[\"All\"],visibility:\"Default\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"EkStep\",languageCode:[\"en\"],version:2,pragma:[\"external\"],pkgVersion:1,versionKey:1592914021107,license:\"CC BY 4.0\",prevState:\"Draft\",idealScreenDensity:\"hdpi\",framework:\"NCFCOPY\",size:1946,lastPublishedOn:\"2020-06-22T13:16:40.672+0000\",compatibilityLevel:4,name:\"G-TEST-PDF-1\",status:\"Draft\",IL_UNIQUE_ID:\"do_test_content_1\",IL_FUNC_OBJECT_TYPE:\"Content\",IL_SYS_NODE_TYPE:\"DATA_NODE\"},\n{ownershipType:[\"createdBy\"],code:\"test.res.2\",prevStatus:\"Live\",channel:\"in.ekstep\",description:\"updated for do_test_content_2\",language:[\"English\"],mimeType:\"application/pdf\",idealScreenSize:\"normal\",createdOn:\"2020-06-22T13:16:40.626+0000\",objectType:\"ContentImage\",contentDisposition:\"inline\",lastUpdatedOn:\"2020-06-22T13:16:42.293+0000\",contentEncoding:\"identity\",sYS_INTERNAL_LAST_UPDATED_ON:\"2020-06-22T13:16:46.836+0000\",contentType:\"Resource\",dialcodeRequired:\"No\",identifier:\"do_test_content_2\",lastStatusChangedOn:\"2020-06-23T12:07:01.269+0000\",audience:[\"Student\"],os:[\"All\"],visibility:\"Default\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"EkStep\",languageCode:[\"en\"],version:2,pragma:[\"external\"],pkgVersion:1,versionKey:1592914021297,license:\"CC BY 4.0\",prevState:\"Draft\",idealScreenDensity:\"hdpi\",framework:\"NCFCOPY\",s3Key:\"ecar_files/do_test_content_2/g-test-pdf-2_1592831806405_do_test_content_2_1.0.ecar\",size:1941,lastPublishedOn:\"2020-06-22T13:16:42.447+0000\",compatibilityLevel:4,name:\"G-TEST-PDF-2\",status:\"Draft\",IL_UNIQUE_ID:\"do_test_content_2\",IL_FUNC_OBJECT_TYPE:\"Content\",IL_SYS_NODE_TYPE:\"DATA_NODE\"}]  as row CREATE (n:domain) SET n += row")
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Content")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "in.ekstep")
            }
        })
        request.put("mode","edit")
        request.put("bookmarkId","do_113054618848985088126")
        request.put("rootId", "do_113054617607118848121")
        val future = HierarchyManager.getHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 200)
            assert(null != response.get("content"))
            val children = response.get("content").asInstanceOf[util.Map[String, AnyRef]].get("children").asInstanceOf[util.List[Map[String, AnyRef]]]
            assert(CollectionUtils.isNotEmpty(children))
            assert(children.size()==1)
            val childrenMap = children.get(0).asInstanceOf[util.Map[String, AnyRef]]
            assert(StringUtils.equalsIgnoreCase(childrenMap.get("status").asInstanceOf[String],"Draft"))
            assert(StringUtils.equalsIgnoreCase(childrenMap.get("identifier").asInstanceOf[String],"do_test_content_1"))
        })
    }


    "getHierarchy mode=edit" should "return removing retired or deleted leafNodes" in {
        val query = "INSERT INTO hierarchy_store.content_hierarchy(identifier, hierarchy) values ('do_11300156035268608015', '{\"identifier\":\"do_11300156035268608015\",\"children\":[{\"ownershipType\":[\"createdBy\"],\"parent\":\"do_11300156035268608015\",\"code\":\"2cb4d698-dc19-4f0c-9990-96f49daff753\",\"channel\":\"in.ekstep\",\"description\":\"Test_TextBookUnit_desc_8330194200\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2020-04-17T11:53:04.855+0530\",\"objectType\":\"Content\",\"children\":[{\"ownershipType\":[\"createdBy\"],\"parent\":\"do_11300156075913216016\",\"code\":\"test-Resourcce\",\"channel\":\"in.ekstep\",\"language\":[\"English\"],\"mimeType\":\"application/pdf\",\"idealScreenSize\":\"normal\",\"createdOn\":\"2020-04-17T11:51:30.230+0530\",\"objectType\":\"Content\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2020-04-17T11:51:30.230+0530\",\"contentEncoding\":\"identity\",\"contentType\":\"Resource\",\"dialcodeRequired\":\"No\",\"identifier\":\"do_1130015599640166\",\"lastStatusChangedOn\":\"2020-04-17T11:51:30.230+0530\",\"audience\":[\"Learner\"],\"os\":[\"All\"],\"visibility\":\"Default\",\"index\":1,\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"languageCode\":[\"en\"],\"version\":2,\"versionKey\":\"1587104490230\",\"license\":\"CC BY 4.0\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"NCF\",\"depth\":2,\"concepts\":[{\"identifier\":\"Num:C2:SC1\",\"name\":\"Counting\",\"description\":\"Counting\",\"objectType\":\"Concept\",\"relation\":\"associatedTo\",\"status\":\"Retired\"}],\"compatibilityLevel\":1,\"name\":\"test resource\",\"status\":\"Draft\"}],\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2020-04-17T11:53:04.855+0530\",\"contentEncoding\":\"gzip\",\"contentType\":\"TextBookUnit\",\"dialcodeRequired\":\"No\",\"identifier\":\"do_11300156075913216016\",\"lastStatusChangedOn\":\"2020-04-17T11:53:04.855+0530\",\"audience\":[\"Learner\"],\"os\":[\"All\"],\"visibility\":\"Parent\",\"index\":1,\"mediaType\":\"content\",\"osId\":\"org.ekstep.launcher\",\"languageCode\":[\"en\"],\"versionKey\":\"1587104584855\",\"license\":\"CC BY 4.0\",\"idealScreenDensity\":\"hdpi\",\"depth\":1,\"compatibilityLevel\":1,\"name\":\"Test_TextBookUnit_name_7240493202\",\"status\":\"Draft\"}]}')"
        executeCassandraQuery(query)
        graphDb.execute("UNWIND [{ownershipType:[\"createdBy\"],code:\"txtbk\",channel:\"in.ekstep\",description:\"Text Book Test\",language:[\"English\"],mimeType:\"application/vnd.ekstep.content-collection\",idealScreenSize:\"normal\",createdOn:\"2020-04-17T11:52:15.303+0530\",contentDisposition:\"inline\",contentEncoding:\"gzip\",lastUpdatedOn:\"2020-04-17T11:53:05.434+0530\",contentType:\"Course\",dialcodeRequired:\"No\",identifier:\"do_11300156035268608015\",audience:[\"Learner\"],lastStatusChangedOn:\"2020-04-17T11:52:15.303+0530\",os:[\"All\"],visibility:\"Default\",IL_SYS_NODE_TYPE:\"DATA_NODE\",childNodes:[\"do_11300156075913216016\",\"do_11300155996401664014\"],mediaType:\"content\",osId:\"org.ekstep.quiz.app\",version:2,versionKey:\"1587104585434\",license:\"CC BY 4.0\",idealScreenDensity:\"hdpi\",depth:0,framework:\"NCF\",compatibilityLevel:1,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"TextBook\",IL_UNIQUE_ID:\"do_11300156035268608015\",status:\"Draft\"},{ownershipType:[\"createdBy\"],code:\"test-Resourcce\",prevStatus:\"Live\",channel:\"in.ekstep\",language:[\"English\"],mimeType:\"application/pdf\",idealScreenSize:\"normal\",createdOn:\"2020-04-17T11:51:30.230+0530\",contentDisposition:\"inline\",contentEncoding:\"identity\",lastUpdatedOn:\"2020-04-17T13:38:24.720+0530\",contentType:\"Resource\",dialcodeRequired:\"No\",audience:[\"Learner\"],lastStatusChangedOn:\"2020-04-17T13:38:22.954+0530\",os:[\"All\"],visibility:\"Default\",IL_SYS_NODE_TYPE:\"DATA_NODE\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",version:2,versionKey:\"1587110904720\",license:\"CC BY 4.0\",idealScreenDensity:\"hdpi\",framework:\"NCF\",compatibilityLevel:1,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"updated\",IL_UNIQUE_ID:\"do_11300155996401664014\",status:\"Draft\"}] as row CREATE (n:domain) SET n += row")
        val request = new Request()
        request.setContext(new util.HashMap[String, AnyRef]() {
            {
                put("objectType", "Content")
                put("graph_id", "domain")
                put("version", "1.0")
                put("schemaName", "collection")
                put("channel", "in.ekstep")
            }
        })
        request.put("mode","edit")
        request.put("rootId", "do_11300156035268608015")
        val future = HierarchyManager.getHierarchy(request)
        future.map(response => {
            assert(response.getResponseCode.code() == 200)
            assert(null != response.get("content"))
            val children = response.get("content").asInstanceOf[util.Map[String, AnyRef]].get("children").asInstanceOf[util.List[Map[String, AnyRef]]]
            assert(CollectionUtils.isNotEmpty(children))
            assert(CollectionUtils.isEmpty(children.get(0).asInstanceOf[util.Map[String, AnyRef]].get("children").asInstanceOf[util.List[Map[String, AnyRef]]]))
        })
    }
}
