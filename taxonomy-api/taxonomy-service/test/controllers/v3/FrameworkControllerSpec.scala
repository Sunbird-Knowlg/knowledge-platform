package controllers.v3

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, status}
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class FrameworkControllerSpec extends BaseSpec {

    "Framework Controller " should {
        "return success response for createFramework API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkController]
            val result = controller.createFramework()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for readFramework API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkController]
            val result = controller.readFramework("do_123",None)(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        
        "return success response for retire API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkController]
            val result = controller.retire("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for updateFramework API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkController]
            val result = controller.updateFramework("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for listFramework API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkController]
            val result = controller.listFramework()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for copyFramework API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkController]
            val result = controller.copyFramework("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        
        "return success response for publish API" in {
            val controller = app.injector.instanceOf[controllers.v3.FrameworkController]
            val result = controller.publish("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
    }
}   
    
    
