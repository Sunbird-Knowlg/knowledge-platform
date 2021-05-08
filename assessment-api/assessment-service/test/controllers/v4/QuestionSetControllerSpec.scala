package controllers.v4

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, defaultAwaitTimeout, status}

@RunWith(classOf[JUnitRunner])
class QuestionSetControllerSpec extends BaseSpec {

	val controller = app.injector.instanceOf[controllers.v4.QuestionSetController]

	"create should create an questionSet successfully for given valid request" in {
		val result = controller.create()(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"read should return an questionSet successfully for given valid identifier" in {
		val result = controller.read("do_123", None, None)(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"update should update the questionSet successfully for given valid identifier" in {
		val result = controller.update("do_123")(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"review should update the questionSet status to Review successfully for given valid identifier" in {
		val result = controller.review("do_123")(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"publish should update the questionSet status to Live successfully for given valid identifier" in {
		val result = controller.publish("do_123")(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"retire should update the questionSet status to Retired successfully for given valid identifier" in {
		val result = controller.retire("do_123")(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"update Hierarchy should update  hierarchy successfully for given valid identifier" in {
		val result = controller.updateHierarchy()(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"read Hierarchy should read Hierarchy successfully for given valid identifier" in {
		val result = controller.getHierarchy("do_123", None)(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}


	"add question should update the questionSet status to Add question successfully for given valid identifier" in {
		val result = controller.add()(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}


	"remove question should update the questionSet status to remove question successfully for given valid identifier" in {
		val result = controller.remove()(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"review should update the questionSet status to Reject successfully for given valid identifier" in {
		val result = controller.reject("do_123")(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"import should create an questionSet successfully for given valid request" in {
		val result = controller.importQuestionSet()(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}
}
