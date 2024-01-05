package org.sunbird.graph.external.dial


import org.sunbird.common.Platform
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.graph.util.CSPMetaUtil

import java.lang
import scala.concurrent.{ExecutionContext, Future}
// $COVERAGE-OFF$ Disabling scoverage
class DialGraphService {
  implicit val ec: ExecutionContext = ExecutionContext.global
  val isrRelativePathEnabled: lang.Boolean = Platform.getBoolean("cloudstorage.metadata.replace_absolute_path", false)

  def saveExternalProps(request: Request): Future[Response] = {
    val externalProps: java.util.Map[String, AnyRef] = request.getRequest
    val updatedExternalProps = if (isrRelativePathEnabled) CSPMetaUtil.saveExternalRelativePath(externalProps) else externalProps
    println(" updated external props ", updatedExternalProps.get(" identifier "), " ", updatedExternalProps.get("identifier").getClass)
    request.setRequest(updatedExternalProps)
    DialPropsManager.saveProps(request)
  }
}
// $COVERAGE-ON$ Enabling scoverage