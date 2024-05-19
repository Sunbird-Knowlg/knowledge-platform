package org.sunbird.graph.schema.validator

import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.{Node, Vertex}
import org.sunbird.graph.schema.IDefinition

import scala.concurrent.{ExecutionContext, Future}

trait SchemaValidator extends IDefinition {

    @throws[Exception]
    abstract override def validate(node: Node, operation: String, setDefaultValue: Boolean)(implicit ec: ExecutionContext, oec:OntologyEngineContext): Future[Node] = {
        if(setDefaultValue){
            val result = schemaValidator.validate(node.getMetadata)
            if(setDefaultValue && operation.equalsIgnoreCase("create")) {
                node.setMetadata(result.getMetadata)
            }
        }

        super.validate(node, operation)
    }

  @throws[Exception]
  abstract override def validates(vertex: Vertex, operation: String, setDefaultValue: Boolean)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Vertex] = {
    if (setDefaultValue) {
      val result = schemaValidator.validate(vertex.getMetadata)
      if (setDefaultValue && operation.equalsIgnoreCase("create")) {
        vertex.setMetadata(result.getMetadata)
      }
    }

    super.validates(vertex, operation)
  }
}
