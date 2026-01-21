
graph = JanusGraphFactory.open('conf/janusgraph-cql.properties')
g = graph.traversal()
println "Vertex Count: " + g.V().count().next()
println "Edge Count: " + g.E().count().next()

// Dump one vertex
v = g.V().limit(1).next()
println "Sample Vertex: " + v
println "Labels: " + v.label()
println "Properties:"
g.V(v).properties().each { p ->
    println "  " + p.key() + ": " + p.value()
}
System.exit(0)
