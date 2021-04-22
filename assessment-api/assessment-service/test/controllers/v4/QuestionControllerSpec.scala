package controllers.v4

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, defaultAwaitTimeout, status}

@RunWith(classOf[JUnitRunner])
class QuestionControllerSpec extends BaseSpec {

	val controller = app.injector.instanceOf[controllers.v4.QuestionController]

	"create should create an question successfully for given valid request" in {
		val result = controller.create()(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"read should return an question successfully for given valid identifier" in {
		val result = controller.read("do_123", None, None)(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"update should update the question successfully for given valid identifier" in {
		val result = controller.update("do_123")(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"review should update the question status to Review successfully for given valid identifier" in {
		val result = controller.review("do_123")(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"publish should update the question status to Live successfully for given valid identifier" in {
		val result = controller.publish("do_123")(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"retire should update the question status to Retired successfully for given valid identifier" in {
		val result = controller.retire("do_123")(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"import should create a question successfully for given valid request" in {
		val result = controller.importQuestion()(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"systemUpdate should update an question successfully for given valid request" in {
		val result = controller.systemUpdate("do_123")(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}
}
