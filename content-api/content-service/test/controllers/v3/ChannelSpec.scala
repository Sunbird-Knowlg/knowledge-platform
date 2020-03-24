package controllers.v3

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, status}
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class ChannelSpec extends BaseSpec {
  "Channel Controller " should {
    "return success response for create API" in {
      val controller = app.injector.instanceOf[controllers.v3.ChannelController]
      val result = controller.create()(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }
  }
  "return success response for update API" in {
    val controller = app.injector.instanceOf[controllers.v3.ChannelController]
    val result = controller.update("do_123")(FakeRequest())
    isOK(result)
    status(result) must equalTo(OK)
  }

  "return success response for read API" in {
    val controller = app.injector.instanceOf[controllers.v3.ChannelController]
    val result = controller.read("do_123")(FakeRequest())
    isOK(result)
    status(result) must equalTo(OK)
  }

  "return success response for retire API" in {
    val controller = app.injector.instanceOf[controllers.v3.ChannelController]
    val result = controller.retire("do_123")(FakeRequest())
    isOK(result)
    status(result) must equalTo(OK)
  }

}



