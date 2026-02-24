FROM sunbird/openjdk-java11-alpine:latest
RUN apk update \
    && apk add  unzip \
    && apk add curl \
    && adduser -u 1001 -h /home/sunbird/ -D sunbird \
    && mkdir -p /home/sunbird
RUN chown -R sunbird:sunbird /home/sunbird

# CSP selection: pass --build-arg CSP=<azure|aws|gcp|oci> at image build time (default: azure).
# The Maven build must have been run with the matching profile (-Pazure / -Paws / -Pgcp / -Poci)
# so that exactly one CSP runtime jar is present in lib/; Java ServiceLoader picks it up automatically.
# cloud_storage_auth_type defaults to OIDC (Kubernetes Workload Identity).
# Override via K8s ConfigMap or `docker run -e` for local ACCESS_KEY-based development.
ARG CSP=azure
ENV cloud_storage_type=${CSP}
ENV cloud_storage_auth_type=OIDC

USER sunbird
COPY ./assessment-api/assessment-service/target/assessment-service-1.0-SNAPSHOT-dist.zip /home/sunbird/
RUN unzip /home/sunbird/assessment-service-1.0-SNAPSHOT-dist.zip -d /home/sunbird/
RUN rm /home/sunbird/assessment-service-1.0-SNAPSHOT-dist.zip
COPY --chown=sunbird ./schemas /home/sunbird/assessment-service-1.0-SNAPSHOT/schemas
WORKDIR /home/sunbird/
CMD java -XX:+PrintFlagsFinal $JAVA_OPTIONS -cp '/home/sunbird/assessment-service-1.0-SNAPSHOT/lib/*' -Dconfig.file=/home/sunbird/assessment-service-1.0-SNAPSHOT/config/application.conf  -Dlogger.file=/home/sunbird/assessment-service-1.0-SNAPSHOT/config/logback.xml play.core.server.ProdServerStart /home/sunbird/assessment-service-1.0-SNAPSHOT
