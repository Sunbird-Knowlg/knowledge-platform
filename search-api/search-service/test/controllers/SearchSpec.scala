package controllers

import org.junit.runner._
import org.specs2.runner._
import play.api.test._
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class SearchSpec extends BaseSpec {

    "SearchApp" should {
        "search contents on search request" in {
            val controller = app.injector.instanceOf[controllers.SearchController]
            val response = controller.search()(FakeRequest())
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
