package org.sunbird.content.dial

import java.util

import org.scalamock.matchers.Matchers
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.AsyncFlatSpec
import org.sunbird.common.{HttpUtil, JsonUtils}
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ResponseCode, ServerException}
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.sunbird.graph.{GraphService, OntologyEngineContext}

import scala.concurrent.Future

class DIALManagerTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {

	implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
	val graphDB = mock[GraphService]
	val httpUtil = mock[HttpUtil]

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
		assert(result(1).get("identifier").get.isInstanceOf[List[String]])
		assert(result(1).get("dialcode").get.isInstanceOf[List[String]])
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
		assert(result(0).nonEmpty)
		assert(result(0).get("identifier").get.isInstanceOf[List[String]])
		assert(result(0).get("dialcode").get.isInstanceOf[List[String]])
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
		assert(result.size==0)
	}

	"validateAndGetRequestMap with valid input" should "return the request map" in {
		(oec.httpUtil _).expects().returns(httpUtil)
		(httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects(*, *, *).returns(getDIALSearchResponse)
		val input = getRequestData()
		val result = DIALManager.validateAndGetRequestMap("test", input)
		assert(result.nonEmpty)
		assert(result.size==5)
		assert(result.get("do_88888").get.contains("L4A6W8"))
		assert(result.get("do_88888").get.contains("D2E1J9"))
		assert(result.get("do_2222").get.size==1)
		assert(result.get("do_2222").get.contains("R4X2P2"))
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
		(oec.graphService _).expects().returns(graphDB).repeated(3)
		(httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects(*, *, *).returns(getDIALSearchResponse)
		(graphDB.getNodeByUniqueIds(_: String, _: SearchCriteria)).expects(*, *).returns(Future(getNodes()))
		(graphDB.getNodeByUniqueId(_: String, _: String, _: Boolean, _: Request)).expects(*, *, *, *).returns(Future(getNode("do_1111")))
		(graphDB.upsertNode(_: String, _: Node, _: Request)).expects(*, *, *).returns(Future(getNode("do_1111")))
		val request = getContentDIALRequest()
		val resFuture = DIALManager.link(request)
		resFuture.map(result => {
			assert(result.getResponseCode.toString=="OK")
		})
	}

	"link DIAL with valid request for collections" should "update the collection content successfully" in {
		(oec.httpUtil _).expects().returns(httpUtil)
		(httpUtil.post(_: String, _:java.util.Map[String, AnyRef], _:java.util.Map[String, String])).expects(*, *, *).returns(getDIALSearchResponse)
		val request = getCollectionDIALRequest()
		val response = DIALManager.link(request)
		response.map(result => {
			assert(result.getResponseCode.toString=="OK")
		})
	}

	def getDIALSearchResponse():Response = {
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
						add("do_4444")
						add("do_5555")
					}})
					put("dialcode", new util.ArrayList[String](){{
						add("L4A6W8")
						add("D2E1J9")
					}})
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
		}}
		result
	}

	def getNode(identifier: String): Node = {
		val node = new Node()
		node.setIdentifier(identifier)
		node.setNodeType("DATA_NODE")
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
			}
		})
		node
	}

}