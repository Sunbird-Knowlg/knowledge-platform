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
        "return success response for read API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.read("do_123", None, None)(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
    }
}
