package controllers.v3

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, status}
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class FrameworkCategoryControllerSpec extends BaseSpec {
    
    "Framework  Category Controller " should {
        "return success response for create API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkCategoryController]
            val result = controller.create("NCF")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for read API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkCategoryController]
            val result = controller.read("do_123","NCF")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for update API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkCategoryController]
            val result = controller.update("do_123","NCF")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for search API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkCategoryController]
            val result = controller.search("NCF")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for retire API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkCategoryController]
            val result = controller.retire("do_123","NCF")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
    }
}
