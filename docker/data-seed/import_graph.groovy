import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal

// Define connection
conn = DriverRemoteConnection.using('/opt/bitnami/janusgraph/conf/remote.yaml')
g = traversal().withRemote(conn)

try {
    // Execute the drop and read
    g.V().drop().iterate()
    g.io("/tmp/graph_snapshot.json").read().iterate()
} finally {
    // Ensure connection is closed
    conn.close()
}
