package controllers

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class EnrichSpec extends BaseSpec {

    "Enrich API" should {

        "return success for valid enrich request" in {
            val response = post("/v3/enrich", """{"request": {"identifiers": ["do_123"]}}""")
            isOK(response)
        }

        "return success for multiple identifiers" in {
            val response = post("/v3/enrich", """{"request": {"identifiers": ["do_123", "do_456"]}}""")
            isOK(response)
        }

        "return client error for empty identifiers list" in {
            val response = post("/v3/enrich", """{"request": {"identifiers": []}}""")
            hasClientError(response)
        }

        "return client error when identifiers field is missing" in {
            val response = post("/v3/enrich", """{"request": {}}""")
            hasClientError(response)
        }

        "return client error when request body is missing" in {
            val response = post("/v3/enrich", """{}""")
            hasClientError(response)
        }

        "response contains api id api.content.enrich" in {
            val response = post("/v3/enrich", """{"request": {"identifiers": ["do_123"]}}""")
            import play.api.test.Helpers.contentAsString
            contentAsString(response) must contain("api.content.enrich")
        }
    }
}
