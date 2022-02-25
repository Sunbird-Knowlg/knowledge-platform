package org.sunbird.managers

import java.util

import org.apache.commons.collections4.MapUtils
import org.parboiled.common.StringUtils
import org.sunbird.common.JsonUtils
import org.sunbird.common.dto.Request
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.utils.HierarchyConstants

class UpdateHierarchyManagerTest extends BaseSpec {

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
		  "{IL_UNIQUE_ID:\"obj-cat:practice-question-set_questionset_all\",IL_SYS_NODE_TYPE:\"DATA_NODE\",lastStatusChangedOn:\"2020-12-10T03:36:57.678+0000\",visibility:\"Default\",consumerId:\"64fe2164-1523-4b44-8884-319b07384234\",description:\"Practice Question Set\",languageCode:[],createdOn:\"2020-12-10T03:36:57.678+0000\",IL_FUNC_OBJECT_TYPE:\"ObjectCategoryDefinition\",versionKey:\"1638263933587\",name:\"Demo Practice Question Set\",lastUpdatedOn:\"2021-11-30T09:18:53.587+0000\",targetObjectType:\"QuestionSet\",categoryId:\"obj-cat:practice-question-set\",status:\"Live\"},"+
		  "{IL_UNIQUE_ID:\"obj-cat:multiple-choice-question_question_all\",IL_SYS_NODE_TYPE:\"DATA_NODE\",lastStatusChangedOn:\"2021-01-04T05:50:31.468+0000\",visibility:\"Default\",consumerId:\"5880ed91-28c4-4d34-9919-036982b0c4ea\",description:\"Multiple Choice Question\",languageCode:[],createdOn:\"2021-01-04T05:50:31.468+0000\",IL_FUNC_OBJECT_TYPE:\"ObjectCategoryDefinition\",versionKey:\"1640699675966\",name:\"Multiple Choice Question\",lastUpdatedOn:\"2021-12-28T13:54:35.966+0000\",targetObjectType:\"Question\",categoryId:\"obj-cat:multiple-choice-question\",status:\"Live\"}," +
		  "{IL_UNIQUE_ID:\"obj-cat:text_question_all\",IL_SYS_NODE_TYPE:\"DATA_NODE\",lastStatusChangedOn:\"2021-08-24T16:23:32.869+0000\",visibility:\"Default\",consumerId:\"64fe2164-1523-4b44-8884-319b07384234\",description:\"Text\",languageCode:[],createdOn:\"2021-08-24T16:23:32.869+0000\",IL_FUNC_OBJECT_TYPE:\"ObjectCategoryDefinition\",versionKey:\"1641394368538\",name:\"Text\",lastUpdatedOn:\"2022-01-05T14:52:48.538+0000\",targetObjectType:\"Question\",categoryId:\"obj-cat:text\",status:\"Live\"}" +
		  "] as row CREATE (n:domain) SET n += row")
		executeCassandraQuery(KEYSPACE_CREATE_SCRIPT, TABLE_CREATE_SCRIPT, CATEGORY_STORE_KEYSPACE, CATEGORY_DEF_DATA_TABLE)
		executeCassandraQuery(CATEGORY_DEF_INPUT:_*)
		println("all query executed...")

	}

	override def beforeEach(): Unit = {

	}

	def getContext(objectType: String): java.util.Map[String, AnyRef] = {
		new util.HashMap[String, AnyRef](){{
			put(HierarchyConstants.GRAPH_ID, HierarchyConstants.TAXONOMY_ID)
			put(HierarchyConstants.VERSION , HierarchyConstants.SCHEMA_VERSION)
			put(HierarchyConstants.OBJECT_TYPE , objectType)
			put(HierarchyConstants.SCHEMA_NAME, objectType.toLowerCase)
			put(HierarchyConstants.CHANNEL, "sunbird")
		}}
	}

	"updateHierarchy" should "create the hierarchy structure for questionset" in {
		graphDb.execute("UNWIND [" +
		 "{IL_UNIQUE_ID:\"do_obs_1234\",IL_FUNC_OBJECT_TYPE:\"QuestionSet\",IL_SYS_NODE_TYPE:\"DATA_NODE\",code:\"c288ea98-0a8d-c927-5ec3-e879a90e9e43\",allowScoring:\"No\",allowSkip:\"Yes\",containsUserData:\"No\",language:[\"English\"],mimeType:\"application/vnd.sunbird.questionset\",showHints:\"No\",createdOn:\"2022-02-06T20:43:01.535+0000\",primaryCategory:\"Observation\",contentDisposition:\"inline\",lastUpdatedOn:\"2022-02-06T20:43:01.535+0000\",contentEncoding:\"gzip\",showSolutions:\"No\",allowAnonymousAccess:\"Yes\",lastStatusChangedOn:\"2022-02-06T20:43:01.535+0000\",createdFor:[\"sunbird\"],requiresSubmit:\"No\",visibility:\"Default\",showTimer:\"No\",setType:\"materialised\",languageCode:[\"en\"],version:1,versionKey:\"1644180181535\",showFeedback:\"No\",license:\"CC BY 4.0\",createdBy:\"crt-01\",compatibilityLevel:5,name:\"Test Observation\",navigationMode:\"non-linear\",allowBranching:\"Yes\",shuffle:true,status:\"Draft\"}" +
		  "] as row CREATE (n:domain) SET n += row")
		val nodesModifiedString = "{\n  \"Q1\": {\n    \"metadata\": {\n      \"code\": \"Q1\",\n      \"name\": \"Q1\",\n      \"description\": \"Q1\",\n      \"mimeType\": \"application/vnd.sunbird.question\",\n      \"primaryCategory\": \"Text\"\n    },\n    \"objectType\": \"Question\",\n    \"root\": false,\n    \"isNew\": true\n  },\n  \"Q2\": {\n    \"metadata\": {\n      \"code\": \"Q2\",\n      \"name\": \"Q2\",\n      \"description\": \"Q2\",\n      \"mimeType\": \"application/vnd.sunbird.question\",\n      \"primaryCategory\": \"Text\"\n    },\n    \"objectType\": \"Question\",\n    \"root\": false,\n    \"isNew\": true\n  },\n  \"Q3\": {\n    \"metadata\": {\n      \"code\": \"Q3\",\n      \"name\": \"Q3\",\n      \"description\": \"Q3\",\n      \"mimeType\": \"application/vnd.sunbird.question\",\n      \"primaryCategory\": \"Text\"\n    },\n    \"objectType\": \"Question\",\n    \"root\": false,\n    \"isNew\": true\n  },\n  \"do_obs_1234\": {\n    \"metadata\": {\n      \"branchingLogic\": {\n        \"Q1\": {\n          \"target\": [\n            \"Q2\"\n          ],\n          \"preCondition\": {},\n          \"source\": []\n        },\n        \"Q2\": {\n          \"target\": [],\n          \"preCondition\": {\n            \"and\": [\n              {\n                \"eq\": [\n                  {\n                    \"var\": \"Q1.response1.value\",\n                    \"type\": \"responseDeclaration\"\n                  },\n                  \"0\"\n                ]\n              }\n            ]\n          },\n          \"source\": [\n            \"Q1\"\n          ]\n        }\n      }\n    },\n    \"objectType\": \"QuestionSet\",\n    \"root\": true,\n    \"isNew\": false\n  }\n}"
		val hierarchyString = "{\n      \"do_obs_1234\": {\n        \"children\": [\n          \"Q1\",\n          \"Q2\",\n          \"Q3\"\n        ],\n        \"root\": true\n      }\n    }"
		val request = new Request()
		val context = getContext(HierarchyConstants.QUESTIONSET_OBJECT_TYPE)
		context.put(HierarchyConstants.ROOT_ID, "do_obs_1234")
		request.setContext(context)
		request.put(HierarchyConstants.NODES_MODIFIED, JsonUtils.deserialize(nodesModifiedString, classOf[util.HashMap[String, AnyRef]]))
		request.put(HierarchyConstants.HIERARCHY, JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]]))
		UpdateHierarchyManager.updateHierarchy(request).map(response => {
			assert(response.getResponseCode.code() == 200)
			val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.questionset_hierarchy where identifier='do_obs_1234'")
			  .one().getString("hierarchy")
			val result = graphDb.execute("MATCH(n) WHERE n.IL_UNIQUE_ID=\"do_obs_1234\" RETURN n.branchingLogic AS branchingLogic;")
			val record = if(result.hasNext) result.next()
			val branchingLogic = JsonUtils.deserialize(record.asInstanceOf[java.util.Map[String, AnyRef]].get("branchingLogic").toString, classOf[util.HashMap[String, AnyRef]])
			assert(MapUtils.isNotEmpty(branchingLogic))
			assert(branchingLogic.size()==2)
			assert(StringUtils.isNotEmpty(hierarchy))
		})
	}

	"updateHierarchy with instruction metadata at section level" should "create the hierarchy structure for questionset" in {
		graphDb.execute("UNWIND [" +
		  "{IL_UNIQUE_ID:\"do_pqs_1234\",IL_FUNC_OBJECT_TYPE:\"QuestionSet\",IL_SYS_NODE_TYPE:\"DATA_NODE\",code:\"c288ea98-0a8d-c927-5ec3-e879a90e9e43\",allowScoring:\"No\",allowSkip:\"Yes\",containsUserData:\"No\",language:[\"English\"],mimeType:\"application/vnd.sunbird.questionset\",showHints:\"No\",createdOn:\"2022-02-06T20:43:01.535+0000\",primaryCategory:\"Practice Question Set\",contentDisposition:\"inline\",lastUpdatedOn:\"2022-02-06T20:43:01.535+0000\",contentEncoding:\"gzip\",showSolutions:\"No\",allowAnonymousAccess:\"Yes\",lastStatusChangedOn:\"2022-02-06T20:43:01.535+0000\",createdFor:[\"sunbird\"],requiresSubmit:\"No\",visibility:\"Default\",showTimer:\"No\",setType:\"materialised\",languageCode:[\"en\"],version:1,versionKey:\"1644180181535\",showFeedback:\"No\",license:\"CC BY 4.0\",createdBy:\"crt-01\",compatibilityLevel:5,name:\"Test Observation\",navigationMode:\"non-linear\",allowBranching:\"Yes\",shuffle:true,status:\"Draft\"}" +
		  "] as row CREATE (n:domain) SET n += row")
		val nodesModifiedString = "{\"section-1\":{\"metadata\":{\"code\":\"section-1\",\"name\":\"section-1\",\"description\":\"section-1\",\"mimeType\":\"application/vnd.sunbird.questionset\",\"primaryCategory\":\"Practice Question Set\",\"createdBy\":\"4e397c42-495e-4fdb-8558-f98176230916\",\"instructions\":{\"default\":\"estv\"}},\"objectType\":\"QuestionSet\",\"root\":false,\"isNew\":true},\"Q1\":{\"metadata\":{\"code\":\"Q1\",\"name\":\"Q1\",\"description\":\"Q1\",\"mimeType\":\"application/vnd.sunbird.question\",\"primaryCategory\":\"Text\",\"createdBy\":\"4e397c42-495e-4fdb-8558-f98176230916\"},\"objectType\":\"Question\",\"root\":false,\"isNew\":true},\"Q2\":{\"metadata\":{\"code\":\"Q2\",\"name\":\"Q2\",\"description\":\"Q2\",\"mimeType\":\"application/vnd.sunbird.question\",\"primaryCategory\":\"Text\",\"visibility\":\"Default\",\"createdBy\":\"4e397c42-495e-4fdb-8558-f98176230916\"},\"objectType\":\"Question\",\"root\":false,\"isNew\":true}}"
		val hierarchyString = "{\"do_pqs_1234\":{\"children\":[\"section-1\"],\"root\":true},\"section-1\":{\"children\":[\"Q1\",\"Q2\"],\"root\":false}}"
		val request = new Request()
		val context = getContext(HierarchyConstants.QUESTIONSET_OBJECT_TYPE)
		context.put(HierarchyConstants.ROOT_ID, "do_pqs_1234")
		request.setContext(context)
		request.put(HierarchyConstants.NODES_MODIFIED, JsonUtils.deserialize(nodesModifiedString, classOf[util.HashMap[String, AnyRef]]))
		request.put(HierarchyConstants.HIERARCHY, JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]]))
		UpdateHierarchyManager.updateHierarchy(request).map(response => {
			assert(response.getResponseCode.code() == 200)
			val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.questionset_hierarchy where identifier='do_pqs_1234'")
			  .one().getString("hierarchy")
			assert(StringUtils.isNotEmpty(hierarchy))
			assert(hierarchy.contains("estv"))
		})
	}

	"updateHierarchy with root and section meta" should "update the hierarchy structure for questionset" in {
		graphDb.execute("UNWIND [" +
		  "{IL_UNIQUE_ID:\"do_pqs_12345\",IL_FUNC_OBJECT_TYPE:\"QuestionSet\",IL_SYS_NODE_TYPE:\"DATA_NODE\",code:\"c288ea98-0a8d-c927-5ec3-e879a90e9e43\",allowScoring:\"No\",allowSkip:\"Yes\",containsUserData:\"No\",language:[\"English\"],mimeType:\"application/vnd.sunbird.questionset\",showHints:\"No\",createdOn:\"2022-02-06T20:43:01.535+0000\",primaryCategory:\"Practice Question Set\",contentDisposition:\"inline\",lastUpdatedOn:\"2022-02-06T20:43:01.535+0000\",contentEncoding:\"gzip\",showSolutions:\"No\",allowAnonymousAccess:\"Yes\",lastStatusChangedOn:\"2022-02-06T20:43:01.535+0000\",createdFor:[\"sunbird\"],requiresSubmit:\"No\",visibility:\"Default\",showTimer:\"No\",setType:\"materialised\",languageCode:[\"en\"],version:1,versionKey:\"1644180181535\",showFeedback:\"No\",license:\"CC BY 4.0\",createdBy:\"crt-01\",compatibilityLevel:5,name:\"Test Observation\",navigationMode:\"non-linear\",allowBranching:\"Yes\",shuffle:true,status:\"Draft\"}" +
		  "] as row CREATE (n:domain) SET n += row")
		val nodesModifiedString = "{\"section-1\":{\"metadata\":{\"code\":\"section-1\",\"name\":\"section-1\",\"description\":\"section-1\",\"mimeType\":\"application/vnd.sunbird.questionset\",\"primaryCategory\":\"Practice Question Set\",\"createdBy\":\"4e397c42-495e-4fdb-8558-f98176230916\"},\"objectType\":\"QuestionSet\",\"root\":false,\"isNew\":true},\"Q1\":{\"metadata\":{\"code\":\"Q1\",\"name\":\"Q1\",\"description\":\"Q1\",\"mimeType\":\"application/vnd.sunbird.question\",\"primaryCategory\":\"Text\",\"createdBy\":\"4e397c42-495e-4fdb-8558-f98176230916\"},\"objectType\":\"Question\",\"root\":false,\"isNew\":true},\"Q2\":{\"metadata\":{\"code\":\"Q2\",\"name\":\"Q2\",\"description\":\"Q2\",\"mimeType\":\"application/vnd.sunbird.question\",\"primaryCategory\":\"Text\",\"visibility\":\"Default\",\"createdBy\":\"4e397c42-495e-4fdb-8558-f98176230916\"},\"objectType\":\"Question\",\"root\":false,\"isNew\":true}}"
		val hierarchyString = "{\"do_pqs_12345\":{\"children\":[\"section-1\"],\"root\":true},\"section-1\":{\"children\":[\"Q1\",\"Q2\"],\"root\":false}}"
		val request = new Request()
		val context = getContext(HierarchyConstants.QUESTIONSET_OBJECT_TYPE)
		context.put(HierarchyConstants.ROOT_ID, "do_pqs_12345")
		request.setContext(context)
		request.put(HierarchyConstants.NODES_MODIFIED, JsonUtils.deserialize(nodesModifiedString, classOf[util.HashMap[String, AnyRef]]))
		request.put(HierarchyConstants.HIERARCHY, JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]]))
		UpdateHierarchyManager.updateHierarchy(request).map(response => {
			assert(response.getResponseCode.code() == 200)
			val idMap = response.getResult.get("identifiers").asInstanceOf[java.util.Map[String, AnyRef]]
			val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.questionset_hierarchy where identifier='do_pqs_12345'")
			  .one().getString("hierarchy")
			assert(StringUtils.isNotEmpty(hierarchy))
			val nodesModifiedString = s"""{"do_pqs_12345":{"root":true,"objectType":"QuestionSet","metadata":{"name":"Updated Name - Test QS","language":["English"],"audience":[],"allowScoring":"No","primaryCategory":"Practice Question Set","attributions":[],"maxScore":0},"isNew":false},"${idMap.get("section-1").asInstanceOf[String]}":{"root":false,"objectType":"QuestionSet","metadata":{"name":"Section 1 - Updated Name","description":"updated desc - next update","primaryCategory":"Practice Question Set","attributions":[]},"isNew":false}}"""
			val hierarchyString = s"""{"do_pqs_12345":{"name":"Updated Name - Test QS","children":["${idMap.get("section-1").asInstanceOf[String]}"],"root":true},"${idMap.get("section-1").asInstanceOf[String]}":{"name":"Section 1 - Updated Name","children":["${idMap.get("Q1").asInstanceOf[String]}","${idMap.get("Q2").asInstanceOf[String]}"],"root":false},"${idMap.get("Q1").asInstanceOf[String]}":{"children":[],"root":false},"${idMap.get("Q2").asInstanceOf[String]}":{"children":[],"root":false}}"""
			val request = new Request()
			val context = getContext(HierarchyConstants.QUESTIONSET_OBJECT_TYPE)
			context.put(HierarchyConstants.ROOT_ID, "do_pqs_12345")
			request.setContext(context)
			request.put(HierarchyConstants.NODES_MODIFIED, JsonUtils.deserialize(nodesModifiedString, classOf[util.HashMap[String, AnyRef]]))
			request.put(HierarchyConstants.HIERARCHY, JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]]))
			UpdateHierarchyManager.updateHierarchy(request).map(response => {
				assert(response.getResponseCode.code() == 200)
				val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.questionset_hierarchy where identifier='do_pqs_12345'")
				  .one().getString("hierarchy")
				assert(StringUtils.isNotEmpty(hierarchy))
			})
		}).flatMap(f => f)
	}

	"updateHierarchy with section without children" should "update the hierarchy structure for questionset" in {
		graphDb.execute("UNWIND [" +
		  "{IL_UNIQUE_ID:\"do_pqs_123456\",IL_FUNC_OBJECT_TYPE:\"QuestionSet\",IL_SYS_NODE_TYPE:\"DATA_NODE\",code:\"c288ea98-0a8d-c927-5ec3-e879a90e9e43\",allowScoring:\"No\",allowSkip:\"Yes\",containsUserData:\"No\",language:[\"English\"],mimeType:\"application/vnd.sunbird.questionset\",showHints:\"No\",createdOn:\"2022-02-06T20:43:01.535+0000\",primaryCategory:\"Practice Question Set\",contentDisposition:\"inline\",lastUpdatedOn:\"2022-02-06T20:43:01.535+0000\",contentEncoding:\"gzip\",showSolutions:\"No\",allowAnonymousAccess:\"Yes\",lastStatusChangedOn:\"2022-02-06T20:43:01.535+0000\",createdFor:[\"sunbird\"],requiresSubmit:\"No\",visibility:\"Default\",showTimer:\"No\",setType:\"materialised\",languageCode:[\"en\"],version:1,versionKey:\"1644180181535\",showFeedback:\"No\",license:\"CC BY 4.0\",createdBy:\"crt-01\",compatibilityLevel:5,name:\"Test Observation\",navigationMode:\"non-linear\",allowBranching:\"Yes\",shuffle:true,status:\"Draft\"}" +
		  "] as row CREATE (n:domain) SET n += row")
		val nodesModifiedString = "{\"section-1\":{\"metadata\":{\"code\":\"section-1\",\"name\":\"section-1\",\"description\":\"section-1\",\"mimeType\":\"application/vnd.sunbird.questionset\",\"primaryCategory\":\"Practice Question Set\",\"createdBy\":\"4e397c42-495e-4fdb-8558-f98176230916\"},\"objectType\":\"QuestionSet\",\"root\":false,\"isNew\":true}}"
		val hierarchyString = "{\"do_pqs_123456\":{\"children\":[\"section-1\"],\"root\":true},\"section-1\":{\"children\":[],\"root\":false}}"
		val request = new Request()
		val context = getContext(HierarchyConstants.QUESTIONSET_OBJECT_TYPE)
		context.put(HierarchyConstants.ROOT_ID, "do_pqs_123456")
		request.setContext(context)
		request.put(HierarchyConstants.NODES_MODIFIED, JsonUtils.deserialize(nodesModifiedString, classOf[util.HashMap[String, AnyRef]]))
		request.put(HierarchyConstants.HIERARCHY, JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]]))
		UpdateHierarchyManager.updateHierarchy(request).map(response => {
			assert(response.getResponseCode.code() == 200)
			val idMap = response.getResult.get("identifiers").asInstanceOf[java.util.Map[String, AnyRef]]
			val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.questionset_hierarchy where identifier='do_pqs_123456'")
			  .one().getString("hierarchy")
			assert(StringUtils.isNotEmpty(hierarchy))
			val nodesModifiedString = s"""{"do_pqs_123456":{"root":true,"objectType":"QuestionSet","metadata":{"name":"Updated Name - Test QS","language":["English"],"audience":[],"allowScoring":"No","primaryCategory":"Practice Question Set","attributions":[],"maxScore":0},"isNew":false},"${idMap.get("section-1").asInstanceOf[String]}":{"root":false,"objectType":"QuestionSet","metadata":{"name":"Section 1 - Updated Name","description":"updated desc - next update","primaryCategory":"Practice Question Set","attributions":[]},"isNew":false}}"""
			val hierarchyString = s"""{"do_pqs_123456":{"name":"Updated Name - Test QS","children":["${idMap.get("section-1").asInstanceOf[String]}"],"root":true},"${idMap.get("section-1").asInstanceOf[String]}":{"name":"Section 1 - Updated Name","children":[],"root":false}}"""
			val request = new Request()
			val context = getContext(HierarchyConstants.QUESTIONSET_OBJECT_TYPE)
			context.put(HierarchyConstants.ROOT_ID, "do_pqs_123456")
			request.setContext(context)
			request.put(HierarchyConstants.NODES_MODIFIED, JsonUtils.deserialize(nodesModifiedString, classOf[util.HashMap[String, AnyRef]]))
			request.put(HierarchyConstants.HIERARCHY, JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]]))
			UpdateHierarchyManager.updateHierarchy(request).map(response => {
				assert(response.getResponseCode.code() == 200)
				val hierarchy = readFromCassandra("Select hierarchy from hierarchy_store.questionset_hierarchy where identifier='do_pqs_123456'")
				  .one().getString("hierarchy")
				assert(StringUtils.isNotEmpty(hierarchy))
			})
		}).flatMap(f => f)
	}

}
