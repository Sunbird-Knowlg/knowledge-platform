package org.sunbird.graph.schema

import org.sunbird.graph.dac.model.Node
import org.sunbird.schema.{ISchemaValidator, SchemaValidatorFactory}

import scala.concurrent.{ExecutionContext, Future}

abstract class IDefinition(graphId: String, schemaName: String, version: String = "1.0") extends CoreDomainObject(graphId, schemaName, version) {

    var schemaValidator: ISchemaValidator = SchemaValidatorFactory.getInstance(schemaName, version)
    def getNode(input: java.util.Map[String, AnyRef]): Node

    @throws[Exception]
    def validate(node: Node, operation: String = "update")(implicit ec: ExecutionContext): Future[Node]

    @throws[Exception]
    def getNode(identifier: String, operation: String = "read", mode: String)(implicit ec: ExecutionContext): Future[Node]

    def getSchemaName(): String ={
        schemaName
    }
}
