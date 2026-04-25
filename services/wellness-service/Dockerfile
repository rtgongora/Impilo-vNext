## Runtime image — build the JAR on the host first (same pattern as experience-bff):
##   cd services && mvn -B -pl wellness-service -am -DskipTests package
## Or use tools/dev/up.ps1 / up.sh which run Maven before compose.
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -r impilo && useradd -r -g impilo impilo

WORKDIR /app
COPY target/wellness-service-*.jar app.jar

RUN chown -R impilo:impilo /app
USER impilo

EXPOSE 8161
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
