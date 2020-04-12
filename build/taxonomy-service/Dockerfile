FROM openjdk:8-jre-alpine
RUN apk update \
    && apk add  unzip \
    && apk add curl \
    && adduser -u 1001 -h /home/sunbird/ -D sunbird \
    && mkdir -p /home/sunbird
RUN chown -R sunbird:sunbird /home/sunbird
USER sunbird
COPY ./taxonomy-api/taxonomy-service/target/taxonomy-service-1.0-SNAPSHOT-dist.zip /home/sunbird/
RUN unzip /home/sunbird/taxonomy-service-1.0-SNAPSHOT-dist.zip -d /home/sunbird/
RUN rm /home/sunbird/taxonomy-service-1.0-SNAPSHOT-dist.zip
COPY --chown=sunbird ./schemas /home/sunbird/taxonomy-service-1.0-SNAPSHOT/schemas
WORKDIR /home/sunbird/
CMD java -XX:+PrintFlagsFinal $JAVA_OPTIONS -cp '/home/sunbird/taxonomy-service-1.0-SNAPSHOT/lib/*' -Dconfig.file=/home/sunbird/taxonomy-service-1.0-SNAPSHOT/config/application.conf -Dlogger.file=/home/sunbird/taxonomy-service-1.0-SNAPSHOT/config/logback.xml play.core.server.ProdServerStart /home/sunbird/taxonomy-service-1.0-SNAPSHOT
