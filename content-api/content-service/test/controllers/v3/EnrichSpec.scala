package controllers.v3

import content.controllers.v3.ContentController
import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class EnrichSpec extends BaseSpec {

    "Content Enrich Controller" should {

        "return success response for valid enrich request" in {
            val controller = app.injector.instanceOf[ContentController]
            val json: JsValue = Json.parse("""{"request": {"identifiers": ["do_123"]}}""")
            val fakeRequest = FakeRequest("POST", "/content/v3/enrich").withJsonBody(json)
            val result = controller.triggerEnrich()(fakeRequest)
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for multiple identifiers" in {
            val controller = app.injector.instanceOf[ContentController]
            val json: JsValue = Json.parse("""{"request": {"identifiers": ["do_123", "do_456", "do_789"]}}""")
            val fakeRequest = FakeRequest("POST", "/content/v3/enrich").withJsonBody(json)
            val result = controller.triggerEnrich()(fakeRequest)
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return client error for empty identifiers list" in {
            val controller = app.injector.instanceOf[ContentController]
            val json: JsValue = Json.parse("""{"request": {"identifiers": []}}""")
            val fakeRequest = FakeRequest("POST", "/content/v3/enrich").withJsonBody(json)
            val result = controller.triggerEnrich()(fakeRequest)
            hasClientError(result)
            status(result) must equalTo(BAD_REQUEST)
        }

        "return client error when identifiers field is missing" in {
            val controller = app.injector.instanceOf[ContentController]
            val json: JsValue = Json.parse("""{"request": {}}""")
            val fakeRequest = FakeRequest("POST", "/content/v3/enrich").withJsonBody(json)
            val result = controller.triggerEnrich()(fakeRequest)
            hasClientError(result)
            status(result) must equalTo(BAD_REQUEST)
        }

        "return client error when request body is missing" in {
            val controller = app.injector.instanceOf[ContentController]
            val fakeRequest = FakeRequest("POST", "/content/v3/enrich")
            val result = controller.triggerEnrich()(fakeRequest)
            hasClientError(result)
            status(result) must equalTo(BAD_REQUEST)
        }

        "response contains api id api.content.enrich" in {
            val controller = app.injector.instanceOf[ContentController]
            val json: JsValue = Json.parse("""{"request": {"identifiers": ["do_123"]}}""")
            val fakeRequest = FakeRequest("POST", "/content/v3/enrich").withJsonBody(json)
            val result = controller.triggerEnrich()(fakeRequest)
            contentAsString(result) must contain("api.content.enrich")
        }
    }
}
