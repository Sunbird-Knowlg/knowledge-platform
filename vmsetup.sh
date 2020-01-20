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
# This should go to the test cases - End
find ./ -type f -name "logback.xml" -print0 | xargs -0 sed -i -e 's/\/data\/logs/logs/g'
find ./ -type f -name "application.conf" -print0 | xargs -0 sed -i -e 's/\/data\//~\//g'
find ./ -type f -name "*.java" -print0 | xargs -0 sed -i -e 's/\/data\//~\//g'
XML_REPORT_PATHS=`find /home/circleci/project  -iname jacoco.xml | awk 'BEGIN { RS = "" ; FS = "\n"; OFS = ","}{$1=$1; print $0}'`
mvn scoverage:report
mvn verify sonar:sonar -Dsonar.projectKey=project-sunbird_knowledge-platform -Dsonar.organization=project-sunbird -Dsonar.host.url=https://sonarcloud.io -Dsonar.scala.coverage.reportPaths=/home/circleci/project/learning-api/hierarchy-manager/target/scoverage.xml,/home/circleci/project/learning-api/content-service/target/scoverage.xml,/home/circleci/project/target/scoverage.xml,/home/circleci/project/ontology-engine/graph-engine_2.11/target/scoverage.xml,/home/circleci/project/ontology-engine/parseq/target/scoverage.xml -Dsonar.coverage.jacoco.xmlReportPaths=${XML_REPORT_PATHS}