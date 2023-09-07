package controllers.v3

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers._
@RunWith(classOf[JUnitRunner])
class CategorySpec extends BaseSpec {
  "CategoryController" should {

    val controller = app.injector.instanceOf[CategoryController]

    "return success response for create api" in {
      val result = controller.createCategory()(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }

    "return success response for read api" in {
      val result = controller.readCategory("test_content_all")(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }

    "return success response for update api" in {
      val result = controller.updateCategory("test_content_all")(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }

    "return success response for retire api" in {
      val result = controller.retireCategory("test_content_all")(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }
  }

}
