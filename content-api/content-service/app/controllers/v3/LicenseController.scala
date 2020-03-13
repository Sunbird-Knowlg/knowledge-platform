package controllers.v3

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import javax.inject.{Inject, Named}
import org.sunbird.content.util.LicenseConstants
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

@Singleton
class LicenseController @Inject()(@Named(ActorNames.LICENSE_ACTOR) licenseActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

    val objectType = "License"
    val schemaName: String = "license"
    val version = "1.0"

    def create() = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val license = body.getOrElse("license", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        license.putAll(headers)
        val licenseRequest = getRequest(license, headers, LicenseConstants.CREATE_LICENSE)
        setRequestContext(licenseRequest, version, objectType, schemaName)
        getResult(ApiId.CREATE_LICENSE, licenseActor, licenseRequest)
    }

    def read(identifier: String, fields: Option[String]) = Action.async { implicit request =>
        val headers = commonHeaders()
        val license = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
        license.putAll(headers)
        license.putAll(Map("identifier" -> identifier, "fields" -> fields.getOrElse("")))
        val licenseRequest = getRequest(license, headers,  LicenseConstants.READ_LICENSE)
        setRequestContext(licenseRequest, version, objectType, schemaName)
        getResult(ApiId.READ_LICENSE, licenseActor, licenseRequest)
    }

    def update(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val license = body.getOrElse("license", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        license.putAll(headers)
        val licenseRequest = getRequest(license, headers,  LicenseConstants.UPDATE_LICENSE)
        setRequestContext(licenseRequest, version, objectType, schemaName)
        licenseRequest.getContext.put("identifier", identifier)
        getResult(ApiId.UPDATE_LICENSE, licenseActor, licenseRequest)
    }

    def retire(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val license = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
        license.putAll(headers)
        val licenseRequest = getRequest(license, headers,  LicenseConstants.RETIRE_LICENSE)
        setRequestContext(licenseRequest, version, objectType, schemaName)
        licenseRequest.getContext.put("identifier", identifier)
        getResult(ApiId.RETIRE_LICENSE, licenseActor, licenseRequest)
    }
}
