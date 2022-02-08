package org.sunbird.managers

import java.util

import org.sunbird.common.JsonUtils
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.utils.HierarchyConstants

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class HierarchyManagerTest extends BaseSpec {

	implicit val oec: OntologyEngineContext = new OntologyEngineContext

	private val KEYSPACE_CREATE_SCRIPT = "CREATE KEYSPACE IF NOT EXISTS hierarchy_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
	private val TABLE_CREATE_SCRIPT = "CREATE TABLE IF NOT EXISTS hierarchy_store.questionset_hierarchy (identifier text,hierarchy text,instructions text,outcomeDeclaration text,PRIMARY KEY (identifier));"
	private val CATEGORY_STORE_KEYSPACE = "CREATE KEYSPACE IF NOT EXISTS category_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
	private val CATEGORY_DEF_DATA_TABLE = "CREATE TABLE IF NOT EXISTS category_store.category_definition_data (identifier text PRIMARY KEY, forms map<text, text>, objectmetadata map<text, text>);"
	private val CATEGORY_DEF_INPUT = List("INSERT INTO category_store.category_definition_data(identifier,objectmetadata) values ('obj-cat:survey_questionset_all',{'config': '{}', 'schema': '{\"properties\":{\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.sunbird.questionset\"]},\"allowBranching\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"},\"audience\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"Education Official\",\"School leaders (HMs)\",\"Administrator\",\"Teachers\",\"Students\",\"Parents\",\"Others\"]}},\"allowScoring\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"No\"},\"setPeriod\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"]}}}'})",
		"INSERT INTO category_store.category_definition_data(identifier,objectmetadata) values ('obj-cat:observation_questionset_all',{'config': '{}', 'schema': '{\"properties\":{\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.sunbird.questionset\"]},\"allowBranching\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"},\"audience\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"Education Official\",\"School leaders (HMs)\",\"Administrator\",\"Teachers\",\"Students\",\"Parents\",\"Others\"]}},\"allowScoring\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"No\"}}}'})",
		"INSERT INTO category_store.category_definition_data(identifier,objectmetadata) values ('obj-cat:practice-question-set_questionset_all',{'config': '{}', 'schema': '{\"properties\":{\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.sunbird.questionset\"]}}}'})",
		"INSERT INTO category_store.category_definition_data(identifier,objectmetadata) values ('obj-cat:multiple-choice-question_question_all',{'config': '{}', 'schema': '{\"properties\":{\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.sunbird.question\"]},\"interactionTypes\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"choice\"]}}}}'})",
		"INSERT INTO category_store.category_definition_data(identifier,objectmetadata) values ('obj-cat:text_question_all',{'config': '{}', 'schema': '{\"properties\":{\"interactionTypes\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"text\"]}},\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.sunbird.question\"]}}}'})")
	private val HIERARCHY_QS_1 = "INSERT INTO hierarchy_store.questionset_hierarchy (identifier,hierarchy) values('do_obs_123', '{\"identifier\":\"do_obs_123\",\"children\":[{\"parent\":\"do_obs_123\",\"code\":\"do_textq_draft_123\",\"description\":\"Text Type Question 1\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.sunbird.question\",\"createdOn\":\"2022-02-08T13:14:51.741+0000\",\"objectType\":\"Question\",\"primaryCategory\":\"Text\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2022-02-08T13:14:51.741+0000\",\"contentEncoding\":\"gzip\",\"showSolutions\":\"No\",\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_textq_draft_123\",\"lastStatusChangedOn\":\"2022-02-08T13:14:51.741+0000\",\"visibility\":\"Parent\",\"showTimer\":\"No\",\"index\":1,\"languageCode\":[\"en\"],\"version\":1,\"versionKey\":\"1644326091747\",\"showFeedback\":\"No\",\"license\":\"CC BY 4.0\",\"depth\":1,\"compatibilityLevel\":4,\"name\":\"Q1\",\"status\":\"Draft\"},{\"parent\":\"do_obs_123\",\"code\":\"do_mcqq_draft_123\",\"description\":\"MCQ\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.sunbird.question\",\"createdOn\":\"2022-02-08T13:14:51.742+0000\",\"objectType\":\"Question\",\"primaryCategory\":\"Multiple Choice Question\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2022-02-08T13:14:51.742+0000\",\"contentEncoding\":\"gzip\",\"showSolutions\":\"No\",\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_mcqq_draft_123\",\"lastStatusChangedOn\":\"2022-02-08T13:14:51.742+0000\",\"visibility\":\"Parent\",\"showTimer\":\"No\",\"index\":2,\"languageCode\":[\"en\"],\"version\":1,\"versionKey\":\"1644326091748\",\"showFeedback\":\"No\",\"license\":\"CC BY 4.0\",\"depth\":1,\"compatibilityLevel\":4,\"name\":\"Q2\",\"status\":\"Draft\"}]}')"
	private val HIERARCHY_QS_2 = "INSERT INTO hierarchy_store.questionset_hierarchy (identifier,hierarchy) values('do_obs_with_section_123', '{\"identifier\":\"do_obs_with_section_123\",\"children\":[{\"parent\":\"do_obs_with_section_123\",\"code\":\"section-1\",\"allowScoring\":\"No\",\"allowSkip\":\"Yes\",\"containsUserData\":\"No\",\"description\":\"section-1\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.sunbird.questionset\",\"showHints\":\"No\",\"createdOn\":\"2022-02-08T18:54:51.117+0000\",\"objectType\":\"QuestionSet\",\"primaryCategory\":\"Observation\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2022-02-08T18:54:51.117+0000\",\"contentEncoding\":\"gzip\",\"showSolutions\":\"No\",\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_section_1\",\"lastStatusChangedOn\":\"2022-02-08T18:54:51.117+0000\",\"requiresSubmit\":\"No\",\"visibility\":\"Parent\",\"showTimer\":\"No\",\"index\":1,\"setType\":\"materialised\",\"languageCode\":[\"en\"],\"version\":1,\"versionKey\":\"1644346491117\",\"showFeedback\":\"No\",\"license\":\"CC BY 4.0\",\"depth\":1,\"createdBy\":\"4e397c42-495e-4fdb-8558-f98176230916\",\"compatibilityLevel\":5,\"name\":\"section-1\",\"navigationMode\":\"non-linear\",\"allowBranching\":\"Yes\",\"shuffle\":true,\"status\":\"Draft\",\"children\":[{\"parent\":\"do_section_1\",\"code\":\"do_textq_draft_123\",\"description\":\"Text Type Question 1\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.sunbird.question\",\"createdOn\":\"2022-02-08T13:14:51.741+0000\",\"objectType\":\"Question\",\"primaryCategory\":\"Text\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2022-02-08T13:14:51.741+0000\",\"contentEncoding\":\"gzip\",\"showSolutions\":\"No\",\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_textq_draft_123\",\"lastStatusChangedOn\":\"2022-02-08T13:14:51.741+0000\",\"visibility\":\"Parent\",\"showTimer\":\"No\",\"index\":1,\"languageCode\":[\"en\"],\"version\":1,\"versionKey\":\"1644326091747\",\"showFeedback\":\"No\",\"license\":\"CC BY 4.0\",\"depth\":1,\"compatibilityLevel\":4,\"name\":\"Q1\",\"status\":\"Draft\"},{\"parent\":\"do_section_1\",\"code\":\"do_mcqq_draft_123\",\"description\":\"MCQ\",\"language\":[\"English\"],\"mimeType\":\"application/vnd.sunbird.question\",\"createdOn\":\"2022-02-08T13:14:51.742+0000\",\"objectType\":\"Question\",\"primaryCategory\":\"Multiple Choice Question\",\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2022-02-08T13:14:51.742+0000\",\"contentEncoding\":\"gzip\",\"showSolutions\":\"No\",\"allowAnonymousAccess\":\"Yes\",\"identifier\":\"do_mcqq_draft_123\",\"lastStatusChangedOn\":\"2022-02-08T13:14:51.742+0000\",\"visibility\":\"Parent\",\"showTimer\":\"No\",\"index\":2,\"languageCode\":[\"en\"],\"version\":1,\"versionKey\":\"1644326091748\",\"showFeedback\":\"No\",\"license\":\"CC BY 4.0\",\"depth\":1,\"compatibilityLevel\":4,\"name\":\"Q2\",\"status\":\"Draft\"}]}]}')"

	override def beforeAll(): Unit = {
		super.beforeAll()
		graphDb.execute("UNWIND [" +
		  "{IL_UNIQUE_ID:\"obj-cat:survey\",IL_SYS_NODE_TYPE:\"DATA_NODE\",lastStatusChangedOn:\"2021-08-20T09:35:01.320+0000\",consumerId:\"64fe2164-1523-4b44-8884-319b07384234\",name:\"Survey\",lastUpdatedOn:\"2021-08-20T09:35:01.320+0000\",description:\"Survey\",languageCode:[],createdOn:\"2021-08-20T09:35:01.320+0000\",IL_FUNC_OBJECT_TYPE:\"ObjectCategory\",versionKey:\"1629452101320\",status:\"Live\"}," +
		  "{IL_UNIQUE_ID:\"obj-cat:observation\",IL_SYS_NODE_TYPE:\"DATA_NODE\",lastStatusChangedOn:\"2021-08-20T09:35:19.249+0000\",consumerId:\"64fe2164-1523-4b44-8884-319b07384234\",name:\"Observation\",lastUpdatedOn:\"2021-08-20T09:35:19.249+0000\",description:\"Observation\",languageCode:[],createdOn:\"2021-08-20T09:35:19.249+0000\",IL_FUNC_OBJECT_TYPE:\"ObjectCategory\",versionKey:\"1629452119249\",status:\"Live\"}," +
		  "{IL_UNIQUE_ID:\"obj-cat:practice-question-set\",IL_SYS_NODE_TYPE:\"DATA_NODE\",lastStatusChangedOn:\"2020-10-12T07:07:24.631+0000\",name:\"Practice Question Set\",lastUpdatedOn:\"2020-10-12T07:07:24.631+0000\",description:\"Practice Question Set\",languageCode:[],createdOn:\"2020-10-12T07:07:24.631+0000\",IL_FUNC_OBJECT_TYPE:\"ObjectCategory\",versionKey:\"1602486444631\",status:\"Live\"}," +
		  "{IL_UNIQUE_ID:\"obj-cat:multiple-choice-question\",IL_SYS_NODE_TYPE:\"DATA_NODE\",lastStatusChangedOn:\"2021-01-04T05:49:19.878+0000\",name:\"Multiple Choice Question\",lastUpdatedOn:\"2021-01-04T05:49:19.878+0000\",description:\"Multiple Choice Question\",languageCode:[],createdOn:\"2021-01-04T05:49:19.878+0000\",IL_FUNC_OBJECT_TYPE:\"ObjectCategory\",versionKey:\"1609739359878\",status:\"Live\"}," +
		  "{IL_UNIQUE_ID:\"obj-cat:text\",IL_SYS_NODE_TYPE:\"DATA_NODE\",lastStatusChangedOn:\"2021-08-20T09:36:41.178+0000\",consumerId:\"64fe2164-1523-4b44-8884-319b07384234\",name:\"Text\",lastUpdatedOn:\"2021-08-20T09:36:41.178+0000\",description:\"Text\",languageCode:[],createdOn:\"2021-08-20T09:36:41.178+0000\",IL_FUNC_OBJECT_TYPE:\"ObjectCategory\",versionKey:\"1629452201178\",status:\"Live\"}," +
		  "{IL_UNIQUE_ID:\"obj-cat:survey_questionset_all\",IL_SYS_NODE_TYPE:\"DATA_NODE\",lastStatusChangedOn:\"2021-08-24T16:22:17.524+0000\",visibility:\"Default\",consumerId:\"64fe2164-1523-4b44-8884-319b07384234\",description:\"Survey\",languageCode:[],createdOn:\"2021-08-24T16:22:17.524+0000\",IL_FUNC_OBJECT_TYPE:\"ObjectCategoryDefinition\",versionKey:\"1643101793375\",name:\"Survey\",lastUpdatedOn:\"2022-01-25T09:09:53.375+0000\",targetObjectType:\"QuestionSet\",categoryId:\"obj-cat:survey\",status:\"Live\"}," +
		  "{IL_UNIQUE_ID:\"obj-cat:observation_questionset_all\",IL_SYS_NODE_TYPE:\"DATA_NODE\",lastStatusChangedOn:\"2021-08-24T16:22:45.014+0000\",visibility:\"Default\",consumerId:\"64fe2164-1523-4b44-8884-319b07384234\",description:\"Observation\",languageCode:[],createdOn:\"2021-08-24T16:22:45.014+0000\",IL_FUNC_OBJECT_TYPE:\"ObjectCategoryDefinition\",versionKey:\"1643101511878\",name:\"Observation\",lastUpdatedOn:\"2022-01-25T09:05:11.878+0000\",targetObjectType:\"QuestionSet\",categoryId:\"obj-cat:observation\",status:\"Live\"}," +
		  "{IL_UNIQUE_ID:\"obj-cat:practice-question-set_questionset_all\",IL_SYS_NODE_TYPE:\"DATA_NODE\",lastStatusChangedOn:\"2020-12-10T03:36:57.678+0000\",visibility:\"Default\",consumerId:\"64fe2164-1523-4b44-8884-319b07384234\",description:\"Practice Question Set\",languageCode:[],createdOn:\"2020-12-10T03:36:57.678+0000\",IL_FUNC_OBJECT_TYPE:\"ObjectCategoryDefinition\",versionKey:\"1638263933587\",name:\"Demo Practice Question Set\",lastUpdatedOn:\"2021-11-30T09:18:53.587+0000\",targetObjectType:\"QuestionSet\",categoryId:\"obj-cat:practice-question-set\",status:\"Live\"}," +
		  "{IL_UNIQUE_ID:\"obj-cat:multiple-choice-question_question_all\",IL_SYS_NODE_TYPE:\"DATA_NODE\",lastStatusChangedOn:\"2021-01-04T05:50:31.468+0000\",visibility:\"Default\",consumerId:\"5880ed91-28c4-4d34-9919-036982b0c4ea\",description:\"Multiple Choice Question\",languageCode:[],createdOn:\"2021-01-04T05:50:31.468+0000\",IL_FUNC_OBJECT_TYPE:\"ObjectCategoryDefinition\",versionKey:\"1640699675966\",name:\"Multiple Choice Question\",lastUpdatedOn:\"2021-12-28T13:54:35.966+0000\",targetObjectType:\"Question\",categoryId:\"obj-cat:multiple-choice-question\",status:\"Live\"}," +
		  "{IL_UNIQUE_ID:\"obj-cat:text_question_all\",IL_SYS_NODE_TYPE:\"DATA_NODE\",lastStatusChangedOn:\"2021-08-24T16:23:32.869+0000\",visibility:\"Default\",consumerId:\"64fe2164-1523-4b44-8884-319b07384234\",description:\"Text\",languageCode:[],createdOn:\"2021-08-24T16:23:32.869+0000\",IL_FUNC_OBJECT_TYPE:\"ObjectCategoryDefinition\",versionKey:\"1641394368538\",name:\"Text\",lastUpdatedOn:\"2022-01-05T14:52:48.538+0000\",targetObjectType:\"Question\",categoryId:\"obj-cat:text\",status:\"Live\"}," +
		  "{IL_UNIQUE_ID:\"do_obs_123\",IL_FUNC_OBJECT_TYPE:\"QuestionSet\",IL_SYS_NODE_TYPE:\"DATA_NODE\",code:\"c288ea98-0a8d-c927-5ec3-e879a90e9e43\",allowScoring:\"No\",allowSkip:\"Yes\",containsUserData:\"No\",language:[\"English\"],mimeType:\"application/vnd.sunbird.questionset\",showHints:\"No\",createdOn:\"2022-02-06T20:43:01.535+0000\",primaryCategory:\"Observation\",contentDisposition:\"inline\",lastUpdatedOn:\"2022-02-06T20:43:01.535+0000\",contentEncoding:\"gzip\",showSolutions:\"No\",allowAnonymousAccess:\"Yes\",lastStatusChangedOn:\"2022-02-06T20:43:01.535+0000\",createdFor:[\"sunbird\"],requiresSubmit:\"No\",visibility:\"Default\",showTimer:\"No\",setType:\"materialised\",languageCode:[\"en\"],version:1,versionKey:\"1644180181535\",showFeedback:\"No\",license:\"CC BY 4.0\",createdBy:\"crt-01\",compatibilityLevel:5,name:\"Test Observation\",navigationMode:\"non-linear\",allowBranching:\"Yes\",shuffle:true,status:\"Draft\"}," +
		  "{IL_UNIQUE_ID:\"do_obs_with_section_123\",IL_FUNC_OBJECT_TYPE:\"QuestionSet\",IL_SYS_NODE_TYPE:\"DATA_NODE\",code:\"c288ea98-0a8d-c927-5ec3-e879a90e9e43\",allowScoring:\"No\",allowSkip:\"Yes\",containsUserData:\"No\",language:[\"English\"],mimeType:\"application/vnd.sunbird.questionset\",showHints:\"No\",createdOn:\"2022-02-06T20:43:01.535+0000\",primaryCategory:\"Observation\",contentDisposition:\"inline\",lastUpdatedOn:\"2022-02-06T20:43:01.535+0000\",contentEncoding:\"gzip\",showSolutions:\"No\",allowAnonymousAccess:\"Yes\",lastStatusChangedOn:\"2022-02-06T20:43:01.535+0000\",createdFor:[\"sunbird\"],requiresSubmit:\"No\",visibility:\"Default\",showTimer:\"No\",setType:\"materialised\",languageCode:[\"en\"],version:1,versionKey:\"1644180181535\",showFeedback:\"No\",license:\"CC BY 4.0\",createdBy:\"crt-01\",compatibilityLevel:5,name:\"Test Observation\",navigationMode:\"non-linear\",allowBranching:\"Yes\",shuffle:true,status:\"Draft\",childNodes:[\"do_section_1\"]}," +
		  "{IL_UNIQUE_ID:\"do_textq_live_123\",IL_SYS_NODE_TYPE:\"DATA_NODE\",code:\"Q2_1616157220157\",showRemarks:\"No\",channel:\"sunbird\",language:[\"English\"],mimeType:\"application/vnd.sunbird.question\",createdOn:\"2022-02-07T12:49:25.908+0000\",IL_FUNC_OBJECT_TYPE:\"Question\",primaryCategory:\"Text\",contentDisposition:\"inline\",lastUpdatedOn:\"2022-02-07T12:49:25.908+0000\",contentEncoding:\"gzip\",showSolutions:\"No\",allowAnonymousAccess:\"Yes\",lastStatusChangedOn:\"2022-02-07T12:49:25.908+0000\",visibility:\"Default\",showTimer:\"No\",consumerId:\"64fe2164-1523-4b44-8884-319b07384234\",languageCode:[\"en\"],version:1,versionKey:\"1644238165908\",showFeedback:\"No\",license:\"CC BY 4.0\",interactionTypes:[\"text\"],compatibilityLevel:4,name:\"Text Type Question\",status:\"Live\"}," +
		  "{IL_UNIQUE_ID:\"do_textq_draft_123\",IL_SYS_NODE_TYPE:\"DATA_NODE\",code:\"Q2_1616157220157\",showRemarks:\"No\",channel:\"sunbird\",language:[\"English\"],mimeType:\"application/vnd.sunbird.question\",createdOn:\"2022-02-07T12:49:25.908+0000\",IL_FUNC_OBJECT_TYPE:\"Question\",primaryCategory:\"Text\",contentDisposition:\"inline\",lastUpdatedOn:\"2022-02-07T12:49:25.908+0000\",contentEncoding:\"gzip\",showSolutions:\"No\",allowAnonymousAccess:\"Yes\",lastStatusChangedOn:\"2022-02-07T12:49:25.908+0000\",visibility:\"Default\",showTimer:\"No\",consumerId:\"64fe2164-1523-4b44-8884-319b07384234\",languageCode:[\"en\"],version:1,versionKey:\"1644238165908\",showFeedback:\"No\",license:\"CC BY 4.0\",interactionTypes:[\"text\"],compatibilityLevel:4,name:\"Text Type Question\",status:\"Draft\"}," +
		  "{IL_UNIQUE_ID:\"do_mcqq_draft_123\",IL_SYS_NODE_TYPE:\"DATA_NODE\",code:\"Q2_1616157220157\",showRemarks:\"No\",channel:\"sunbird\",language:[\"English\"],mimeType:\"application/vnd.sunbird.question\",createdOn:\"2022-02-07T12:49:25.908+0000\",IL_FUNC_OBJECT_TYPE:\"Question\",primaryCategory:\"Multiple Choice Question\",contentDisposition:\"inline\",lastUpdatedOn:\"2022-02-07T12:49:25.908+0000\",contentEncoding:\"gzip\",showSolutions:\"No\",allowAnonymousAccess:\"Yes\",lastStatusChangedOn:\"2022-02-07T12:49:25.908+0000\",visibility:\"Default\",showTimer:\"No\",consumerId:\"64fe2164-1523-4b44-8884-319b07384234\",languageCode:[\"en\"],version:1,versionKey:\"1644238165908\",showFeedback:\"No\",license:\"CC BY 4.0\",interactionTypes:[\"choice\"],compatibilityLevel:4,name:\"Text Type Question\",status:\"Draft\"}," +
		  "{IL_UNIQUE_ID:\"do_mcqq_live_123\",IL_SYS_NODE_TYPE:\"DATA_NODE\",code:\"Q2_1616157220157\",showRemarks:\"No\",channel:\"sunbird\",language:[\"English\"],mimeType:\"application/vnd.sunbird.question\",createdOn:\"2022-02-07T12:49:25.908+0000\",IL_FUNC_OBJECT_TYPE:\"Question\",primaryCategory:\"Multiple Choice Question\",contentDisposition:\"inline\",lastUpdatedOn:\"2022-02-07T12:49:25.908+0000\",contentEncoding:\"gzip\",showSolutions:\"No\",allowAnonymousAccess:\"Yes\",lastStatusChangedOn:\"2022-02-07T12:49:25.908+0000\",visibility:\"Default\",showTimer:\"No\",consumerId:\"64fe2164-1523-4b44-8884-319b07384234\",languageCode:[\"en\"],version:1,versionKey:\"1644238165908\",showFeedback:\"No\",license:\"CC BY 4.0\",interactionTypes:[\"choice\"],compatibilityLevel:4,name:\"Text Type Question\",status:\"Live\"}" +
		  "] as row CREATE (n:domain) SET n += row")
		executeCassandraQuery(KEYSPACE_CREATE_SCRIPT, TABLE_CREATE_SCRIPT, CATEGORY_STORE_KEYSPACE, CATEGORY_DEF_DATA_TABLE, HIERARCHY_QS_1, HIERARCHY_QS_2)
		executeCassandraQuery(CATEGORY_DEF_INPUT: _*)
		println("all query executed...")

	}

	override def beforeEach(): Unit = {

	}

	def getContext(objectType: String): java.util.Map[String, AnyRef] = {
		new util.HashMap[String, AnyRef]() {
			{
				put(HierarchyConstants.GRAPH_ID, HierarchyConstants.TAXONOMY_ID)
				put(HierarchyConstants.VERSION, HierarchyConstants.SCHEMA_VERSION)
				put(HierarchyConstants.OBJECT_TYPE, objectType)
				put(HierarchyConstants.SCHEMA_NAME, objectType.toLowerCase)
				put(HierarchyConstants.CHANNEL, "sunbird")
			}
		}
	}

	"addLeafNodesToHierarchy" should "add leaf node under root" in {
		val request = new Request()
		request.setContext(getContext(HierarchyConstants.QUESTIONSET_OBJECT_TYPE))
		request.putAll(mapAsJavaMap(Map(HierarchyConstants.ROOT_ID -> "do_obs_123", "mode" -> "edit", "children" -> List("do_textq_live_123").asJava)))
		val future = HierarchyManager.addLeafNodesToHierarchy(request)
		future.map(response => {
			assert(response.getResponseCode.code() == 200)
			assert(response.getResult.get("children").asInstanceOf[util.List[String]].containsAll(request.get("children").asInstanceOf[util.List[String]]))
			val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.questionset_hierarchy where identifier='do_obs_123'")
			  .one().getString("hierarchy")
			assert(hierarchy.contains("do_textq_live_123"))
		})
	}

	"addLeafNodesToHierarchy with section and valid branching logic" should "add leaf node under section along with branching logic" in {
		val request = new Request()
		request.setContext(getContext(HierarchyConstants.QUESTIONSET_OBJECT_TYPE))
		val branchingLogicStr = "{\"do_textq_live_123\":{\"target\":[],\"preCondition\":{\"and\":[{\"eq\":[{\"var\":\"do_textq_draft_123.response1.value\",\"type\":\"responseDeclaration\"},\"0\"]}]},\"source\":[\"do_textq_draft_123\"]}}"
		request.putAll(mapAsJavaMap(Map(HierarchyConstants.ROOT_ID -> "do_obs_with_section_123", "mode" -> "edit", "collectionId" -> "do_section_1", "children" -> List("do_textq_live_123").asJava, "branchingLogic" -> JsonUtils.deserialize(branchingLogicStr, classOf[util.HashMap[String, AnyRef]]))))
		val future = HierarchyManager.addLeafNodesToHierarchy(request)
		future.map(response => {
			assert(response.getResponseCode.code() == 200)
			val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.questionset_hierarchy where identifier='do_obs_with_section_123'")
			  .one().getString("hierarchy")
			assert(hierarchy.contains("do_textq_live_123"))
			assert(hierarchy.contains("branchingLogic"))
		})
	}

	"addLeafNodesToHierarchy with section and invalid branching logic" should "throw client error" in {
		val request = new Request()
		request.setContext(getContext(HierarchyConstants.QUESTIONSET_OBJECT_TYPE))
		val branchingLogicStr = "{\"do_text_live_123\":{\"target\":[],\"preCondition\":{\"and\":[{\"eq\":[{\"var\":\"do_textq_draft_123.response1.value\",\"type\":\"responseDeclaration\"},\"0\"]}]},\"source\":[\"do_textq_draft_123\"]}}"
		request.putAll(mapAsJavaMap(Map(HierarchyConstants.ROOT_ID -> "do_obs_with_section_123", "mode" -> "edit", "collectionId" -> "do_section_1", "children" -> List("do_textq_live_123").asJava, "branchingLogic" -> JsonUtils.deserialize(branchingLogicStr, classOf[util.HashMap[String, AnyRef]]))))
		val exception = intercept[ClientException] {
			HierarchyManager.addLeafNodesToHierarchy(request)
		}
		exception.getErrCode shouldEqual "ERR_BAD_REQUEST"
		exception.getMessage shouldEqual "Branch Rule Found For The Node Which Is Not A Children Having Identifier : [do_text_live_123]"

	}
}
