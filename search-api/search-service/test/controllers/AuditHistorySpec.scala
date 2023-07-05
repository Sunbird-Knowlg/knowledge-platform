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

    "return success response with allFields" in {
      val controller = app.injector.instanceOf[controllers.AuditHistoryController]
      val fields = controller.setSearchCriteria("3.0", true)

      fields.contains("logRecord") must beTrue
      fields.contains("summary") must beTrue
    }

    "return fields without 'summary' for versionId 3.0" in {
      val controller = app.injector.instanceOf[controllers.AuditHistoryController]
      val fields = controller.setSearchCriteria("3.0", false)

      fields.contains("logRecord") must beTrue
      fields.contains("summary") must beFalse
    }

    "return fields with 'summary' for versionId other than 3.0" in {
      val controller = app.injector.instanceOf[controllers.AuditHistoryController]
      val fields = controller.setSearchCriteria("2.0", false)

      fields.contains("logRecord") must beFalse
      fields.contains("summary") must beTrue
    }

  }
}
