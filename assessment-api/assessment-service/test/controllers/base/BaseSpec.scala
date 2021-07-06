package controllers.base

import com.typesafe.config.ConfigFactory
import modules.TestModule
import org.specs2.mutable.Specification
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers.{POST, contentAsString, contentType, defaultAwaitTimeout, route, status, _}
import play.api.test.{FakeHeaders, FakeRequest}

import scala.concurrent.Future

class BaseSpec extends Specification {
    implicit val app = new GuiceApplicationBuilder()
      .disable(classOf[modules.AssessmentModule])
      .bindings(new TestModule)
      .build
    implicit val config = ConfigFactory.load()

    def post(apiURL: String, request: String, h: FakeHeaders = FakeHeaders(Seq()))
    : Future[Result] = {
        val headers = h.add(("content-type", "application/json"))
        route(app, FakeRequest(POST, apiURL, headers, Json.toJson(Json.parse(request)))).get
    }

    def isOK(response: Future[Result]) {
        status(response) must equalTo(OK)
        contentType(response) must beSome.which(_ == "application/json")
        contentAsString(response) must contain(""""status":"successful"""")
    }

    def hasClientError(response: Future[Result]) {
        status(response) must equalTo(BAD_REQUEST)
        contentType(response) must beSome.which(_ == "application/json")
        contentAsString(response) must contain(""""err":"CLIENT_ERROR","status":"failed"""")
    }
}
