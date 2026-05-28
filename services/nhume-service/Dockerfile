## Runtime image — build the JAR on the host first:
##   cd services && mvn -B -pl nhume-service -am -DskipTests package
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -r impilo && useradd -r -g impilo impilo

WORKDIR /app
ARG JAR_REFRESH=1
COPY target/nhume-service-*.jar app.jar

RUN chown -R impilo:impilo /app
USER impilo

EXPOSE 8210
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
