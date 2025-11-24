package org.sunbird.content.actors

import com.google.inject.Singleton
import javax.inject.Inject
import org.sunbird.actor.core.BaseActor
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.content.util.{AssetCopyManager, AssetLicenseValidateManager}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.util.RequestUtil

import scala.concurrent.{ExecutionContext, Future}
@Singleton
class AssetActor @Inject()(implicit oec: OntologyEngineContext, ss: StorageService) extends BaseActor {
  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case "copy" => copy(request)
      case "validateLicense" => validateLicense(request)
      case _ => ERROR(request.getOperation)
    }
  }

  def copy(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    AssetCopyManager.copy(request)
  }

  def validateLicense(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    AssetLicenseValidateManager.urlValidate(request)
  }
}
