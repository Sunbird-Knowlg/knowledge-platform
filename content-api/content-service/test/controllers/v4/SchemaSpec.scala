package controllers.v4

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, defaultAwaitTimeout, status}

class SchemaSpec extends BaseSpec {

  "Schema controller" should {
    "return success response for read API" in {
      val controller = app.injector.instanceOf[controllers.v4.SchemaController]
      val result = controller.read("do_1234", None)(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }
  }
}