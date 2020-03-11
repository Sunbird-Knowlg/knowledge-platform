package org.sunbird.content.util

import org.sunbird.common.dto.{Request, Response, ResponseHandler}

import scala.concurrent.{ExecutionContext, Future}

object CopyManager {

    def copy(request:Request)(implicit ec:ExecutionContext): Future[Response] = {
       Future( ResponseHandler.OK())
    }

    def validateExistingNode() = {

    }

    def copyContent() = {

    }

    def copyCollection()= {

    }

    def validateRequiredKeys: Unit = {
        val keys: List[String] = List("createdBy", "createdFor", "organisation", "framework")
//        keys.filter(key => )
    }

    def emptyCheckFilter(): Boolean = {
        true
    }
}
