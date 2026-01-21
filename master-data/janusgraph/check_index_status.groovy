
jg = JanusGraphFactory.open('conf/janusgraph-cql.properties')
mgmt = jg.openManagement()
println "Checking Index Status..."
mgmt.getGraphIndexes(Vertex.class).each { index ->
    println "Index: ${index.name()}"
    index.getFieldKeys().each { key ->
        status = mgmt.getGraphIndex(index.name()).getIndexStatus(key)
        println "  Key: ${key.name()} Status: $status"
    }
}
System.exit(0)
