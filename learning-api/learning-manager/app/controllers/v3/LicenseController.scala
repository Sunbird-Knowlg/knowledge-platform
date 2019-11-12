package controllers.v3

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import javax.inject.{Inject, Named}
import play.api.mvc.ControllerComponents
import utils.{ActorNames, LicenseApiIds, LicenseOperations}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

@Singleton
class LicenseController @Inject()(@Named(ActorNames.LICENSE_ACTOR) licenseActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

  val objectType = "license"
  val version = "1.0"

  def create() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val license = body.getOrElse(objectType, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    license.putAll(headers)
    val contentRequest = getRequest(license, headers, LicenseOperations.createLicense.name())
    setRequestContext(contentRequest, version, objectType)
    getResult(LicenseApiIds.create, licenseActor, contentRequest)
  }

  def read(identifier: String, fields: Option[String]) = Action.async { implicit request =>
    val headers = commonHeaders()
    val license = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    license.putAll(headers)
    license.putAll(Map("identifier" -> identifier, "fields" -> fields.getOrElse("")))
    val licenseRequest = getRequest(license, headers, LicenseOperations.readLicense.name())
    setRequestContext(licenseRequest, version, objectType)
    getResult(LicenseApiIds.read, licenseActor, licenseRequest)
  }

  def update(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val license = body.getOrElse(objectType, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    license.putAll(headers)
    license.putAll(Map("identifier" -> identifier))
    val licenseRequest = getRequest(license, headers, LicenseOperations.updateLicense.name())
    setRequestContext(licenseRequest, version, objectType)
    getResult(LicenseApiIds.update, licenseActor, licenseRequest)
  }

  def retire(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val license = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    license.putAll(headers)
    license.putAll(Map("identifier" -> identifier))
    val licenseRequest = getRequest(license, headers, LicenseOperations.retireLicense.name())
    setRequestContext(licenseRequest, version, objectType)
    getResult(LicenseApiIds.retire, licenseActor, licenseRequest)
  }
}
