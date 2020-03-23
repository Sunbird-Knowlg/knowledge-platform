package controllers.v3

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, status}
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class LicenseSpec  extends BaseSpec {

    "License Controller " should {

        val controller = app.injector.instanceOf[controllers.v3.LicenseController]

        "return success response for create API" in {
            val result = controller.create()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for update API" in {
            val result = controller.update("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for read API" in {
            val result = controller.read("do_123", None)(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for retire API" in {
            val result = controller.retire("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
    }
}
