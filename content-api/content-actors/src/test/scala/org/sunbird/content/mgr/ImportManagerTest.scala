package org.sunbird.content.mgr

import java.util

import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.{BooleanUtils, StringUtils}
import org.scalamock.matchers.Matchers
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.AsyncFlatSpec
import org.sunbird.common.{HttpUtil, JsonUtils}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.kafka.client.KafkaClient


class ImportManagerTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {

	implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]

	"getRequest with list input" should "return request data as list with java types" in {
		val reqMap : java.util.Map[String, AnyRef] = new util.HashMap[String, AnyRef](){{
			put("content", new util.ArrayList[util.Map[String, AnyRef]](){{
				add(new util.HashMap[String, AnyRef](){{
					put("source","https://dock.sunbirded.org/api/content/v1/read/do_11307822356267827219477")
					put("metadata", new util.HashMap[String, AnyRef](){{
						put("name", "Test Content")
						put("description", "Test Content")
					}})
					put("collection", new util.ArrayList[util.Map[String, AnyRef]](){{
						add(new util.HashMap[String, AnyRef](){{
							put("identifier", "do_123")
							put("unitId", "do_3456")
						}})
					}})
				}})
				add(new util.HashMap[String, AnyRef](){{
					put("source","https://dock.sunbirded.org/api/content/v1/read/do_11307822356267827219477")
					put("metadata", new util.HashMap[String, AnyRef](){{
						put("name", "Test Content 2")
						put("description", "Test Content 2")
					}})
					put("collection", new util.ArrayList[util.Map[String, AnyRef]](){{
						add(new util.HashMap[String, AnyRef](){{
							put("identifier", "do_123")
							put("unitId", "do_4567")
						}})
					}})
				}})

			}})
		}}
		val request = new Request()
		request.putAll(reqMap)
		val result: util.List[util.Map[String, AnyRef]] = ImportManager.getRequest(request)
		assert(CollectionUtils.isNotEmpty(result))
		assert(result.isInstanceOf[util.List[AnyRef]])
		assert(result.size==2)
		assert(MapUtils.isNotEmpty(result.get(0)))
		assert(MapUtils.isNotEmpty(result.get(1)))
	}

	"getRequest with map input" should "return request data as list with java types" in {
		val reqMap : java.util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {{
			put("content", new util.HashMap[String, AnyRef](){{
				put("source","https://dock.sunbirded.org/api/content/v1/read/do_11307822356267827219477")
				put("metadata", new util.HashMap[String, AnyRef](){{
					put("name", "Test Content 2")
					put("description", "Test Content 2")
				}})
				put("collection", new util.ArrayList[util.Map[String, AnyRef]](){{
					add(new util.HashMap[String, AnyRef](){{
						put("identifier", "do_123")
						put("unitId", "do_3456")
					}})
				}})
			}})
		}}
		val request = new Request()
		request.putAll(reqMap)
		val result: util.List[util.Map[String, AnyRef]] = ImportManager.getRequest(request)
		assert(CollectionUtils.isNotEmpty(result))
		assert(result.isInstanceOf[util.List[AnyRef]])
		assert(result.size==1)
		assert(MapUtils.isNotEmpty(result.get(0)))
	}

	"getRequestData with invalid input" should "throw client exception" in {
		val exception = intercept[ClientException] {
			ImportManager.getRequest(new Request())
		}
		assert(exception.getMessage ==  "Invalid Request! Please Provide Valid Request.")
	}


	"getInstructionEvent with valid input" should "return kafka event string" in {
		val source = "https://dock.sunbirded.org/api/content/v1/read/do_11307822356267827219477"
		val metadata = new util.HashMap[String, AnyRef](){{
			put("source","https://dock.sunbirded.org/api/content/v1/read/do_11307822356267827219477")
			put("name", "Test Content 2")
			put("code", "test.content.1")
			put("mimeType","application/pdf")
			put("contentType","Resource")
			put("description", "Test Content 2")
			put("channel", "in.ekstep")
			put("versionKey", "12345")
		}}
		val collection = new util.ArrayList[util.Map[String, AnyRef]](){{
			add(new util.HashMap[String, AnyRef](){{
				put("identifier", "do_123")
				put("unitId", "do_3456")
			}})
		}}
		val result = ImportManager.getInstructionEvent("do_11307822356267827219477", source, metadata, collection, "")
		assert(StringUtils.isNoneBlank(result))
		val resultMap = JsonUtils.deserialize(result, classOf[util.Map[String, AnyRef]])
		assert(MapUtils.isNotEmpty(resultMap))
		val edata = resultMap.getOrDefault("edata", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
		assert(MapUtils.isNotEmpty(edata))
		assert(StringUtils.equalsIgnoreCase("auto-create", edata.get("action").asInstanceOf[String]))
	}

	"importContent with valid input" should "return the response having processId" in {
		val request = getRequest()
		request.putAll(new util.HashMap[String, AnyRef](){{
			put("content", new util.HashMap[String, AnyRef](){{
				put("stage", "Draft")
				put("source","https://dock.sunbirded.org/api/content/v1/read/do_11307822356267827219477")
				put("metadata", new util.HashMap[String, AnyRef](){{
					put("name", "Test Content 2")
					put("description", "Test Content 2")
				}})
				put("collection", new util.ArrayList[util.Map[String, AnyRef]](){{
					add(new util.HashMap[String, AnyRef](){{
						put("identifier", "do_123")
						put("unitId", "do_3456")
					}})
				}})
			}})
		}})
		implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
		val kfClient = mock[KafkaClient]
		val hUtil = mock[HttpUtil]
		(oec.httpUtil _).expects().returns(hUtil)
		val resp :Response = ResponseHandler.OK()
		resp.putAll(new util.HashMap[String, AnyRef](){{
			put("content", new util.HashMap[String, AnyRef](){{
				put("mimeType", "application/pdf")
				put("code", "test.res.1")
				put("framework", "NCF")
				put("contentType", "Resource")
				put("artifactUrl", "http://test.com/test.pdf")
			}})
		}})
		(hUtil.get(_: String, _: String, _: util.Map[String, String])).expects(*, *, *).returns(resp)
		(oec.kafkaClient _).expects().returns(kfClient)
		(kfClient.send(_: String, _: String)).expects(*, *).returns(None)
		val resFuture = ImportManager.importContent(request)
		resFuture.map(result => {
			assert(null != result)
			assert(result.getResponseCode.toString=="OK")
			assert(null != result.getResult.get("processId"))
		})
	}

	"validateStage with invalid input" should "return false" in {
		val result = ImportManager.validateStage("Flagged")
		assert(BooleanUtils.isFalse(result))
	}

	"validateStage with valid input" should "return true" in {
		val result = ImportManager.validateStage("Review")
		assert(BooleanUtils.isTrue(result))
	}

	private def getRequest(): Request = {
		val request = new Request()
		request.setContext(new util.HashMap[String, AnyRef]() {
			{
				put("graph_id", "domain")
				put("version", "1.0")
				put("objectType", "Content")
				put("schemaName", "content")
				put("X-Channel-Id", "in.ekstep")
			}
		})
		request.setObjectType("Content")
		request
	}

}
