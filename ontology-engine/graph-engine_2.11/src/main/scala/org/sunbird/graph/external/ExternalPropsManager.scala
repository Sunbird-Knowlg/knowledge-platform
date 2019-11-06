
package org.sunbird.graph.external

import org.sunbird.common.dto.{Request, Response}
import org.sunbird.graph.external.store.ExternalStoreFactory
import org.sunbird.schema.SchemaValidatorFactory

import scala.concurrent.{ExecutionContext, Future}

object ExternalPropsManager {
    def saveProps(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        val objectType: String = request.getObjectType
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val store = ExternalStoreFactory.getExternalStore(SchemaValidatorFactory.getExternalStoreName(objectType, version))
        store.insert(request.getRequest)
    }

}