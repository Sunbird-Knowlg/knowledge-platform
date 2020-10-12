package controllers.v4

import java.io.File

import controllers.base.BaseSpec
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, status}
import play.api.test.Helpers._
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.libs.json.JsValue
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.{BadPart, FilePart}
import play.api.libs.json.Json

@RunWith(classOf[JUnitRunner])
class AssetSpec extends BaseSpec {

    "AssetController " should {
        "return success response for create API" in {
            val controller = app.injector.instanceOf[controllers.v4.AssetController]
            val json: JsValue = Json.parse("""{"request": {"asset": { "primaryCategory": "Asset"}}}""")
            val fakeRequest = FakeRequest("POST", "/asset/v4/create ").withJsonBody(json)
            val result = controller.create()(fakeRequest)
            isOK(result)
            status(result) must equalTo(OK)
        }


        "return success response for update API" in {
            val controller = app.injector.instanceOf[controllers.v4.AssetController]
            val result = controller.update("do_123")(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for read API" in {
            val controller = app.injector.instanceOf[controllers.v4.AssetController]
            val result = controller.read("do_123", None, None)(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for upload API with file" in {
            val controller = app.injector.instanceOf[controllers.v4.AssetController]
            val file = new File("test/resources/sample.pdf")
            val files = Seq[FilePart[TemporaryFile]](FilePart("file", "sample.pdf", None, SingletonTemporaryFileCreator.create(file.toPath)))
            val multipartBody = MultipartFormData(Map[String, Seq[String]](), files, Seq[BadPart]())
            val fakeRequest = FakeRequest().withMultipartFormDataBody(multipartBody)
            val result = controller.upload("01234", None, None)(fakeRequest)
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for upload API with fileUrl" in {
            val controller = app.injector.instanceOf[controllers.v4.AssetController]
            val file = new File("test/resources/sample.pdf")
            val files = Seq[FilePart[TemporaryFile]](FilePart("file", "sample.pdf", None, SingletonTemporaryFileCreator.create(file.toPath)))
            val multipartBody = MultipartFormData(Map[String, Seq[String]]("fileUrl" -> Seq("https://abc.com/content/sample.pdf"), "filePath" -> Seq("/program/id")), files, Seq[BadPart]())
            val fakeRequest = FakeRequest().withMultipartFormDataBody(multipartBody)
            val result = controller.upload("01234", None, None)(fakeRequest)
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for upload API with fileUrl and fileFormat" in {
            val controller = app.injector.instanceOf[controllers.v4.AssetController]
            val file = new File("test/resources/sample.pdf")
            val files = Seq[FilePart[TemporaryFile]](FilePart("file", "sample.pdf", None, SingletonTemporaryFileCreator.create(file.toPath)))
            val multipartBody = MultipartFormData(Map[String, Seq[String]](), files, Seq[BadPart]())
            val fakeRequest = FakeRequest().withMultipartFormDataBody(multipartBody)
            val result = controller.upload("01234", Some("composed-h5p-zip"), None)(fakeRequest)
            isOK(result)
            status(result) must equalTo(OK)
        }

        "return success response for pre signed Url upload API" in {
            val controller = app.injector.instanceOf[controllers.v4.AssetController]
            val result = controller.uploadPreSigned("01234", None)(FakeRequest())
            isOK(result)
            status(result) must equalTo(OK)
        }
    }

    "Asset controller with invalid request " should {
        "return client error response for create API" in {
            val controller = app.injector.instanceOf[controllers.v4.AssetController]
            val json: JsValue = Json.parse("""{"request": {"asset": { "contentType": "Asset"}}}""")
            val fakeRequest = FakeRequest("POST", "/asset/v4/create ").withJsonBody(json)
            val result = controller.create()(fakeRequest)
            status(result) must equalTo(BAD_REQUEST)
        }
    }

    "Asset controller with invalid request " should {
        "return client error response for create API" in {
            val controller = app.injector.instanceOf[controllers.v4.AssetController]
            val json: JsValue = Json.parse("""{"request": {"asset": { "name": "Asset"}}}""")
            val fakeRequest = FakeRequest("POST", "/asset/v4/create ").withJsonBody(json)
            val result = controller.create()(fakeRequest)
            status(result) must equalTo(BAD_REQUEST)
        }
    }
}
