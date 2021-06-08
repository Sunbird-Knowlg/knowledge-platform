package controllers.v3

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers.{OK, status}

@RunWith(classOf[JUnitRunner])
class HealthControllerSpec extends BaseSpec {

	"return api health status report - successful response" in {
		val controller = app.injector.instanceOf[controllers.HealthController]
		val result = controller.health()(FakeRequest())
		isOK(result)
		status(result)(Helpers.defaultAwaitTimeout) must equalTo(OK)
	}
}
