package controllers.v3

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, defaultAwaitTimeout, status}

@RunWith(classOf[JUnitRunner])
class AssessmentItemControllerSpec extends BaseSpec {

	val controller = app.injector.instanceOf[assessment.controllers.v3.AssessmentItemController]

	"create should create an assessment item successfully for given valid request" in {
		val result = controller.create()(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"read should return an assessment item successfully for given valid identifier" in {
		val result = controller.read("do_123", None, None)(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"update should update the assessment item successfully for given valid identifier" in {
		val result = controller.update("do_123")(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"retire should update the assessment item status to Retired successfully for given valid identifier" in {
		val result = controller.retire("do_123")(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}
}
