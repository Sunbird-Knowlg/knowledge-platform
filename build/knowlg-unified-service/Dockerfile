# Use JRE instead of JDK for smaller runtime image
FROM --platform=linux/x86_64 eclipse-temurin:11.0.20.1_1-jre-focal

# Install only essential packages and clean up in single layer
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends unzip curl \
    && adduser --uid 1001 --home /home/sunbird/ --disabled-login sunbird \
    && mkdir -p /home/sunbird \
    && chown -R sunbird:sunbird /home/sunbird \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

USER sunbird

# Copy and extract distribution in single layer
COPY ./knowlg-unified-service/target/knowlg-unified-service-1.0-SNAPSHOT-dist.zip /home/sunbird/
RUN unzip -q /home/sunbird/knowlg-unified-service-1.0-SNAPSHOT-dist.zip -d /home/sunbird/ \
    && rm /home/sunbird/knowlg-unified-service-1.0-SNAPSHOT-dist.zip

# Copy schemas
COPY --chown=sunbird ./schemas /home/sunbird/knowlg-unified-service-1.0-SNAPSHOT/schemas
COPY --chown=sunbird ./inquiry-api-service/test_schema /home/sunbird/knowlg-unified-service-1.0-SNAPSHOT/test_schema

WORKDIR /home/sunbird/

# Use optimized JVM flags for containers
CMD java -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    $JAVA_OPTIONS \
    -cp '/home/sunbird/knowlg-unified-service-1.0-SNAPSHOT/lib/*' \
    -Dconfig.file=/home/sunbird/knowlg-unified-service-1.0-SNAPSHOT/config/application.conf \
    -Dlogger.file=/home/sunbird/knowlg-unified-service-1.0-SNAPSHOT/config/logback.xml \
    play.core.server.ProdServerStart /home/sunbird/knowlg-unified-service-1.0-SNAPSHOT
