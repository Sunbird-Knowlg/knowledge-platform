package org.sunbird.graph.schema

import org.sunbird.graph.OntologyEngineContext

import scala.concurrent.ExecutionContext

object DefinitionFactory {

    private var definitions: Map[String, DefinitionDTO] = Map()

    def getDefinition(graphId: String, schemaName: String, version: String, categoryId: String = ""): DefinitionDTO = {
        val key = getKey(graphId, schemaName, version, categoryId)
        val definition = definitions.getOrElse(key, new DefinitionDTO(graphId, schemaName, version, categoryId))
        if (!definitions.contains(key))
            definitions += (key -> definition)
        definition
    }
    
    def getKey(graphId: String, schemaName: String, version: String, categoryId: String): String = {
        List(graphId, schemaName, version, categoryId) filter (_.nonEmpty) mkString ":"
    }





}
