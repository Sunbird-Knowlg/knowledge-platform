package org.sunbird.graph

class OntologyEngineContext {

    private val graphDB = new GraphService

    def graphService = {
        graphDB
    }

    def extStoreDB = {

    }

    def redis = {

    }
}
