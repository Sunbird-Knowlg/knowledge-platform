// schema_init.groovy
// Purpose: Initialize JanusGraph Schema & Indexes BEFORE data load.
// Usage: docker exec sunbird_janusgraph ./bin/gremlin.sh -e /tmp/schema_init.groovy

import org.janusgraph.core.JanusGraphFactory
import org.janusgraph.core.schema.SchemaAction
import org.janusgraph.core.schema.SchemaStatus
import org.janusgraph.core.Cardinality
import org.janusgraph.core.Multiplicity
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.Direction

// 1. Connect to Graph
// Uses standard container configuration (assuming /opt/janusgraph/conf/janusgraph-cql-server.properties or similar is default)
// If running from inside container with Environment variables set:
jg = JanusGraphFactory.open('conf/janusgraph-cql.properties')
mgmt = jg.openManagement()

println "--- STARTING SCHEMA INITIALIZATION ---"

// 2. Define Property Keys
// Helper closure to create property if missing
makeProperty = { key, dataType, cardinality ->
    if (!mgmt.containsPropertyKey(key)) {
        println "Creating Property: $key"
        mgmt.makePropertyKey(key).dataType(dataType).cardinality(cardinality).make()
    }
}

makeProperty('IL_UNIQUE_ID', String.class, Cardinality.SINGLE)
makeProperty('IL_FUNC_OBJECT_TYPE', String.class, Cardinality.SINGLE) // ObjectType
makeProperty('IL_SYS_NODE_TYPE', String.class, Cardinality.SINGLE)    // NodeType
makeProperty('IL_TAG_NAME', String.class, Cardinality.SINGLE) 

makeProperty('identifier', String.class, Cardinality.SINGLE)
makeProperty('code', String.class, Cardinality.SINGLE)
makeProperty('name', String.class, Cardinality.SINGLE)
makeProperty('status', String.class, Cardinality.SINGLE)
makeProperty('channel', String.class, Cardinality.SINGLE)
makeProperty('framework', String.class, Cardinality.SINGLE)
makeProperty('mimeType', String.class, Cardinality.SINGLE)
makeProperty('contentType', String.class, Cardinality.SINGLE)
makeProperty('pkgVersion', Double.class, Cardinality.SINGLE)
makeProperty('versionKey', String.class, Cardinality.SINGLE)
makeProperty('visibility', String.class, Cardinality.SINGLE)
makeProperty('childNodes', String.class, Cardinality.LIST) // Array/List in data
makeProperty('depth', Integer.class, Cardinality.SINGLE)
makeProperty('index', Integer.class, Cardinality.SINGLE)
makeProperty('description', String.class, Cardinality.SINGLE)
makeProperty('createdBy', String.class, Cardinality.SINGLE)
makeProperty('createdOn', String.class, Cardinality.SINGLE)
makeProperty('lastUpdatedOn', String.class, Cardinality.SINGLE)
makeProperty('lastStatusChangedOn', String.class, Cardinality.SINGLE)
makeProperty('portalOwner', String.class, Cardinality.SINGLE)
makeProperty('downloadUrl', String.class, Cardinality.SINGLE)
makeProperty('artifactUrl', String.class, Cardinality.SINGLE)
makeProperty('appId', String.class, Cardinality.SINGLE)
makeProperty('consumerId', String.class, Cardinality.SINGLE)
makeProperty('mediaType', String.class, Cardinality.SINGLE)
makeProperty('compatibilityLevel', Integer.class, Cardinality.SINGLE)
makeProperty('osId', String.class, Cardinality.SINGLE)
makeProperty('language', String.class, Cardinality.LIST)

// ... (Add other common properties as needed)


// 3. Define Vertex Labels
makeVLabel = { name ->
    if (!mgmt.containsVertexLabel(name)) {
        println "Creating VertexLabel: $name"
        mgmt.makeVertexLabel(name).make()
    }
}

makeVLabel('Content')
makeVLabel('Framework')
makeVLabel('Category')
makeVLabel('CategoryInstance')
makeVLabel('Term')
makeVLabel('ObjectCategory')
makeVLabel('ObjectCategoryDefinition')
makeVLabel('Channel')
makeVLabel('License')
makeVLabel('Concept')
makeVLabel('Asset')
makeVLabel('Domain')
makeVLabel('Dimension')

// 4. Define Edge Labels
makeELabel = { name, multiplicity ->
    if (!mgmt.containsEdgeLabel(name)) {
        println "Creating EdgeLabel: $name"
        mgmt.makeEdgeLabel(name).multiplicity(multiplicity).make()
    }
}

makeELabel('hasSequenceMember', Multiplicity.MULTI)
makeELabel('associatedTo', Multiplicity.MULTI)

// 5. Define Indexes (CRITICAL: Index-First Strategy)
// Helper for Composite Index
makeCompositeIndex = { name, keyName, unique ->
    if (!mgmt.containsGraphIndex(name)) {
        println "Creating Composite Index: $name (Unique: $unique)"
        def builder = mgmt.buildIndex(name, Vertex.class)
        // Handle index creation
        def key = mgmt.getPropertyKey(keyName)
        if (key) {
           builder.addKey(key)
           if (unique) builder.unique()
           builder.buildCompositeIndex()
           println "Index $name CREATED."
        } else {
            println "ERROR: Property key $keyName missing for index $name"
        }
    } else {
        println "Index $name already exists."
    }
}

// 5a. Unique Indexes
makeCompositeIndex('byUniqueId', 'IL_UNIQUE_ID', true)

// 5b. Non-Unique Indexes (Explicitly forcing 'byCode' to be non-unique)
makeCompositeIndex('byCode', 'code', false)
makeCompositeIndex('byIdentifier', 'identifier', false)
makeCompositeIndex('byChannel', 'channel', false)
makeCompositeIndex('byFramework', 'framework', false)
makeCompositeIndex('byMimeType', 'mimeType', false)
makeCompositeIndex('byContentType', 'contentType', false)
makeCompositeIndex('byVisibility', 'visibility', false)
makeCompositeIndex('byObjectTypeAndStatus', 'IL_FUNC_OBJECT_TYPE', false) // Note: Simplified to single key for base
makeCompositeIndex('byNodeType', 'IL_SYS_NODE_TYPE', false)

// 6. Commit Changes
println "Committing Transaction..."
mgmt.commit()

// 7. Verify & Wait for REGISTERED status
println "Waiting for Index Registration..."
waitIndex = { indexName ->
    try {
        org.janusgraph.graphdb.database.management.ManagementSystem.awaitGraphIndexStatus(jg, indexName).status(SchemaStatus.REGISTERED).call()
        println "Index $indexName is REGISTERED/ENABLED."
    } catch (Exception e) {
        println "Warning: Index $indexName status check failed or timed out: ${e.message}"
    }
}

waitIndex('byUniqueId')
waitIndex('byCode')
waitIndex('byIdentifier')

println "--- SCHEMA INITIALIZATION COMPLETE ---"
System.exit(0)
