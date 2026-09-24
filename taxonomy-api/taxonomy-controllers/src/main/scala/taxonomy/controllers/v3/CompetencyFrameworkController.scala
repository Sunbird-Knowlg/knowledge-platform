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

    def retire(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val framework = body.getOrDefault(Constants.COMPETENCY_FRAMEWORK, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        framework.putAll(headers)
        val frameworkRequest = getRequest(framework, headers, Constants.RETIRE_FRAMEWORK)
        setRequestContext(frameworkRequest, Constants.COMPETENCY_FRAMEWORK_SCHEMA_VERSION, objectType, Constants.COMPETENCY_FRAMEWORK_SCHEMA_NAME)
        frameworkRequest.getContext.put(Constants.IDENTIFIER, identifier)
        getResult(ApiId.RETIRE_COMPETENCY_FRAMEWORK, frameworkActor, frameworkRequest)
    }

    def updateCompetencyFramework(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val framework = body.getOrDefault(Constants.COMPETENCY_FRAMEWORK, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        framework.putAll(headers)
        val frameworkRequest = getRequest(framework, headers, Constants.UPDATE_FRAMEWORK)
        setRequestContext(frameworkRequest, Constants.COMPETENCY_FRAMEWORK_SCHEMA_VERSION, objectType, Constants.COMPETENCY_FRAMEWORK_SCHEMA_NAME)
        frameworkRequest.getContext.put(Constants.IDENTIFIER, identifier)
        getResult(ApiId.UPDATE_COMPETENCY_FRAMEWORK, frameworkActor, frameworkRequest)
    }

    def publish(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val framework = body.getOrDefault(Constants.COMPETENCY_FRAMEWORK, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        framework.putAll(headers)
        framework.putAll(Map("identifier" -> identifier).asJava)
        val frameworkRequest = getRequest(framework, headers, Constants.PUBLISH_FRAMEWORK)
        setRequestContext(frameworkRequest, Constants.COMPETENCY_FRAMEWORK_SCHEMA_VERSION, objectType, Constants.COMPETENCY_FRAMEWORK_SCHEMA_NAME)
        getResult(ApiId.PUBLISH_COMPETENCY_FRAMEWORK, frameworkActor, frameworkRequest)
    }


    def review(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val framework = body.getOrDefault(Constants.COMPETENCY_FRAMEWORK, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        framework.putAll(headers)
        framework.putAll(Map("identifier" -> identifier).asJava)
        val frameworkRequest = getRequest(framework, headers, Constants.REVIEW_FRAMEWORK)
        setRequestContext(frameworkRequest, Constants.COMPETENCY_FRAMEWORK_SCHEMA_VERSION, objectType, Constants.COMPETENCY_FRAMEWORK_SCHEMA_NAME)
        getResult(ApiId.REVIEW_COMPETENCY_FRAMEWORK, frameworkActor, frameworkRequest)
    }

    def reject(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val framework = body.getOrDefault(Constants.COMPETENCY_FRAMEWORK, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        framework.putAll(headers)
        framework.putAll(Map("identifier" -> identifier).asJava)
        val frameworkRequest = getRequest(framework, headers, Constants.REJECT_FRAMEWORK)
        setRequestContext(frameworkRequest, Constants.COMPETENCY_FRAMEWORK_SCHEMA_VERSION, objectType, Constants.COMPETENCY_FRAMEWORK_SCHEMA_NAME)
        getResult(ApiId.REJECT_COMPETENCY_FRAMEWORK, frameworkActor, frameworkRequest)
    }

}
