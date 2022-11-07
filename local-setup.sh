#!/bin/bash

mkdir -p ~/sunbird-dbs/neo4j ~/sunbird-dbs/cassandra ~/sunbird-dbs/redis ~/sunbird-dbs/es ~/sunbird-dbs/kafka
export sunbird_dbs_path=~/sunbird-dbs

docker compose up
