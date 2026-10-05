package taxonomy.controllers.v3

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestProbe
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import play.api.libs.json.Json
import play.api.test.{FakeRequest, Helpers}
import org.sunbird.common.dto.{Request => SbRequest, Response, ResponseParams}
import org.sunbird.common.exception.ResponseCode
import taxonomy.utils.Constants

import scala.concurrent.ExecutionContext.Implicits.global

// No controller-test harness exists anywhere else in this module (or in any other taxonomy/content/
// assessment controllers module) to mirror, so this establishes its own lightweight one: the injected
// actor is a Pekko TestProbe standing in for the ScalaMock-based "mock the DI seam" convention used at
// the actor layer, since a plain ActorRef can't be usefully mocked with ScalaMock.
class FrameworkControllerTest extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem("FrameworkControllerTest")

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

  private def newController(): (FrameworkController, TestProbe) = {
    val frameworkProbe = TestProbe()
    val controller = new FrameworkController(frameworkProbe.ref, Helpers.stubControllerComponents(), system)
    (controller, frameworkProbe)
  }

  "FrameworkController" should "invoke frameworkActor with CREATE_FRAMEWORK op and Framework schema context on createFramework" in {
    val (controller, frameworkProbe) = newController()
    val body = Json.parse("""{"request":{"framework":{"name":"F1","code":"f1","channel":"sunbird"}}}""")
    controller.createFramework().apply(FakeRequest().withJsonBody(body))
    val req = frameworkProbe.expectMsgType[SbRequest]
    frameworkProbe.reply(successResponse())
    req.getOperation shouldBe Constants.CREATE_FRAMEWORK
    req.getContext.get(Constants.SCHEMA_NAME) shouldBe Constants.FRAMEWORK_SCHEMA_NAME
  }

  it should "invoke frameworkActor with READ_FRAMEWORK op on readFramework" in {
    val (controller, frameworkProbe) = newController()
    controller.readFramework("f1", None, None).apply(FakeRequest())
    val req = frameworkProbe.expectMsgType[SbRequest]
    frameworkProbe.reply(successResponse())
    req.getOperation shouldBe Constants.READ_FRAMEWORK
    req.getContext.get(Constants.SCHEMA_NAME) shouldBe Constants.FRAMEWORK_SCHEMA_NAME
  }

  it should "invoke frameworkActor with UPDATE_FRAMEWORK op and set identifier in context on updateFramework" in {
    val (controller, frameworkProbe) = newController()
    val body = Json.parse("""{"request":{"framework":{"description":"updated"}}}""")
    controller.updateFramework("f1").apply(FakeRequest().withJsonBody(body))
    val req = frameworkProbe.expectMsgType[SbRequest]
    frameworkProbe.reply(successResponse())
    req.getOperation shouldBe Constants.UPDATE_FRAMEWORK
    req.getContext.get(Constants.SCHEMA_NAME) shouldBe Constants.FRAMEWORK_SCHEMA_NAME
    req.getContext.get(Constants.IDENTIFIER) shouldBe "f1"
  }

  it should "invoke frameworkActor with RETIRE_FRAMEWORK op on retire" in {
    val (controller, frameworkProbe) = newController()
    val body = Json.parse("""{"request":{}}""")
    controller.retire("f1").apply(FakeRequest().withJsonBody(body))
    val req = frameworkProbe.expectMsgType[SbRequest]
    frameworkProbe.reply(successResponse())
    req.getOperation shouldBe Constants.RETIRE_FRAMEWORK
    req.getContext.get(Constants.IDENTIFIER) shouldBe "f1"
  }

  it should "invoke frameworkActor with PUBLISH_FRAMEWORK op on publish" in {
    val (controller, frameworkProbe) = newController()
    val body = Json.parse("""{"request":{}}""")
    controller.publish("f1").apply(FakeRequest().withJsonBody(body))
    val req = frameworkProbe.expectMsgType[SbRequest]
    frameworkProbe.reply(successResponse())
    req.getOperation shouldBe Constants.PUBLISH_FRAMEWORK
    req.getContext.get(Constants.SCHEMA_NAME) shouldBe Constants.FRAMEWORK_SCHEMA_NAME
  }

  it should "invoke frameworkActor with REVIEW_FRAMEWORK op on review" in {
    val (controller, frameworkProbe) = newController()
    val body = Json.parse("""{"request":{}}""")
    controller.review("f1").apply(FakeRequest().withJsonBody(body))
    val req = frameworkProbe.expectMsgType[SbRequest]
    frameworkProbe.reply(successResponse())
    req.getOperation shouldBe Constants.REVIEW_FRAMEWORK
  }

  it should "invoke frameworkActor with REJECT_FRAMEWORK op on reject" in {
    val (controller, frameworkProbe) = newController()
    val body = Json.parse("""{"request":{}}""")
    controller.reject("f1").apply(FakeRequest().withJsonBody(body))
    val req = frameworkProbe.expectMsgType[SbRequest]
    frameworkProbe.reply(successResponse())
    req.getOperation shouldBe Constants.REJECT_FRAMEWORK
  }

}
