package controllers

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class EnrichSpec extends BaseSpec {

    "Enrich API" should {

        "return success for valid enrich request" in {
            val response = post("/v3/enrich", """{"request": {"identifiers": ["do_123"]}}""")
            isOK(response)
            status(response) must equalTo(OK)
        }

        "return success for multiple identifiers" in {
            val response = post("/v3/enrich", """{"request": {"identifiers": ["do_123", "do_456"]}}""")
            isOK(response)
            status(response) must equalTo(OK)
        }

        "return client error for empty identifiers list" in {
            val response = post("/v3/enrich", """{"request": {"identifiers": []}}""")
            status(response) must equalTo(BAD_REQUEST)
            contentAsString(response) must contain("CLIENT_ERROR")
        }

        "return client error when identifiers field is missing" in {
            val response = post("/v3/enrich", """{"request": {}}""")
            status(response) must equalTo(BAD_REQUEST)
            contentAsString(response) must contain("CLIENT_ERROR")
        }

        "return client error when request body is missing" in {
            val response = post("/v3/enrich", """{}""")
            status(response) must equalTo(BAD_REQUEST)
            contentAsString(response) must contain("CLIENT_ERROR")
        }

        "response contains api id api.content.enrich" in {
            val response = post("/v3/enrich", """{"request": {"identifiers": ["do_123"]}}""")
            contentAsString(response) must contain("api.content.enrich")
        }
    }
}
