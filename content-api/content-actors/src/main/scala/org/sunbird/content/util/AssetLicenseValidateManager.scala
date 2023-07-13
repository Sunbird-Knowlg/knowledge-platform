package org.sunbird.content.util

import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.url.mgr.impl.URLFactoryManager
import org.sunbird.common.exception.{ClientException, ResponseCode}
import scala.concurrent.{ExecutionContext, Future}
object AssetLicenseValidateManager {

  def urlValidate(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    val field = request.getRequest.getOrDefault("field", "").asInstanceOf[String]
    val provider = request.getRequest.getOrDefault("provider", "").asInstanceOf[String]
    val url = request.getRequest.getOrDefault("url", "").asInstanceOf[String]
    val urlMgr = URLFactoryManager.getUrlManager(getProvider(provider).toLowerCase)
    val data: java.util.Map[String, AnyRef] = urlMgr.validateURL(getUrl(url), field)
    if (!data.getOrDefault("valid", false.asInstanceOf[AnyRef]).asInstanceOf[Boolean])
      throw new ClientException("INVALID_MEDIA_LICENSE", s"Please Provide Asset With Valid License For : $provider | Url : $url")
    Future {
      val response = ResponseHandler.OK()
      response.put(field, data)
      response
    }
  }

  def getProvider(provider: String): String = {
    Option(provider).getOrElse(throw new ClientException(ResponseCode.CLIENT_ERROR.name(), "Please specify provider"))
  }

  def getUrl(url: String): String = {
    Option(url).getOrElse(throw new ClientException(ResponseCode.CLIENT_ERROR.name(), "Please specify url"))
  }

}
