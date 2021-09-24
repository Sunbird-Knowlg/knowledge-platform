package controllers

import org.junit.runner._
import org.specs2.runner._
import play.api.libs.json.{JsValue, Json}
import play.api.test._
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class SearchSpec extends BaseSpec {

    "SearchApp" should {

        "return success response for search API" in {
            val controller = app.injector.instanceOf[controllers.SearchController]
            val response = controller.search()(FakeRequest())
            isOK(response)
            status(response) must equalTo(OK)
        }

        "return client error response for search API" in {
            val controller = app.injector.instanceOf[controllers.SearchController]
            val json: JsValue = Json.parse("""{"request": {"filters": {"visibility": ["Private"]}}}""")
            val fakeRequest = FakeRequest("POST", "/v3/search").withJsonBody(json).withHeaders(FakeHeaders())
            val result = controller.search()(fakeRequest)
            status(result) must equalTo(BAD_REQUEST)
        }

        "search contents on private search request" in {
            val controller = app.injector.instanceOf[controllers.SearchController]
            val json: JsValue = Json.parse("""{"request": {"filters": {"objectType": ["Framework"]}}}""")
            val fakeRequest = FakeRequest("POST", "/v3/private/search").withJsonBody(json).withHeaders(FakeHeaders())
            val response = controller.privateSearch()(fakeRequest)
            isOK(response)
            status(response) must equalTo(OK)
        }

        "getcount of search filters" in {
            val controller = app.injector.instanceOf[controllers.SearchController]
            val response = controller.count()(FakeRequest())
            isOK(response)
            status(response) must equalTo(OK)
        }
    }
}
