package org.sunbird.graph

import org.sunbird.common.HttpUtil

class OntologyEngineContext {

    private val graphDB = new GraphService
    private val hUtil = new HttpUtil

    def graphService = {
        graphDB
    }

    def extStoreDB = {

    }

    def redis = {

    }

    def httpUtil = hUtil
}
