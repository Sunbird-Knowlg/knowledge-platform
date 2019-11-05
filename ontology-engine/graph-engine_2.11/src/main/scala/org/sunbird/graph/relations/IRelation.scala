package org.sunbird.graph.relations

import org.sunbird.common.dto.Request

abstract class IRelation {

    def validate(request: Request): List[String]

    def createRelation(req: Request):String

    def getRelationType():String
}
