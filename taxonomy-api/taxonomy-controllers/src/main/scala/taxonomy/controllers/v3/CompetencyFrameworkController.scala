package taxonomy.controllers.v3

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

import org.apache.pekko.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import javax.inject.{Inject, Named}
import play.api.mvc.ControllerComponents
import org.sunbird.common.dto.ResponseHandler
import taxonomy.controllers.BaseController
import taxonomy.utils.{ActorNames, ApiId, Constants, JavaJsonUtils}

@Singleton
class CompetencyFrameworkController @Inject()(
    @Named(ActorNames.FRAMEWORK_ACTOR) frameworkActor: ActorRef,
    @Named(ActorNames.CATEGORY_INSTANCE_ACTOR) categoryInstanceActor: ActorRef,
    cc: ControllerComponents,
    actorSystem: ActorSystem
)(implicit exec: ExecutionContext) extends BaseController(cc) {

    val objectType = "CompetencyFramework"

    def createCompetencyFramework() = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val framework = body.getOrDefault(Constants.COMPETENCY_FRAMEWORK, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        framework.putAll(headers)
        val frameworkRequest = getRequest(framework, headers, Constants.CREATE_FRAMEWORK)
        setRequestContext(frameworkRequest, Constants.COMPETENCY_FRAMEWORK_SCHEMA_VERSION, objectType, Constants.COMPETENCY_FRAMEWORK_SCHEMA_NAME)
        getResult(ApiId.CREATE_COMPETENCY_FRAMEWORK, frameworkActor, frameworkRequest)
    }

    def readCompetencyFramework(identifier: String, fields: Option[String], categories: Option[String]) = Action.async { implicit request =>
        val headers = commonHeaders()
        val framework = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
        framework.putAll(headers)
        framework.putAll(Map(Constants.IDENTIFIER -> identifier, Constants.CATEGORIES -> categories.getOrElse("")).asJava)
        val readRequest = getRequest(framework, headers, Constants.READ_FRAMEWORK)
        setRequestContext(readRequest, Constants.COMPETENCY_FRAMEWORK_SCHEMA_VERSION, objectType, Constants.COMPETENCY_FRAMEWORK_SCHEMA_NAME)
        getResult(ApiId.READ_COMPETENCY_FRAMEWORK, frameworkActor, readRequest)
    }

    private def dispatchFrameworkAction(identifier: String, operation: String, apiId: String, identifierInContext: Boolean)
                                        (implicit request: play.api.mvc.Request[play.api.mvc.AnyContent]): Future[play.api.mvc.Result] = {
        val headers = commonHeaders()
        val body = requestBody()
        val framework = body.getOrDefault(Constants.COMPETENCY_FRAMEWORK, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        framework.putAll(headers)
        if (!identifierInContext) framework.putAll(Map("identifier" -> identifier).asJava)
        val frameworkRequest = getRequest(framework, headers, operation)
        setRequestContext(frameworkRequest, Constants.COMPETENCY_FRAMEWORK_SCHEMA_VERSION, objectType, Constants.COMPETENCY_FRAMEWORK_SCHEMA_NAME)
        if (identifierInContext) frameworkRequest.getContext.put(Constants.IDENTIFIER, identifier)
        getResult(apiId, frameworkActor, frameworkRequest)
    }

    def retire(identifier: String) = Action.async { implicit request =>
        dispatchFrameworkAction(identifier, Constants.RETIRE_FRAMEWORK, ApiId.RETIRE_COMPETENCY_FRAMEWORK, identifierInContext = true)
    }

    def updateCompetencyFramework(identifier: String) = Action.async { implicit request =>
        dispatchFrameworkAction(identifier, Constants.UPDATE_FRAMEWORK, ApiId.UPDATE_COMPETENCY_FRAMEWORK, identifierInContext = true)
    }

    def publish(identifier: String) = Action.async { implicit request =>
        dispatchFrameworkAction(identifier, Constants.PUBLISH_FRAMEWORK, ApiId.PUBLISH_COMPETENCY_FRAMEWORK, identifierInContext = false)
    }

    def review(identifier: String) = Action.async { implicit request =>
        dispatchFrameworkAction(identifier, Constants.REVIEW_FRAMEWORK, ApiId.REVIEW_COMPETENCY_FRAMEWORK, identifierInContext = false)
    }

    def reject(identifier: String) = Action.async { implicit request =>
        dispatchFrameworkAction(identifier, Constants.REJECT_FRAMEWORK, ApiId.REJECT_COMPETENCY_FRAMEWORK, identifierInContext = false)
    }

}
