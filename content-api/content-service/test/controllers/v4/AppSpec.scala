package controllers.v4

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, status}
import play.api.test.Helpers._
import play.api.libs.json.JsValue
import play.api.libs.json.Json

@RunWith(classOf[JUnitRunner])
class AppSpec extends BaseSpec {

  "AppController " should {
    "return success response for register API" in {
      val controller = app.injector.instanceOf[controllers.v4.AppController]
      val json: JsValue = Json.parse("""{"request":{"app":{"name":"Test Integration App","description":"Description of Test Integration App","provider":{"name":"Test Organisation","copyright":"CC BY 4.0"},"osType":"android","osMetadata":{"packageId":"org.test.integration","appVersion":"1.0","compatibilityVer":"1.0"},"appTarget":{"mimeType":["application/pdf"]}}}}""")
      val fakeRequest = FakeRequest("POST", "/app/v4/register").withJsonBody(json)
      val result = controller.register()(fakeRequest)
      isOK(result)
      status(result) must equalTo(OK)
    }

    "return success response for update API" in {
      val controller = app.injector.instanceOf[controllers.v4.AppController]
      val result = controller.update("android-org.test.integration")(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }

    "return success response for read API" in {
      val controller = app.injector.instanceOf[controllers.v4.AppController]
      val result = controller.read("android-org.test.integration", None)(FakeRequest())
      isOK(result)
      status(result) must equalTo(OK)
    }
  }
}
