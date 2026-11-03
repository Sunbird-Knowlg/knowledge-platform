// schema_init.groovy — Initialize JanusGraph schema & indexes on server startup.
// Loaded via ScriptFileGremlinPlugin in gremlin-server.yaml.
import org.janusgraph.core.schema.SchemaAction
import org.janusgraph.core.schema.SchemaStatus
import org.janusgraph.core.Cardinality
import org.janusgraph.core.Multiplicity
import org.apache.tinkerpop.gremlin.structure.Vertex
import java.time.temporal.ChronoUnit

jg = graph
mgmt = jg.openManagement()

println "--- STARTING SCHEMA INITIALIZATION ---"

// Helper closures
makeProperty = { key, dataType, cardinality ->
    if (!mgmt.containsPropertyKey(key))
        mgmt.makePropertyKey(key).dataType(dataType).cardinality(cardinality).make()
}

makeVLabel = { name ->
    if (!mgmt.containsVertexLabel(name))
        mgmt.makeVertexLabel(name).make()
}

makeELabel = { name, multiplicity ->
    if (!mgmt.containsEdgeLabel(name))
        mgmt.makeEdgeLabel(name).multiplicity(multiplicity).make()
}

// --- Property Keys ---
makeProperty('IL_UNIQUE_ID',           String.class,  Cardinality.SINGLE)
makeProperty('IL_FUNC_OBJECT_TYPE',    String.class,  Cardinality.SINGLE)
makeProperty('IL_SYS_NODE_TYPE',       String.class,  Cardinality.SINGLE)
makeProperty('IL_TAG_NAME',            String.class,  Cardinality.SINGLE)
makeProperty('identifier',             String.class,  Cardinality.SINGLE)
makeProperty('code',                   String.class,  Cardinality.SINGLE)
makeProperty('name',                   String.class,  Cardinality.SINGLE)
makeProperty('status',                 String.class,  Cardinality.SINGLE)
makeProperty('channel',                String.class,  Cardinality.SINGLE)
makeProperty('framework',              String.class,  Cardinality.SINGLE)
makeProperty('mimeType',               String.class,  Cardinality.SINGLE)
makeProperty('contentType',            String.class,  Cardinality.SINGLE)
makeProperty('pkgVersion',             Double.class,  Cardinality.SINGLE)
makeProperty('versionKey',             String.class,  Cardinality.SINGLE)
makeProperty('visibility',             String.class,  Cardinality.SINGLE)
makeProperty('childNodes',             String.class,  Cardinality.LIST)
makeProperty('depth',                  Integer.class, Cardinality.SINGLE)
makeProperty('index',                  Integer.class, Cardinality.SINGLE)
makeProperty('description',            String.class,  Cardinality.SINGLE)
makeProperty('createdBy',              String.class,  Cardinality.SINGLE)
makeProperty('createdOn',              String.class,  Cardinality.SINGLE)
makeProperty('lastUpdatedOn',          String.class,  Cardinality.SINGLE)
makeProperty('lastStatusChangedOn',    String.class,  Cardinality.SINGLE)
makeProperty('portalOwner',            String.class,  Cardinality.SINGLE)
makeProperty('downloadUrl',            String.class,  Cardinality.SINGLE)
makeProperty('artifactUrl',            String.class,  Cardinality.SINGLE)
makeProperty('appId',                  String.class,  Cardinality.SINGLE)
makeProperty('consumerId',             String.class,  Cardinality.SINGLE)
makeProperty('mediaType',              String.class,  Cardinality.SINGLE)
makeProperty('objectType',             String.class,  Cardinality.SINGLE)
makeProperty('primaryCategory',        String.class,  Cardinality.SINGLE)
makeProperty('resourceType',           String.class,  Cardinality.SINGLE)
makeProperty('compatibilityLevel',     Integer.class, Cardinality.SINGLE)
makeProperty('osId',                   String.class,  Cardinality.SINGLE)
makeProperty('language',               String.class,  Cardinality.LIST)

// --- Vertex Labels ---
['Content', 'Framework', 'Category', 'CategoryInstance', 'Term',
 'ObjectCategory', 'ObjectCategoryDefinition', 'Channel', 'License',
 'Concept', 'Asset', 'Domain', 'Dimension'].each { makeVLabel(it) }

// --- Edge Labels ---
makeELabel('hasSequenceMember', Multiplicity.MULTI)
makeELabel('associatedTo',      Multiplicity.MULTI)

// --- Composite Indexes ---
indexDefs = [
    [name: 'byUniqueId',            key: 'IL_UNIQUE_ID',           unique: true],
    [name: 'byCode',                key: 'code',                   unique: false],
    [name: 'byIdentifier',          key: 'identifier',             unique: false],
    [name: 'byChannel',             key: 'channel',                unique: false],
    [name: 'byFramework',           key: 'framework',              unique: false],
    [name: 'byMimeType',            key: 'mimeType',               unique: false],
    [name: 'byContentType',         key: 'contentType',            unique: false],
    [name: 'byVisibility',          key: 'visibility',             unique: false],
    [name: 'byObjectTypeAndStatus', key: 'IL_FUNC_OBJECT_TYPE',    unique: false],
    [name: 'byNodeType',            key: 'IL_SYS_NODE_TYPE',       unique: false],
    // Keys SearchAsyncOperations.INDEXED_KEYS pushes into native search queries; keep in
    // sync with that list and with the helm chart schema_init.groovy.
    [name: 'byStatus',              key: 'status',                 unique: false],
    [name: 'byCreatedBy',           key: 'createdBy',              unique: false],
    [name: 'byObjectType',          key: 'objectType',             unique: false],
    [name: 'byPrimaryCategory',     key: 'primaryCategory',        unique: false],
    [name: 'byResourceType',        key: 'resourceType',           unique: false],
    [name: 'byMediaType',           key: 'mediaType',              unique: false],
    [name: 'byName',                key: 'name',                   unique: false],
    [name: 'byVersionKey',          key: 'versionKey',             unique: false],
]

indexDefs.each { def idx ->
    if (!mgmt.containsGraphIndex(idx.name)) {
        def builder = mgmt.buildIndex(idx.name, Vertex.class)
            .addKey(mgmt.getPropertyKey(idx.key))
        if (idx.unique) builder.unique()
        builder.buildCompositeIndex()
    }
}

mgmt.commit()

// --- Index Lifecycle: INSTALLED → REGISTERED → ENABLED ---
indexNames = indexDefs.collect { it.name }

// Register
mgmtR = jg.openManagement()
indexNames.each { name ->
    def idx = mgmtR.getGraphIndex(name)
    if (idx.getIndexStatus(idx.getFieldKeys()[0]) == SchemaStatus.INSTALLED)
        mgmtR.updateIndex(idx, SchemaAction.REGISTER_INDEX).get()
}
mgmtR.commit()

// Wait for REGISTERED
indexNames.each { name ->
    org.janusgraph.graphdb.database.management.ManagementSystem
        .awaitGraphIndexStatus(jg, name)
        .status(SchemaStatus.REGISTERED, SchemaStatus.ENABLED)
        .timeout(5L, ChronoUnit.MINUTES)
        .call()
}

// Enable
mgmtE = jg.openManagement()
indexNames.each { name ->
    def idx = mgmtE.getGraphIndex(name)
    if (idx.getIndexStatus(idx.getFieldKeys()[0]) == SchemaStatus.REGISTERED)
        mgmtE.updateIndex(idx, SchemaAction.ENABLE_INDEX).get()
}
mgmtE.commit()

// Wait for ENABLED
indexNames.each { name ->
    org.janusgraph.graphdb.database.management.ManagementSystem
        .awaitGraphIndexStatus(jg, name)
        .status(SchemaStatus.ENABLED)
        .timeout(5L, ChronoUnit.MINUTES)
        .call()
}

println "--- SCHEMA INITIALIZATION COMPLETE ---"
