package org.sunbird.graph.schema

import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.schema.{ISchemaValidator, SchemaValidatorFactory}

import scala.concurrent.{ExecutionContext, Future}

abstract class IDefinition(graphId: String, schemaName: String, version: String = "1.0", ocd: ObjectCategoryDefinition = ObjectCategoryDefinition())(implicit ec: ExecutionContext, oec: OntologyEngineContext) {//extends CoreDomainObject(graphId, schemaName, version, categoryId) {

    var schemaValidator: ISchemaValidator = if(ocd.categoryName.isBlank) SchemaValidatorFactory.getInstance(schemaName, version) else new CategoryDefinitionValidator(schemaName, version).loadSchema(ocd)
    
    def getNode(input: java.util.Map[String, AnyRef]): Node

    @throws[Exception]
    def validate(node: Node, operation: String = "update", setDefaultValue: Boolean = true)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node]

    @throws[Exception]
    def getNode(identifier: String, operation: String = "read", mode: String, versioning: Option[String] = None)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node]

    def getSchemaName(): String ={
        schemaName
    }

    def getSchemaVersion(): String = {
        version
    }
}
