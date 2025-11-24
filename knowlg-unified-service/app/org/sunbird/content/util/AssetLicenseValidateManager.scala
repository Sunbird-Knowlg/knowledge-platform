package org.sunbird.content.util

import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.url.mgr.impl.URLFactoryManager
import org.sunbird.common.exception.{ClientException, ResponseCode}
import scala.concurrent.{ExecutionContext, Future}
object AssetLicenseValidateManager {

  def urlValidate(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    val field = request.getRequestString("field", "")
    val provider = request.getRequestString("provider", "")
    val url = request.getRequestString("url", "")
    val urlMgr = URLFactoryManager.getUrlManager(getProvider(provider).toLowerCase)
    val data: java.util.Map[String, AnyRef] = urlMgr.validateURL(getUrl(url), field)
    Future(ResponseHandler.OK().put(field, data))
  }

  def getProvider(provider: String): String = {
    Option(provider).getOrElse(throw new ClientException(ResponseCode.CLIENT_ERROR.name(), "Please specify provider"))
  }

  def getUrl(url: String): String = {
    Option(url).getOrElse(throw new ClientException(ResponseCode.CLIENT_ERROR.name(), "Please specify url"))
  }

}
