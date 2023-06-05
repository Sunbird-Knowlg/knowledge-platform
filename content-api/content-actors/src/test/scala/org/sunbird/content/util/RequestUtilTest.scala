package org.sunbird.content.util

import java.util

import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.util.RequestUtil


class RequestUtilTest extends FlatSpec with Matchers with AsyncMockFactory {
    
    
    ignore should "throw clientException for invalid request" in {
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
       val exception = intercept[ClientException] {
            val context = new util.HashMap[String, AnyRef](){{
                put("graphId", "domain")
                put("version", "1.0")
                put("schemaName", "content")
                put("objectType", "Content")
            }}
            val request = new Request()
            request.setContext(context)
            request.setOperation("create")
            request.put("status", "Live")
            RequestUtil.restrictProperties(request)
        }
        exception.getErrCode shouldEqual  "ERROR_RESTRICTED_PROP"
    }

}
