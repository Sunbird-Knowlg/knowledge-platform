## Loading seed data to neo4j database
1. Download [neo4j graph.db file](../master-data/master-data/neo4j-graph.db.zip) and extract it.
2. Before loading seed data, stop neo4j container.
```shell
docker stop sunbird_neo4j
```   
3. Replace the graph.db file in local path: `$sunbird_dbs_path/neo4j/data/databases` with the extracted graph.db file.
4. Start neo4j container.
```shell
docker start sunbird_neo4j
```  

## Loading seed data to cassandra database
1. Download [cassandra backup file](../master-data/master-data/cassandra_backup.tar.gz) and extract it.
2. stop cassandra container.
```shell
docker stop sunbird_cassandra
```    
3. Place the extracted `cassandra_backup` folder in local path: `$sunbird_dbs_path/cassandra/backups`.
4. Start cassandra container.
```shell
docker start sunbird_cassandra
```  
5. Start cassandra shell by executing following command and type `cqlsh` to start cassandra cypher shell.
```shell
docker exec -it sunbird_cassandra sh
```
6. Load database schema by executing below command in cypher shell.
```shell
cqlsh
source '/mnt/backups/cassandra_backup/db_schema.cql';
```
7. Press `ctrl + z` to get out of cypher shell and stay in cassandra container shell.
8. Now, run the following commands to load the data into earlier created tables.
```shell
sstableloader -d 127.0.0.1 /mnt/backups/cassandra_backup/data/dev_category_store/category_definition_data-fc4c9690c2bc11eb91450f9648eeaf0a
sstableloader -d 127.0.0.1 /mnt/backups/cassandra_backup/data/dev_hierarchy_store/content_hierarchy-ea2f4a20c2bc11eb91450f9648eeaf0a
sstableloader -d 127.0.0.1 /mnt/backups/cassandra_backup/data/sunbirddev_dialcode_store/system_config-1970bbe0c2c011eb91450f9648eeaf0a
```
