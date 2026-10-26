package taxonomy.controllers.v3

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

import org.apache.pekko.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import javax.inject.{Inject, Named}
import play.api.mvc.ControllerComponents
import org.sunbird.common.dto.ResponseHandler
import taxonomy.utils.{ActorNames, ApiId, Constants, JavaJsonUtils}

@Singleton
class FrameworkController @Inject()(@Named(ActorNames.FRAMEWORK_ACTOR) frameworkActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends FrameworkControllerBase(frameworkActor, cc) {

    override protected val objectType = "Framework"
    override protected val schemaName = Constants.FRAMEWORK_SCHEMA_NAME
    override protected val bodyKey = Constants.FRAMEWORK

    def createFramework() = doCreate(ApiId.CREATE_FRAMEWORK)

    def readFramework(identifier: String, fields: Option[String], categories: Option[String]) = doRead(identifier, fields, categories, ApiId.READ_FRAMEWORK)

    def updateFramework(identifier: String) = doUpdate(identifier, ApiId.UPDATE_FRAMEWORK)

    def retire(identifier: String) = doDispatch(identifier, Constants.RETIRE_FRAMEWORK, ApiId.RETIRE_FRAMEWORK, identifierInContext = true)

    def publish(identifier: String) = doDispatch(identifier, Constants.PUBLISH_FRAMEWORK, ApiId.PUBLISH_FRAMEWORK, identifierInContext = false)

    def review(identifier: String) = doDispatch(identifier, Constants.REVIEW_FRAMEWORK, ApiId.REVIEW_FRAMEWORK, identifierInContext = false)

    def reject(identifier: String) = doDispatch(identifier, Constants.REJECT_FRAMEWORK, ApiId.REJECT_FRAMEWORK, identifierInContext = false)

    def listFramework() = Action.async { implicit request =>
        val result = ResponseHandler.OK()
        val response = JavaJsonUtils.serialize(result)
        Future(Ok(response).as("application/json"))
    }

    def copyFramework(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val framework = body.getOrDefault(Constants.FRAMEWORK, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        framework.putAll(headers)
        framework.putAll(Map("identifier" -> identifier ).asJava)
        val frameworkRequest = getRequest(framework, headers, Constants.COPY_FRAMEWORK)
        setRequestContext(frameworkRequest, Constants.FRAMEWORK_SCHEMA_VERSION, "Framework", Constants.FRAMEWORK_SCHEMA_NAME)
        getResult(ApiId.COPY_FRAMEWORK, frameworkActor, frameworkRequest)
    }
}
