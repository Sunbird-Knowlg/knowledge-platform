package controllers.v3
import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, status}
import play.api.test.Helpers._
import taxonomy.controllers.v3.LockController

@RunWith(classOf[JUnitRunner])
class LockControllerSpec  extends BaseSpec {

  "Lock Controller " should {

    "return success response for create lock API" in {
      val controller = app.injector.instanceOf[LockController]
      val result = controller.createLock()(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }

    "return success response for refresh lock API" in {
      val controller = app.injector.instanceOf[LockController]
      val result = controller.refreshLock()(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }

    "return success response for retire lock API" in {
      val controller = app.injector.instanceOf[LockController]
      val result = controller.retireLock()(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }

    "return success response for list lock API" in {
      val controller = app.injector.instanceOf[LockController]
      val result = controller.listLock()(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }

  }
}