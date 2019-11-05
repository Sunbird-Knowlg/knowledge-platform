package org.sunbird.graph.engine

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.{ClientException, ResponseCode, ServerException}
import org.sunbird.graph.relations.IRelation

import scala.concurrent.{ExecutionContext, Future}


object RelationManager {

    @throws[Exception]
    def createNewRelations(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        val relations: List[IRelation] = request.get("relations").asInstanceOf[List[IRelation]]

        relations.foreach(relation => {
            val errList =  relation.validate(request)
            if( null!= errList && !errList.isEmpty){
                throw new ClientException(ResponseCode.CLIENT_ERROR.name, "Error while validating relations :: " + errList)
            }
        })
        relations.foreach(relation => {
            val msg = relation.createRelation(request)
            if(StringUtils.isNotBlank(msg))
                throw new ServerException(ResponseCode.SERVER_ERROR.name(), "Error while creating relation :: " +  msg)
        })
        Future(new Response)
    }
}
