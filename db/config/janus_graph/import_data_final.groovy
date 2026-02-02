
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.T
import org.apache.tinkerpop.gremlin.structure.Direction
import groovy.json.JsonSlurper

// Open Graph
graph = JanusGraphFactory.open('/opt/bitnami/janusgraph/conf/janusgraph-cql.properties')
// Bind graph and traversal to global binding for access in closures
binding.graph = graph
binding.g = graph.traversal()

println "--- STARTING DATA MIGRATION (User Script Fixed) ---"

// --- 1. NODES ---
println "Importing Nodes..."

if (!binding.hasVariable('state')) {
    binding.state = [accumulating: false, jsonBuffer: '', nodeLine: '']
}

new File('/data/nodes.csv').eachLine { line, idx ->
    try {
        if (idx == 1) return // skip header

        def state = binding.state
        // Access g via binding
        def g = binding.g

        if (state.accumulating) {
            state.nodeLine += ' ' + line.trim()
            if (line.trim().endsWith('}')) state.accumulating = false
            else return
        } else {
            state.nodeLine = line
            if (!line.trim().endsWith('}')) {
                state.accumulating = true
                return
            }
        }

        def parts = state.nodeLine.split(/,(?=\s*\[?["{])/)
        
        if (parts.size() < 3) {
             println "Skipping malformed line $idx: ${state.nodeLine}"
             return
        }

        def nodeIdVal = parts[0].toLong()
        def labelRaw = parts[1].replaceAll(/\[|\]|"/, '')
        def label = labelRaw
        def propsRaw = parts[2..-1].join(',')

        def propsFixed = propsRaw.replaceAll(/([{,]\s*)(\w+):/, '$1"$2":')
                                 .replaceAll(/\bTRUE\b/, 'true')
                                 .replaceAll(/\bFALSE\b/, 'false')

        def propsMap = new JsonSlurper().parseText(propsFixed)

        def existing = g.V().has('node_id', nodeIdVal).tryNext().orElse(null)
        
        if (!existing) {
            def uniqueId = propsMap['IL_UNIQUE_ID']
            if (uniqueId) {
                existing = g.V().has('IL_UNIQUE_ID', uniqueId).tryNext().orElse(null)
            }
        }

        if (!existing) {
            // binding.graph.addVertex works
            def v = binding.graph.addVertex(T.label, label, 'node_id', nodeIdVal)
            propsMap.each { k, vprop ->
                if (vprop instanceof List) vprop = vprop.join(',')
                else if (vprop instanceof BigDecimal) vprop = vprop.doubleValue()
                v.property(k, vprop)
            }
        }
    } catch (Exception e) {
        println "Error on line $idx: ${e.message}"
    }
}
binding.graph.tx().commit()
println "\nNodes Imported."


// --- 2. RELATIONSHIPS ---
println "Importing Relationships..."

new File('/data/relationships.csv').eachLine { line, idx ->
    try {
        if (idx == 1) return

        def g = binding.g // Access g
        def graph = binding.graph 

        def matcher = line =~ /^(\d+),\s*"([^"]+)",\s*(\d+),\s*(.*)$/
        if (!matcher.matches()) {
            println "Skipping malformed edge line $idx: $line"
            return
        }

        def fromId = matcher[0][1].toLong()
        def relType = matcher[0][2]
        def toId = matcher[0][3].toLong()
        def propsRaw = matcher[0][4].trim()

        def propsMap = [:]

        if (propsRaw) {
            propsRaw = propsRaw.replaceAll(/^\{|\}$/, '')
            if (propsRaw) {
                propsRaw.split(',').each { kv ->
                    def kvParts = kv.split(':')
                    if (kvParts.size() == 2) {
                        def k = kvParts[0].trim()
                        def v = kvParts[1].trim()
                        if (v ==~ /^\d+$/) v = v.toLong()
                        else if (v ==~ /^\d+\.\d+$/) v = v.toDouble()
                        propsMap[k] = v
                    }
                }
            }
        }

        def fromV = g.V().has('node_id', fromId).tryNext().orElse(null)
        def toV = g.V().has('node_id', toId).tryNext().orElse(null)

        if (fromV && toV) {
            def existing = fromV.edges(Direction.OUT, relType).find { it.inVertex().value('node_id') == toId }
            if (!existing) {
                def e = fromV.addEdge(relType, toV)
                propsMap.each { k, v -> e.property(k, v) }
            }
        } else {
            println "Skipping edge $idx: from ${fromId} or to ${toId} vertex not found"
        }
    } catch (Exception e) {
        println "Error on edge line $idx: ${e.message}"
    }
}
binding.graph.tx().commit()
println "Relationships Imported."

// Verify
println "Vertices: " + binding.g.V().count().next()
println "Edges: " + binding.g.E().count().next()

System.exit(0)
