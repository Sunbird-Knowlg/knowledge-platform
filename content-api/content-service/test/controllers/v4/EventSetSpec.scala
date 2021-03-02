package controllers.v4

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
            val controller = app.injector.instanceOf[controllers.v4.EventSetController]
            val json: JsValue = Json.parse("""{"request": {"eventset": {"name": "EventSet","primaryCategory": "Event Set"}}}""")
            val fakeRequest = FakeRequest("POST", "/eventset/v4/create ").withJsonBody(json)
            val result = controller.create()(fakeRequest)
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for update API" in {
            val controller = app.injector.instanceOf[controllers.v4.EventSetController]
            val result = controller.update("do_123")(FakeRequest("POST", "/eventset/v4/update "))
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for read API" in {
            val controller = app.injector.instanceOf[controllers.v4.CollectionController]
            val result = controller.read("do_123", None, None)(FakeRequest("POST", "/eventset/v4/read "))
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for hierarchy get API" in {
            val controller = app.injector.instanceOf[controllers.v4.CollectionController]
            val result = controller.getHierarchy("do_123", None)(FakeRequest("POST", "/eventset/v4/hierarchy "))
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for discard API" in {
            val controller = app.injector.instanceOf[controllers.v4.CollectionController]
            val result = controller.discard("0123")(FakeRequest("POST", "/eventset/v4/discard "))
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for retire API" in {
            val controller = app.injector.instanceOf[controllers.v4.CollectionController]
            val result = controller.retire("0123")(FakeRequest("POST", "/eventset/v4/retire "))
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for collectionLinkDialCode API" in {
            val controller = app.injector.instanceOf[controllers.v4.CollectionController]
            val result = controller.collectionLinkDialCode("do_123")(FakeRequest("POST", "/eventset/v4/dialcode/link "))
            isOK(result)
            status(result) must equalTo(OK)
        }
    }

    "return success response for hierarchy update API" in {
        val controller = app.injector.instanceOf[controllers.v4.CollectionController]
        val json: JsValue = Json.parse("""{"request": {"data": {"mimeType": "application/vnd.ekstep.content-collection"}}}""")
        val fakeRequest = FakeRequest("POST", "/collection/v4/hierarchy/update").withJsonBody(json)
        val result = controller.updateHierarchy()(fakeRequest)
        isOK(result)
        status(result) must equalTo(OK)
    }

    "Collection Controller with invalid request " should {
        "return client error response for create API" in {
            val controller = app.injector.instanceOf[controllers.v4.CollectionController]
            val json: JsValue = Json.parse("""{"request": {"collection": { "contentType": "TextBook"}}}""")
            val fakeRequest = FakeRequest("POST", "/collection/v4/create ").withJsonBody(json)
            val result = controller.create()(fakeRequest)
            status(result) must equalTo(BAD_REQUEST)
        }
    }

    "Collection Controller with invalid request " should {
        "return client error response for create API" in {
            val controller = app.injector.instanceOf[controllers.v4.CollectionController]
            val json: JsValue = Json.parse("""{"request": {"collection": { "name": "Textbook"}}}""")
            val fakeRequest = FakeRequest("POST", "/collection/v4/create ").withJsonBody(json)
            val result = controller.create()(fakeRequest)
            status(result) must equalTo(BAD_REQUEST)
        }
    }
}