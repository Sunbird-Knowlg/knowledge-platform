package org.sunbird.content.`import`

import java.util

import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.scalamock.matchers.Matchers
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.AsyncFlatSpec
import org.sunbird.common.HttpUtil
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.kafka.client.KafkaClient

class ImportManagerTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {

  //implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
  //val graphDB = mock[GraphService]
  //val httpUtil = mock[HttpUtil]
  //val kfClient = mock[KafkaClient]

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

}
