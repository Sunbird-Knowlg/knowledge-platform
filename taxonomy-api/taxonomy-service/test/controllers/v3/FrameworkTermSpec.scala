package controllers.v3

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, status}
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class FrameworkTermSpec extends BaseSpec {

  "FrameworkTerm Controller " should {

    "return success response for create frameworkTerm API" in {
      val controller = app.injector.instanceOf[controllers.v3.FrameworkTermController]
      val result = controller.createFrameworkTerm("NCF","board")(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }

    "return success response for update frameworkTerm API" in {
      val controller = app.injector.instanceOf[controllers.v3.FrameworkTermController]
      val result = controller.updateFrameworkTerm("class1","NCF","board")(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }

    "return success response for retire frameworkTerm API" in {
      val controller = app.injector.instanceOf[controllers.v3.FrameworkTermController]
      val result = controller.retireFrameworkTerm("class1","NCF","board")(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }

    "return success response for read framework API" in {
      val controller = app.injector.instanceOf[controllers.v3.FrameworkTermController]
      val result = controller.readFrameworkTerm("class1","NCF","board" )(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }


  }
}
