package controllers.v4

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, status, _}


@RunWith(classOf[JUnitRunner])
class ObjectCategorySpec extends BaseSpec {

    "Category Controller " should {

        val controller = app.injector.instanceOf[ObjectCategoryController]

        "return success response for create API" in {
            val result = controller.create()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for update API" in {
            val result = controller.update("obj-cat:test")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for read API" in {
            val result = controller.read("obj-cat:test", None)(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

    }
}

