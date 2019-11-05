package org.sunbird.graph.schema

object DefinitionFactory {

    private var definitions: Map[String, DefinitionDTO] = Map()

    def getDefinition(graphId: String, objectType: String, version: String): DefinitionDTO = {
        val key = getKey(graphId, objectType, version)
        val definition = definitions.getOrElse(key, new DefinitionDTO(graphId, objectType, version))
        if (!definitions.contains(key))
            definitions + (key -> definition)
        definition
    }

    def getKey(graphId: String, objectType: String, version: String): String = {
        graphId + ":" + objectType + ":" + version
    }





}
