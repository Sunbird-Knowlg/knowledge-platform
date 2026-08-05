ARG DHI_IMAGE_DEV=dhi.io/eclipse-temurin:11.0.31.11-jdk-debian13-dev
ARG DHI_IMAGE_RUNTIME=dhi.io/eclipse-temurin:11.0.31-debian13

FROM --platform=linux/x86_64 dhi.io/busybox:1.38.0-alpine3.24 as shell

# ---- prep stage: unzip/curl need apt + root, so use the dev variant here ----
FROM --platform=linux/x86_64 ${DHI_IMAGE_DEV} AS build

RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y unzip curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /home/sunbird
COPY ./search-api/search-service/target/search-service-1.0-SNAPSHOT-dist.zip .
RUN unzip search-service-1.0-SNAPSHOT-dist.zip -d . && rm search-service-1.0-SNAPSHOT-dist.zip
COPY ./schemas /home/sunbird/search-service-1.0-SNAPSHOT/schemas

# ---- runtime stage
FROM --platform=linux/x86_64 ${DHI_IMAGE_RUNTIME}
COPY --from=shell /lib/ld-musl-x86_64.so.1 /lib/ld-musl-x86_64.so.1
COPY --from=shell /bin/busybox /bin/sh

ARG CSP=azure
ENV cloud_storage_type=${CSP}
ENV cloud_storage_auth_type=OIDC

USER nonroot
COPY --from=build --chown=nonroot:nonroot /home/sunbird /home/sunbird
WORKDIR /home/sunbird/

CMD java -XX:+PrintFlagsFinal $JAVA_OPTIONS -cp '/home/sunbird/search-service-1.0-SNAPSHOT/lib/*' -Dconfig.file=/home/sunbird/search-service-1.0-SNAPSHOT/config/application.conf -Dlogger.file=/home/sunbird/search-service-1.0-SNAPSHOT/config/logback.xml play.core.server.ProdServerStart /home/sunbird/search-service-1.0-SNAPSHOT
