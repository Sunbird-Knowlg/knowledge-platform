import org.junit.runner._
import org.specs2.runner._
import play.api.test.Helpers._
import play.api.test._

@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends BaseSpec {
    "Application" should {
        "send 404 on a bad request" in {
            route(app, FakeRequest(GET, "/boum")) must beSome.which (status(_) == NOT_FOUND)
        }
        "return api health status report - successful response" in {
            val controller = app.injector.instanceOf[controllers.HealthController]
            val result = controller.health()(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
    }
}
