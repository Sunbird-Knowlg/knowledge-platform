package controllers.v3

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, status}
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class FrameworkSpec extends BaseSpec {

    "Framework Controller " should {

        "return success response for create framework API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkController]
            val result = controller.createFramework()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for update framework API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkController]
            val result = controller.updateFramework("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for retire framework API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkController]
            val result = controller.retire("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for read framework API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkController]
            val result = controller.readFramework("do_123", Option(""))(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for list framework API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkController]
            val result = controller.listFramework()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for copy framework API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkController]
            val result = controller.copyFramework("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for list framework API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkController]
            val result = controller.publish("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

    }
}
