FROM --platform=linux/x86_64 eclipse-temurin:11.0.20.1_1-jdk-focal
RUN apt-get update \
    && apt-get install -y unzip curl \
    && adduser --uid 1001 --home /home/sunbird/ --disabled-login sunbird \
    && mkdir -p /home/sunbird \
    && chown -R sunbird:sunbird /home/sunbird
USER sunbird
COPY ./content-api/content-service/target/content-service-1.0-SNAPSHOT-dist.zip /home/sunbird/
RUN unzip /home/sunbird/content-service-1.0-SNAPSHOT-dist.zip -d /home/sunbird/
RUN rm /home/sunbird/content-service-1.0-SNAPSHOT-dist.zip
COPY --chown=sunbird ./schemas /home/sunbird/content-service-1.0-SNAPSHOT/schemas
WORKDIR /home/sunbird/
CMD java -XX:+PrintFlagsFinal $JAVA_OPTIONS -cp '/home/sunbird/content-service-1.0-SNAPSHOT/lib/*' -Dconfig.file=/home/sunbird/content-service-1.0-SNAPSHOT/config/application.conf -Dlogger.file=/home/sunbird/content-service-1.0-SNAPSHOT/config/logback.xml play.core.server.ProdServerStart /home/sunbird/content-service-1.0-SNAPSHOT
