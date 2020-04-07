package controllers

import org.junit.runner._
import org.specs2.runner._
import play.api.test._
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class HealthSpec extends BaseSpec {

    "Application" should {
        "return api health status report - successful" in {
            val controller = app.injector.instanceOf[HealthController]
            val result = controller.health()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
    }
}
