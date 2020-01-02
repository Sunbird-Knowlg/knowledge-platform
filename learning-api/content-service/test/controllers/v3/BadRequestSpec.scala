package controllers.v3

import org.junit.runner._
import org.specs2.runner._

import org.specs2.mutable.Specification
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.api.test.FakeRequest

@RunWith(classOf[JUnitRunner])
class BadRequestSpec extends Specification {
    implicit val app = new GuiceApplicationBuilder().build
    "Application" should {
        "send 404 on a bad request - /boum" in {
            route(app, FakeRequest(GET, "/boum")) must beSome.which (status(_) == NOT_FOUND)
        }
    }
}
