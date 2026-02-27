FROM --platform=linux/x86_64 eclipse-temurin:11.0.20.1_1-jdk-focal
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y unzip curl \
    && adduser --uid 1001 --home /home/sunbird/ --disabled-login sunbird \
    && mkdir -p /home/sunbird \
    && chown -R sunbird:sunbird /home/sunbird \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# CSP selection: pass --build-arg CSP=<azure|aws|gcp|oci> at image build time (default: azure).
# The Maven build must have been run with the matching profile (-Pazure / -Paws / -Pgcp / -Poci)
# so that exactly one CSP runtime jar is present in lib/; Java ServiceLoader picks it up automatically.
# cloud_storage_auth_type defaults to OIDC (Kubernetes Workload Identity).
# Override via K8s ConfigMap or `docker run -e` for local ACCESS_KEY-based development.
ARG CSP=azure
ENV cloud_storage_type=${CSP}
ENV cloud_storage_auth_type=OIDC

USER sunbird
COPY ./content-api/content-service/target/content-service-1.0-SNAPSHOT-dist.zip /home/sunbird/
RUN unzip /home/sunbird/content-service-1.0-SNAPSHOT-dist.zip -d /home/sunbird/
RUN rm /home/sunbird/content-service-1.0-SNAPSHOT-dist.zip
COPY --chown=sunbird ./schemas /home/sunbird/content-service-1.0-SNAPSHOT/schemas
WORKDIR /home/sunbird/
CMD java -XX:+PrintFlagsFinal $JAVA_OPTIONS -cp '/home/sunbird/content-service-1.0-SNAPSHOT/lib/*' -Dconfig.file=/home/sunbird/content-service-1.0-SNAPSHOT/config/application.conf -Dlogger.file=/home/sunbird/content-service-1.0-SNAPSHOT/config/logback.xml play.core.server.ProdServerStart /home/sunbird/content-service-1.0-SNAPSHOT
