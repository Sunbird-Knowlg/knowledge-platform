package controllers.v3

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, defaultAwaitTimeout, status}

@RunWith(classOf[JUnitRunner])
class ItemSetControllerSpec extends BaseSpec {

	val controller = app.injector.instanceOf[controllers.v3.ItemSetController]

	"create should create an itemset successfully for given valid request" in {
		val result = controller.create()(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"read should return an itemset successfully for given valid identifier" in {
		val result = controller.read("do_123", None)(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"update should update the itemset successfully for given valid identifier" in {
		val result = controller.update("do_123")(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"review should update the itemset status to Review successfully for given valid identifier" in {
		val result = controller.review("do_123")(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}

	"retire should update the itemset status to Retired successfully for given valid identifier" in {
		val result = controller.retire("do_123")(FakeRequest())
		isOK(result)
		status(result)(defaultAwaitTimeout) must equalTo(OK)
	}
}
