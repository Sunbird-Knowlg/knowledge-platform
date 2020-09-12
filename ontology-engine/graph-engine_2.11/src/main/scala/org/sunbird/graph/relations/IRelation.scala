package org.sunbird.graph.relations

import org.sunbird.common.dto.Request
import org.sunbird.graph.OntologyEngineContext

import scala.concurrent.ExecutionContext

abstract class IRelation {

    def validate(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): List[String]

    def createRelation(req: Request):String

    def getRelationType():String
}
