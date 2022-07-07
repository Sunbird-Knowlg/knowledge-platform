package org.sunbird.content.dial

import org.scalamock.matchers.Matchers
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.AsyncFlatSpec
import org.sunbird.common.dto.{Property, Request, Response}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ResponseCode, ServerException}
import org.sunbird.common.{HttpUtil, JsonUtils}
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.graph.{GraphService, OntologyEngineContext}

import java.util
import scala.concurrent.Future

class DIALManagerTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {

	implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
	val graphDB: GraphService = mock[GraphService]
	val httpUtil: HttpUtil = mock[HttpUtil]

	"getRequestData with list input" should "return request data as list with scala types" in {
		val reqMap : java.util.Map[String, AnyRef] = new util.HashMap[String, AnyRef](){{
			put("content", new util.ArrayList[util.Map[String, AnyRef]](){{
				add(new util.HashMap[String, AnyRef](){{
					put("identifier","do_1111")
					put("dialcode", new util.ArrayList[String](){{
						add("ABC111")
						add("ABC222")
					}})
				}})
				add(new util.HashMap[String, AnyRef](){{
					put("identifier",new util.ArrayList[String](){{
						add("do_2222")
						add("do_3333")
					}})
					put("dialcode", "ABC333")
				}})
				add(new util.HashMap[String, AnyRef](){{
					put("identifier",new util.ArrayList[String](){{
						add("do_88888")
						add("do_99999")
					}})
					put("dialcode", new util.ArrayList[String]())
				}})
			}})
		}}
		val request = new Request()
		request.putAll(reqMap)
		val result: List[Map[String, List[String]]] = DIALManager.getRequestData(request)
		assert(null!=result && result.nonEmpty)
		assert(result.isInstanceOf[List[AnyRef]])
		assert(result.size==3)
		assert(result(1).nonEmpty)
		assert(result(1)("identifier").isInstanceOf[List[String]])
		assert(result(1)("dialcode").isInstanceOf[List[String]])
	}

	"getRequestData with map input" should "return request data as list with scala types" in {
		val reqMap : java.util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {{
			put("content", new util.HashMap[String, AnyRef](){{
				put("identifier", "do_123")
				put("dialcode", new util.ArrayList[String](){{
					add("ABC123")
					add("BCD123")
				}})
			}})
		}}
		val request = new Request()
		request.putAll(reqMap)
		val result: List[Map[String, List[String]]] = DIALManager.getRequestData(request)
		assert(null!=result && result.nonEmpty)
		assert(result.isInstanceOf[List[AnyRef]])
		assert(result.size==1)
		assert(result.head.nonEmpty)
		assert(result.head("identifier").isInstanceOf[List[String]])
		assert(result.head("dialcode").isInstanceOf[List[String]])
	}

	"getRequestData with invalid input" should "throw client exception" in {
		val exception = intercept[ClientException] {
			DIALManager.getRequestData(new Request())
		}
		assert(exception.getMessage ==  "Invalid Request! Please Provide Valid Request.")
	}

	"getList with java list input" should "return scala list" in {
		val input = new util.ArrayList[String](){{
			add("ABC123")
			add("")
			add(" ")
			add("BCD123")
		}}
		val result:List[String] = DIALManager.getList(input)
		assert(result.nonEmpty)
		assert(result.size==2)
	}

	"getList with String input" should "return scala List" in {
		val input = "do_123"
		val result:List[String] = DIALManager.getList(input)
		assert(result.nonEmpty)
		assert(result.size==1)
	}

	"getList with empty java list" should "return empty scala List" in {
		val input = new util.ArrayList[String]()
		val result:List[String] = DIALManager.getList(input)
		assert(result.isEmpty)
	}

	"validateAndGetRequestMap with valid input" should "return the request map" in {
		(oec.httpUtil _).expects().returns(httpUtil)
		(httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects(*, *, *).returns(getDIALSearchResponse)
		val input = getRequestData()
		val result = DIALManager.validateAndGetRequestMap("test", input)
		assert(result.nonEmpty)
		assert(result.size==5)
		assert(result("do_88888").contains("L4A6W8"))
		assert(result("do_88888").contains("D2E1J9"))
		assert(result("do_2222").size==1)
		assert(result("do_2222").contains("R4X2P2"))
	}

	"validateReqStructure with valid request" should "not throw any exception" in {
		DIALManager.validateReqStructure(List("ABC123"), List("do_123"))
		assert(true)
	}

	"validateReqStructure with empty contents" should "throw client exception" in {
		val exception = intercept[ClientException] {
			DIALManager.validateReqStructure(List("ABC123"), List())
		}
		assert(exception.getMessage ==  "Invalid Request! Please Provide Required Properties In Request.")
	}

	"validateReqStructure with more than 10 contents" should "throw client exception" in {
		val exception = intercept[ClientException] {
			DIALManager.validateReqStructure(List("ABC123"), List("do_111","do_222","do_3333","do_444","do_555","do_1111","do_2222","do_3333","do_4444","do_5555"))
		}
		assert(exception.getMessage ==  "Max Limit For Link Content To DIAL Code In A Request Is 10")
	}

	"validateDialCodes with valid channel and valid dialcodes" should "return true" in {
		(oec.httpUtil _).expects().returns(httpUtil)
		(httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects(*, *, *).returns(getDIALSearchResponse)
		val result = DIALManager.validateDialCodes("test", List("L4A6W8","BCD123","ABC123","PQR123","JKL123"))
		assert(result)
	}

	"validateDialCodes with invalid channel and valid dialcodes" should "throw ResourceNotFoundException" in {
		(oec.httpUtil _).expects().returns(httpUtil)
		val resp = new Response
		resp.put("count",0)
		resp.put("dialcodes", util.Arrays.asList())
		(httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects(*, *, *).returns(resp)
		val exception = intercept[ResourceNotFoundException] {
			DIALManager.validateDialCodes("test", List("L4A6W8","BCD123","ABC123","PQR123","JKL123"))
		}
		assert(exception.getMessage ==  "DIAL Code Not Found With Id(s): [L4A6W8, BCD123, ABC123, PQR123, JKL123]")
	}

	"validateDialCodes with invalid search response" should "throw ServerException" in {
		(oec.httpUtil _).expects().returns(httpUtil)
		val resp = new Response
		resp.setResponseCode(ResponseCode.SERVER_ERROR)
		(httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects(*, *, *).returns(resp)
		val exception = intercept[ServerException] {
			DIALManager.validateDialCodes("test", List("L4A6W8","BCD123","ABC123","PQR123","JKL123"))
		}
		assert(exception.getMessage ==  "Something Went Wrong While Processing Your Request. Please Try Again After Sometime!")
	}

	"link DIAL with valid request for content" should "update the contents successfully" in {
		(oec.httpUtil _).expects().returns(httpUtil)
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		(httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects(*, *, *).returns(getDIALSearchResponse)

		(graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes())).noMoreThanOnce()
		val nodes: util.List[Node] = getCategoryNode()
		(graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).noMoreThanOnce()

		(graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(new Response()))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getNode("do_1111")))
		(graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getNode("do_1111")))
		val request = getContentDIALRequest()

		DIALManager.link(request).map(result => {
			assert(result.getResponseCode.toString=="OK")
		})
	}

	"link DIAL with valid request for collections" should "update the collection successfully" in {
		(oec.httpUtil _).expects().returns(httpUtil)
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		(httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects(*, *, *).returns(getDIALSearchResponse)

		val nodes: util.List[Node] = getCategoryNode()
		(graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

		(graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getNode("do_1111"))).anyNumberOfTimes()
		(graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getNode("do_1111")))
		(graphDB.saveExternalProps(_: Request)).expects(*).returns(Future(new Response()))
		(graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234"))))

		val request = getCollectionDIALRequest()

		val response = DIALManager.link(request)
		response.map(result => {
			assert(result.getResponseCode.toString=="OK")
		})
	}

	"validateDuplicateDIALCodes with duplicate dial codes" should "throw client exception" in {
		val exception = intercept[ClientException] {
			DIALManager.validateDuplicateDIALCodes(Map("do_2222" -> List("E8B7Z6", "R4X2P2"), "do_1111" -> List("N4Z7D5", "E8B7Z6", "L4A6W8", "D2E1J9", "R4X2P2")))
		}
		assert(exception.getErrCode ==  "ERR_DUPLICATE_DIAL_CODES")
	}

	"link DIAL with valid node invalid unit Id for collections" should "update the collection partially" in {
		(oec.httpUtil _).expects().returns(httpUtil)
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		(httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects(*, *, *).returns(getDIALSearchResponse)

		val nodes: util.List[Node] = getCategoryNode()
		(graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

		(graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getNode("do_1111"))).anyNumberOfTimes()
		(graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getNode("do_1111")))
		(graphDB.saveExternalProps(_: Request)).expects(*).returns(Future(new Response()))
		(graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234"))))

		val request = getCollectionPartialSuccessRequest()

		val response = DIALManager.link(request)
		response.map(result => {
			assert(result.getResponseCode.toString=="PARTIAL_SUCCESS")
		})
	}

	"link DIAL with invalid unit Id request for collection" should "respond with Resource not found message" in {
		(oec.httpUtil _).expects().returns(httpUtil)
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		(httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects(*, *, *).returns(getDIALSearchResponse)

		val nodes: util.List[Node] = getCategoryNode()
		(graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

		(graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(getCassandraHierarchy())).anyNumberOfTimes()
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getNode("do_1111"))).anyNumberOfTimes()
		(graphDB.saveExternalProps(_: Request)).expects(*).returns(Future(new Response()))

		val request = getCollectionRNFRequest()

		val response = DIALManager.link(request)
		response.map(result => {
			assert(result.getResponseCode.toString=="RESOURCE_NOT_FOUND")
		})
	}

	"reserve DIAL" should "update content with reservedDialcodes" in {
		(oec.httpUtil _).expects().returns(httpUtil)
		(oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
		(httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects(*, *, *).returns(getGenerateDIALResponse)

		val nodes: util.List[Node] = getCategoryNode()
		(graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(nodes)).anyNumberOfTimes()

		val contentId: String = "do_123456"
//		(graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(new Response())).anyNumberOfTimes()
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getNode(contentId))).anyNumberOfTimes()
//		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getNode(contentId+".img"))).anyNumberOfTimes()
		(graphDB.getNodeProperty(_: String, _: String, _: String)).expects(*, *, *).returns(Future(new Property("versionKey", new org.neo4j.driver.internal.value.StringValue("1234"))))
		(graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getNode(contentId)))

		val request = getReserveDIALRequest(contentId)

		val response = DIALManager.reserve(request)
		response.map(result => {
			assert(result.getResponseCode.toString=="OK")
		})
	}


	def getDIALSearchResponse:Response = {
		val resString = "{\n  \"id\": \"sunbird.dialcode.search\",\n  \"ver\": \"3.0\",\n  \"ts\": \"2020-04-21T19:39:14ZZ\",\n  \"params\": {\n    \"resmsgid\": \"1dfcc25b-6c37-49f8-a6c3-7185063e8752\",\n    \"msgid\": null,\n    \"err\": null,\n    \"status\": \"successful\",\n    \"errmsg\": null\n  },\n  \"responseCode\": \"OK\",\n  \"result\": {\n    \"dialcodes\": [\n      {\n        \"dialcode_index\": 7609876,\n        \"identifier\": \"N4Z7D5\",\n        \"channel\": \"testr01\",\n        \"batchcode\": \"testPub0001.20200421T193801\",\n        \"publisher\": \"testPub0001\",\n        \"generated_on\": \"2020-04-21T19:38:01.603+0000\",\n        \"status\": \"Draft\",\n        \"objectType\": \"DialCode\"\n      },\n      {\n        \"dialcode_index\": 7610113,\n        \"identifier\": \"E8B7Z6\",\n        \"channel\": \"testr01\",\n        \"batchcode\": \"testPub0001.20200421T193801\",\n        \"publisher\": \"testPub0001\",\n        \"generated_on\": \"2020-04-21T19:38:01.635+0000\",\n        \"status\": \"Draft\",\n        \"objectType\": \"DialCode\"\n      },\n      {\n        \"dialcode_index\": 7610117,\n        \"identifier\": \"R4X2P2\",\n        \"channel\": \"testr01\",\n        \"batchcode\": \"testPub0001.20200421T193801\",\n        \"publisher\": \"testPub0001\",\n        \"generated_on\": \"2020-04-21T19:38:01.637+0000\",\n        \"status\": \"Draft\",\n        \"objectType\": \"DialCode\"\n      },\n      {\n        \"dialcode_index\": 7610961,\n        \"identifier\": \"L4A6W8\",\n        \"channel\": \"testr01\",\n        \"batchcode\": \"testPub0001.20200421T193801\",\n        \"publisher\": \"testPub0001\",\n        \"generated_on\": \"2020-04-21T19:38:01.734+0000\",\n        \"status\": \"Draft\",\n        \"objectType\": \"DialCode\"\n      },\n      {\n        \"dialcode_index\": 7611164,\n        \"identifier\": \"D2E1J9\",\n        \"channel\": \"testr01\",\n        \"batchcode\": \"testPub0001.20200421T193801\",\n        \"publisher\": \"testPub0001\",\n        \"generated_on\": \"2020-04-21T19:38:01.759+0000\",\n        \"status\": \"Draft\",\n        \"objectType\": \"DialCode\"\n      }\n    ],\n    \"count\": 5\n  }\n}";
		JsonUtils.deserialize(resString, classOf[Response])
	}

	def getRequestData(): List[Map[String, List[String]]] = {
		val reqMap : java.util.Map[String, AnyRef] = new util.HashMap[String, AnyRef](){{
			put("content", new util.ArrayList[util.Map[String, AnyRef]](){{
				add(new util.HashMap[String, AnyRef](){{
					put("identifier","do_1111")
					put("dialcode", new util.ArrayList[String](){{
						add("N4Z7D5")
						add("E8B7Z6")
					}})
				}})
				add(new util.HashMap[String, AnyRef](){{
					put("identifier",new util.ArrayList[String](){{
						add("do_2222")
						add("do_3333")
					}})
					put("dialcode", "R4X2P2")
				}})
				add(new util.HashMap[String, AnyRef](){{
					put("identifier",new util.ArrayList[String](){{
						add("do_88888")
						add("do_99999")
					}})
					put("dialcode", new util.ArrayList[String](){{
						add("L4A6W8")
						add("D2E1J9")
					}})
				}})
			}})
		}}
		val request = new Request()
		request.putAll(reqMap)
		DIALManager.getRequestData(request)
	}

	def getContentDIALRequest(): Request = {
		val request = new Request()
		request.setObjectType("Content")
		request.setContext(getContext())
		request.getContext.put("linkType","content")
		val reqMap : java.util.Map[String, AnyRef] = new util.HashMap[String, AnyRef](){{
			put("content", new util.ArrayList[util.Map[String, AnyRef]](){{
				add(new util.HashMap[String, AnyRef](){{
					put("identifier","do_1111")
					put("dialcode", new util.ArrayList[String](){{
						add("N4Z7D5")
						add("E8B7Z6")
						add("R4X2P2")
						add("L4A6W8")
						add("D2E1J9")
					}})
				}})
			}})
		}}
		request.putAll(reqMap)
		request
	}

	def getCollectionDIALRequest(): Request = {
		val request = new Request()
		request.setObjectType("Content")
		request.setContext(getContext())
		request.getContext.put("linkType","collection")
		request.getContext.put("identifier","do_1111")
		request.putAll(getRequest())
		request
	}

	def getContext():util.Map[String, AnyRef] = {
		val contextMap: java.util.Map[String, AnyRef] = new util.HashMap[String, AnyRef](){{
			put("graph_id", "domain")
			put("version" , "1.0")
			put("objectType" , "Content")
			put("schemaName", "content")
			put("channel", "test")
		}}
		contextMap
	}

	def getRequest():util.Map[String, AnyRef] = {
		val reqMap : java.util.Map[String, AnyRef] = new util.HashMap[String, AnyRef](){{
			put("content", new util.ArrayList[util.Map[String, AnyRef]](){{
				add(new util.HashMap[String, AnyRef](){{
					put("identifier","do_1111")
					put("dialcode", new util.ArrayList[String](){{
						add("N4Z7D5")
						add("E8B7Z6")
						add("L4A6W8")
						add("D2E1J9")
					}})
				}})
				add(new util.HashMap[String, AnyRef](){{
					put("identifier",new util.ArrayList[String](){{
						add("do_2222")
					}})
					put("dialcode", "R4X2P2")
				}})
			}})
		}}
		reqMap
	}

	def getNodes(): util.List[Node] = {
		val result = new util.ArrayList[Node](){{
			add(getNode("do_1111"))
			add(getNode("do_2222"))
			add(getNode("do_3333"))
			add(getNode("do_4444"))
			add(getNode("do_5555"))
			add(getNode("do_123456"))
		}}
		result
	}

	def getNode(identifier: String): Node = {
		val node = new Node()
		node.setIdentifier(identifier)
		node.setNodeType("DATA_NODE")
		node.setObjectType("Content")
		node.setMetadata(new util.HashMap[String, AnyRef]() {
			{
				put("identifier", identifier)
				put("name", "Test Content")
				put("code", "test.resource")
				put("contentType", "Resource")
				put("mimeType", "application/pdf")
				put("status", "Draft")
				put("channel", "test")
				put("versionKey", "1234")
				put("primaryCategory", "Learning Resource")
			}
		})
		node
	}

	def getCollectionPartialSuccessRequest(): Request = {
		val request = new Request()
		request.setObjectType("Content")
		request.setContext(getContext())
		request.getContext.put("linkType","collection")
		request.getContext.put("identifier","do_1111")

		val reqMap : java.util.Map[String, AnyRef] = new util.HashMap[String, AnyRef](){{
			put("content", new util.ArrayList[util.Map[String, AnyRef]](){{
				add(new util.HashMap[String, AnyRef](){{
					put("identifier","do_1111")
					put("dialcode", new util.ArrayList[String](){{
						add("N4Z7D5")
						add("E8B7Z6")
						add("L4A6W8")
						add("D2E1J9")
					}})
				}})
				add(new util.HashMap[String, AnyRef](){{
					put("identifier",new util.ArrayList[String](){{
						add("do_22223")
					}})
					put("dialcode", "R4X2P2")
				}})

			}})
		}}

		request.putAll(reqMap)
		request
	}

	def getCollectionRNFRequest(): Request = {
		val request = new Request()
		request.setObjectType("Content")
		request.setContext(getContext())
		request.getContext.put("linkType","collection")
		request.getContext.put("identifier","do_1111")

		val reqMap : java.util.Map[String, AnyRef] = new util.HashMap[String, AnyRef](){{
			put("content", new util.ArrayList[util.Map[String, AnyRef]](){{
				add(new util.HashMap[String, AnyRef](){{
					put("identifier",new util.ArrayList[String](){{
						add("do_22223")
					}})
					put("dialcode", new util.ArrayList[String](){{
						add("N4Z7D5")
						add("E8B7Z6")
						add("L4A6W8")
						add("D2E1J9")
						add("R4X2P2")
					}})
				}})

			}})
		}}

		request.putAll(reqMap)
		request
	}

	private def getCategoryDefinitionNode(identifier: String): Node = {
		val node = new Node()
		node.setIdentifier(identifier)
		node.setNodeType("DATA_NODE")
		node.setMetadata(new util.HashMap[String, AnyRef]() {
			{
				put("identifier", identifier)
				put("categoryId", "obj-cat:1234")
				put("objectType", "ObjectCategoryDefinition")
				put("name", "Test Category Definition")
				put("targetObjectType", "Content")
				put("objectMetadata", "{\"config\":{},\"schema\":{\"trackable\":{\"type\":\"object\",\"properties\":{\"enabled\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"},\"autoBatch\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"}},\"additionalProperties\":false}}}")
			}
		})
		node
	}

	private def getCategoryNode(): util.List[Node] = {
		val node = new Node()
		node.setIdentifier("board")
		node.setNodeType("DATA_NODE")
		node.setObjectType("Category")
		node.setMetadata(new util.HashMap[String, AnyRef]() {
			{
				put("code", "board")
				put("orgIdFieldName", "boardIds")
				put("targetIdFieldName", "targetBoardIds")
				put("searchIdFieldName", "se_boardIds")
				put("searchLabelFieldName", "se_boards")
				put("status", "Live")
			}
		})
		util.Arrays.asList(node)
	}

	def getCassandraHierarchy(): Response = {
		val hierarchyString: String =
			"""{"ownershipType": ["createdBy"],"subject": ["Mathematics"],"channel": "0126825293972439041","organisation": ["Sunbird"],
				|"language": ["English"],"mimeType": "application/vnd.ekstep.content-collection","objectType": "Content","gradeLevel": ["Class 4"],
				|"primaryCategory": "Digital Textbook","children": [{"ownershipType": ["createdBy"],"parent": "do_1111","code": "do_1132828084877148161531",
				|"keywords": [],"credentials": {"enabled": "No"},"channel": "0126825293972439041","description": "This chapter describes about human body",
				|"language": ["English"],"mimeType": "application/vnd.ekstep.content-collection","idealScreenSize": "normal","createdOn": "2021-05-20T08:58:33.470+0000",
				|"objectType": "Content","primaryCategory": "Textbook Unit","children": [{"ownershipType": ["createdBy"],"parent": "do_2222",
				|"code": "do_1132828084876574721523","keywords": [],"credentials": {"enabled": "No"},"channel": "0126825293972439041",
				|"description": "This section describes about various part of the body such as head, hands, legs etc.","language": ["English"],
				|"mimeType": "application/vnd.ekstep.content-collection","idealScreenSize": "normal","createdOn": "2021-05-20T08:58:33.466+0000",
				|"objectType": "Content","primaryCategory": "Textbook Unit","children": [{"ownershipType": ["createdBy"],"parent": "do_1132833371215134721712",
				|"code": "do_1132828084876738561525","keywords": ["legs etc."],"credentials": {"enabled": "No"},"channel": "0126825293972439041","description": "xyz",
				|"language": ["English"],"mimeType": "application/vnd.ekstep.content-collection","idealScreenSize": "normal","createdOn": "2021-05-20T08:58:33.475+0000",
				|"objectType": "Content","primaryCategory": "Textbook Unit","children": [{"ownershipType": ["createdBy"],"parent": "do_1132833371215872001720",
				|"previewUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_1132339274094346241120/test-874-kb.mp4",
				|"channel": "b00bc992ef25f1a9a8d63291e20efc8d",
				|"downloadUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1132339274094346241120/untitled-content_1616331971279_do_1132339274094346241120_14.0.ecar",
				|"organisation": ["Sunbird"],"language": ["English"],"mimeType": "video/mp4","variants": {"spine":
				|{"ecarUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1132339274094346241120/untitled-content_1616331975047_do_1132339274094346241120_14.0_spine.ecar","size": 1381.0}},"objectType": "Content","primaryCategory": "Learning Resource","appId": "local.sunbird.portal","contentEncoding": "identity","artifactUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_1132339274094346241120/test-874-kb.mp4",
				|"lockKey": "34a029c4-ac81-4934-9792-11b7a57d6c13","sYS_INTERNAL_LAST_UPDATED_ON": "2021-03-21T13:20:24.579+0000","contentType": "Resource",
				|"trackable": {"enabled": "No","autoBatch": "No"},"identifier": "do_1132339274094346241120","audience": ["Student"],"visibility": "Default",
				|"consumerId": "273f3b18-5dda-4a27-984a-060c7cd398d3","index": 1,"mediaType": "content","osId": "org.ekstep.quiz.app","languageCode": ["en"],
				|"lastPublishedBy": "8454cb21-3ce9-4e30-85b5-fade097880d8","version": 2,"license": "CC BY 4.0","prevState": "Live","size": 849897.0,
				|"lastPublishedOn": "2021-03-21T13:06:11.272+0000","name": "Untitled Content","status": "Live","code": "a88b0257-670b-455b-98b8-6e359ebac009","credentials": {"enabled": "No"},"prevStatus": "Processing","description": "updated","streamingUrl": "https://sunbirddevmedia-inct.streaming.media.azure.net/9c0ebb33-af08-403f-afb4-eb24749f40a1/test-874-kb.ism/manifest(format=m3u8-aapl-v3)","idealScreenSize": "normal","createdOn": "2021-03-11T13:34:14.475+0000","contentDisposition": "inline","lastUpdatedOn": "2021-03-21T13:06:09.526+0000","dialcodeRequired": "No","lastStatusChangedOn": "2021-03-21T18:36:15.799+0530","createdFor": ["ORG_001"],"creator": "Reviewer User","os": ["All"],"se_FWIds": ["NCFCOPY"],"pkgVersion": 14.0,"versionKey": "1616331969523","idealScreenDensity": "hdpi","framework": "NCFCOPY","depth": 4,"s3Key": "ecar_files/do_1132339274094346241120/untitled-content_1616331971279_do_1132339274094346241120_14.0.ecar","createdBy": "95e4942d-cbe8-477d-aebd-ad8e6de4bfc8","compatibilityLevel": 1,"resourceType": "Learn"},{"ownershipType": ["createdBy"],"parent": "do_1132833371215872001720","unitIdentifiers": ["do_1132239562839900161634"],"copyright": "2021 MIT","previewUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_113223967141863424174-latest","plugins": [{"identifier": "org.sunbird.questionunit.quml","semanticVersion": "1.1"}],"subject": ["Hindi"],"channel": "01309282781705830427","downloadUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_113223967141863424174/esa_1614253812772_do_113223967141863424174_1.0.ecar","language": ["English"],"source": "https://dock.sunbirded.org/api/content/v1/read/do_1132239617341767681638","mimeType": "application/vnd.ekstep.ecml-archive","variants": {"spine": {"ecarUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_113223967141863424174/esa_1614253813394_do_113223967141863424174_1.0_spine.ecar","size": 24293.0}},"objectType": "Content","se_mediums": ["English"],"gradeLevel": ["Class 10"],"primaryCategory": "Exam Question","appId": "dev.dock.portal","contentEncoding": "gzip","artifactUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_113223967141863424174/artifact/1614253223147_do_1132239617341767681638.zip","sYS_INTERNAL_LAST_UPDATED_ON": "2021-02-25T11:50:16.032+0000","contentType": "Resource","se_gradeLevels": ["Class 10"],"trackable": {"enabled": "No","autoBatch": "No"},"identifier": "do_113223967141863424174","audience": ["Student"],"visibility": "Default","author": "anusha","maxQuestions": 1,"consumerId": "b7054510-3ca4-49fd-b373-b100b3f65e18","index": 2,"mediaType": "content","osId": "org.ekstep.quiz.app","languageCode": ["en"],"lastPublishedBy": "5a587cc1-e018-4859-a0a8-e842650b9d64","version": 2,"se_subjects": ["Hindi"],"license": "CC BY 4.0","prevState": "Review","size": 384798.0,"lastPublishedOn": "2021-02-25T11:50:12.771+0000","name": "esa","topic": ["तोप"],"status": "Live","code": "d19f43ce-753d-2c70-a9fd-70302af424a0","credentials": {"enabled": "No"},"prevStatus": "Processing","origin": "do_1132239617341767681638","streamingUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_113223967141863424174-latest","medium": ["English"],"idealScreenSize": "normal","createdOn": "2021-02-25T11:50:01.500+0000","se_boards": ["CBSE"],"processId": "2b00cca7-42d5-4e35-aa03-1c22dfe03de8","contentDisposition": "inline","lastUpdatedOn": "2021-02-25T11:50:12.678+0000","originData": {"identifier": "do_1132239617341767681638","repository": "https://dock.sunbirded.org/api/content/v1/read/do_1132239617341767681638"},"collectionId": "do_1132239562836049921627","dialcodeRequired": "No","editorVersion": 3,"lastStatusChangedOn": "2021-02-25T11:50:16.017+0000","creator": "anusha","os": ["All"],"questionCategories": ["SA"],"cloudStorageKey": "content/do_113223967141863424174/artifact/1614253223147_do_1132239617341767681638.zip","se_FWIds": ["ekstep_ncert_k-12"],"marks": "12","bloomsLevel": ["Apply"],"pkgVersion": 1.0,"versionKey": "1614253812678","idealScreenDensity": "hdpi","framework": "ekstep_ncert_k-12","depth": 4,"s3Key": "ecar_files/do_113223967141863424174/esa_1614253812772_do_113223967141863424174_1.0.ecar","lastSubmittedOn": "2021-02-25T11:50:11.539+0000","createdBy": "19ba0e4e-9285-4335-8dd0-f674bf03fa4d","se_topics": ["तोप"],"compatibilityLevel": 1,"itemSetPreviewUrl": "https://dockstorage.blob.core.windows.net/sunbird-content-dock/content/do_1132239617341767681638/artifact/do_1132239617341767681638_1614253222002.pdf","board": "CBSE","programId": "463cfa30-775c-11eb-8c56-93946e419809"}],"contentDisposition": "inline","lastUpdatedOn": "2021-05-20T08:58:33.475+0000","contentEncoding": "gzip","contentType": "TextBookUnit","dialcodeRequired": "Yes","identifier": "do_1132833371215872001720","lastStatusChangedOn": "2021-05-20T08:58:33.475+0000","audience": ["Student"],"os": ["All"],"visibility": "Parent","discussionForum": {"enabled": "Yes"},"index": 1,"mediaType": "content","osId": "org.ekstep.launcher","languageCode": ["en"],"version": 2,"versionKey": "1621501113475","license": "CC BY 4.0","idealScreenDensity": "hdpi","framework": "tn_k-12","depth": 3,"compatibilityLevel": 1,"name": "5.1.1 Key parts in the head","topic": [],"status": "Draft"},{"ownershipType": ["createdBy"],"parent": "do_1132833371215134721712","code": "do_1132828084876165121519","keywords": [],"credentials": {"enabled": "No"},"channel": "0126825293972439041","description": "","language": ["English"],"mimeType": "application/vnd.ekstep.content-collection","idealScreenSize": "normal","createdOn": "2021-05-20T08:58:33.473+0000","objectType": "Content","primaryCategory": "Textbook Unit","contentDisposition": "inline","lastUpdatedOn": "2021-05-20T08:58:33.473+0000","contentEncoding": "gzip","contentType": "TextBookUnit","dialcodeRequired": "No","identifier": "do_1132833371215708161718","lastStatusChangedOn": "2021-05-20T08:58:33.473+0000","audience": ["Student"],"os": ["All"],"visibility": "Parent","discussionForum": {"enabled": "Yes"},"index": 2,"mediaType": "content","osId": "org.ekstep.launcher","languageCode": ["en"],"version": 2,"versionKey": "1621501113473","license": "CC BY 4.0","idealScreenDensity": "hdpi","framework": "tn_k-12","depth": 3,"compatibilityLevel": 1,"name": "5.1.2 Other parts","topic": [],"status": "Draft"},{"ownershipType": ["createdBy"],"parent": "do_1132833371215134721712","unitIdentifiers": ["do_11323721176414617611924"],"copyright": "2021 MIT","organisationId": "e7328d77-42a7-44c8-84f4-8cfea235f07d","previewUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_1132372524622561281279-latest","plugins": [{"identifier": "org.sunbird.questionunit.quml","semanticVersion": "1.1"}],"subject": ["Mathematics"],"channel": "01309282781705830427","downloadUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1132372524622561281279/untitled_1615875562931_do_1132372524622561281279_1.0.ecar","language": ["English"],"source": "https://dock.sunbirded.org/api/content/v1/read/do_11323724954450329611930","mimeType": "application/vnd.ekstep.ecml-archive","variants": {"spine": {"ecarUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1132372524622561281279/untitled_1615875563539_do_1132372524622561281279_1.0_spine.ecar","size": 19563.0}},"objectType": "Content","se_mediums": ["English"],"gradeLevel": ["Class 5"],"primaryCategory": "Exam Question","appId": "dev.dock.portal","contentEncoding": "gzip","artifactUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1132372524622561281279/artifact/1615875430184_do_11323724954450329611930.zip","sYS_INTERNAL_LAST_UPDATED_ON": "2021-03-16T06:19:26.162+0000","contentType": "Resource","se_gradeLevels": ["Class 5"],"trackable": {"enabled": "No","autoBatch": "No"},"identifier": "do_1132372524622561281279","audience": ["Student"],"visibility": "Default","author": "N18","maxQuestions": 1,"consumerId": "f73cfcc5-4d43-4fa0-8b81-46166c81bc2b","learningOutcome": ["identify the need to find area and perimeter of rectangle and square."],"index": 3,"mediaType": "content","osId": "org.ekstep.quiz.app","languageCode": ["en"],"lastPublishedBy": "5a587cc1-e018-4859-a0a8-e842650b9d64","version": 2,"se_subjects": ["Mathematics"],"license": "CC BY 4.0","prevState": "Review","size": 374996.0,"lastPublishedOn": "2021-03-16T06:19:22.931+0000","name": "Untitled","topic": ["Speed, Distance and Time"],"status": "Live","code": "2544c8b8-7946-b6c0-e1c7-ced4aee4ea8c","credentials": {"enabled": "No"},"prevStatus": "Processing","origin": "do_11323724954450329611930","streamingUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_1132372524622561281279-latest","medium": ["English"],"idealScreenSize": "normal","createdOn": "2021-03-16T06:19:04.712+0000","se_boards": ["CBSE"],"processId": "9995e013-a7c9-4da1-b2c9-2f59da33414a","contentDisposition": "inline","lastUpdatedOn": "2021-03-16T06:19:20.817+0000","originData": {"identifier": "do_11323724954450329611930","repository": "https://dock.sunbirded.org/api/content/v1/read/do_11323724954450329611930"},"collectionId": "do_11323721176353996811921","dialcodeRequired": "No","editorVersion": 3,"lastStatusChangedOn": "2021-03-16T06:19:26.147+0000","creator": "N18","os": ["All"],"questionCategories": ["MTF"],"cloudStorageKey": "content/do_1132372524622561281279/artifact/1615875430184_do_11323724954450329611930.zip","se_FWIds": ["ekstep_ncert_k-12"],"marks": "2","bloomsLevel": ["Apply"],"pkgVersion": 1.0,"versionKey": "1615875560817","idealScreenDensity": "hdpi","framework": "ekstep_ncert_k-12","depth": 3,"s3Key": "ecar_files/do_1132372524622561281279/untitled_1615875562931_do_1132372524622561281279_1.0.ecar","lastSubmittedOn": "2021-03-16T06:19:17.005+0000","createdBy": "60f91e9e-34ee-4f9f-a907-d312d0e8063e","se_topics": ["Speed, Distance and Time"],"compatibilityLevel": 1,"itemSetPreviewUrl": "https://dockstorage.blob.core.windows.net/sunbird-content-dock/content/do_11323724954450329611930/artifact/do_11323724954450329611930_1615875429226.pdf","board": "CBSE","programId": "800eb440-8613-11eb-a663-4f63bbe94184"}],"contentDisposition": "inline","lastUpdatedOn": "2021-05-20T08:58:33.466+0000","contentEncoding": "gzip","contentType": "TextBookUnit","dialcodeRequired": "Yes","identifier": "do_1132833371215134721712","lastStatusChangedOn": "2021-05-20T08:58:33.466+0000","audience": ["Student"],"os": ["All"],"visibility": "Parent","discussionForum": {"enabled": "Yes"},"index": 1,"mediaType": "content","osId": "org.ekstep.launcher","languageCode": ["en"],"version": 2,"versionKey": "1621501113466","license": "CC BY 4.0","idealScreenDensity": "hdpi","framework": "tn_k-12","depth": 2,"compatibilityLevel": 1,"name": "5.1 Parts of Body","topic": ["Role Of The Sense Organs"],"status": "Draft"},{"ownershipType": ["createdBy"],"parent": "do_2222","code": "do_1132828084877066241529","keywords": [],"credentials": {"enabled": "No"},"channel": "0126825293972439041","description": "","language": ["English"],"mimeType": "application/vnd.ekstep.content-collection","idealScreenSize": "normal","createdOn": "2021-05-20T08:58:33.476+0000","objectType": "Content","primaryCategory": "Textbook Unit","children": [{"ownershipType": ["createdBy"],"parent": "do_1132833371215953921722","code": "do_1132828084876492801521","keywords": ["test key","check"],"credentials": {"enabled": "No"},"channel": "0126825293972439041","description": "","language": ["English"],"mimeType": "application/vnd.ekstep.content-collection","idealScreenSize": "normal","createdOn": "2021-05-20T08:58:33.468+0000","objectType": "Content","primaryCategory": "Textbook Unit","children": [{"ownershipType": ["createdBy"],"parent": "do_1132833371215298561714","code": "do_1132828084876820481527","keywords": ["abcd","cgf"],"credentials": {"enabled": "No"},"channel": "0126825293972439041","description": "labeled new","language": ["English"],"mimeType": "application/vnd.ekstep.content-collection","idealScreenSize": "normal","createdOn": "2021-05-20T08:58:33.464+0000","objectType": "Content","primaryCategory": "Textbook Unit","children": [{"ownershipType": ["createdBy"],"parent": "do_1132833371214970881710","previewUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_1132338069147811841118/test-874-kb.mp4","channel": "b00bc992ef25f1a9a8d63291e20efc8d","downloadUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1132338069147811841118/untitled-content_1615468830522_do_1132338069147811841118_2.0.ecar","organisation": ["Sunbird"],"language": ["English"],"mimeType": "video/mp4","variants": {"spine": {"ecarUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1132338069147811841118/untitled-content_1615468834470_do_1132338069147811841118_2.0_spine.ecar","size": 1361.0}},"objectType": "Content","primaryCategory": "Learning Resource","appId": "dev.sunbird.portal","contentEncoding": "identity","artifactUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_1132338069147811841118/test-874-kb.mp4","lockKey": "d73707c8-9999-4fc9-9b34-0207f74faf43","sYS_INTERNAL_LAST_UPDATED_ON": "2021-03-12T08:10:31.335+0000","contentType": "Resource","trackable": {"enabled": "No","autoBatch": "No"},"identifier": "do_1132338069147811841118","audience": ["Student"],"visibility": "Default","consumerId": "273f3b18-5dda-4a27-984a-060c7cd398d3","index": 1,"mediaType": "content","osId": "org.ekstep.quiz.app","languageCode": ["en"],"lastPublishedBy": "8454cb21-3ce9-4e30-85b5-fade097880d8","version": 2,"license": "CC BY 4.0","prevState": "Live","size": 849876.0,"lastPublishedOn": "2021-03-11T13:20:30.514+0000","name": "Untitled Content","status": "Live","code": "9deb2c69-7240-472a-98e7-ed438e76262b","credentials": {"enabled": "No"},"prevStatus": "Processing","streamingUrl": "https://sunbirddevmedia-inct.streaming.media.azure.net/f17bccc5-cab3-4da8-a5eb-11d7211f1507/test-874-kb.ism/manifest(format=m3u8-aapl-v3)","idealScreenSize": "normal","createdOn": "2021-03-11T09:29:05.654+0000","contentDisposition": "inline","lastUpdatedOn": "2021-03-11T13:20:28.256+0000","dialcodeRequired": "No","lastStatusChangedOn": "2021-03-11T18:50:28.256+0530","createdFor": ["ORG_001"],"creator": "Reviewer User","os": ["All"],"se_FWIds": ["NCFCOPY"],"pkgVersion": 2.0,"versionKey": "1615455090358","idealScreenDensity": "hdpi","framework": "NCFCOPY","depth": 5,"s3Key": "ecar_files/do_1132338069147811841118/untitled-content_1615468830522_do_1132338069147811841118_2.0.ecar","createdBy": "95e4942d-cbe8-477d-aebd-ad8e6de4bfc8","compatibilityLevel": 1,"resourceType": "Learn"},{"ownershipType": ["createdBy"],"parent": "do_1132833371214970881710","unitIdentifiers": ["do_1132238266042040321422"],"copyright": "2021 MIT","previewUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_11322383952751820816-latest","plugins": [{"identifier": "org.sunbird.questionunit.quml","semanticVersion": "1.1"}],"subject": ["Hindi"],"channel": "01309282781705830427","downloadUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11322383952751820816/sapractice_1614238238045_do_11322383952751820816_1.0.ecar","language": ["English"],"source": "https://dock.sunbirded.org/api/content/v1/read/do_1132238287156183041424","mimeType": "application/vnd.ekstep.ecml-archive","variants": {"spine": {"ecarUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11322383952751820816/sapractice_1614238238800_do_11322383952751820816_1.0_spine.ecar","size": 13171.0}},"objectType": "Content","se_mediums": ["English"],"gradeLevel": ["Class 10"],"primaryCategory": "Exam Question","appId": "dev.dock.portal","contentEncoding": "gzip","artifactUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11322383952751820816/artifact/1614237122171_do_1132238287156183041424.zip","sYS_INTERNAL_LAST_UPDATED_ON": "2021-02-25T07:30:44.916+0000","contentType": "Resource","se_gradeLevels": ["Class 10"],"trackable": {"enabled": "No","autoBatch": "No"},"identifier": "do_11322383952751820816","audience": ["Student"],"visibility": "Default","author": "anusha","maxQuestions": 1,"consumerId": "273f3b18-5dda-4a27-984a-060c7cd398d3","index": 2,"mediaType": "content","osId": "org.ekstep.quiz.app","languageCode": ["en"],"lastPublishedBy": "5a587cc1-e018-4859-a0a8-e842650b9d64","version": 2,"se_subjects": ["Hindi"],"license": "CC BY 4.0","prevState": "Review","size": 362236.0,"lastPublishedOn": "2021-02-25T07:30:38.043+0000","name": "sa:practice","status": "Live","code": "f239c77e-ed71-9133-0145-7468a92bce79","credentials": {"enabled": "No"},"prevStatus": "Processing","origin": "do_1132238287156183041424","streamingUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_11322383952751820816-latest","medium": ["English"],"idealScreenSize": "normal","createdOn": "2021-02-25T07:30:23.577+0000","se_boards": ["CBSE"],"processId": "04d5aec9-ed09-4a57-963d-9fa654fecf8d","contentDisposition": "inline","lastUpdatedOn": "2021-02-25T07:30:37.956+0000","originData": {"identifier": "do_1132238287156183041424","repository": "https://dock.sunbirded.org/api/content/v1/read/do_1132238287156183041424"},"collectionId": "do_1132238266036551681415","dialcodeRequired": "No","editorVersion": 3,"lastStatusChangedOn": "2021-02-25T07:30:44.908+0000","creator": "anusha","os": ["All"],"questionCategories": ["SA"],"cloudStorageKey": "content/do_11322383952751820816/artifact/1614237122171_do_1132238287156183041424.zip","se_FWIds": ["ekstep_ncert_k-12"],"marks": "12","pkgVersion": 1.0,"versionKey": "1614238237956","idealScreenDensity": "hdpi","framework": "ekstep_ncert_k-12","depth": 5,"s3Key": "ecar_files/do_11322383952751820816/sapractice_1614238238045_do_11322383952751820816_1.0.ecar","lastSubmittedOn": "2021-02-25T07:30:36.709+0000","createdBy": "19ba0e4e-9285-4335-8dd0-f674bf03fa4d","compatibilityLevel": 1,"itemSetPreviewUrl": "https://dockstorage.blob.core.windows.net/sunbird-content-dock/content/do_1132238287156183041424/artifact/do_1132238287156183041424_1614237121022.pdf","board": "CBSE","programId": "94564340-7737-11eb-96e0-29a9f8ed81cf"},{"ownershipType": ["createdBy"],"parent": "do_1132833371214970881710","unitIdentifiers": ["do_11322165488232038412588"],"copyright": "2021 MIT","previewUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_1132216902566133761410-latest","plugins": [{"identifier": "org.sunbird.questionunit.quml","semanticVersion": "1.1"}],"subject": ["Environmental Studies"],"channel": "01309282781705830427","downloadUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1132216902566133761410/mcqmcq_1613975872529_do_1132216902566133761410_1.0.ecar","language": ["English"],"source": "https://dock.sunbirded.org/api/content/v1/read/do_11322168163282944012605","mimeType": "application/vnd.ekstep.ecml-archive","variants": {"spine": {"ecarUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1132216902566133761410/mcqmcq_1613975873161_do_1132216902566133761410_1.0_spine.ecar","size": 17182.0}},"objectType": "Content","se_mediums": ["English"],"gradeLevel": ["Class 10"],"primaryCategory": "Exam Question","appId": "dev.dock.portal","contentEncoding": "gzip","artifactUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1132216902566133761410/artifact/1613975740738_do_11322168163282944012605.zip","sYS_INTERNAL_LAST_UPDATED_ON": "2021-02-22T06:37:55.328+0000","contentType": "Resource","se_gradeLevels": ["Class 10"],"trackable": {"enabled": "No","autoBatch": "No"},"identifier": "do_1132216902566133761410","audience": ["Student"],"visibility": "Default","author": "color4","maxQuestions": 1,"consumerId": "7411b6bd-89f3-40ec-98d1-229dc64ce77d","learningOutcome": ["Understand the importance of values in life"],"index": 3,"mediaType": "content","osId": "org.ekstep.quiz.app","languageCode": ["en"],"lastPublishedBy": "ae94b68c-a535-4dce-8e7a-fb9662b0ad68","version": 2,"se_subjects": ["Environmental Studies"],"license": "CC BY 4.0","prevState": "Review","size": 370363.0,"lastPublishedOn": "2021-02-22T06:37:52.529+0000","name": "MCQMCQ","topic": ["Animals"],"status": "Live","code": "0cbae0f8-e3eb-1d31-e2e5-0337dc7d697d","credentials": {"enabled": "No"},"prevStatus": "Processing","origin": "do_11322168163282944012605","streamingUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/ecml/do_1132216902566133761410-latest","medium": ["English"],"idealScreenSize": "normal","createdOn": "2021-02-22T06:37:41.405+0000","se_boards": ["CBSE"],"processId": "fbcec2af-cb7a-4ed1-8683-ff04b475947e","contentDisposition": "inline","lastUpdatedOn": "2021-02-22T06:37:52.447+0000","originData": {"identifier": "do_11322168163282944012605","repository": "https://dock.sunbirded.org/api/content/v1/read/do_11322168163282944012605"},"collectionId": "do_11322165488181248012584","dialcodeRequired": "No","editorVersion": 3,"lastStatusChangedOn": "2021-02-22T06:37:55.314+0000","creator": "color4","os": ["All"],"questionCategories": ["MCQ"],"cloudStorageKey": "content/do_1132216902566133761410/artifact/1613975740738_do_11322168163282944012605.zip","se_FWIds": ["ekstep_ncert_k-12"],"marks": "1","bloomsLevel": ["Understand"],"pkgVersion": 1.0,"versionKey": "1613975872447","idealScreenDensity": "hdpi","framework": "ekstep_ncert_k-12","depth": 5,"s3Key": "ecar_files/do_1132216902566133761410/mcqmcq_1613975872529_do_1132216902566133761410_1.0.ecar","lastSubmittedOn": "2021-02-22T06:37:51.179+0000","createdBy": "0ce5b67e-b48e-489b-a818-e938e8bfc14b","se_topics": ["Animals"],"compatibilityLevel": 1,"itemSetPreviewUrl": "https://dockstorage.blob.core.windows.net/sunbird-content-dock/content/do_11322168163282944012605/artifact/do_11322168163282944012605_1613975739805.pdf","board": "CBSE","programId": "b2433a00-74cd-11eb-9f3c-f39a9ab9f5ce"}],"contentDisposition": "inline","lastUpdatedOn": "2021-05-20T08:58:33.464+0000","contentEncoding": "gzip","contentType": "TextBookUnit","dialcodeRequired": "Yes","identifier": "do_1132833371214970881710","lastStatusChangedOn": "2021-05-20T08:58:33.464+0000","audience": ["Student"],"os": ["All"],"visibility": "Parent","discussionForum": {"enabled": "Yes"},"index": 1,"mediaType": "content","osId": "org.ekstep.launcher","languageCode": ["en"],"version": 2,"versionKey": "1621501113464","license": "CC BY 4.0","idealScreenDensity": "hdpi","framework": "tn_k-12","depth": 4,"compatibilityLevel": 1,"name": "dsffgdg","topic": [],"status": "Draft"}],"contentDisposition": "inline","lastUpdatedOn": "2021-05-20T08:58:33.468+0000","contentEncoding": "gzip","contentType": "TextBookUnit","dialcodeRequired": "No","identifier": "do_1132833371215298561714","lastStatusChangedOn": "2021-05-20T08:58:33.468+0000","audience": ["Student"],"os": ["All"],"visibility": "Parent","discussionForum": {"enabled": "Yes"},"index": 1,"mediaType": "content","osId": "org.ekstep.launcher","languageCode": ["en"],"version": 2,"versionKey": "1621501113468","license": "CC BY 4.0","idealScreenDensity": "hdpi","framework": "tn_k-12","depth": 3,"compatibilityLevel": 1,"name": "5.2.1 Respiratory System","topic": ["Look and say","Role Of The Sense Organs"],"status": "Draft"}],"contentDisposition": "inline","lastUpdatedOn": "2021-05-20T08:58:33.476+0000","contentEncoding": "gzip","contentType": "TextBookUnit","dialcodeRequired": "No","identifier": "do_1132833371215953921722","lastStatusChangedOn": "2021-05-20T08:58:33.476+0000","audience": ["Student"],"os": ["All"],"visibility": "Parent","discussionForum": {"enabled": "Yes"},"index": 2,"mediaType": "content","osId": "org.ekstep.launcher","languageCode": ["en"],"version": 2,"versionKey": "1621501113476","license": "CC BY 4.0","idealScreenDensity": "hdpi","framework": "tn_k-12","depth": 2,"compatibilityLevel": 1,"name": "5.2 Organ Systems","topic": [],"status": "Draft"},{"ownershipType": ["createdBy"],"parent": "do_2222","copyright": "Sunbird","previewUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_1132344630588948481134/test-874-kb.mp4","subject": ["Math"],"channel": "b00bc992ef25f1a9a8d63291e20efc8d","downloadUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1132344630588948481134/untitled-content_1615535618825_do_1132344630588948481134_2.0.ecar","organisation": ["Sunbird"],"showNotification": true,"language": ["English"],"mimeType": "video/mp4","variants": {"spine": {"ecarUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_1132344630588948481134/untitled-content_1615535619590_do_1132344630588948481134_2.0_spine.ecar","size": 35301.0}},"objectType": "Content","se_mediums": ["English"],"gradeLevel": ["Grade 1"],"appIcon": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_1132344630588948481134/artifact/2a4b8abd789184932399d222d03d9b5c.thumb.jpg","primaryCategory": "Learning Resource","appId": "dev.sunbird.portal","contentEncoding": "identity","artifactUrl": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_1132344630588948481134/test-874-kb.mp4","lockKey": "1d28d983-2704-44bd-803e-5feb4e62da62","sYS_INTERNAL_LAST_UPDATED_ON": "2021-03-12T08:10:34.367+0000","contentType": "Resource","se_gradeLevels": ["Grade 1"],"trackable": {"enabled": "No","autoBatch": "No"},"identifier": "do_1132344630588948481134","lastUpdatedBy": "95e4942d-cbe8-477d-aebd-ad8e6de4bfc8","audience": ["Student"],"visibility": "Default","consumerId": "273f3b18-5dda-4a27-984a-060c7cd398d3","index": 3,"mediaType": "content","osId": "org.ekstep.quiz.app","languageCode": ["en"],"lastPublishedBy": "8454cb21-3ce9-4e30-85b5-fade097880d8","version": 2,"se_subjects": ["Math"],"license": "CC BY 4.0","prevState": "Review","size": 883817.0,"lastPublishedOn": "2021-03-12T07:53:38.825+0000","name": "Untitled Content","status": "Live","code": "8851e754-6e20-44d4-9070-e1a9664163ad","credentials": {"enabled": "No"},"prevStatus": "Review","description": "updated desrciption","streamingUrl": "https://sunbirddevmedia-inct.streaming.media.azure.net/40ae07aa-069e-4056-8f2b-014bc9a2d21b/test-874-kb.ism/manifest(format=m3u8-aapl-v3)","medium": ["English"],"posterImage": "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11299104587967692816/artifact/2a4b8abd789184932399d222d03d9b5c.jpg","idealScreenSize": "normal","createdOn": "2021-03-12T07:44:01.371+0000","se_boards": ["NCERT"],"copyrightYear": 2020,"contentDisposition": "inline","licenseterms": "By creating any type of content (resources, books, courses etc.) on DIKSHA, you consent to publish it under the Creative Commons License Framework. Please choose the applicable creative commons license you wish to apply to your content.","lastUpdatedOn": "2021-03-12T07:53:38.505+0000","dialcodeRequired": "No","lastStatusChangedOn": "2021-03-12T07:53:38.494+0000","createdFor": ["ORG_001"],"creator": "Reviewer User","os": ["All"],"se_FWIds": ["NCFCOPY"],"pkgVersion": 2.0,"versionKey": "1615535618583","idealScreenDensity": "hdpi","framework": "NCFCOPY","depth": 2,"s3Key": "ecar_files/do_1132344630588948481134/untitled-content_1615535618825_do_1132344630588948481134_2.0.ecar","lastSubmittedOn": "2021-03-12T07:53:10.005+0000","createdBy": "95e4942d-cbe8-477d-aebd-ad8e6de4bfc8","compatibilityLevel": 1,"board": "NCERT","resourceType": "Learn"}],"contentDisposition": "inline","lastUpdatedOn": "2021-05-20T08:58:33.470+0000","contentEncoding": "gzip","contentType": "TextBookUnit","dialcodeRequired": "No","identifier": "do_2222","lastStatusChangedOn": "2021-05-20T08:58:33.470+0000","audience": ["Student"],"os": ["All"],"visibility": "Parent","discussionForum": {"enabled": "Yes"},"index": 1,"mediaType": "content","osId": "org.ekstep.launcher","languageCode": ["en"],"version": 2,"versionKey": "1621501113470","license": "CC BY 4.0","idealScreenDensity": "hdpi","framework": "tn_k-12","depth": 1,"compatibilityLevel": 1,"name": "5. Human Body","topic": [],"status": "Draft"}],"appId": "dev.sunbird.portal","contentEncoding": "gzip","sYS_INTERNAL_LAST_UPDATED_ON": "2021-05-20T09:12:06.988+0000","contentType": "TextBook","trackable": {"enabled": "No","autoBatch": "No"},"identifier": "do_1111","audience": ["Student"],"visibility": "Default","consumerId": "01814e02-fc27-4165-ae53-3d1816e55817","childNodes": ["do_1132339274094346241120","do_1132833371215872001720","do_1132833371215134721712","do_2222","do_113223967141863424174","do_1132833371214970881710","do_1132833371215708161718","do_1132372524622561281279","do_1132338069147811841118","do_1132833371215298561714","do_1132833371215953921722","do_11322383952751820816","do_1132216902566133761410","do_1132344630588948481134"],"discussionForum": {"enabled": "Yes"},"mediaType": "content","osId": "org.ekstep.quiz.app","languageCode": ["en"],"version": 2,"license": "CC BY 4.0","name": "TestCSVUpload","status": "Draft","code": "org.sunbird.yhqB6L","credentials": {"enabled": "No"},"description": "Enter description for TextBook","medium": ["English"],"idealScreenSize": "normal","createdOn": "2021-05-19T15:00:44.279+0000","contentDisposition": "inline","additionalCategories": ["Textbook"],"lastUpdatedOn": "2021-05-20T07:10:32.805+0000","dialcodeRequired": "No","lastStatusChangedOn": "2021-05-19T15:00:44.279+0000","createdFor": ["0126825293972439041"],"creator": "Book Creator","os": ["All"],"versionKey": "1621501113536","idealScreenDensity": "hdpi","framework": "tn_k-12","depth": 0,"createdBy": "8454cb21-3ce9-4e30-85b5-fade097880d8","compatibilityLevel": 1,"userConsent": "Yes","board": "State (Tamil Nadu)","resourceType": "Book"}""".stripMargin
		val response = new Response
		response.put("hierarchy", hierarchyString)
	}

	def getReserveDIALRequest(identifier: String): Request = {
		val request = new Request()
		request.setObjectType("Content")
		request.setContext(getContext())
		request.getContext.put("identifier",identifier)
		request.put("identifier",identifier)
		request.putAll(getReserveRequest())
		request
	}

	def getReserveRequest():util.Map[String, AnyRef] = {
		val reqMap : java.util.Map[String, AnyRef] = new util.HashMap[String, AnyRef](){
			put("dialcodes", new util.HashMap[String, AnyRef](){
				put("count", 2.asInstanceOf[Integer])
				put("qrCodeSpec", new util.HashMap[String, AnyRef](){
					put("errorCorrectionLevel", "H")
				})
			})
		}
		reqMap
	}

	def getGenerateDIALResponse:Response = {
		val resString = "{\"id\": \"api.dialcode.generate\",\"ver\": \"1.0\",\"ts\": \"2022-07-05T09:47:26.000Z\",\"params\": {\"resmsgid\": \"79eb8b00-fc47-11ec-af25-0f53946b16ec\",\"msgid\": \"79be1260-fc47-11ec-8c03-63ca5ce41074\",\"status\": \"successful\",\"err\": null,\"errmsg\": null},\"responseCode\": \"OK\",\"result\": {\"dialcodes\": [\"K2C3R6\",\"H2E8F9\"],\"count\": 2,\"batchcode\": \"do_11357423520695910411\",\"publisher\": null}}"
		JsonUtils.deserialize(resString, classOf[Response])
	}

	def getInvalidChannelReserveDIALRequest(identifier: String): Request = {
		val request = new Request()
		request.setObjectType("Content")
		request.setContext(getInavlidChannelContext())
		request.getContext.put("identifier",identifier)
		request.put("identifier",identifier)
		request.putAll(getReserveRequest())
		request
	}

	def getInavlidChannelContext():util.Map[String, AnyRef] = {
		val contextMap: java.util.Map[String, AnyRef] = new util.HashMap[String, AnyRef](){{
			put("graph_id", "domain")
			put("version" , "1.0")
			put("objectType" , "Content")
			put("schemaName", "content")
			put("channel", "invalidChannel")
		}}
		contextMap
	}

}