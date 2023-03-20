package org.sunbird.graph.health

import org.sunbird.common.dto.Response
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.BaseSpec

import scala.concurrent.Future

class TestHealthCheckManager extends BaseSpec {

    "check health api" should "return true" in {
        val future: Future[Response] = HealthCheckManager.checkAllSystemHealth()
        future map { response => {
            assert(ResponseCode.OK == response.getResponseCode)
            assert(response.get("healthy") == true)
        }
        }
    }

    "check generate check with status false" should "return service unavailable map" in {
        val check: Map[String, Any] = HealthCheckManager.generateCheck(false, "redis cache")
        assert(Some(false) == check.get("healthy"))
        assert(Some("redis cache") == check.get("name"))
        assert(Some("503") == check.get("err"))

    }

    "check generate check with status true" should "return healthy service map" in {
        val check: Map[String, Any] = HealthCheckManager.generateCheck(true, "redis cache")
        assert(Some(true) == check.get("healthy"))
        assert(Some("redis cache") == check.get("name"))
    }
}
