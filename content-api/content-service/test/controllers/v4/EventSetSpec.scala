package controllers.v4

import content.controllers.v4.EventSetController
import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, status, _}

@RunWith(classOf[JUnitRunner])
class EventSetSpec extends BaseSpec {

    "EventSet Controller " should {
        "return success response for create API" in {
            val controller = app.injector.instanceOf[EventSetController]
            val json: JsValue = Json.parse("""{"request": {"eventset": {"name": "EventSet","primaryCategory": "Event Set"}}}""")
            val fakeRequest = FakeRequest("POST", "/eventset/v4/create ").withJsonBody(json)
            val result = controller.create()(fakeRequest)
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for update API" in {
            val controller = app.injector.instanceOf[EventSetController]
            val result = controller.update("do_123")(FakeRequest("POST", "/eventset/v4/update "))
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for read API" in {
            val controller = app.injector.instanceOf[EventSetController]
            val result = controller.read("do_123", None, None)(FakeRequest("POST", "/eventset/v4/read "))
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for hierarchy get API" in {
            val controller = app.injector.instanceOf[EventSetController]
            val result = controller.getHierarchy("do_123", None, None)(FakeRequest("POST", "/eventset/v4/hierarchy "))
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for discard API" in {
            val controller = app.injector.instanceOf[EventSetController]
            val result = controller.discard("0123")(FakeRequest("POST", "/eventset/v4/discard "))
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for retire API" in {
            val controller = app.injector.instanceOf[EventSetController]
            val result = controller.retire("0123")(FakeRequest("POST", "/eventset/v4/retire "))
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return error response when updating status using update API" in {
            val controller = app.injector.instanceOf[EventSetController]
            val json: JsValue = Json.parse("""{"request": {"eventset": {"status": "Live"}}}""")
            val fakeRequest = FakeRequest("POST", "/eventset/v4/update ").withJsonBody(json)
            val result = controller.update("do_123")(fakeRequest)
            status(result) must equalTo(BAD_REQUEST)
        }

        "return success response for publish API" in {
            val controller = app.injector.instanceOf[EventSetController]
            val result = controller.publish("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

    }


}