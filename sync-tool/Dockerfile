FROM eclipse-temurin:11-jre-alpine

# Security: run as a non-root user (docker:S6471) -- this was the only
# Dockerfile in the repo without a USER directive.
RUN adduser -D -u 1001 sunbird

WORKDIR /app
RUN chown -R sunbird:sunbird /app

COPY --chown=sunbird:sunbird target/sync-tool-1.0-SNAPSHOT.jar /app/sync-tool.jar

USER sunbird

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTIONS -Dconfig.file=/app/config/application.conf -jar /app/sync-tool.jar \"$@\"", "--"]
