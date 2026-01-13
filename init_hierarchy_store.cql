CREATE KEYSPACE IF NOT EXISTS dev_hierarchy_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};

CREATE TABLE IF NOT EXISTS dev_hierarchy_store.framework_hierarchy (identifier text, hierarchy text, PRIMARY KEY (identifier));
