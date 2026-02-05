package controllers.v4

import content.controllers.v4.EventController
import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, status, _}

@RunWith(classOf[JUnitRunner])
class EventSpec extends BaseSpec {

    "Event Controller " should {
        "return success response for create API" in {
            val controller = app.injector.instanceOf[EventController]
            val json: JsValue = Json.parse("""{"request": {"event": {"name": "Event","primaryCategory": "Event"}}}""")
            val fakeRequest = FakeRequest("POST", "/event/v4/create ").withJsonBody(json)
            val result = controller.create()(fakeRequest)
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for read API" in {
            val controller = app.injector.instanceOf[EventController]
            val result = controller.read("do_123", None, None)(FakeRequest("POST", "/event/v4/read "))
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for update API" in {
            val controller = app.injector.instanceOf[EventController]
            val result = controller.update("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return error response when updating status using update API" in {
            val controller = app.injector.instanceOf[EventController]
            val json: JsValue = Json.parse("""{"request": {"event": {"status": "Live"}}}""")
            val fakeRequest = FakeRequest("POST", "/event/v4/update ").withJsonBody(json)
            val result = controller.update("do_123")(fakeRequest)
            status(result) must equalTo(BAD_REQUEST)
        }

        "return success response for publish API" in {
            val controller = app.injector.instanceOf[EventController]
            val result = controller.publish("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

    }

}