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
// actors are Pekko TestProbes standing in for the ScalaMock-based "mock the DI seam" convention used at
// the actor layer, since a plain ActorRef can't be usefully mocked with ScalaMock.
class CompetencyFrameworkControllerTest extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem("CompetencyFrameworkControllerTest")

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

  private def newController(): (CompetencyFrameworkController, TestProbe, TestProbe) = {
    val frameworkProbe = TestProbe()
    val categoryProbe = TestProbe()
    val controller = new CompetencyFrameworkController(frameworkProbe.ref, categoryProbe.ref, Helpers.stubControllerComponents(), system)
    (controller, frameworkProbe, categoryProbe)
  }

  "CompetencyFrameworkController" should "invoke frameworkActor with CREATE_FRAMEWORK op and CompetencyFramework schema context on createCompetencyFramework" in {
    val (controller, frameworkProbe, _) = newController()
    val body = Json.parse("""{"request":{"competencyFramework":{"name":"CF1","code":"cf1","channel":"sunbird"}}}""")
    controller.createCompetencyFramework().apply(FakeRequest().withJsonBody(body))
    val req = frameworkProbe.expectMsgType[SbRequest]
    frameworkProbe.reply(successResponse())
    req.getOperation shouldBe Constants.CREATE_FRAMEWORK
    req.getContext.get(Constants.SCHEMA_NAME) shouldBe Constants.COMPETENCY_FRAMEWORK_SCHEMA_NAME
  }

  it should "invoke frameworkActor with READ_FRAMEWORK op on readCompetencyFramework" in {
    val (controller, frameworkProbe, _) = newController()
    controller.readCompetencyFramework("cf1", None, None).apply(FakeRequest())
    val req = frameworkProbe.expectMsgType[SbRequest]
    frameworkProbe.reply(successResponse())
    req.getOperation shouldBe Constants.READ_FRAMEWORK
    req.getContext.get(Constants.SCHEMA_NAME) shouldBe Constants.COMPETENCY_FRAMEWORK_SCHEMA_NAME
  }

  it should "invoke frameworkActor with UPDATE_FRAMEWORK op and set identifier in context on updateCompetencyFramework" in {
    val (controller, frameworkProbe, _) = newController()
    val body = Json.parse("""{"request":{"competencyFramework":{"description":"updated"}}}""")
    controller.updateCompetencyFramework("cf1").apply(FakeRequest().withJsonBody(body))
    val req = frameworkProbe.expectMsgType[SbRequest]
    frameworkProbe.reply(successResponse())
    req.getOperation shouldBe Constants.UPDATE_FRAMEWORK
    req.getContext.get(Constants.SCHEMA_NAME) shouldBe Constants.COMPETENCY_FRAMEWORK_SCHEMA_NAME
    req.getContext.get(Constants.IDENTIFIER) shouldBe "cf1"
  }

  it should "invoke frameworkActor with RETIRE_FRAMEWORK op on retire" in {
    val (controller, frameworkProbe, _) = newController()
    val body = Json.parse("""{"request":{}}""")
    controller.retire("cf1").apply(FakeRequest().withJsonBody(body))
    val req = frameworkProbe.expectMsgType[SbRequest]
    frameworkProbe.reply(successResponse())
    req.getOperation shouldBe Constants.RETIRE_FRAMEWORK
    req.getContext.get(Constants.IDENTIFIER) shouldBe "cf1"
  }

  it should "invoke frameworkActor with PUBLISH_FRAMEWORK op on publish" in {
    val (controller, frameworkProbe, _) = newController()
    val body = Json.parse("""{"request":{}}""")
    controller.publish("cf1").apply(FakeRequest().withJsonBody(body))
    val req = frameworkProbe.expectMsgType[SbRequest]
    frameworkProbe.reply(successResponse())
    req.getOperation shouldBe Constants.PUBLISH_FRAMEWORK
    req.getContext.get(Constants.SCHEMA_NAME) shouldBe Constants.COMPETENCY_FRAMEWORK_SCHEMA_NAME
  }

  it should "invoke frameworkActor with SEND_FOR_REVIEW_FRAMEWORK op on sendForReview" in {
    val (controller, frameworkProbe, _) = newController()
    val body = Json.parse("""{"request":{}}""")
    controller.sendForReview("cf1").apply(FakeRequest().withJsonBody(body))
    val req = frameworkProbe.expectMsgType[SbRequest]
    frameworkProbe.reply(successResponse())
    req.getOperation shouldBe Constants.SEND_FOR_REVIEW_FRAMEWORK
  }

  it should "invoke frameworkActor with REJECT_FRAMEWORK op on reject" in {
    val (controller, frameworkProbe, _) = newController()
    val body = Json.parse("""{"request":{}}""")
    controller.reject("cf1").apply(FakeRequest().withJsonBody(body))
    val req = frameworkProbe.expectMsgType[SbRequest]
    frameworkProbe.reply(successResponse())
    req.getOperation shouldBe Constants.REJECT_FRAMEWORK
  }

  it should "invoke categoryInstanceActor with frameworkObjectType=competencyframework forced on createCategoryInstance" in {
    val (controller, _, categoryProbe) = newController()
    // the incoming body's "category" object omits frameworkObjectType entirely
    val body = Json.parse("""{"request":{"category":{"name":"Competency","code":"competency"}}}""")
    controller.createCategoryInstance("cf1").apply(FakeRequest().withJsonBody(body))
    val req = categoryProbe.expectMsgType[SbRequest]
    categoryProbe.reply(successResponse())
    req.getOperation shouldBe Constants.CREATE_CATEGORY_INSTANCE
    req.getRequest.get(Constants.FRAMEWORK_OBJECT_TYPE) shouldBe Constants.COMPETENCY_FRAMEWORK_SCHEMA_NAME
  }

}
