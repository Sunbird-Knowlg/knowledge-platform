package taxonomy.controllers.v3

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestProbe
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.{BadPart, FilePart}
import play.api.test.{FakeRequest, Helpers}
import org.sunbird.common.dto.{Request => SbRequest, Response, ResponseParams}
import org.sunbird.common.exception.ClientException
import org.sunbird.common.exception.ResponseCode
import taxonomy.utils.Constants

import java.io.File
import java.nio.file.Files
import scala.concurrent.ExecutionContext.Implicits.global

class FrameworkTermControllerTest extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem("FrameworkTermControllerTest")

  override def afterAll(): Unit = {
    system.terminate()
  }

  private def successResponse(): Response = {
    val response = new Response
    response.setResponseCode(ResponseCode.OK)
    val params = new ResponseParams
    params.setStatus("successful")
    response.setParams(params)
    response
  }

  private def newController(): (FrameworkTermController, TestProbe) = {
    val termProbe = TestProbe()
    val controller = new FrameworkTermController(termProbe.ref, Helpers.stubControllerComponents(), system)
    (controller, termProbe)
  }

  private def tempCsvFilePart(fileName: String = "terms.csv"): FilePart[TemporaryFile] = {
    val path = Files.createTempFile("upload", ".csv")
    Files.write(path, "not a real csv, just bytes for the multipart part".getBytes("UTF-8"))
    FilePart("file", fileName, None, SingletonTemporaryFileCreator.create(path))
  }

  "FrameworkTermController.bulkValidateTerm" should "invoke termActor with BULK_VALIDATE_TERM op, carrying the framework field and the uploaded file" in {
    val (controller, termProbe) = newController()
    val multipartBody = MultipartFormData(Map("framework" -> Seq("fw1")), Seq(tempCsvFilePart()), Seq[BadPart]())
    controller.bulkValidateTerm().apply(FakeRequest().withMultipartFormDataBody(multipartBody))
    val req = termProbe.expectMsgType[SbRequest]
    termProbe.reply(successResponse())
    req.getOperation shouldBe Constants.BULK_VALIDATE_TERM
    req.getRequest.get("framework") shouldBe "fw1"
    req.getRequest.get("file") shouldBe a[File]
    req.getRequest.get("fileName") shouldBe "terms.csv"
  }

  it should "throw ERR_INVALID_DATA when no file part is present" in {
    val (controller, _) = newController()
    val multipartBody = MultipartFormData(Map("framework" -> Seq("fw1")), Seq.empty[FilePart[TemporaryFile]], Seq[BadPart]())
    val thrown = intercept[ClientException] {
      controller.bulkValidateTerm().apply(FakeRequest().withMultipartFormDataBody(multipartBody))
    }
    thrown.getErrCode shouldBe "ERR_INVALID_DATA"
  }

  "FrameworkTermController.bulkCommitTerm" should "invoke termActor with BULK_COMMIT_TERM op, carrying the framework field and the uploaded file" in {
    val (controller, termProbe) = newController()
    val multipartBody = MultipartFormData(Map("framework" -> Seq("fw1")), Seq(tempCsvFilePart()), Seq[BadPart]())
    controller.bulkCommitTerm().apply(FakeRequest().withMultipartFormDataBody(multipartBody))
    val req = termProbe.expectMsgType[SbRequest]
    termProbe.reply(successResponse())
    req.getOperation shouldBe Constants.BULK_COMMIT_TERM
    req.getRequest.get("framework") shouldBe "fw1"
    req.getRequest.get("file") shouldBe a[File]
  }

  it should "throw ERR_INVALID_DATA when no file part is present" in {
    val (controller, _) = newController()
    val multipartBody = MultipartFormData(Map("framework" -> Seq("fw1")), Seq.empty[FilePart[TemporaryFile]], Seq[BadPart]())
    val thrown = intercept[ClientException] {
      controller.bulkCommitTerm().apply(FakeRequest().withMultipartFormDataBody(multipartBody))
    }
    thrown.getErrCode shouldBe "ERR_INVALID_DATA"
  }

  "FrameworkTermController.bulkDownloadTerm" should "invoke termActor with BULK_DOWNLOAD_TERM op using the framework path param, no body" in {
    val (controller, termProbe) = newController()
    controller.bulkDownloadTerm("fw1").apply(FakeRequest())
    val req = termProbe.expectMsgType[SbRequest]
    termProbe.reply(successResponse())
    req.getOperation shouldBe Constants.BULK_DOWNLOAD_TERM
    req.getRequest.get("framework") shouldBe "fw1"
  }

}
