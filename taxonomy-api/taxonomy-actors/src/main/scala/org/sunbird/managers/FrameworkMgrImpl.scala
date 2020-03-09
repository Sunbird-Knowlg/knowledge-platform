package org.sunbird.managers

import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.graph.nodes.DataNode

import scala.concurrent.{ExecutionContext, Future}

object FrameworkMgrImpl {
    
    def validateCreateRequest(request: Request)(implicit ec: ExecutionContext): Future[Response]  = {
        var response: Response = ResponseHandler.OK
        var isValid = false
        val channelId = request.get("channel").asInstanceOf[String]
        BaseFrameworkManager.validateObject(channelId).map( resp => {
            var isValid = resp
            if (isValid)
                response
            else
                ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "Invalid Channel Id. Channel doesn't exist")
        })
    }
}