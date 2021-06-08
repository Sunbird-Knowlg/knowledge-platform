#!/bin/bash
sudo apt update
sudo apt install redis-server -y
curl -O https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-6.3.2.deb
sudo dpkg -i elasticsearch-6.3.2.deb
sudo service elasticsearch start
sudo service elasticsearch status
# This should go to the test cases - Start
redis-cli SADD edge_license "CC BY-NC-SA 4.0"
redis-cli SADD edge_license "CC BY-NC 4.0"
redis-cli SADD edge_license "CC BY-SA 4.0"
redis-cli SADD edge_license "CC BY 4.0"
redis-cli SADD edge_license "Standard Youtube License"
redis-cli SADD cat_NCFboard "Other"
redis-cli SADD cat_NCFboard "State (Tamil Nadu)"
redis-cli SADD cat_NCFboard "State (Rajasthan)"
redis-cli SADD cat_NCFboard "CBSE"
redis-cli SADD cat_NCFboard "State (Uttar Pradesh)"
redis-cli SADD cat_NCFboard "ICSE"
redis-cli SADD cat_NCFboard "State (Andhra Pradesh)"
redis-cli SADD cat_NCFboard "State (Maharashtra)"
# This should go to the test cases - End
find ./ -type f -name "logback.xml" -print0 | xargs -0 sed -i -e 's/\/data\/logs/logs/g'
find ./ -type f -name "application.conf" -print0 | xargs -0 sed -i -e 's/\/data\//~\//g'
find ./ -type f -name "*.java" -print0 | xargs -0 sed -i -e 's/\/data\//~\//g'

mvn scoverage:report
JAVA_REPORT_PATHS=`find /home/circleci/project  -iname jacoco.xml | awk 'BEGIN { RS = "" ; FS = "\n"; OFS = ","}{$1=$1; print $0}'`
mvn verify sonar:sonar -Dsonar.projectKey=project-sunbird_knowledge-platform -Dsonar.organization=project-sunbird -Dsonar.host.url=https://sonarcloud.io -Dsonar.coverage.exclusions=**/CustomProblemHandler.java -Dsonar.scala.coverage.reportPaths=/home/circleci/project/content-api/hierarchy-manager/target/scoverage.xml,/home/circleci/project/content-api/content-service/target/scoverage.xml,/home/circleci/project/target/scoverage.xml,/home/circleci/project/ontology-engine/graph-engine_2.11/target/scoverage.xml,/home/circleci/project/ontology-engine/parseq/target/scoverage.xml -Dsonar.coverage.jacoco.xmlReportPaths=${JAVA_REPORT_PATHS}
