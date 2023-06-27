package controllers.v3

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers._
@RunWith(classOf[JUnitRunner])
class CategoryInstanceSpec extends BaseSpec {
  "CategoryInstanceController" should {

    val controller = app.injector.instanceOf[CategoryInstanceController]

    "return success response for create api" in {
      val result = controller.createCategoryInstance("NCF")(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }

    "return success response for read api" in {
      val result = controller.readCategoryInstance("board","NCF")(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }

    "return success response for update api" in {
      val result = controller.updateCategoryInstance("board","NCF")(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }

    "return success response for retire api" in {
      val result = controller.retireCategoryInstance("board","NCF")(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }
  }

}
