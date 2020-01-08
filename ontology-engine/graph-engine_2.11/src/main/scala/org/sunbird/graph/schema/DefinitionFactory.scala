package org.sunbird.graph.schema

object DefinitionFactory {

    private var definitions: Map[String, DefinitionDTO] = Map()

    def getDefinition(graphId: String, schemaName: String, version: String): DefinitionDTO = {
        val key = getKey(graphId, schemaName, version)
        val definition = definitions.getOrElse(key, new DefinitionDTO(graphId, schemaName, version))
        if (!definitions.contains(key))
            definitions += (key -> definition)
        definition
    }

    def getKey(graphId: String, schemaName: String, version: String): String = {
        graphId + ":" + schemaName + ":" + version
    }





}
