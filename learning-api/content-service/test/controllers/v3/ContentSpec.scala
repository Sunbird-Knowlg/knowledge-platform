package controllers.v3

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, status}
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class ContentSpec  extends BaseSpec {

    "Content Controller " should {
        "return success response for create API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.create()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for update API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.update("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for read API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.read("do_123", None, None)(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for hierarchy add API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.addHierarchy()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for hierarchy remove API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.removeHierarchy()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
    }
}
