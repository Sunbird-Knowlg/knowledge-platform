package controllers

import org.junit.runner._
import org.specs2.runner._
import play.api.libs.json.{JsValue, Json}
import play.api.test._
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class AuditHistorySpec extends BaseSpec {

  "AuditHistory" should {
    "return success response for auditHistory API" in {
      lazy val controller = app.injector.instanceOf[controllers.AuditHistoryController]
      val response = controller.readAuditHistory("1234","domain")(FakeRequest())
      isOK(response)
      status(response) must equalTo(OK)
    }


  }
}
