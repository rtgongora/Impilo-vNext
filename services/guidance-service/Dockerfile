FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -r impilo && useradd -r -g impilo impilo

WORKDIR /app
COPY target/guidance-service-*.jar app.jar

RUN chown -R impilo:impilo /app
USER impilo

EXPOSE 8260
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
