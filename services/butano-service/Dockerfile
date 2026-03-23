FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S impilo && adduser -S impilo -G impilo

WORKDIR /app
COPY target/butano-service-*.jar app.jar

RUN mkdir -p /tmp/butano-lucene && chown impilo:impilo /app /tmp/butano-lucene
USER impilo

EXPOSE 8090

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
