package controllers.v4

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
class ObjectCategoryDefinitionSpec extends BaseSpec {

	"ObjectCategoryDefinitionController" should {

		val controller = app.injector.instanceOf[ObjectCategoryDefinitionController]

		"return success response for create api" in {
			val result = controller.create()(FakeRequest())
			isOK(result)
			status(result) must equalTo(OK)
		}

		"return success response for read api" in {
			val result = controller.read("test_content_all", None)(FakeRequest())
			isOK(result)
			status(result) must equalTo(OK)
		}

		"return success response for update api" in {
			val result = controller.update("test_content_all")(FakeRequest())
			isOK(result)
			status(result) must equalTo(OK)
		}

		"return success response for read category api" in {
			val result = controller.readCategoryDefinition(None)(FakeRequest())
			isOK(result)
			status(result) must equalTo(OK)
		}
	}
}
