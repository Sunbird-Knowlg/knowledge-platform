package controllers.v3

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, status}
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class ContentSpec extends BaseSpec {

    "Content Controller " should {
        "return success response for create API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.create()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for update API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.update("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for read API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.read("do_123", None, None)(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for hierarchy add API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.addHierarchy()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for hierarchy remove API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.removeHierarchy()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for hierarchy get API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.getHierarchy("do_123", None)(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for flag API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.flag("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for acceptFlag API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.acceptFlag("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for rejectFlag API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.rejectFlag("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for bundle API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.bundle()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for publish API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.publish("0123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for review API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.review("0123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for discard API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.discard("0123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for retire API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.retire("0123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for linkDialCode API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.linkDialCode()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for reserveDialCode API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.reserveDialCode("01234")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for releaseDialcodes API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.releaseDialcodes("01234")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for rejectContent API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.rejectContent("01234")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for publishUnlisted API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.publishUnlisted("01234")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
        "return success response for presignedUrl upload API" in {
            val controller = app.injector.instanceOf[controllers.v3.ContentController]
            val result = controller.uploadPreSigned("01234", None)(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
    }
}
