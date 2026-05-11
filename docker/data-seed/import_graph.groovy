g.V().drop().iterate()
g.io("/tmp/graph_snapshot.json").with(IO.graphson).read().iterate()
